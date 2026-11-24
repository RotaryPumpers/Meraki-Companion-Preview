package com.rotarypumpers.meraki.core;
/** One user-facing explanation, with raw diagnostic detail retained separately. */
public final class RecipeResultText {
 public static String reason(String raw){
  if(raw==null||raw.isBlank())return "Check the machine before continuing.";
  if(raw.contains("Home -> grinder"))return "Couldn't open the grinder screen. Dose was not applied.";
  if(raw.contains("grinder -> dose selector"))return "Couldn't open the dose selector. Dose was not applied.";
  if(raw.contains("Brew -> Home"))return "Couldn't return Home before setting the dose.";
  if(raw.contains("grinder -> Home"))return "Return to Home wasn't verified. Check the dose on the machine.";
  if(raw.contains("animation/button layout"))return "The screen wasn't ready for the next step.";
  if(raw.contains("machine warning blocks"))return "A machine warning blocked navigation to the grinder.";
  if(raw.contains("navigation did not reach"))return "Couldn't reach the required screen. Check the dose on the machine.";
  String value=raw.replace("Dose update incomplete; check machine before another attempt:","").replace("Dose update incomplete; check machine before another attempt","").replace("Yield update incomplete; check machine before another attempt:","").replace("; no retry","").trim();
  if(value.isEmpty())return "Check the machine before continuing.";
  return value.length()>180?value.substring(0,180)+"…":value;
 }
 public static String stopped(int completed,String step,String raw){return stopped(completed,3,step,raw);}
 public static String stopped(int completed,int total,String step,String raw){String name=step==null?"Setting":step.replace("set_","");if(!name.isEmpty())name=Character.toUpperCase(name.charAt(0))+name.substring(1);return "Recipe stopped · "+completed+" of "+total+" verified.\n"+name+": "+reason(raw);}
 private RecipeResultText(){}
}
