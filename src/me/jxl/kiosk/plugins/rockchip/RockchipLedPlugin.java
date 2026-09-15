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
    // LedEffectRegistry for the name -> LedRichEffect mapping. Includes
    // "None" for internal validation (onEvent below, and the plugin's own
    // Settings dropdown) — 25 entries.
    private static final String[] EFFECTS={
        "None","Pulse","Blink","Rainbow","Candle","Random",
        "Sunrise/Sunset","Moonlight Glow","Lightning Storm","Wake-Up Alarm","Candle Flicker",
        "Fairytwinkle","Fireworks Burst","Beacon Pulse","Heartbeat Pulse","Soft Glow","Rolling Fog (Pronounced)",
        "Pacifica (Calm Lagoon)","Pacifica (Storm)","Pacifica (Deep Current)",
        "Aurora (Solar Storm)","Aurora (Pastel Dream)","Aurora (Red Sky)",
        "Bubbles","Disco Sparkle",
    };
    // The list handed to host.publishLight, which caps a registered light at
    // 24 effects (PluginBridge.kt's publishLight: require(effects.size <= 24
    // ...), with no message — surfaces as the unhelpful "Failed requirement."
    // if exceeded). Drops "None": PluginLightState.validate on the host side
    // already treats "None" as valid even when absent from the declared
    // list, so sending it still works — it just won't appear as a pickable
    // option in Home Assistant's effect dropdown, only via this plugin's own
    // Settings screen (which validates against the full EFFECTS above).
    private static final String[] ENTITY_EFFECTS=Arrays.copyOfRange(EFFECTS,1,EFFECTS.length);
    // probe() returns -errno from open("/dev/ledjni"). These two mean the
    // node is not there at all, which is the one failure no amount of
    // retrying, permission fixing or root escalation can clear -- root
    // cannot conjure a device node the kernel never published. EACCES (-13)
    // is deliberately NOT here: that node exists, and the root fallback may
    // still reach it.
    private static final int ENOENT=-2;
    private static final int ENODEV=-19;
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
        requireDevice(settings);
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
    /**
     * Refuse to start at all on a panel this plugin cannot drive.
     *
     * Kiosk Satellite treats a plugin as enabled only when start() returns:
     * PluginBridge.enable writes enabled=true immediately after the call and
     * routes any throw through fail(), which writes enabled=false and keeps
     * the message as the plugin's error. detect() runs on the worker thread
     * instead, so a panel with no LED used to end up with this plugin
     * switched on, re-probing hardware that will never appear and offering
     * settings that can never do anything. Probing here, synchronously and
     * before any state is built, converts that into what it actually is:
     * this plugin cannot be enabled on this panel.
     *
     * Runs before alive/worker are set up, so a throw leaves nothing to
     * unwind.
     *
     * The condition is "cannot open the device", NOT "the node is missing".
     * A truly absent node does report ENOENT to an unprivileged app -- an
     * earlier revision of this comment claimed SELinux masks that as EACCES,
     * which is wrong, and the errno 13 that seemed to prove it came from a
     * stray file a diagnostic session had left at /dev/ledjni.
     *
     * The wider condition earns its place on its own: a node that exists but
     * will not open, on a panel with no root fallback, cannot drive an LED
     * either, and keying on ENOENT alone would enable there and merely
     * complain. So any probe failure refuses, unless something might still
     * rescue it.
     *
     * Three deliberate exemptions.
     *
     * Simulation mode never opens the device, so it stays enableable
     * anywhere -- that is the whole point of it.
     *
     * Root fallback, when armed, defers to detect(): the root helper may
     * well reach a node this process cannot, and asking it here would mean
     * blocking the host's enable call behind a root-manager prompt that can
     * sit unanswered for minutes. detect() already does that wait properly,
     * with a status to explain itself.
     *
     * A native library that will not load leaves us unable to probe at all,
     * which makes the question unanswerable rather than answered no; that
     * also falls through to detect() and reports as it always did.
     *
     * Both escape hatches named in the message are reachable while the
     * plugin is off -- its settings page stays open, saying "Enable this
     * plugin from its entry row to run it" -- so refusing here cannot strand
     * anyone outside the settings that would fix it. Verified on a panel.
     */
    private void requireDevice(Map<String,Object> settings) {
        if(Boolean.TRUE.equals(settings.get("simulation")))return;
        if(Boolean.TRUE.equals(settings.get("allowRoot")))return;
        try {
            if(!loaded){NativeLed.load(host.nativeLibraryPath("rockchip_led"));loaded=true;}
        } catch(Throwable unavailable){return;}
        int result=NativeLed.probe();
        if(result==0)return;
        throw new IllegalStateException(
            (result==ENOENT||result==ENODEV
                ?"This panel has no /dev/ledjni device, so there is no LED for this plugin to control. "
                :error(result)+" ")+
            "A Rockchip chipset alone does not imply LED support. Turn on Root fallback below if this "+
            "panel is rooted, or Simulation mode to enable the plugin without hardware -- both stay "+
            "reachable while the plugin is off.");
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
            // Reachable when the node disappears under a running plugin (a
            // driver unload), or when start() could not probe because the
            // native library would not load. A fresh enable is refused by
            // requireDevice long before this.
            else if(result==ENOENT || result==ENODEV) {status("This panel has no accessible /dev/ledjni device. Rockchip chipset alone does not imply LED support.",true);return;}
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
        host.publishLight("panel",Boolean.TRUE.equals(settings.get("simulation"))?"Rockchip LED (simulation)":"Rockchip LED",ENTITY_EFFECTS,state);
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
