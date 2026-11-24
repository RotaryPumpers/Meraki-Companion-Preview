package com.rotarypumpers.meraki.core;
import org.json.*;import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** Visualizer-compatible shot envelope with Meraki provenance. */
public final class VisualizerExport {
 public static final int MAX_BYTES=10*1024*1024;
 public static JSONObject build(JSONObject shot,boolean notes,String remoteId)throws Exception{
  if(!shot.optBoolean("complete")||!shot.optBoolean("finished")||!"complete".equals(shot.optString("capture_quality"))||shot.optBoolean("demo"))throw new IllegalArgumentException("Choose a complete recording with no data gaps");
  JSONArray gaps=shot.optJSONArray("sampling_gaps");if(gaps!=null&&gaps.length()!=0)throw new IllegalArgumentException("This recording has data gaps");
  double duration=finite(shot,"duration_estimate_s");if(duration<15||duration>3600)throw new IllegalArgumentException("Choose a full extraction between 15 seconds and one hour");
  JSONArray src=shot.getJSONArray("samples");if(src.length()<3||src.length()>2000)throw new IllegalArgumentException("A recorded weight curve is required");
  double gap=shot.optDouble("sampling_gap_limit_s",.75);if(!Double.isFinite(gap)||gap<=0||gap>3.5)throw new IllegalArgumentException("Unsupported sampling cadence");
  double first=finite(src.getJSONObject(0),"uptime");
  if(shot.has("auto_tare_wait_s")){double wait=finite(shot,"auto_tare_wait_s"),start=shot.getJSONArray("start_bounds").getDouble(1);if(!Double.isFinite(start)||wait<=0||wait>Recorder.AUTO_TARE_LIMIT_S||Math.abs(first-start-wait)>.001)throw new IllegalArgumentException("Invalid automatic tare timing");first=start;}
  double prev=first-1;double[] ts=new double[src.length()],ws=new double[src.length()];
  for(int i=0;i<src.length();i++){JSONObject s=src.getJSONObject(i);double t=finite(s,"uptime"),w=finite(s,"weight_g");if(t<=prev||(i>0&&t-prev>gap+.001)||w< -2||w>500)throw new IllegalArgumentException("Weight curve has invalid timing or values");ts[i]=t-first;ws[i]=w;prev=t;}
  if(ts[ts.length-1]>7200||ts[ts.length-1]<duration-.751)throw new IllegalArgumentException("Weight curve does not cover the extraction");
  Instant started=Instant.parse(shot.getString("start_utc"));JSONArray weight=new JSONArray(),flow=new JSONArray();double[] rates=CupFlow.rates(src,gap);
  for(int i=0;i<ts.length;i++){
   long ms=Math.round(ts[i]*1000);String stamp=String.format(Locale.US,"%02d:%02d:%02d.%03d",ms/3600000,(ms/60000)%60,(ms/1000)%60,ms%1000);
   weight.put(new JSONObject().put("timestamp",stamp).put("brew_time",String.format(Locale.US,"%.3f",ts[i])).put("actual_weight",ws[i]).put("old_weight",i==0?ws[i]:ws[i-1]).put("actual_smoothed_weight",ws[i]).put("old_smoothed_weight",i==0?ws[i]:ws[i-1]).put("not_mutated_weight",ws[i]));
   double v=rates[i];if(Double.isFinite(v))flow.put(new JSONObject().put("timestamp",stamp).put("brew_time",String.format(Locale.US,"%.3f",ts[i])).put("flow_value",v).put("smoothed_weight",ws[i]));
  }
  JSONObject recipe=shot.optJSONObject("recipe");String name=recipe==null?"Meraki extraction":recipe.optString("name","Meraki extraction");
  JSONObject brew=new JSONObject().put("config",new JSONObject().put("unix_timestamp",started.getEpochSecond())).put("brew_time",Math.round(duration*10.0)/10.0).put("brew_quantity",finite(shot,"settled_yield_g")).put("brew_beverage_quantity",finite(shot,"settled_yield_g")).put("brew_quantity_type","GR").put("brew_beverage_quantity_type","GR").put("grind_size",String.valueOf(shot.optInt("grind_setting")));
  JSONObject grind=shot.optJSONObject("grind_workflow");if(grind!=null&&grind.has("settled_dose_g"))brew.put("grind_weight",finite(grind,"settled_dose_g"));
  if(notes&&!shot.optString("notes").isEmpty())brew.put("note",shot.getString("notes"));
  JSONObject provenance=new JSONObject().put("source","Meraki Companion").put("local_shot_id",shot.getString("start_utc")).put("original_start_utc",started.toString()).put("weight_units","g").put("time_units","s").put("flow_units","g/s").put("flow_source",CupFlow.METHOD).put("graph_clock","relative elapsed timestamps, anchored at 00:00 to avoid midnight wrap").put("pressure_telemetry","not measured; omitted").put("temperature_telemetry","not measured; omitted").put("dose_target_g",shot.opt("dose_target_g")).put("yield_target_g",shot.opt("yield_target_g")).put("brew_target_c",shot.opt("brew_target_c")).put("pre_infusion_settings",shot.optJSONObject("pre_infusion")).put("brew_stop_elapsed_s",(shot.getJSONArray("stop_bounds").getDouble(0)+shot.getJSONArray("stop_bounds").getDouble(1))/2-first).put("graph_includes_final_drips",true).put("duration_is_estimate",true).put("notes_included",notes);
  if(shot.optJSONObject("brand_assignment")!=null)provenance.put("brand_assignment",new JSONObject(shot.getJSONObject("brand_assignment").toString()));
  if(shot.optJSONObject("bag")!=null)provenance.put("bag",new JSONObject(shot.getJSONObject("bag").toString()));
  if(shot.has("auto_tare_wait_s"))provenance.put("auto_tare_wait_s",shot.getDouble("auto_tare_wait_s")).put("pre_tare_samples_omitted",shot.optInt("pre_tare_samples_omitted")).put("auto_tare_note","Graph starts at first observed near-zero tare; pre-tare cup weights omitted, no replacement samples");
  double drip=CupFlow.firstDetectedDrip(shot);provenance.put("first_detected_drip_s",Double.isFinite(drip)?Math.round(drip*10)/10.0:JSONObject.NULL).put("first_drip_method","estimated sustained scale rise >=0.3g in two samples; not a directly sensed drop");
  JSONObject result=new JSONObject().put("application","MERAKI_COMPANION").put("preparation",new JSONObject().put("name",name)).put("mill",new JSONObject().put("name","Meraki integrated grinder")).put("brew",brew).put("brewFlow",new JSONObject().put("weight",weight).put("realtimeFlow",flow).put("waterFlow",new JSONArray()).put("pressureFlow",new JSONArray()).put("temperatureFlow",new JSONArray()).put("weightSecond",new JSONArray()).put("realtimeFlowSecond",new JSONArray()).put("waterDispensed",new JSONArray()).put("waterDispensedFlowSecond",new JSONArray()).put("brewbyweight",new JSONArray())).put("meta",new JSONObject().put("meraki",provenance));
  if(remoteId!=null&&!remoteId.isEmpty()){requireId(remoteId);result.put("visualizerId",remoteId);}
  if(result.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Shot export exceeds Visualizer's 10 MB limit");return result;
 }
 private static double finite(JSONObject o,String key)throws Exception{double v=o.getDouble(key);if(!Double.isFinite(v))throw new IllegalArgumentException("Missing or invalid "+key);return v;}
 public static void requireId(String s){if(s==null||!s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException("Invalid Visualizer shot ID");}
}
