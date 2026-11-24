package com.rotarypumpers.meraki.core;
import org.json.JSONObject;
/** Only brew yield is representable; temperature and grinder dose never enter this API. */
public final class YieldRequest {
 private YieldRequest(){}
 public static int grams(double value){
  if(!Double.isFinite(value)||value!=Math.rint(value)||value<10||value>99)throw new IllegalArgumentException("Machine yield must be a whole number from10 to99g");
  return (int)value;
 }
 private static JSONObject base(String type,String id,String epoch)throws Exception{
  if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||epoch==null||!epoch.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid yield request identity");
  return new JSONObject().put("type",type).put("id",id).put("epoch",epoch);
 }
 public static JSONObject set(String id,String epoch,int value)throws Exception{return base("set_yield",id,epoch).put("grams",grams(value));}
 public static JSONObject query(String id,String epoch)throws Exception{return base("get_result",id,epoch);}
}
