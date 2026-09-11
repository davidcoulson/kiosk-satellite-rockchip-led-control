// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/** Owns all hardware access, effects and root helper lifetime for this plugin. */
public final class RockchipLedPlugin implements KioskPlugin {
    // The original five, still driven by LedMath.frame (unchanged), plus the
    // 19 ported from davidcoulson/kiosk-satellite's led_effects.dart — see
    // LedEffectRegistry for the name -> LedRichEffect mapping.
    private static final String[] EFFECTS={
        "None","Pulse","Blink","Rainbow","Candle","Random",
        "Sunrise/Sunset","Moonlight Glow","Lightning Storm","Wake-Up Alarm","Candle Flicker",
        "Fairytwinkle","Fireworks Burst","Beacon Pulse","Heartbeat Pulse","Soft Glow","Rolling Fog (Pronounced)",
        "Pacifica (Calm Lagoon)","Pacifica (Storm)","Pacifica (Deep Current)",
        "Aurora (Solar Storm)","Aurora (Pastel Dream)","Aurora (Red Sky)",
        "Bubbles","Disco Sparkle",
    };
    private final AtomicBoolean alive=new AtomicBoolean();
    private PluginHost host;
    private ScheduledExecutorService worker;
    private volatile LedTransport transport;
    private Map<String,Object> settings=new HashMap<>();
    private long effectStart;
    private long testStart=-1;
    private boolean loaded;
    private int[] lastFrame;
    private boolean lastPower;
    // Non-null while the selected effect is one of the 19 ported ones —
    // LedMath.frame handles the original five, which need no per-instance
    // state. A fresh instance every time configure() runs (same as every
    // other per-effect timing field here), so restarting an effect always
    // starts from clean state.
    private LedRichEffect richEffect;

    public void start(PluginHost host, Map<String,Object> settings) {
        this.host=host;
        alive.set(true);
        worker=Executors.newSingleThreadScheduledExecutor(r -> {Thread t=new Thread(r,"rockchip-led");t.setDaemon(true);return t;});
        configure(settings);
        worker.scheduleWithFixedDelay(() -> safe(this::frame),0,60,TimeUnit.MILLISECONDS);
    }
    public void configure(Map<String,Object> values) {
        Map<String,Object> copy=new HashMap<>(values);
        submit(() -> {
            boolean recheck=transport==null || !Objects.equals(settings.get("simulation"),copy.get("simulation")) || !Objects.equals(settings.get("allowRoot"),copy.get("allowRoot"));
            settings=copy; effectStart=System.nanoTime();testStart=-1;lastFrame=null;
            Supplier<LedRichEffect> supplier=LedEffectRegistry.RICH_EFFECTS.get(copy.get("effect"));
            richEffect=supplier!=null?supplier.get():null;
            if (recheck) detect();
            frame();publish();
        });
    }
    public void execute(String command,Map<String,Object> args) {
        submit(() -> {
            switch(command) {
                case "detect":detect();frame();publish();break;
                case "test":
                    if (transport==null) {status("No LED access. Check hardware access first.",true);return;}
                    testStart=System.nanoTime();lastFrame=null;break;
                case "off":settings.put("power",false);testStart=-1;frame();publish();host.saveSettings(settings);break;
                default:throw new IllegalArgumentException("Unknown LED command");
            }
        });
    }
    public void onEvent(String event,Map<String,Object> payload) {
        if (!"light.panel".equals(event)) return;
        Map<String,Object> values=new HashMap<>(payload);
        submit(() -> {
            if (transport==null) return;
            if (values.get("on") instanceof Boolean) settings.put("power",values.get("on"));
            if (values.containsKey("brightness")) settings.put("brightness",Math.round(unit(values.get("brightness"))*100));
            int[] rgb=LedMath.color((String)settings.get("color"));
            String[] keys={"red","green","blue"};
            for(int i=0;i<3;i++) if(values.containsKey(keys[i])) rgb[i]=(int)Math.round(unit(values.get(keys[i]))*255);
            settings.put("color",String.format(Locale.ROOT,"#%02X%02X%02X",rgb[0],rgb[1],rgb[2]));
            if(values.containsKey("effect")) {
                String effect=String.valueOf(values.get("effect"));
                if(!Arrays.asList(EFFECTS).contains(effect)) throw new IllegalArgumentException("Unknown LED effect");
                settings.put("effect",effect);
            }
            testStart=-1;effectStart=System.nanoTime();lastFrame=null;
            frame();publish();host.saveSettings(settings);
        });
    }
    private static double unit(Object value) {
        if(!(value instanceof Number)) throw new IllegalArgumentException("Expected a light number");
        double number=((Number)value).doubleValue();
        if(!Double.isFinite(number)||number<0||number>1)throw new IllegalArgumentException("Light value is outside 0 to 1");
        return number;
    }
    private void detect() throws Exception {
        closeTransport();host.removeLight("panel");
        if(Boolean.TRUE.equals(settings.get("simulation"))) transport=new LedTransport.Simulation();
        else {
            if(!loaded){NativeLed.load(host.nativeLibraryPath("rockchip_led"));loaded=true;}
            LedTransport direct=new LedTransport.Direct();
            int result=direct.probe();
            if(result==0)transport=direct;
            else if(result==-2 || result==-19) {status("This panel has no accessible /dev/ledjni device. Rockchip chipset alone does not imply LED support.",true);return;}
            else if(Boolean.TRUE.equals(settings.get("allowRoot"))) {
                status("Waiting for root access. Approve Kiosk Satellite in your root manager if prompted.",false);
                LedTransport.Root root=new LedTransport.Root(host.packagePath(),host.nativeLibraryPath("rockchip_led"));
                try {result=root.probe();if(result!=0)throw new IllegalStateException(error(result));transport=root;}
                catch(Exception e){root.close();throw e;}
            } else {status(error(result)+" Enable root fallback only if this panel is rooted.",true);return;}
        }
        lastFrame=null;lastPower=true;status(transport.label(),false);
    }
    private void frame() throws Exception {
        LedTransport device=transport;
        if(device==null || !alive.get() || settings.isEmpty())return;
        long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-effectStart);
        boolean power=Boolean.TRUE.equals(settings.get("power"));
        int[] baseColor=LedMath.color((String)settings.get("color"));
        int[] rgb=richEffect!=null
            ?richEffect.tick(elapsed,baseColor)
            :LedMath.frame((String)settings.get("effect"),elapsed,((Number)settings.get("period")).doubleValue(),baseColor);
        if(testStart>=0) {
            long test=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-testStart);
            if(test<3000){power=true;rgb=new int[3];rgb[(int)(test/1000)]=255;}
            else{testStart=-1;lastFrame=null;}
        }
        if(!power){if(lastPower){check(device.off());lastPower=false;}return;}
        int limit=((Number)settings.get("maxDrive")).intValue();double brightness=((Number)settings.get("brightness")).doubleValue();
        int[] drive={LedMath.scale(rgb[0],brightness,limit),LedMath.scale(rgb[1],brightness,limit),LedMath.scale(rgb[2],brightness,limit)};
        if(!lastPower||!Arrays.equals(drive,lastFrame)){check(device.write(drive[0],drive[1],drive[2]));lastFrame=drive;lastPower=true;}
    }
    private void publish(){
        if(transport==null||!Boolean.TRUE.equals(settings.get("exposeLight"))){host.removeLight("panel");return;}
        int[] rgb=LedMath.color((String)settings.get("color"));Map<String,Object> state=new HashMap<>();
        state.put("on",settings.get("power"));state.put("brightness",((Number)settings.get("brightness")).doubleValue()/100);
        state.put("red",rgb[0]/255.0);state.put("green",rgb[1]/255.0);state.put("blue",rgb[2]/255.0);state.put("effect",settings.get("effect"));
        host.publishLight("panel",Boolean.TRUE.equals(settings.get("simulation"))?"Rockchip LED (simulation)":"Rockchip LED",EFFECTS,state);
    }
    private void status(String text,boolean error){if(alive.get())host.status(text,error);}
    private static String error(int code){return "LED access failed (errno "+(-code)+"). "+(code==-13||code==-1?"The device permissions or SELinux policy denied access.":"Check the panel driver and its /dev/ledjni node.");}
    private static void check(int code){if(code!=0)throw new IllegalStateException(error(code));}
    private interface Task{void run() throws Exception;}
    private void safe(Task task){
        if(!alive.get())return;
        try{task.run();}catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Throwable e){closeTransport();if(alive.get()){host.removeLight("panel");status(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),true);}}
    }
    private void submit(Task task){if(alive.get())worker.execute(() -> safe(task));}
    private void closeTransport(){LedTransport old=transport;transport=null;if(old!=null){try{old.off();}catch(Exception ignored){}old.close();}}
    public void stop() throws Exception {
        alive.set(false);worker.shutdownNow();
        if(!worker.awaitTermination(1000,TimeUnit.MILLISECONDS)) {
            LedTransport old=transport;if(old instanceof LedTransport.Root)old.close();
            throw new IllegalStateException("LED worker did not stop");
        }
        closeTransport();
    }
}
