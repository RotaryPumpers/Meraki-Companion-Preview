package com.rotarypumpers.meraki;
import org.json.*;
import java.util.Locale;

/** Recipe comparisons use saved recipe snapshots, not the current library recipe. */
final class ShotAssessment {
 static String summary(JSONObject shot) {
  JSONObject recipe=shot.optJSONObject("recipe");
  if(recipe==null)return "";
  String yield=metric(shot,recipe,"settled_yield_g","yield_g","yield_max_g","Yield","g",false);
  String time=recipe.has("shot_time_window")?(com.rotarypumpers.meraki.core.ShotTimeWindow.enabled(recipe)?com.rotarypumpers.meraki.core.ShotTimeWindow.result(shot):""):metric(shot,recipe,"duration_estimate_s","time_s","time_max_s","Time","s",true);
  return yield+(time.isEmpty()?"":" · "+time);
 }
 private static String metric(JSONObject shot,JSONObject recipe,String actual,String min,String max,String title,String unit,boolean timing){
  double lo=recipe.optDouble(min,Double.NaN),hi=recipe.optDouble(max,lo),value=shot.optDouble(actual,Double.NaN);
  if(!Double.isFinite(lo)||!Double.isFinite(hi)||lo<=0||hi<lo)return "";
  if(!Double.isFinite(value)||value<0)return title+" unavailable";
  if(timing){
   if(!shot.optBoolean("finished",shot.optBoolean("complete")))return "Time incomplete";
   JSONArray bounds=shot.optJSONArray("duration_bounds_s");
   if(bounds!=null){
    double a=bounds.optDouble(0,Double.NaN),b=bounds.optDouble(1,Double.NaN);
    if(bounds.length()!=2||!Double.isFinite(a)||!Double.isFinite(b)||a<0||b<a)return "Time uncertain";
    if(a<lo&&b>=lo||a<=hi&&b>hi)return "Time near target · timing uncertain";
   }else if("gapped".equals(shot.optString("capture_quality")))return "Time uncertain";
  }
  double rounded=Math.round(value*10.0)/10.0;
  if(rounded<lo)return String.format(Locale.US,timing?"%.1f s fast":"Yield %.1f g below",lo-rounded);
  if(rounded>hi)return String.format(Locale.US,timing?"%.1f s slow":"Yield %.1f g above",rounded-hi);
  return title+" on target";
 }
 static String result(JSONObject shot){
  double y=shot.optDouble("settled_yield_g",Double.NaN),t=shot.optDouble("duration_estimate_s",Double.NaN);
  return (Double.isFinite(y)?String.format(Locale.US,"%.1f g",y):"Yield unavailable")+" · "+(Double.isFinite(t)?String.format(Locale.US,"%.1f s",t):"Time unavailable");
 }
 static String difference(JSONObject shot,JSONObject reference){
  StringBuilder s=new StringBuilder();
  for(String key:new String[]{"settled_yield_g","duration_estimate_s"}){
   double a=shot.optDouble(key,Double.NaN),b=reference.optDouble(key,Double.NaN);
   if(!Double.isFinite(a)||!Double.isFinite(b))continue;
   if(s.length()>0)s.append(" · ");
   s.append(String.format(Locale.US,key.equals("settled_yield_g")?"Yield %+.1f g":"Time %+.1f s (estimate)",a-b));
  }
  return s.length()==0?"Final results unavailable":s.toString();
 }
 static JSONArray candidates(JSONArray shots,JSONObject current){
  JSONArray result=new JSONArray();String id=current.optString("start_utc");
  for(int favourite=1;favourite>=0;favourite--)for(int i=shots.length()-1;i>=0;i--){
   JSONObject s=shots.optJSONObject(i);if(s==null||id.equals(s.optString("start_utc"))||s.optBoolean("favourite")!=(favourite==1))continue;
   try{new ReplayTimeline(s);result.put(s);}catch(IllegalArgumentException ignored){}
  }
  return result;
 }
}
