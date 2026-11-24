package com.rotarypumpers.meraki.core;
import org.json.*;import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;import java.io.IOException;
/** Dates belong to a bag; each shot carries an immutable snapshot of its own age. */
public final class BagTracking{
 public static JSONObject validate(JSONObject source)throws Exception{
  JSONObject b=new JSONObject();String id=source.getString("id");UUID.fromString(id);String name=source.getString("name").trim();if(name.isEmpty()||name.length()>80)throw new IOException("Bean name must be 1–80 characters");b.put("id",id).put("name",name);
  String brand=source.optString("brand","").trim();
  if(brand.length()>80)throw new IOException("Brand must be at most 80 characters");
  if(!brand.isEmpty())b.put("brand",brand);
  for(String key:new String[]{"roast_date","opened_date"})if(source.has(key)&&!source.optString(key).isEmpty()){String value=source.getString(key);LocalDate date=LocalDate.parse(value);if(!date.toString().equals(value)||date.getYear()<1900||date.getYear()>2100)throw new IOException("Invalid bag date");b.put(key,value);}
  return b;
 }
 public static JSONObject snapshot(JSONObject bag,Instant brewed,ZoneId zone)throws Exception{
  JSONObject b=validate(bag);String basis=b.has("roast_date")?"roasted":b.has("opened_date")?"opened":"unknown";b.put("age_basis",basis).put("age_recorded_utc",brewed.toString()).put("age_timezone",zone.getId());
  if(!basis.equals("unknown")){LocalDate date=LocalDate.parse(b.getString(basis.equals("roasted")?"roast_date":"opened_date"));long days=ChronoUnit.DAYS.between(date,brewed.atZone(zone).toLocalDate());if(days>=0)b.put("age_days",days);}
  return b;
 }
 public static void validateSnapshot(JSONObject b)throws Exception{JSONObject expected=snapshot(b,Instant.parse(b.getString("age_recorded_utc")),ZoneId.of(b.getString("age_timezone")));if(!expected.getString("age_basis").equals(b.optString("age_basis"))||expected.has("age_days")!=b.has("age_days")||(expected.has("age_days")&&(!(b.get("age_days") instanceof Number)||expected.getLong("age_days")!=b.getDouble("age_days"))))throw new IOException("Invalid saved bag age");}
 public static String label(JSONObject snapshot){if(snapshot==null)return "";String name=snapshot.optString("name","Bean");if(!snapshot.has("age_days"))return name+" · age unknown";String basis=snapshot.optString("age_basis");return name+" · "+snapshot.optLong("age_days")+(snapshot.optLong("age_days")==1?" day ":" days ")+(basis.equals("roasted")?"since roast":"since opening");}
 public static String current(JSONObject bag){try{return label(snapshot(bag,Instant.now(),ZoneId.systemDefault()));}catch(Exception e){return "Bag unavailable";}}
}
