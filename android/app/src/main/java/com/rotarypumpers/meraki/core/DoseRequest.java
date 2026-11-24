package com.rotarypumpers.meraki.core;
import org.json.JSONObject;
/** Only target grind dose is representable; no grind-start command. */
public final class DoseRequest {
 private DoseRequest(){}
 public static int raw(double value){
  if(!Double.isFinite(value)||Math.abs(value*10-Math.rint(value*10))>1e-7||value<1||value>30.9)throw new IllegalArgumentException("Machine dose must be1.0 to30.9g in0.1g steps");
  return (int)Math.rint(value*10);
 }
 private static JSONObject base(String type,String id,String epoch)throws Exception{
  if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||epoch==null||!epoch.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid dose request identity");
  return new JSONObject().put("type",type).put("id",id).put("epoch",epoch);
 }
 public static JSONObject set(String id,String epoch,int value)throws Exception{return base("set_dose",id,epoch).put("dose_raw",raw(value/10.0));}
 public static JSONObject recipe(String id,String epoch,int raw)throws Exception{return set(id,epoch,raw).put("return_home",true);}
 public static JSONObject query(String id,String epoch)throws Exception{return base("get_result",id,epoch);}
}
