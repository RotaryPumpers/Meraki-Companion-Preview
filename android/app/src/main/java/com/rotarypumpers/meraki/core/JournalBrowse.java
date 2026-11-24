package com.rotarypumpers.meraki.core;
import org.json.*;import java.time.*;import java.time.format.*;import java.util.*;
/** Presentation-only queries. Does not modify the saved journal. */
public final class JournalBrowse {
 public static final String[] FILTERS={"All shots","Favourites","Complete","Needs review","Short events"};
 public static String localTime(String raw){return localTime(raw,ZoneId.systemDefault());}
 public static String localTime(String raw,ZoneId zone){try{return Instant.parse(raw).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a",Locale.getDefault()));}catch(Exception e){return "Date unavailable";}}
 public static boolean review(JSONObject s){JSONArray gaps=s.optJSONArray("sampling_gaps");return !s.optBoolean("complete")||"gapped".equals(s.optString("capture_quality"))||(gaps!=null&&gaps.length()>0);}
 public static String quality(JSONObject s){
  JSONArray gaps=s.optJSONArray("sampling_gaps");
  if("gapped".equals(s.optString("capture_quality"))||(gaps!=null&&gaps.length()>0))return "Data gap";
  return s.optBoolean("complete")?"Complete":"Incomplete";
 }
 public static String name(JSONObject s){JSONObject r=s.optJSONObject("recipe");String n=r==null?"":r.optString("name").trim();return n.isEmpty()?"Unassigned shot":n;}
 private static Instant when(JSONObject s){try{return Instant.parse(s.optString("start_utc"));}catch(Exception e){return null;}}
 public static JSONArray select(JSONArray source,String query,int filter,boolean oldest){
  String q=query.trim().toLowerCase(Locale.ROOT);List<JSONObject> rows=new ArrayList<>();
  for(int i=0;i<source.length();i++){JSONObject s=source.optJSONObject(i);if(s==null)continue;
   if(filter==4?!ShotInsights.shortEvent(s):ShotInsights.shortEvent(s))continue;
   if(filter==1&&!s.optBoolean("favourite")||filter==2&&review(s)||filter==3&&!review(s))continue;
   String hay=name(s)+" "+s.optString("notes")+" "+localTime(s.optString("start_utc"))+" "+s.optString("start_utc");
   if(!hay.toLowerCase(Locale.ROOT).contains(q))continue;rows.add(s);
  }
  rows.sort((a,b)->{Instant x=when(a),y=when(b);if(x==null)return y==null?0:1;if(y==null)return -1;return oldest?x.compareTo(y):y.compareTo(x);});
  JSONArray result=new JSONArray();for(JSONObject s:rows)result.put(s);return result;
 }
 public static String doseAndRatio(JSONObject s){
  JSONObject g=s.optJSONObject("grind_workflow");double dose=g==null?Double.NaN:g.optDouble("settled_dose_g",Double.NaN);boolean measured=Double.isFinite(dose)&&dose>0;
  if(!measured)dose=s.optDouble("dose_target_g",Double.NaN);
  double yield=s.optDouble("settled_yield_g",Double.NaN);
  if(!Double.isFinite(dose)||dose<=0)return "Dose unavailable";
  String result=String.format(Locale.US,measured?"Preceding grind %.1f g":"Target dose %.1f g",dose);
  if(Double.isFinite(yield)&&yield>=0)result+=String.format(Locale.US," · Ratio 1:%.2f",yield/dose);
  return result;
 }
}
