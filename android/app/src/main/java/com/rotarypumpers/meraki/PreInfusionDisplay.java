package com.rotarypumpers.meraki;
import org.json.JSONObject;
import java.util.Locale;
/** Recorded preferences only; never a pressure sensor or a measured phase duration. */
final class PreInfusionDisplay {
 static boolean enabled(JSONObject p){return p!=null&&p.optInt("enabled",-1)==1;}
 static double seconds(JSONObject p){double s=p==null?Double.NaN:p.optDouble("duration_setting_s",Double.NaN);return enabled(p)&&Double.isFinite(s)&&s>0?s:0;}
 static String summary(JSONObject p){
  if(p==null||!p.has("enabled"))return "Pre-infusion not recorded";
  if(p.optInt("enabled",-1)==0)return "Pre-infusion off (machine setting)";
  double s=seconds(p);int bar=p.optInt("pressure_choice",-1);
  if(!enabled(p)||s<=0||(bar!=3&&bar!=4))return "Pre-infusion settings unavailable";
  return String.format(Locale.US,"Pre-infusion on · %d bar · %s s (machine settings)",bar,s==Math.rint(s)?Long.toString((long)s):Double.toString(s));
 }
}
