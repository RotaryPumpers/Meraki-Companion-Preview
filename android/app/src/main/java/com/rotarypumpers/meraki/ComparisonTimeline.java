package com.rotarypumpers.meraki;
import org.json.JSONObject;
/** Two independent recordings aligned only by elapsed brew time. */
final class ComparisonTimeline {
 final ReplayTimeline current,reference;
 ComparisonTimeline(JSONObject a,JSONObject b){current=new ReplayTimeline(a);reference=new ReplayTimeline(b);}
 double duration(){return Math.max(current.duration(),reference.duration());}
 double position(double seconds){return Math.max(0,Math.min(duration(),seconds));}
 double[] weightAt(double seconds){double t=position(seconds);return new double[]{current.at(Math.min(t,current.duration()))[0],reference.at(Math.min(t,reference.duration()))[0]};}
}
