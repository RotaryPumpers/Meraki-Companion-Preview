package com.rotarypumpers.meraki;
import org.json.*;import com.rotarypumpers.meraki.core.*;import java.util.*;import java.time.*;import java.time.format.*;
/** One local, network-free renderer shared by the app and visual regression previews. */
final class ShotDetailPage {
 static String taste(String value){switch(value){case "Too sharp":return "Sour";case "Just right":return "Balanced";case "Too bitter":return "Bitter";case "Sour":case "Balanced":case "Bitter":case "Liked":case "Sharp":case "Harsh/dry":return value;default:return value.length()<=256?value:"";}}
 static JSONArray weights(JSONObject s)throws Exception{JSONArray out=new JSONArray(),rows=s.optJSONArray("samples");double start=ShotInsights.bound(s,"start_bounds"),stop=ShotInsights.bound(s,"stop_bounds");if(rows==null||!Double.isFinite(start))return out;double previous=Double.NaN;
  for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;double t=row.optDouble("uptime"),w=row.optDouble("weight_g");if(t<start)continue;if(Double.isFinite(stop)&&t>stop)break;if(Double.isFinite(previous)&&t-previous>ShotInsights.gapLimit(s))out.put(new JSONArray().put(t-start).put(JSONObject.NULL));out.put(new JSONArray().put(t-start).put(Double.isFinite(w)?w:JSONObject.NULL));previous=t;}
  double w=ShotInsights.weightAt(s,stop);if(Double.isFinite(w))out.put(new JSONArray().put(stop-start).put(w));return out;
 }
 static JSONArray flows(JSONObject s)throws Exception{JSONArray out=new JSONArray();for(double[] p:ShotInsights.curve(s))out.put(new JSONArray().put(p[0]).put(Double.isFinite(p[1])?p[1]:JSONObject.NULL));return out;}
 static JSONObject data(JSONObject s,JSONArray all)throws Exception{
  JSONObject data=new JSONObject();String date;try{date=Instant.parse(s.optString("start_utc")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · h:mm a",Locale.getDefault()));}catch(Exception e){date="Date unavailable";}
  JSONObject grind=s.optJSONObject("grind_workflow");double dose=grind==null?Double.NaN:grind.optDouble("settled_dose_g");boolean measured=Double.isFinite(dose)&&dose>0;if(!measured)dose=s.optDouble("dose_target_g");double yield=s.optDouble("settled_yield_g"),duration=s.optDouble("duration_estimate_s"),stopWeight=ShotInsights.weightAt(s,ShotInsights.bound(s,"stop_bounds"));
  data.put("name",JournalBrowse.name(s)).put("date",date).put("favourite",s.optBoolean("favourite")).put("yield",number(yield,1," g")).put("duration",number(duration,1," s")).put("dose",number(dose,1," g")).put("doseLabel",measured?"Dose":"Target dose").put("ratio",dose>0&&Double.isFinite(yield)?String.format(Locale.US,"1:%.2f",yield/dose):"—").put("stopWeight",number(stopWeight,1," g")).put("average",number(ShotInsights.average(s),2," g/s")).put("rating",s.optInt("rating")).put("notes",s.optString("notes")).put("taste",taste(s.optString("taste"))).put("review",JournalBrowse.review(s)).put("weights",weights(s)).put("flows",flows(s)).put("durationSeconds",Double.isFinite(duration)&&duration>0?duration:1);
  JSONObject recipe=s.optJSONObject("recipe");if(recipe!=null){for(String key:new String[]{"time_s","time_max_s","yield_g","yield_max_g"}){double value=recipe.optDouble(key);if(Double.isFinite(value)&&value>0)data.put(key,value);}}
  data.put("brand",s.optJSONObject("brand_assignment")!=null?s.getJSONObject("brand_assignment").optString("brand"):s.optJSONObject("bag")!=null?s.getJSONObject("bag").optString("brand"):"").put("bagLabel",BagTracking.label(s.optJSONObject("bag"))).put("timeWindow",ShotTimeWindow.result(s));
  double grindSeconds=grind==null?Double.NaN:grind.optDouble("duration_estimate_s");data.put("grindTime",Double.isFinite(grindSeconds)&&grindSeconds>0?"~"+number(grindSeconds,1," s"):"Not recorded");
  data.put("doseContext",measured?"From the grind just before this shot":"Recipe target; grind not recorded");
  List<JSONObject> refs=ShotInsights.references(s,all);refs.sort(Comparator.comparing(x->x.optString("start_utc")));data.put("referenceCount",refs.size());JSONArray other=new JSONArray();double graphDuration=Double.isFinite(duration)&&duration>0?duration:1;
  String status="Favourite a shot to build your reference",range="More favourites needed",mean="—";boolean within=false;double average=ShotInsights.average(s);
  if(!refs.isEmpty()){JSONObject latest=refs.get(refs.size()-1);other=flows(latest);graphDuration=Math.max(graphDuration,latest.optDouble("duration_estimate_s",1));List<Double> values=new ArrayList<>();double sum=0;for(JSONObject ref:refs){double v=ShotInsights.average(ref);values.add(v);sum+=v;}Collections.sort(values);mean=number(sum/values.size(),2," g/s");status="Four favourites establish your usual range";
   if(values.size()>=4){double lo=ShotInsights.percentile(values,.25),hi=ShotInsights.percentile(values,.75);range=String.format(Locale.US,"%.2f–%.2f g/s",lo,hi);if(Double.isFinite(average)){within=average>=lo&&average<=hi;status=within?"Within your usual range":average<lo?"Slower than your usual range":"Faster than your usual range";}else status="Complete data needed for comparison";}
  }
  data.put("referenceFlows",other).put("flowDuration",graphDuration).put("referenceMean",mean).put("referenceRange",range).put("status",status).put("within",within);
  JSONObject recordedPi=s.optJSONObject("pre_infusion");data.put("preInfusionLabel",PreInfusionDisplay.summary(recordedPi)).put("preInfusionSeconds",PreInfusionDisplay.seconds(recordedPi));
  double drip=CupFlow.firstDetectedDrip(s);data.put("firstDrip",Double.isFinite(drip)?"~"+number(drip,1," s"):"Not available");
  data.put("pressureContext","Pump pressure: nominal 9 bar extraction; not measured");

  JSONArray gaps=new JSONArray(),source=s.optJSONArray("sampling_gaps");double start=ShotInsights.bound(s,"start_bounds");if(source!=null&&Double.isFinite(start))for(int i=0;i<source.length();i++){JSONObject gap=source.optJSONObject(i);if(gap!=null)gaps.put(new JSONArray().put(gap.optDouble("before_uptime")-start).put(gap.optDouble("after_uptime")-start));}data.put("gaps",gaps);return data;
 }
 static String number(double n,int decimals,String suffix){return Double.isFinite(n)?String.format(Locale.US,"%."+decimals+"f",n)+suffix:"—";}
 static String render(String template,String font,String serif,String serifBold,JSONObject data){String json=data.toString().replace("<","\\u003c").replace("&","\\u0026").replace("\u2028","\\u2028").replace("\u2029","\\u2029");return template.replace("@@FONT@@",font).replace("@@SERIF@@",serif).replace("@@SERIF_BOLD@@",serifBold).replace("@@DATA@@",json);}
}
