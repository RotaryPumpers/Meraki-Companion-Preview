package com.rotarypumpers.meraki.core;
import org.json.*;import java.io.IOException;import java.util.Locale;
/** Current machine preferences. This is a configured pressure, not measured pressure. */
public final class PreinfusionState {
 private PreinfusionState(){}
 public static void validate(JSONObject p)throws Exception{
  if(!(p.get("enabled") instanceof Boolean))throw new IOException("Unknown pre-infusion state");
  for(String k:new String[]{"pressure_bar","duration_s","pause_s"}){Object v=p.get(k);if(!(v instanceof Integer)&&!(v instanceof Long))throw new IOException("Invalid pre-infusion setting");}
  int bar=p.getInt("pressure_bar"),sec=p.getInt("duration_s"),pause=p.getInt("pause_s");if((bar!=3&&bar!=4)||sec<1||sec>60||pause<0||pause>60)throw new IOException("Unsupported pre-infusion settings");
 }
 public static boolean sameSettings(JSONObject a,JSONObject b)throws Exception{validate(a);validate(b);return a.getInt("pressure_bar")==b.getInt("pressure_bar")&&a.getInt("duration_s")==b.getInt("duration_s")&&a.getInt("pause_s")==b.getInt("pause_s");}
 public static String summary(JSONObject p)throws Exception{validate(p);return String.format(Locale.US,"%s · %d bar · %d s",p.getBoolean("enabled")?"On":"Off",p.getInt("pressure_bar"),p.getInt("duration_s"));}
 public static JSONObject query(String id,String epoch)throws Exception{return request("get_preinfusion",id,epoch);}
 private static JSONObject request(String type,String id,String epoch)throws Exception{if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||epoch==null||!epoch.matches("[0-9a-f]{32}"))throw new IOException("Invalid pre-infusion request identity");return new JSONObject().put("type",type).put("id",id).put("epoch",epoch);}
 public static JSONObject set(String id,String epoch,boolean enabled,JSONObject expected)throws Exception{validate(expected);JSONObject copy=new JSONObject();for(String key:new String[]{"enabled","pressure_bar","duration_s","pause_s"})copy.put(key,expected.get(key));return request("set_preinfusion",id,epoch).put("preinfusion",new JSONObject().put("enabled",enabled).put("expected",copy));}
 public static JSONObject result(String id,String epoch)throws Exception{return request("get_result",id,epoch);}
}
