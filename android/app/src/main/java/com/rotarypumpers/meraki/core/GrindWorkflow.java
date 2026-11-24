package com.rotarypumpers.meraki.core;
import java.util.*;
/** Read-only motor-command edges; no inference of cup presence. */
public final class GrindWorkflow {
 public static final class Result {
  public double startLo,startHi,stopLo,stopHi,doseSetting;public int grind;public Double settledDose;public String utc;public boolean cupMoved;
  public double duration(){return (stopLo+stopHi-startLo-startHi)/2;}
 }
 private Sample previous;private Result active,pending;private boolean reported;private final List<Sample> tail=new ArrayList<>();
 public static boolean grinding(Sample s){return (s.registers[33]&0x40)!=0;}
 public static double dose(Sample s){return (short)s.registers[38]/10.0;}
 public void reset(){previous=null;active=null;pending=null;tail.clear();reported=false;}
 public Result accept(Sample s){
  if(previous!=null&&(s.uptime<=previous.uptime||s.uptime-previous.uptime>3.5))reset();
  boolean on=grinding(s);Result completed=null;
  if(on&&previous!=null&&!grinding(previous)){active=new Result();active.utc=s.utc;active.startLo=previous.uptime;active.startHi=s.uptime;active.doseSetting=s.registers[4]/10.0;active.grind=(short)s.registers[37];pending=null;tail.clear();reported=false;}
  if(!on&&previous!=null&&grinding(previous)&&active!=null){pending=active;active=null;pending.stopLo=previous.uptime;pending.stopHi=s.uptime;tail.clear();}
  if(pending!=null&&!on&&!reported){
   double v=dose(s);if(v<0||previous!=null&&v<dose(previous)-2)pending.cupMoved=true;
   if(!pending.cupMoved&&s.uptime-pending.stopHi<=3.5){tail.add(s);while(tail.size()>1&&s.uptime-tail.get(0).uptime>2)tail.remove(0);if(tail.size()>=2&&s.uptime-tail.get(0).uptime>=.8){double lo=Double.POSITIVE_INFINITY,hi=0;for(Sample row:tail){lo=Math.min(lo,dose(row));hi=Math.max(hi,dose(row));}if(hi-lo<=.200001)pending.settledDose=(lo+hi)/2;}}
   if(s.uptime-pending.stopHi>=3.5||s.active){reported=true;completed=pending;}
  }
  previous=s;return completed;
 }
 public Result peek(double brewTime){Result r=pending;return r!=null&&brewTime>=r.stopHi&&brewTime-r.stopHi<=300?r:null;}
 public Result consume(double brewTime){Result r=pending;pending=null;tail.clear();return r!=null&&brewTime>=r.stopHi&&brewTime-r.stopHi<=300?r:null;}
}
