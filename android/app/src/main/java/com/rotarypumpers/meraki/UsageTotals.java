package com.rotarypumpers.meraki;
import com.rotarypumpers.meraki.core.*;import org.json.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;
/** Separate durable event ledger: journal deletion cannot erase counted usage. */
final class UsageTotals {
 private final File file;private JSONObject state;private boolean failed;
 UsageTotals(File directory){file=new File(directory,"usage-totals-v1.json");}
 private void load()throws Exception{if(failed)throw new IOException("Usage totals unreadable; original retained");if(state!=null)return;try{state=file.exists()?new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)):new JSONObject().put("version",1).put("brews",new JSONObject()).put("grinds",new JSONObject());validate(state);validateCorrections(state);}catch(Exception e){failed=true;throw e;}}
 private static void validate(JSONObject s)throws Exception{if(s.getInt("version")!=1)throw new IOException("Unsupported totals version");for(String key:new String[]{"brews","grinds"}){JSONObject events=s.getJSONObject(key);java.util.Iterator<String> it=events.keys();while(it.hasNext()){String id=it.next();Instant.parse(id);double v=events.getDouble(id);if(!Double.isFinite(v)||v<(key.equals("grinds")?5:1)||v>(key.equals("grinds")?500:1))throw new IOException("Invalid usage entry");}}}
 private void save(JSONObject next)throws Exception{validate(next);validateCorrections(next);File temp=new File(file.getParentFile(),file.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(next.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);state=next;}
 private static void grindInto(JSONObject state,JSONObject g)throws Exception{if(g==null||g.optBoolean("demo"))return;double grams=g.optDouble("settled_dose_g",Double.NaN);String id=g.optString("start_utc");if(!Double.isFinite(grams)||grams<5||grams>500||id.isEmpty())return;Instant.parse(id);JSONObject events=state.getJSONObject("grinds");if(!events.has(id))events.put(id,grams);}
 private static String brand(JSONObject bag){
  if(bag==null)return "";
  String value=bag.optString("brand","").trim();
  return value.length()<=80?value:"";
 }
 private static void attribute(JSONObject ledger,String kind,String id,String brand)throws JSONException{
  if(brand.isEmpty()||!ledger.getJSONObject(kind).has(id))return;
  String key=kind+"_brands";
  JSONObject labels=ledger.optJSONObject(key);
  if(labels==null){labels=new JSONObject();ledger.put(key,labels);}
  if(!labels.has(id))labels.put(id,brand);
 }
 synchronized void shot(JSONObject s)throws Exception{load();if(s.optBoolean("demo"))return;JSONObject n=new JSONObject(state.toString());if(ShotInsights.countedBrew(s)){String id=s.getString("start_utc");Instant.parse(id);n.getJSONObject("brews").put(id,1);attribute(n,"brews",id,brand(s.optJSONObject("bag")));}JSONObject grind=s.optJSONObject("grind_workflow");
 grindInto(n,grind);
 if(grind!=null&&!grind.optBoolean("demo"))attribute(n,"grinds",grind.optString("start_utc"),brand(s.optJSONObject("bag")));if(!n.toString().equals(state.toString()))save(n);}
 synchronized void grind(JSONObject g)throws Exception{grind(g,null);}
 synchronized void grind(JSONObject g,JSONObject bag)throws Exception{
  load();JSONObject n=new JSONObject(state.toString());grindInto(n,g);
  if(g!=null&&!g.optBoolean("demo"))attribute(n,"grinds",g.optString("start_utc"),brand(bag));if(!n.toString().equals(state.toString()))save(n);}
 synchronized void backfill(JSONArray shots,File events)throws Exception{load();for(int i=0;i<shots.length();i++)shot(shots.getJSONObject(i));if(events.exists())try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(events),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)if(!line.trim().isEmpty())grind(new JSONObject(line));}}
 synchronized JSONObject backup()throws Exception{load();return new JSONObject(state.toString());}
 synchronized void merge(JSONObject incoming)throws Exception{validate(incoming);validateCorrections(incoming);load();JSONObject n=new JSONObject(state.toString());for(String key:new String[]{"brews","grinds"}){JSONObject source=incoming.getJSONObject(key),dest=n.getJSONObject(key);java.util.Iterator<String> it=source.keys();while(it.hasNext()){String id=it.next();if(!dest.has(id))dest.put(id,source.get(id));
 JSONObject labels=incoming.optJSONObject(key+"_brands");
 if(labels!=null){String label=labels.optString(id,"").trim();if(label.length()<=80)attribute(n,key,id,label);}}}mergeCorrections(n,incoming);save(n);}

 /** Explicit corrections live in this atomic ledger, never rewrite recorded samples or bag dates. */
 private static Set<String> keys(JSONObject object){Set<String> out=new TreeSet<>();Iterator<String> it=object.keys();while(it.hasNext())out.add(it.next());return out;}
 private static void validateCorrections(JSONObject value)throws Exception{
  for(String kind:new String[]{"brews","grinds"}){
   JSONObject labels=value.optJSONObject(kind+"_brands"),edits=value.optJSONObject(kind+"_brand_corrections");
   if(value.has(kind+"_brands")&&labels==null)throw new IOException("Invalid brand labels");
   if(value.has(kind+"_brand_corrections")&&edits==null)throw new IOException("Invalid brand corrections");
   if(labels!=null)for(String id:keys(labels)){
    if(!value.getJSONObject(kind).has(id)||!(labels.get(id) instanceof String)||labels.getString(id).length()>80)throw new IOException("Invalid brand label");
   }
   if(edits!=null)for(String id:keys(edits)){
    JSONObject edit=edits.getJSONObject(id);String name=checkedBrand(edit.getString("brand"));Instant.parse(edit.getString("assigned_utc"));
    if(!value.getJSONObject(kind).has(id)||labels==null||!name.equals(labels.optString(id)))throw new IOException("Invalid brand correction");
   }
  }
 }
 private static String checkedBrand(String value)throws IOException{
  if(value==null||value.trim().isEmpty()||value.trim().length()>80)throw new IOException("Enter a brand (1–80 characters)");
  String brand=value.trim();for(int i=0;i<brand.length();i++)if(Character.isISOControl(brand.charAt(i)))throw new IOException("Use a single-line brand name");return brand;
 }
 private static void mergeCorrections(JSONObject target,JSONObject source)throws Exception{
  for(String kind:new String[]{"brews","grinds"}){
   String key=kind+"_brand_corrections";JSONObject incoming=source.optJSONObject(key);if(incoming==null)continue;
   JSONObject dest=target.optJSONObject(key);if(dest==null){dest=new JSONObject();target.put(key,dest);}
   JSONObject labels=target.optJSONObject(kind+"_brands");if(labels==null){labels=new JSONObject();target.put(kind+"_brands",labels);}
   for(String id:keys(incoming)){
    JSONObject edit=incoming.getJSONObject(id),previous=dest.optJSONObject(id);
    if(previous==null||Instant.parse(edit.getString("assigned_utc")).isAfter(Instant.parse(previous.getString("assigned_utc")))){
     dest.put(id,new JSONObject(edit.toString()));labels.put(id,edit.getString("brand"));
    }
   }
  }
 }
 static final class BrandPlan {
  final String brand,expected;final Map<String,Map<String,String>> events;
  final int shots,grinds,replaced;
  BrandPlan(String label,String snapshot,Map<String,Map<String,String>> selected){
   brand=label;expected=snapshot;events=selected;shots=selected.get("brews").size();grinds=selected.get("grinds").size();
   int count=0;for(Map<String,String> group:selected.values())for(String previous:group.values())if(!previous.isEmpty()&&!previous.equals(label))count++;replaced=count;
  }
  String summary(){return shots+(shots==1?" shot · ":" shots · ")+grinds+(grinds==1?" measured grind":" measured grinds");}
 }
 private BrandPlan plan(Map<String,Set<String>> ids,String brand,boolean onlyMissing)throws Exception{
  load();String label=checkedBrand(brand);Map<String,Map<String,String>> selected=new LinkedHashMap<>();
  for(String kind:new String[]{"brews","grinds"}){
   Map<String,String> group=new TreeMap<>();JSONObject labels=state.optJSONObject(kind+"_brands");
   for(String id:ids.get(kind))if(state.getJSONObject(kind).has(id)){
    String old=labels==null?"":labels.optString(id,"");if((!onlyMissing||old.isEmpty())&&!old.equals(label))group.put(id,old);
   }selected.put(kind,Collections.unmodifiableMap(group));
  }
  if(selected.get("brews").isEmpty()&&selected.get("grinds").isEmpty())throw new IOException(onlyMissing?"No unassigned records in this selection":"These records already use that brand");
  return new BrandPlan(label,state.toString(),Collections.unmodifiableMap(selected));
 }
 synchronized BrandPlan planMonth(YearMonth month,ZoneId zone,String brand,boolean onlyMissing)throws Exception{
  load();Instant from=month.atDay(1).atStartOfDay(zone).toInstant(),to=month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
  Map<String,Set<String>> ids=new LinkedHashMap<>();for(String kind:new String[]{"brews","grinds"}){
   Set<String> selected=new TreeSet<>();for(String id:keys(state.getJSONObject(kind))){Instant t=Instant.parse(id);if(!t.isBefore(from)&&t.isBefore(to))selected.add(id);}ids.put(kind,selected);
  }return plan(ids,brand,onlyMissing);
 }
 synchronized BrandPlan planShots(JSONArray journal,Set<String> shotIds,String brand)throws Exception{
  load();Map<String,Set<String>> ids=new LinkedHashMap<>();ids.put("brews",new TreeSet<>());ids.put("grinds",new TreeSet<>());
  for(int i=0;i<journal.length();i++){
   JSONObject shot=journal.optJSONObject(i);if(shot==null||shot.optBoolean("demo")||!shotIds.contains(shot.optString("start_utc")))continue;
   String id=shot.optString("start_utc");if(!state.getJSONObject("brews").has(id))continue;ids.get("brews").add(id);
   JSONObject grind=shot.optJSONObject("grind_workflow");if(grind!=null&&!grind.optBoolean("demo"))ids.get("grinds").add(grind.optString("start_utc"));
  }return plan(ids,brand,false);
 }
 synchronized void assign(BrandPlan plan)throws Exception{
  load();if(!state.toString().equals(plan.expected))throw new IOException("Totals changed. Review the selection again");
  JSONObject next=new JSONObject(state.toString());String at=Instant.now().toString();
  for(String kind:new String[]{"brews","grinds"}){
   JSONObject labels=next.optJSONObject(kind+"_brands");if(labels==null){labels=new JSONObject();next.put(kind+"_brands",labels);}
   JSONObject corrections=next.optJSONObject(kind+"_brand_corrections");if(corrections==null){corrections=new JSONObject();next.put(kind+"_brand_corrections",corrections);}
   for(String id:plan.events.get(kind).keySet()){
    labels.put(id,plan.brand);corrections.put(id,new JSONObject().put("brand",plan.brand).put("assigned_utc",at));
   }
  }save(next);
 }
 synchronized String shotBrand(JSONObject shot)throws Exception{
  load();JSONObject labels=state.optJSONObject("brews_brands");return labels==null?brand(shot.optJSONObject("bag")):labels.optString(shot.optString("start_utc"),brand(shot.optJSONObject("bag")));
 }
 synchronized JSONObject annotated(JSONObject shot)throws Exception{
  load();JSONObject edits=state.optJSONObject("brews_brand_corrections"),edit=edits==null?null:edits.optJSONObject(shot.optString("start_utc"));
  if(edit==null)return shot;return new JSONObject(shot.toString()).put("brand_assignment",new JSONObject(edit.toString()));
 }
 static final class Counts {
  int shots;
  double grams;
  String label(){return shots+" shots · "+String.format(Locale.US,"%.1f g ground",grams);}
 }
 static final class Monthly {
  final Counts totals=new Counts();
  final Map<String,Counts> brands=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
 }
 synchronized Monthly monthly(YearMonth month,ZoneId zone)throws Exception{
  load();
  Instant start=month.atDay(1).atStartOfDay(zone).toInstant();
  Instant end=month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
  Monthly result=new Monthly();
  for(String kind:new String[]{"brews","grinds"}){
   JSONObject events=state.getJSONObject(kind),labels=state.optJSONObject(kind+"_brands");
   Iterator<String> keys=events.keys();
   while(keys.hasNext()){
    String id=keys.next();
    Instant timestamp=Instant.parse(id);
    if(timestamp.isBefore(start)||!timestamp.isBefore(end))continue;
    String brand=labels==null?"":labels.optString(id,"");
    Counts counts=result.brands.computeIfAbsent(brand,key->new Counts());
    if(kind.equals("brews")){counts.shots++;result.totals.shots++;}
    else{double grams=events.getDouble(id);counts.grams+=grams;result.totals.grams+=grams;}
   }
  }
  return result;
 }
 static final class Summary {
  final String shotCount,grindCount,groundWeight,shotsSinceUTC,grindsSinceUTC;
  Summary(JSONObject brews,JSONObject grinds)throws Exception{
   shotCount=Integer.toString(brews.length());grindCount=Integer.toString(grinds.length());
   shotsSinceUTC=first(brews);grindsSinceUTC=first(grinds);double grams=0;
   java.util.Iterator<String> keys=grinds.keys();while(keys.hasNext())grams+=grinds.getDouble(keys.next());
   groundWeight=String.format(java.util.Locale.US,grams>=1000?"%.3f kg":"%.1f g",grams>=1000?grams/1000:grams);
  }
  private static String first(JSONObject events)throws Exception{
   String first="";java.util.Iterator<String> keys=events.keys();
   while(keys.hasNext()){String id=keys.next();if(first.isEmpty()||Instant.parse(id).isBefore(Instant.parse(first)))first=id;}
   return first;
  }
 }
 synchronized Summary summary()throws Exception{load();return new Summary(state.getJSONObject("brews"),state.getJSONObject("grinds"));}
}
