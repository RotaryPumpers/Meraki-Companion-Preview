package com.rotarypumpers.meraki.core;
import org.json.JSONObject;
/** Only temperature is representable; recipe dose/yield never enter this API. */
public final class TemperatureRequest {
 private TemperatureRequest(){}
 public static int celsius(double value){
  if(!Double.isFinite(value)||value!=Math.rint(value)||value<85||value>96)throw new IllegalArgumentException("Machine temperature must be a whole number from85 to96°C");
  return (int)value;
 }
 private static JSONObject base(String type,String id,String epoch)throws Exception{
  if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||epoch==null||!epoch.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid temperature request identity");
  return new JSONObject().put("type",type).put("id",id).put("epoch",epoch);
 }
 public static JSONObject set(String id,String epoch,int value)throws Exception{return base("set_temperature",id,epoch).put("celsius",celsius(value));}
 public static JSONObject query(String id,String epoch)throws Exception{return base("get_result",id,epoch);}
}
