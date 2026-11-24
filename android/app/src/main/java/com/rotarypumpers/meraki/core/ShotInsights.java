package com.rotarypumpers.meraki.core;
import org.json.*;
import java.util.*;
/** Derived cup output, never pump flow or a pressure measurement. */
public final class ShotInsights {
 public static boolean shortEvent(JSONObject s){double t=s.optDouble("duration_estimate_s",Double.NaN);return Double.isFinite(t)&&t>=0&&t<15;}
 public static boolean countedBrew(JSONObject s){double t=s.optDouble("duration_estimate_s",Double.NaN);String kind=s.optString("event_type","brew");return !s.optBoolean("demo")&&kind.equals("brew")&&Double.isFinite(t)&&t>=15&&s.optJSONArray("stop_bounds")!=null;}
 public static double bound(JSONObject s,String key){JSONArray b=s.optJSONArray(key);return b!=null&&b.length()==2?(b.optDouble(0)+b.optDouble(1))/2:Double.NaN;}
 public static double gapLimit(JSONObject s){return s.optDouble("sampling_gap_limit_s",.75);}
 public static double weightAt(JSONObject s,double t){JSONArray a=s.optJSONArray("samples");if(a==null||!Double.isFinite(t))return Double.NaN;
  JSONObject prev=null;for(int i=0;i<a.length();i++){JSONObject row=a.optJSONObject(i);if(row==null)continue;double at=row.optDouble("uptime");if(at==t)return row.optDouble("weight_g");if(at>t){if(prev==null)return Double.NaN;double before=prev.optDouble("uptime"),span=at-before;if(span<=0||span>gapLimit(s))return Double.NaN;return prev.optDouble("weight_g")+(row.optDouble("weight_g")-prev.optDouble("weight_g"))*(t-before)/span;}prev=row;}return Double.NaN;
 }
 public static double average(JSONObject s){if(!countedBrew(s)||JournalBrowse.review(s))return Double.NaN;double stop=bound(s,"stop_bounds"),w=weightAt(s,stop),t=s.optDouble("duration_estimate_s");return Double.isFinite(w)&&w>=0&&t>0?w/t:Double.NaN;}
 /** The chart and upload use the same gap-aware least-squares estimator. */
 public static List<double[]> curve(JSONObject s){List<double[]> out=new ArrayList<>();JSONArray rows=s.optJSONArray("samples");double start=bound(s,"start_bounds"),stop=bound(s,"stop_bounds");if(rows==null||!Double.isFinite(start)||!Double.isFinite(stop))return out;double[] rates=CupFlow.rates(rows,gapLimit(s));for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;double t=row.optDouble("uptime");if(t<start)continue;if(t>stop)break;out.add(new double[]{t-start,rates[i]});}return out;
 }
 public static boolean sameRecipe(JSONObject a,JSONObject b){JSONObject ar=a.optJSONObject("recipe"),br=b.optJSONObject("recipe");if(ar==null||br==null||ar.optString("id").isEmpty()||!ar.optString("id").equals(br.optString("id")))return false;
  for(String k:new String[]{"dose_target_g","yield_target_g","brew_target_c","grind_setting"})if(!a.has(k)||!b.has(k)||Double.compare(a.optDouble(k),b.optDouble(k))!=0)return false;
  JSONObject ap=a.optJSONObject("pre_infusion"),bp=b.optJSONObject("pre_infusion");if(ap==null||bp==null)return false;
  for(String k:new String[]{"enabled","pressure_choice","duration_setting_s"})if(ap.optInt(k,-1)!=bp.optInt(k,-1))return false;return true;
 }
 public static List<JSONObject> references(JSONObject shot,JSONArray all){List<JSONObject> list=new ArrayList<>();for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&!s.optString("start_utc").equals(shot.optString("start_utc"))&&s.optBoolean("favourite")&&sameRecipe(shot,s)&&Double.isFinite(average(s)))list.add(s);}return list;}
 public static double percentile(List<Double> sorted,double fraction){double p=(sorted.size()-1)*fraction;int lo=(int)p,hi=(int)Math.ceil(p);return sorted.get(lo)+(sorted.get(hi)-sorted.get(lo))*(p-lo);}
}
