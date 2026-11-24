package com.rotarypumpers.meraki.core;
import org.json.*;import java.io.IOException;import java.text.Normalizer;
public final class HomeAppearance{
 public static final String[] COLORS={"standard","steel_blue","grey","forest_green","dark_purple"};
 public static final String[] LABELS={"Standard Home","Steel Blue","Grey","Forest Green","Dark Purple"};
 public static int index(String color){for(int i=0;i<COLORS.length;i++)if(COLORS[i].equals(color))return i;return -1;}
 public static JSONObject value(String color,String name)throws Exception{if(index(color)<0)throw new IOException("Choose a Home color");if(color.equals("standard"))return new JSONObject().put("color",color).put("name","");if(name==null||name.trim().isEmpty()||name.length()>24)throw new IOException("Home name must be1–24 characters");for(int i=0;i<name.length();i++)if(name.charAt(i)<32||name.charAt(i)>126)throw new IOException("Use letters, numbers and simple punctuation for the Home name");return new JSONObject().put("color",color).put("name",name);}
 public static String suggest(String name){String n=Normalizer.normalize(name==null?"":name,Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^ -~]","?");return n.length()>24?n.substring(0,21)+"...":n;}
 public static JSONObject recipe(JSONObject r)throws Exception{return value(r.optString("home_color","standard"),r.optString("home_name",suggest(r.optString("name"))));}
 public static void requireService(JSONObject home,boolean available)throws Exception{if(!available&&!home.getString("color").equals("standard"))throw new IOException("Home color needs the updated machine service; choose Standard Home to use this service");}
 public static String summary(JSONObject r){try{String c=r.optString("color","standard");int i=index(c);if(i<0)return "Home appearance unavailable";return LABELS[i]+(c.equals("standard")?"":" · "+r.optString("name"));}catch(Exception e){return "Home appearance unavailable";}}
}
