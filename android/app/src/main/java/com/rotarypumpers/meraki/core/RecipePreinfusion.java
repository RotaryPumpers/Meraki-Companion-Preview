package com.rotarypumpers.meraki.core;

import java.io.IOException;
import java.util.UUID;
import org.json.JSONObject;

/** Optional fourth recipe stage. No pressure/time/pause changes or automatic retries. */
public final class RecipePreinfusion {
 public static final String[] MODES={"keep","on","off"};
 public static final String[] LABELS={"Leave unchanged","On","Off"};
 public interface Transport {
  boolean allowed();
  JSONObject read() throws Exception;
  JSONObject set(String id,boolean enabled,JSONObject expected) throws Exception;
 }
 public interface Journal {
  void before(String id,boolean target,JSONObject expected) throws Exception;
  void confirmed(String id) throws Exception;
 }
 public static String mode(JSONObject recipe)throws Exception {
  if(!recipe.has("preinfusion_mode"))return "keep";
  Object value=recipe.get("preinfusion_mode");
  if(!(value instanceof String))throw new IOException("Choose a pre-infusion option");
  for(String mode:MODES)if(mode.equals(value))return mode;
  throw new IOException("Unsupported recipe pre-infusion option");
 }
 private static void validMode(String mode)throws IOException {
  for(String item:MODES)if(item.equals(mode))return;
  throw new IOException("Unsupported recipe pre-infusion option");
 }
 public static JSONObject prepare(String mode,Transport transport)throws Exception {
  validMode(mode);if(mode.equals("keep"))return null;
  if(!transport.allowed())throw new IOException("App paused");
  JSONObject state=transport.read();PreinfusionState.validate(state);if(!state.optBoolean("saved_match"))throw new IOException("Saved pre-infusion settings do not match");
  return new JSONObject(state.toString());
 }
 public static boolean apply(RecipeControls.Progress progress,String mode,JSONObject expected,Transport transport,Journal journal)throws Exception {
  validMode(mode);if(!progress.complete)return false;
  if(mode.equals("keep"))return true;
  if(expected==null)throw new IOException("Read pre-infusion before applying the recipe");
  PreinfusionState.validate(expected);
  if(!transport.allowed())throw new IOException("App paused");
  JSONObject current=transport.read();PreinfusionState.validate(current);if(!current.optBoolean("saved_match"))throw new IOException("Saved pre-infusion settings do not match");
  if(!PreinfusionState.sameSettings(current,expected)||current.getBoolean("enabled")!=expected.getBoolean("enabled"))throw new IOException("Pre-infusion changed during the recipe; check the machine");
  boolean target=mode.equals("on");
  if(current.getBoolean("enabled")==target)return true;
  String id=UUID.randomUUID().toString();journal.before(id,target,expected);
  if(!transport.allowed())throw new IOException("App paused; no remaining recipe steps will run");
  JSONObject result=transport.set(id,target,new JSONObject(expected.toString()));
  String status=result.optString("status");JSONObject actual=result.optJSONObject("preinfusion");
  if((!status.equals("verified")&&!status.equals("unchanged"))||actual==null)throw new IOException("Pre-infusion not confirmed; check the recipe result");
  PreinfusionState.validate(actual);
  if(actual.getBoolean("enabled")!=target||!PreinfusionState.sameSettings(actual,expected)||!actual.optBoolean("saved_match")||!actual.optBoolean("settings_preserved")||(status.equals("verified")&&!actual.optBoolean("native_reply")))throw new IOException("Pre-infusion result did not verify all settings");
  journal.confirmed(id);return true;
 }
 private RecipePreinfusion(){}
}
