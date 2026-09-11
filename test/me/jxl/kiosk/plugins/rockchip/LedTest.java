// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.rockchip;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import me.jxl.kiosk.plugins.PluginHost;

public final class LedTest {
    private static final class Host implements PluginHost {
        volatile Map<String,Object> state;
        volatile Map<String,Object> saved;
        volatile String status="";
        final AtomicInteger changes=new AtomicInteger();
        public void showWindow(String a,String b,String c){throw new AssertionError("No overlay required");}
        public void hideWindow(){}
        public void log(String value){}
        public void status(String value,boolean error){status=value;changes.incrementAndGet();}
        public void saveSettings(Map<String,Object> value){saved=new HashMap<>(value);}
        public void publishLight(String key,String name,String[] effects,Map<String,Object> value){
            assert key.equals("panel");assert name.contains("simulation");assert effects.length==25;
            state=new HashMap<>(value);changes.incrementAndGet();
        }
        public void removeLight(String key){state=null;changes.incrementAndGet();}
    }
    private static Map<String,Object> defaults(){
        Map<String,Object> s=new HashMap<>();
        s.put("power",false);s.put("color","#00A8FF");s.put("brightness",50);s.put("effect","None");s.put("period",2000);
        s.put("exposeLight",true);s.put("maxDrive",15);s.put("allowRoot",false);s.put("simulation",true);return s;
    }
    private interface Condition{boolean done();}
    private static void waitFor(Condition condition)throws Exception{
        long end=System.nanoTime()+2_000_000_000L;
        while(!condition.done() && System.nanoTime()<end)Thread.sleep(10);
        if(!condition.done())throw new AssertionError("Timed out");
    }
    public static void main(String[] args)throws Exception{
        for(int channel=0;channel<=255;channel++){
            assert LedMath.scale(channel,100,15)>=0 && LedMath.scale(channel,100,15)<=15;
            assert LedMath.scale(channel,0,15)==0;
        }
        assert LedMath.scale(255,100,15)==15;
        assert LedMath.scale(255,100,255)==255;
        assert Arrays.equals(LedMath.color("#12aB00"),new int[]{18,171,0});
        for(String effect:new String[]{"None","Pulse","Blink","Rainbow","Candle","Random"}){
            for(long elapsed=0;elapsed<10000;elapsed+=61){
                for(int c:LedMath.frame(effect,elapsed,2000,new int[]{255,190,80}))assert c>=0&&c<=255;
            }
        }
        // The 19 ported rich effects: fresh instance per name (matching how
        // RockchipLedPlugin.configure() creates one), ticked across enough
        // elapsed time to cover every state-machine transition and a couple
        // of full periods for the slower ones (Sunrise/Sunset at 300s,
        // Wake-Up Alarm's one-shot 600s ramp), checking every channel stays
        // in range throughout.
        for(Map.Entry<String,java.util.function.Supplier<LedRichEffect>> entry:LedEffectRegistry.RICH_EFFECTS.entrySet()){
            LedRichEffect rich=entry.getValue().get();
            for(long elapsed=0;elapsed<650_000;elapsed+=61){
                for(int c:rich.tick(elapsed,new int[]{255,190,80})){
                    assert c>=0&&c<=255:entry.getKey()+" produced out-of-range channel "+c+" at "+elapsed+"ms";
                }
            }
        }
        assert LedTransport.Root.quote("a'b").equals("'a'\\''b'");
        Host host=new Host();RockchipLedPlugin plugin=new RockchipLedPlugin();
        plugin.start(host,defaults());waitFor(() -> host.state!=null);
        assert host.status.startsWith("Simulation");assert host.state.get("on").equals(false);
        Map<String,Object> command=new HashMap<>();command.put("on",true);command.put("brightness",0.3);
        command.put("red",1.0);command.put("green",0.0);command.put("blue",0.0);command.put("effect","Pulse");
        plugin.onEvent("light.panel",command);waitFor(() -> host.saved!=null);
        assert host.saved.get("color").equals("#FF0000");assert ((Number)host.saved.get("brightness")).intValue()==30;
        assert host.saved.get("effect").equals("Pulse");assert host.state.get("on").equals(true);
        host.saved=null;plugin.execute("off",Collections.emptyMap());waitFor(() -> host.saved!=null);
        assert host.saved.get("power").equals(false);
        Map<String,Object> settings=defaults();settings.put("exposeLight",false);plugin.configure(settings);waitFor(() -> host.state==null);
        plugin.stop();int count=host.changes.get();Thread.sleep(150);assert host.changes.get()==count;
        for(Thread thread:Thread.getAllStackTraces().keySet())assert !thread.isAlive()||!thread.getName().equals("rockchip-led");
        System.out.println("PASS: channel limits, color math, 25 effects, root argument quoting, simulation, HA commands, settings persistence, entity removal and clean worker shutdown.");
    }
}
