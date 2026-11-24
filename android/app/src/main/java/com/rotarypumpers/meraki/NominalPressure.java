package com.rotarypumpers.meraki;
import org.json.*;
/** Display model only. Never used as sensor data or machine control. */
final class NominalPressure {
 static double at(JSONObject shot,double seconds){
  JSONObject pi=shot==null?null:shot.optJSONObject("pre_infusion");
  if(pi==null||!pi.has("enabled"))return Double.NaN;
  if(pi.optInt("enabled")==1){double duration=pi.optDouble("duration_setting_s",Double.NaN);int pressure=pi.optInt("pressure_choice",-1);if(!Double.isFinite(duration)||duration<0||(pressure!=3&&pressure!=4))return Double.NaN;if(seconds<duration)return pressure;}
  else if(pi.optInt("enabled")!=0)return Double.NaN;
  return 9;
 }
}
