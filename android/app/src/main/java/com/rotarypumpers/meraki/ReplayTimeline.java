package com.rotarypumpers.meraki;
import org.json.*;
final class ReplayTimeline {
 final JSONArray samples; final double start,end,gapLimit;
 ReplayTimeline(JSONObject shot){gapLimit=gapLimit(shot);samples=shot.optJSONArray("samples");if(samples==null||samples.length()<2)throw new IllegalArgumentException("This record needs at least two samples to replay.");
  double first=samples.optJSONObject(0).optDouble("uptime"),last=samples.optJSONObject(samples.length()-1).optDouble("uptime");
  start=bound(shot,"start_bounds",first);end=Math.max(start,Math.min(last,bound(shot,"stop_bounds",last)));
  if(!Double.isFinite(start)||!Double.isFinite(end)||end<=start)throw new IllegalArgumentException("This record has no replayable time span.");
 }
 static double gapLimit(JSONObject shot){double n=shot==null?3.5:shot.optDouble("sampling_gap_limit_s",3.5);return Double.isFinite(n)&&n>0&&n<=3.5?n:3.5;}
 static double bound(JSONObject s,String key,double fallback){JSONArray a=s.optJSONArray(key);return a!=null&&a.length()==2?(a.optDouble(0)+a.optDouble(1))/2:fallback;}
 double duration(){return end-start;}
 static double power(JSONObject s){JSONArray r=s.optJSONArray("registers");return s.has("p1_w")?s.optDouble("p1_w"):r==null?Double.NaN:r.optDouble(51)/10.0;}
 double[] at(double seconds){double t=start+Math.max(0,Math.min(duration(),seconds));JSONObject prev=null;
  for(int i=0;i<samples.length();i++){JSONObject next=samples.optJSONObject(i);double nt=next.optDouble("uptime");
   if(Math.abs(t-nt)<0.000001)return new double[]{next.optDouble("weight_g"),power(next)};
   if(nt>t){if(prev==null)return new double[]{Double.NaN,Double.NaN};double pt=prev.optDouble("uptime");if(nt-pt>gapLimit)return new double[]{Double.NaN,Double.NaN};double f=(t-pt)/(nt-pt);return new double[]{mix(prev.optDouble("weight_g"),next.optDouble("weight_g"),f),mix(power(prev),power(next),f)};}prev=next;
  }return new double[]{Double.NaN,Double.NaN};
 }
 private double mix(double a,double b,double f){return a+(b-a)*f;}
}
