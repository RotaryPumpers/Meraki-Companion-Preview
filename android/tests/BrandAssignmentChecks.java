package com.rotarypumpers.meraki;
import org.json.*;import java.nio.file.*;import java.time.*;import java.util.*;
public final class BrandAssignmentChecks {
 static int checks;static void ok(boolean v,String name){checks++;if(!v)throw new AssertionError(name);}
 static void fail(RunnableEx r,String name)throws Exception{try{r.run();throw new AssertionError(name);}catch(java.io.IOException expected){checks++;}}
 interface RunnableEx{void run()throws Exception;}
 static JSONObject shot(String id,String grind,String brand)throws Exception{
  JSONObject s=new JSONObject().put("start_utc",id).put("duration_estimate_s",25).put("stop_bounds",new JSONArray().put(25).put(25.5));
  if(grind!=null)s.put("grind_workflow",new JSONObject().put("start_utc",grind).put("settled_dose_g",18.15));
  if(brand!=null)s.put("bag",new JSONObject().put("name","Same bean").put("brand",brand).put("roast_date","2026-10-14"));return s;
 }
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("brand-check");UsageTotals u=new UsageTotals(dir.toFile());JSONArray journal=new JSONArray();
  for(int i=1;i<=51;i++){
   String id=Instant.parse("2026-11-01T17:00:00Z").plusSeconds(i*60).toString();JSONObject s=shot(id,Instant.parse(id).minusSeconds(30).toString(),null);journal.put(s);u.shot(s);
  }
  JSONObject before=u.backup();UsageTotals.BrandPlan p=u.planMonth(YearMonth.of(2026,11),ZoneId.of("America/Los_Angeles")," Ruta Maya ",true);
  ok(p.shots==51&&p.grinds==51,"51 old shots and linked grinds selected");ok(p.brand.equals("Ruta Maya"),"trim label");ok(before.toString().equals(u.backup().toString()),"review/cancel no write");
  u.assign(p);UsageTotals.Monthly m=u.monthly(YearMonth.of(2026,11),ZoneId.of("America/Los_Angeles"));ok(m.brands.get("Ruta Maya").shots==51,"whole-month historical label");ok(!m.brands.containsKey(""),"no unknown left");
  JSONObject after=u.backup();for(String key:new String[]{"brews","grinds"})ok(before.getJSONObject(key).toString().equals(after.getJSONObject(key).toString()),key+" values identical");
  ok(u.summary().shotCount.equals("51")&&u.summary().grindCount.equals("51"),"lifetime counts unchanged");
  ok(journal.getJSONObject(0).optJSONObject("brand_assignment")==null,"original untouched");
  JSONObject tagged=u.annotated(journal.getJSONObject(0));ok(tagged.getJSONObject("brand_assignment").getString("brand").equals("Ruta Maya"),"shot annotation");
  u.backfill(journal,dir.resolve("absent").toFile());ok(u.shotBrand(journal.getJSONObject(0)).equals("Ruta Maya"),"backfill retains correction");
  UsageTotals restarted=new UsageTotals(dir.toFile());ok(restarted.shotBrand(journal.getJSONObject(0)).equals("Ruta Maya"),"restart retains correction");
  UsageTotals restored=new UsageTotals(Files.createTempDirectory("brand-restore").toFile());restored.merge(after);restored.merge(before);restored.merge(after);ok(restored.monthly(YearMonth.of(2026,11),ZoneId.of("America/Los_Angeles")).brands.get("Ruta Maya").shots==51,"backup roundtrip/dedup/old merge");
  Set<String> chosen=Set.of(journal.getJSONObject(0).getString("start_utc"),journal.getJSONObject(1).getString("start_utc"));p=u.planShots(journal,chosen,"Mayorga");ok(p.shots==2&&p.grinds==2&&p.replaced==4,"selected correction preview");u.assign(p);
  m=u.monthly(YearMonth.of(2026,11),ZoneId.of("America/Los_Angeles"));ok(m.brands.get("Mayorga").shots==2&&m.brands.get("Ruta Maya").shots==49,"only selected shots relabelled");
  // Correction keeps original bag attributes and all raw samples immutable.
  JSONObject historical=shot("2026-11-03T18:00:00Z",null,"Old");u.shot(historical);u.assign(u.planShots(new JSONArray().put(historical),Set.of(historical.getString("start_utc")),"New"));JSONObject decorated=u.annotated(historical);
  ok(decorated.getJSONObject("bag").getString("brand").equals("Old")&&decorated.getJSONObject("bag").getString("roast_date").equals("2026-10-14"),"original recorded bag retained");ok(u.shotBrand(historical).equals("New"),"effective correction");
  p=u.planMonth(YearMonth.of(2026,11),ZoneId.of("UTC"),"Other",false);u.shot(shot("2026-11-04T18:00:00Z",null,null));JSONObject newer=u.backup();final UsageTotals.BrandPlan stale=p;fail(()->u.assign(stale),"stale review rejected");ok(newer.toString().equals(u.backup().toString()),"stale plan no partial save");
  p=u.planMonth(YearMonth.of(2026,11),ZoneId.of("UTC"),"Missing only",true);ok(p.shots==1&&p.grinds==0,"unknown-only protects other brands");u.assign(p);
  for(String bad:new String[]{""," ","a".repeat(81),"bad\nbrand"})fail(()->u.planMonth(YearMonth.of(2026,11),ZoneId.of("UTC"),bad,false),"bad label");
  // Local month boundaries and a linked grind in the previous month.
  UsageTotals edge=new UsageTotals(Files.createTempDirectory("brand-edge").toFile());JSONObject cross=shot("2026-11-01T07:00:05Z","2026-11-01T06:59:40Z",null);edge.shot(cross);
  edge.grind(new JSONObject().put("start_utc","2026-11-03T17:00:00Z").put("settled_dose_g",17.7));
  p=edge.planMonth(YearMonth.of(2026,11),ZoneId.of("America/Los_Angeles"),"Month",true);ok(p.shots==1&&p.grinds==1,"local boundaries and standalone grind");edge.assign(p);
  ok(edge.monthly(YearMonth.of(2026,10),ZoneId.of("America/Los_Angeles")).brands.containsKey(""),"month avoids previous month grind");
  p=edge.planShots(new JSONArray().put(cross),Set.of(cross.getString("start_utc")),"Selected");ok(p.shots==1&&p.grinds==1,"selected includes linked cross-month grind");edge.assign(p);ok(edge.monthly(YearMonth.of(2026,10),ZoneId.of("America/Los_Angeles")).brands.get("Selected").grams==18.15,"linked grind keeps date/weight");
  // Deleted journal records remain assignable through the ledger.
  p=edge.planMonth(YearMonth.of(2026,11),ZoneId.of("America/Los_Angeles"),"Deleted history",false);ok(p.shots==1&&p.grinds==1,"no journal needed for month");
  // Older backup corrections cannot overwrite a newer manual correction.
  JSONObject earlier=u.backup();JSONObject newerCopy=new JSONObject(earlier.toString());String id=journal.getJSONObject(0).getString("start_utc");newerCopy.getJSONObject("brews_brand_corrections").getJSONObject(id).put("brand","Newest").put("assigned_utc","2099-01-01T00:00:00Z");newerCopy.getJSONObject("brews_brands").put(id,"Newest");u.merge(newerCopy);u.merge(earlier);ok(u.shotBrand(journal.getJSONObject(0)).equals("Newest"),"newer correction wins on restore");
  JSONObject invalid=new JSONObject(u.backup().toString());invalid.getJSONObject("brews_brand_corrections").getJSONObject(id).put("brand","");String stable=u.backup().toString();fail(()->u.merge(invalid),"malformed correction rejected");ok(stable.equals(u.backup().toString()),"malformed restore leaves ledger intact");
  System.out.println("BRAND_ASSIGNMENT_CHECKS_OK checks="+checks);
 }
}
