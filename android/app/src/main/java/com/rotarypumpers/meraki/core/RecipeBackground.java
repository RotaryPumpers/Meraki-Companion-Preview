package com.rotarypumpers.meraki.core;
import org.json.*;import java.io.IOException;
/** Record-card presentation only. No machine commands or absolute file/URI paths. */
public final class RecipeBackground {
 public static String mode(JSONObject r){return r==null?"standard":r.optString("record_background","standard");}
 public static void copy(JSONObject from,JSONObject to)throws Exception{
  if(!from.has("record_background")&&!from.has("record_photo"))return;
  String mode=mode(from);if(!mode.equals("standard")&&!mode.equals("home_color")&&!mode.equals("photo"))throw new IOException("Choose a Record background");
  String photo=from.optString("record_photo","");if(!photo.isEmpty()&&!photo.matches("[a-f0-9]{64}"))throw new IOException("Invalid product photo reference");
  if(mode.equals("photo")&&photo.isEmpty())throw new IOException("Choose a product photo first");
  to.put("record_background",mode);if(!photo.isEmpty())to.put("record_photo",photo);
 }
 public static int homeColor(JSONObject r){String color=r==null?"standard":r.optString("home_color","standard");switch(color){case "steel_blue":return 0xff304b5b;case "grey":return 0xff34383d;case "forest_green":return 0xff1d382b;case "dark_purple":return 0xff352a42;default:return 0xff241b12;}}
}
