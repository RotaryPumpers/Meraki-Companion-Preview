package com.rotarypumpers.meraki.core;

import java.io.IOException;
import java.util.UUID;

/** Tested recipe-control coordinator; Android UI/journal adapters still required. */
public final class RecipeControls {
 public static final class Plan {
  public final int temperatureC,yieldGrams,doseRaw;
  public Plan(double temp,double yield,double dose,double yieldMax) {
   if(!Double.isFinite(temp)||temp!=Math.rint(temp)||temp<85||temp>96)throw new IllegalArgumentException("Temperature must be a whole number from85 to96°C");
   if(!Double.isFinite(yield)||yield!=Math.rint(yield)||yield<10||yield>99)throw new IllegalArgumentException("Yield must be a whole number from10 to99g");
   if(!Double.isNaN(yieldMax)&&(!Double.isFinite(yieldMax)||yieldMax!=yield))throw new IllegalArgumentException("Choose one exact yield before applying this recipe");
   double raw=dose*10;
   if(!Double.isFinite(dose)||Math.abs(raw-Math.rint(raw))>1e-7||raw<10||raw>309)throw new IllegalArgumentException("Dose must be1.0 to30.9g in0.1g steps");
   temperatureC=(int)temp;yieldGrams=(int)yield;doseRaw=(int)Math.rint(raw);
  }
 }
 public interface Journal {
  boolean hasPending() throws IOException;
  // Must durably record all targets before first command, then each pending request.
  void begin(Plan plan) throws IOException;
  void before(String kind,int target,String requestId) throws IOException;
  void confirmed(String kind,int target,String requestId) throws IOException;
  void complete() throws IOException;
 }
 public interface Transport {
  boolean allThreeAvailable() throws IOException;
  boolean allowed();
  Outcome set(String kind,int target,String requestId) throws IOException;
 }
 public static final class Outcome {
  public final String status;public final int target,temperatureC,yieldGrams,doseRaw;public final boolean otherSettingsPreserved,destinationVerified;
  public Outcome(String status,int target,boolean preserved,boolean destination,int temp,int yield,int dose){this.status=status;this.target=target;temperatureC=temp;yieldGrams=yield;doseRaw=dose;otherSettingsPreserved=preserved;destinationVerified=destination;}
 }
 public static final class Progress {
  public final int completed;public final String stoppedAt;public final boolean complete;
  Progress(int n,String step,boolean done){completed=n;stoppedAt=step;complete=done;}
 }
 public static Progress apply(Plan plan,Journal journal,Transport transport) throws IOException {
  if(journal.hasPending())throw new IOException("Check the previous recipe result before another update");
  if(!transport.allowed()||!transport.allThreeAvailable())throw new IOException("All three controls must be ready before applying a recipe");
  journal.begin(plan);String[] kinds={"set_temperature","set_yield","set_dose"};int[] targets={plan.temperatureC,plan.yieldGrams,plan.doseRaw};
  for(int i=0;i<3;i++){
   if(!transport.allowed())return new Progress(i,kinds[i],false);
   String id=UUID.randomUUID().toString();journal.before(kinds[i],targets[i],id);
   Outcome r=transport.set(kinds[i],targets[i],id);
   boolean unchanged=r!=null&&"unchanged".equals(r.status);
   if(r==null||(!unchanged&&!"verified".equals(r.status))||r.target!=targets[i]||!r.otherSettingsPreserved||(!unchanged&&!r.destinationVerified))return new Progress(i,kinds[i],false);
   if(r.temperatureC!=plan.temperatureC||(i>=1&&r.yieldGrams!=plan.yieldGrams)||(i>=2&&r.doseRaw!=plan.doseRaw))return new Progress(i,kinds[i],false);
   journal.confirmed(kinds[i],targets[i],id);
  }
  journal.complete();return new Progress(3,"",true);
 }
 private RecipeControls(){}
}
