package com.rotarypumpers.meraki;
import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.time.*;import com.rotarypumpers.meraki.core.*;

final class JsonStore {
    private boolean loadFailed; private final File directory;JsonStore(File directory){this.directory=directory;}
    synchronized JSONArray load()throws Exception{try{JSONArray result=read();if(!new File(directory,"journal-v2.json.gz").exists()&&new File(directory,"journal.json").exists())save(result);return result;}catch(Exception e){loadFailed=true;throw e;}}
    private JSONArray read()throws Exception{File packed=new File(directory,"journal-v2.json.gz"),legacy=new File(directory,"journal.json");if(!packed.exists())return legacy.exists()?new JSONArray(new String(java.nio.file.Files.readAllBytes(legacy.toPath()),StandardCharsets.UTF_8)):new JSONArray();try(java.util.zip.GZIPInputStream in=new java.util.zip.GZIPInputStream(new FileInputStream(packed));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>128*1024*1024)throw new IOException("Journal exceeds supported size");out.write(b,0,n);}JSONObject root=new JSONObject(out.toString("UTF-8"));if(root.getInt("version")!=2)throw new IOException("Unsupported journal version");return root.getJSONArray("shots");}}
    synchronized void save(JSONArray data)throws Exception{if(loadFailed)throw new IOException("Journal could not be read; writes blocked to preserve it");File tmp=new File(directory,"journal-v2.tmp"),dest=new File(directory,"journal-v2.json.gz");byte[] bytes=new JSONObject().put("version",2).put("shots",data).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>128*1024*1024)throw new IOException("Journal exceeds supported size; export a backup");try(FileOutputStream out=new FileOutputStream(tmp);java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(out)){gzip.write(bytes);gzip.finish();out.getFD().sync();}java.nio.file.Files.move(tmp.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
    synchronized void append(String filename,JSONObject data)throws Exception{try(FileOutputStream out=new FileOutputStream(new File(directory,filename),true)){out.write((data+"\n").getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
    // Bound diagnostic sessions only; journal, recipes and captures are never pruned here.
    synchronized void appendSession(String filename,JSONObject data)throws Exception{
        File active=new File(directory,filename);
        if(active.length()>24L*1024*1024)throw new IOException("Raw recording limit reached; reconnect for a new session");
        append(filename,data);
    }
    synchronized void pruneSessions(String active){
        File[] files=directory.listFiles(f->f.isFile()&&!java.nio.file.Files.isSymbolicLink(f.toPath())&&f.getName().matches("session-[0-9]+\\.jsonl"));
        if(files==null)return;java.util.Arrays.sort(files,java.util.Comparator.comparing(File::getName));long total=0;for(File f:files)total+=f.length();
        for(File f:files)if(total>75L*1024*1024&&!f.getName().equals(active)){long size=f.length();if(f.delete())total-=size;}
    }
    synchronized JSONArray delete(JSONArray current,String id)throws Exception{
        JSONArray next=new JSONArray();for(int i=0;i<current.length();i++){JSONObject shot=current.getJSONObject(i);if(!id.equals(shot.optString("start_utc")))next.put(shot);}save(next);return next;
    }
    static JSONObject sample(Sample s)throws JSONException{
        JSONObject p=new JSONObject().put("enabled",s.preferences[0]).put("pressure_choice",Sample.u32(s.preferences,1)).put("duration_setting_s",Sample.u32(s.preferences,5));
        return new JSONObject().put("water_low",WaterLowState.low(s.registers)).put("received_utc",s.utc).put("uptime",s.uptime).put("registers",new JSONArray(s.registers)).put("preferences",p).put("brew_active",s.active?1:0).put("weight_g",s.weight).put("flow_raw",s.registers[52]).put("p1_w",s.p1).put("grind_setting",s.registers[37]).put("dose_target_g",s.registers[4]/10.0).put("yield_target_g",s.registers[23]/10.0).put("brew_target_c",s.registers[11]/10.0+5);
    }
    static JSONObject shot(Recorder.Shot s)throws JSONException{
        Sample first=s.observedStart==null?s.samples.get(0):s.observedStart;JSONObject o=new JSONObject().put("sampling_gap_limit_s",s.gapLimit).put("start_utc",first.utc).put("complete",s.complete).put("finished",s.finished).put("capture_quality",s.gaps.isEmpty()?(s.complete?"complete":"incomplete"):"gapped").put("completion_reason",s.reason).put("source","Android foreground live observation").put("dose_target_g",first.registers[4]/10.0).put("yield_target_g",first.registers[23]/10.0).put("brew_target_c",first.registers[11]/10.0+5).put("grind_setting",first.registers[37]).put("pre_infusion",sample(first).getJSONObject("preferences")).put("start_bounds",new JSONArray(new double[]{s.startLo,s.startHi}));
        if(s.grind!=null)o.put("grind_workflow",grind(s.grind));
        if(s.preTareSamples>0)o.put("auto_tare_wait_s",s.autoTareWait).put("pre_tare_samples_omitted",s.preTareSamples);
        JSONArray gaps=new JSONArray();for(double[] gap:s.gaps)gaps.put(new JSONObject().put("before_uptime",gap[0]).put("after_uptime",gap[1]).put("duration_s",gap[1]-gap[0]));o.put("sampling_gaps",gaps);
        if(s.duration()!=null)o.put("duration_estimate_s",s.duration()).put("stop_bounds",new JSONArray(new double[]{s.stopLo,s.stopHi})).put("duration_bounds_s",new JSONArray(new double[]{s.stopLo-s.startHi,s.stopHi-s.startLo}));if(s.yield!=null)o.put("settled_yield_g",s.yield);
        JSONArray samples=new JSONArray();for(Sample x:s.samples)samples.put(sample(x));return o.put("samples",samples);
    }
    static JSONObject grind(GrindWorkflow.Result g)throws JSONException{
        JSONObject o=new JSONObject().put("start_utc",g.utc).put("source","motor_command_and_grinder_scale").put("association","preceding_grind_within_300_seconds").put("start_bounds",new JSONArray(new double[]{g.startLo,g.startHi})).put("stop_bounds",new JSONArray(new double[]{g.stopLo,g.stopHi})).put("duration_estimate_s",g.duration()).put("duration_bounds_s",new JSONArray(new double[]{Math.max(0,g.stopLo-g.startHi),g.stopHi-g.startLo})).put("dose_setting_g",g.doseSetting).put("grind_setting",g.grind).put("cup_movement_detected",g.cupMoved);
        if(g.settledDose!=null)o.put("settled_dose_g",g.settledDose);return o;
    }
    static JSONObject validateImported(JSONObject o)throws JSONException{
        Instant.parse(o.getString("start_utc"));JSONArray samples=o.getJSONArray("samples");boolean thingSummary="MerakiThing".equals(o.optString("source"))&&o.optString("thing_id").matches("pi-[0-9a-f]{64}");
        if((samples.length()==0&&!thingSummary)||samples.length()>2000)throw new JSONException("Invalid sample count");
        double prev=-1;for(int i=0;i<samples.length();i++){JSONObject x=samples.getJSONObject(i);double t=x.getDouble("uptime");if(!Double.isFinite(t)||t<=prev)throw new JSONException("Invalid sample order");prev=t;if(thingSummary&&!x.has("registers")){if(!Double.isFinite(x.getDouble("weight_g")))throw new JSONException("Invalid cup weight");continue;}JSONArray r=x.getJSONArray("registers");if((r.length()!=72&&r.length()!=112))throw new JSONException("Invalid registers");for(int j=0;j<r.length();j++){double raw=r.getDouble(j);if(raw<0||raw>65535||raw!=Math.floor(raw))throw new JSONException("Invalid register value");}}
        if(o.optBoolean("complete")&&(!o.has("settled_yield_g")||!o.has("duration_estimate_s")))throw new JSONException("Completed record has no final result");
        if(o.has("bag"))try{com.rotarypumpers.meraki.core.BagTracking.validateSnapshot(o.getJSONObject("bag"));}catch(Exception e){throw new JSONException("Invalid saved bag snapshot");}
        if(o.optString("notes").length()>8000)throw new JSONException("Notes exceed 8000 characters");
        JSONObject capture=o.optJSONObject("screen_capture");if(capture!=null&&capture.has("png_base64")){
            String encoded=capture.getString("png_base64");if(encoded.length()>1500000)throw new JSONException("Screenshot too large");
            byte[] bytes;try{bytes=java.util.Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new JSONException("Invalid screenshot encoding");}
            if(bytes.length<24||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71||java.nio.ByteBuffer.wrap(bytes,16,8).getInt()!=480||java.nio.ByteBuffer.wrap(bytes,20,4).getInt()!=480)throw new JSONException("Expected 480 x 480 PNG screenshot");
        }
        return new JSONObject(o.toString());
    }
}
