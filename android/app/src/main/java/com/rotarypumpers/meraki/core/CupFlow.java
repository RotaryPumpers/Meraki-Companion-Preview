package com.rotarypumpers.meraki.core;
import org.json.*;
import java.util.Arrays;
/** Derived cup output only. Raw samples and pump-flow registers remain untouched. */
public final class CupFlow {
 public static final String METHOD="trailing two-second least-squares scale-weight slope; one-second minimum; gaps preserved; negative slopes clipped or unavailable";
 public static double[] rates(JSONArray rows,double gap){
  int n=rows==null?0:rows.length();double[] result=new double[n];Arrays.fill(result,Double.NaN);int segment=0,left=0;
  for(int i=0;i<n;i++){
   JSONObject row=rows.optJSONObject(i);double t=row==null?Double.NaN:row.optDouble("uptime"),w=row==null?Double.NaN:row.optDouble("weight_g");
   if(!Double.isFinite(t)||!Double.isFinite(w)){segment=i+1;left=segment;continue;}
   if(i>0){JSONObject before=rows.optJSONObject(i-1);double bt=before==null?Double.NaN:before.optDouble("uptime"),bw=before==null?Double.NaN:before.optDouble("weight_g");if(!Double.isFinite(bt)||!Double.isFinite(bw)||t<=bt||t-bt>gap||w<bw-2){segment=i;left=i;}}
   left=Math.max(left,segment);while(left<i&&t-rows.optJSONObject(left).optDouble("uptime")>2.0001)left++;
   double span=t-rows.optJSONObject(left).optDouble("uptime");if(span<.999||i-left<2)continue;
   double sx=0,sy=0,sxx=0,sxy=0;int count=i-left+1;
   for(int j=left;j<=i;j++){JSONObject p=rows.optJSONObject(j);double x=p.optDouble("uptime")-t,y=p.optDouble("weight_g");sx+=x;sy+=y;sxx+=x*x;sxy+=x*y;}
   double denominator=count*sxx-sx*sx;if(denominator<=0)continue;double slope=(count*sxy-sx*sy)/denominator;
   if(Double.isFinite(slope)&&slope>=-.1)result[i]=Math.max(0,slope);
  }return result;
 }
 /** First sustained scale rise >=0.3g above the start baseline, not a sensed drop. */
 public static double firstDetectedDrip(JSONObject shot){
  if(JournalBrowse.review(shot))return Double.NaN;
  JSONArray rows=shot.optJSONArray("samples");double start=ShotInsights.bound(shot,"start_bounds"),stop=ShotInsights.bound(shot,"stop_bounds"),gap=ShotInsights.gapLimit(shot),baseline=ShotInsights.weightAt(shot,start);
  if(rows==null||!Double.isFinite(start)||!Double.isFinite(stop))return Double.NaN;
  // Saved recordings may begin at the first active sample, just after the
  // start bracket. Accept a baseline only from three stable, near-zero tare
  // readings within the first 1.5s; never assume an unseen zero or cup weight.
  if(!Double.isFinite(baseline)&&rows.length()>=3){double[] tare=new double[3];boolean stable=true;double previousT=Double.NaN;for(int i=0;i<3;i++){JSONObject r=rows.optJSONObject(i);double t=r==null?Double.NaN:r.optDouble("uptime"),w=r==null?Double.NaN:r.optDouble("weight_g");if(!Double.isFinite(t)||!Double.isFinite(w)||t<start||t-start>1.5||Math.abs(w)>.300001||(i>0&&(t<=previousT||t-previousT>gap)))stable=false;tare[i]=w;previousT=t;}Arrays.sort(tare);if(stable&&tare[2]-tare[0]<=.200001)baseline=tare[1];}
  if(!Double.isFinite(baseline))return Double.NaN;
  double previous=start,previousWeight=baseline,candidate=Double.NaN;int sustained=0;
  for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)return Double.NaN;double t=r.optDouble("uptime"),w=r.optDouble("weight_g");if(t<start)continue;if(t>stop)break;
   if(!Double.isFinite(t)||!Double.isFinite(w)||t<previous||t-previous>gap||w<previousWeight-2||w-previousWeight>10)return Double.NaN;
   if(w-baseline>=.3-1e-8){if(sustained++==0)candidate=t-start;if(sustained>=2)return candidate;}else{sustained=0;candidate=Double.NaN;}
   previous=t;previousWeight=w;
  }return Double.NaN;
 }
}
