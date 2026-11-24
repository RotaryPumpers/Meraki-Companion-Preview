package com.rotarypumpers.meraki.core;
import org.json.*;import java.time.*;
/** Called for each saved shot, not when the recorder first connects. */
public final class ShotContext{
 public static void attach(JSONObject shot,JSONObject recipe,JSONObject bag,ZoneId zone)throws Exception{
  if(recipe!=null){JSONObject snapshot=new JSONObject(recipe.toString());snapshot.remove("record_background");snapshot.remove("record_photo");shot.put("recipe",snapshot);}
  if(bag!=null)shot.put("bag",BagTracking.snapshot(bag,Instant.parse(shot.getString("start_utc")),zone));
 }
}
