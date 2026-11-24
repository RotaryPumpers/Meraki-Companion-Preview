package com.rotarypumpers.meraki;
import android.content.Context;import android.graphics.*;import android.view.View;import org.json.*;import java.util.Locale;

class ShotChart extends View {
    private int mode=0;
    void mode(int value){mode=value;invalidate();}
    private double replayTime=Double.NaN,comparisonDuration=Double.NaN,comparisonMax=Double.NaN;private boolean diagnostic;
    private JSONArray samples=new JSONArray();private JSONObject shot;private final Paint p=new Paint(3);
    ShotChart(Context c){super(c);p.setTypeface(c.getResources().getFont(R.font.space_grotesk));}
    void data(JSONArray s){samples=s==null?new JSONArray():s;shot=null;diagnostic=false;replayTime=Double.NaN;
        if(samples.length()>0){JSONObject first=samples.optJSONObject(0);if(first!=null&&first.optJSONObject("preferences")!=null){try{shot=new JSONObject().put("sampling_gap_limit_s","arm_display_cache".equals(first.optString("source"))?0.75:3.5).put("pre_infusion",first.getJSONObject("preferences"));}catch(Exception ignored){}}}invalidate();}
    void shot(JSONObject s){shot=s;diagnostic=false;replayTime=Double.NaN;JSONArray all=s.optJSONArray("samples");samples=new JSONArray();
        if(all!=null){double stop=ReplayTimeline.bound(s,"stop_bounds",Double.POSITIVE_INFINITY);JSONObject prev=null;for(int i=0;i<all.length();i++){JSONObject row=all.optJSONObject(i);double t=row.optDouble("uptime");if(t<=stop){samples.put(row);prev=row;}else{if(prev!=null&&stop>prev.optDouble("uptime")&&t-prev.optDouble("uptime")<=ReplayTimeline.gapLimit(s)){try{double f=(stop-prev.optDouble("uptime"))/(t-prev.optDouble("uptime"));samples.put(new JSONObject().put("uptime",stop).put("weight_g",prev.optDouble("weight_g")+(row.optDouble("weight_g")-prev.optDouble("weight_g"))*f).put("p1_w",ReplayTimeline.power(prev)+(ReplayTimeline.power(row)-ReplayTimeline.power(prev))*f));}catch(Exception ignored){}}break;}}}invalidate();}
    void diagnostic(JSONObject s){shot=s;diagnostic=true;replayTime=Double.NaN;samples=s.optJSONArray("samples");if(samples==null)samples=new JSONArray();invalidate();}
    void comparisonScale(double duration,double maxWeight){comparisonDuration=duration;comparisonMax=maxWeight;invalidate();}
    static double weightScale(JSONObject shot){double max=50;JSONArray rows=shot.optJSONArray("samples");if(rows!=null)for(int i=0;i<rows.length();i++){double value=rows.optJSONObject(i).optDouble("weight_g",0);if(Double.isFinite(value))max=Math.max(max,Math.ceil(value/10)*10);}return max;}
    void replayAt(double t){replayTime=t;invalidate();}
    private float x(double t,double t0,double t1,float left,float right){return (float)(left+(right-left)*Math.max(0,Math.min(1,(t-t0)/(t1-t0))));}
    protected void onDraw(Canvas c){
        super.onDraw(c);float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),left=32*d,right=w-10*d;
        p.setTextSize(10*d);p.setStyle(Paint.Style.FILL);
        if(samples.length()<2){p.setColor(0xffc2c7c2);c.drawText("Waiting for samples",12*d,22*d,p);return;}
        double t0=samples.optJSONObject(0).optDouble("uptime"),t1=samples.optJSONObject(samples.length()-1).optDouble("uptime");if(t1<=t0)return;
        if(!diagnostic){drawCombined(c,d,w,h);return;}
        JSONObject pi=shot==null?null:shot.optJSONObject("pre_infusion");boolean enabled=pi!=null&&pi.optInt("enabled")==1;
        double start=t0,stop=Double.NaN;JSONArray bounds=shot==null?null:shot.optJSONArray("start_bounds");if(bounds!=null&&bounds.length()==2)start=(bounds.optDouble(0)+bounds.optDouble(1))/2;
        bounds=shot==null?null:shot.optJSONArray("stop_bounds");if(bounds!=null&&bounds.length()==2)stop=(bounds.optDouble(0)+bounds.optDouble(1))/2;
        double piEnd=enabled?start+pi.optDouble("duration_setting_s",0):start;
        for(int panel=0;panel<2;panel++){
            float base=panel*(h-32*d)/2,top=base+32*d,bottom=base+(h-32*d)/2-25*d;
            p.setColor(0xffc2c7c2);c.drawText(panel==0?"MACHINE SCALE · g":"ELECTRICAL P1 · W (not pressure)",10*d,base+15*d,p);
            double max=panel==0?50:300;for(int i=0;i<samples.length();i++){JSONObject s=samples.optJSONObject(i);double v=value(s,panel);if(Double.isFinite(v)&&v>max)max=panel==0?Math.ceil(v/50)*50:Math.ceil(v/300)*300;}
            if(enabled&&piEnd>t0){p.setColor(0x454c9fce);c.drawRect(left,top,x(Double.isFinite(stop)?Math.min(piEnd,stop):piEnd,t0,t1,left,right),bottom,p);float mark=x(piEnd,t0,t1,left,right);p.setColor(0xff78badb);c.drawLine(mark,top,mark,bottom,p);}
            if(Double.isFinite(stop)){float mark=x(stop,t0,t1,left,right);p.setColor(0x337e7167);c.drawRect(mark,top,right,bottom,p);p.setColor(0xffd9c5ac);c.drawLine(mark,top,mark,bottom,p);}
            for(int tick=0;tick<=2;tick++){float y=bottom-(bottom-top)*tick/2;p.setColor(0xff505954);c.drawLine(left,y,right,y,p);p.setColor(0xffc2c7c2);c.drawText(String.format(Locale.US,"%.0f",max*tick/2),0,y,p);}
            Path path=new Path(),gaps=new Path();boolean begun=false;float priorX=0,priorY=0;for(int i=0;i<samples.length();i++){JSONObject s=samples.optJSONObject(i);double v=value(s,panel),t=s.optDouble("uptime");if(!Double.isFinite(v)){begun=false;continue;}float px=x(t,t0,t1,left,right),py=(float)(bottom-(bottom-top)*Math.max(0,v)/max);if(!begun||i>0&&t-samples.optJSONObject(i-1).optDouble("uptime")>ReplayTimeline.gapLimit(shot)){if(begun){gaps.moveTo(priorX,priorY);gaps.lineTo(px,py);}path.moveTo(px,py);}else path.lineTo(px,py);begun=true;priorX=px;priorY=py;}
            p.setColor(panel==0?0xffa9c397:0xffefb48d);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);int save=c.save();if(Double.isFinite(replayTime))c.clipRect(left,top,x(replayTime,t0,t1,left,right),bottom);c.drawPath(path,p);drawGap(c,gaps,d);c.restoreToCount(save);p.setStyle(Paint.Style.FILL);
            if(Double.isFinite(replayTime)){p.setColor(0xfff2f3ef);float cursor=x(replayTime,t0,t1,left,right);c.drawLine(cursor,top,cursor,bottom,p);if(shot!=null)try{ReplayTimeline timeline=new ReplayTimeline(shot);double v=timeline.at(replayTime-timeline.start)[panel];if(Double.isFinite(v)){p.setColor(panel==0?0xffa9c397:0xffefb48d);c.drawCircle(cursor,(float)(bottom-(bottom-top)*Math.max(0,v)/max),3*d,p);}}catch(IllegalArgumentException ignored){}}
            p.setColor(0xffc2c7c2);for(int tick=0;tick<=2;tick++){double t=t0+(t1-t0)*tick/2;String label=String.format(Locale.US,"%.0fs",t-start);float px=x(t,t0,t1,left,right);if(tick==2)px-=p.measureText(label);c.drawText(label,px,bottom+15*d,p);}
        }
        p.setColor(0xff78badb);String label=enabled?String.format(Locale.US,"Blue: PI setting %d bar · %ds (estimated)",pi.optInt("pressure_choice"),pi.optInt("duration_setting_s")):"Pre-infusion: "+(pi==null?"not recorded":"off");c.drawText(label,10*d,h-18*d,p);
        p.setColor(0xffc2c7c2);c.drawText(diagnostic?"Grey: after brew stop · final drips / settling":"Post-shot samples: Shot options → Diagnostic graph",10*d,h-4*d,p);
    }
    private void drawCombined(Canvas c,float d,float w,float h){
        float left=32*d,right=w-32*d,top=34*d,bottom=h-32*d;
        double start=shot==null?samples.optJSONObject(0).optDouble("uptime"):ReplayTimeline.bound(shot,"start_bounds",samples.optJSONObject(0).optDouble("uptime"));
        double dataEnd=samples.optJSONObject(samples.length()-1).optDouble("uptime");double end=Double.isFinite(comparisonDuration)?start+comparisonDuration:dataEnd;if(end<=start)return;
        double max=50;for(int i=0;i<samples.length();i++)max=Math.max(max,Math.ceil(samples.optJSONObject(i).optDouble("weight_g",0)/10)*10);
        if(Double.isFinite(comparisonMax))max=comparisonMax;
        p.setStyle(Paint.Style.FILL);p.setTextSize(11*d);if(mode!=2){p.setColor(0xffefb48d);c.drawText("Weight · g",left,17*d,p);}if(mode!=1){p.setColor(0xffc4c8c0);String legend="Pressure setting · bar";c.drawText(legend,right-p.measureText(legend),17*d,p);}
        for(int tick=0;tick<=2;tick++){float y=bottom-(bottom-top)*tick/2;p.setColor(0xff505954);c.drawLine(left,y,right,y,p);if(mode!=2){p.setColor(0xffefb48d);c.drawText(String.format(Locale.US,"%.0f",max*tick/2),0,y,p);}if(mode!=1){p.setColor(0xffc4c8c0);c.drawText(Integer.toString(tick*5),right+5*d,y,p);}}
        JSONObject piSetting=shot==null?null:shot.optJSONObject("pre_infusion");double piSeconds=PreInfusionDisplay.seconds(piSetting);
        if(piSeconds>0){float endPi=x(Math.min(start+piSeconds,dataEnd),start,end,left,right);p.setColor(0x2478bcec);c.drawRect(left,top,endPi,bottom,p);p.setColor(0xff78bcec);p.setPathEffect(new DashPathEffect(new float[]{3*d,3*d},0));c.drawLine(endPi,top,endPi,bottom,p);p.setPathEffect(null);}
        Path weightPath=new Path(),gapPath=new Path();boolean begun=false;double prior=0;float priorX=0,priorY=0;
        for(int i=0;i<samples.length();i++){JSONObject row=samples.optJSONObject(i);double t=row.optDouble("uptime"),v=row.optDouble("weight_g");if(!Double.isFinite(v)){begun=false;continue;}float xx=x(t,start,end,left,right),yy=(float)(bottom-(bottom-top)*Math.max(0,v)/max);if(!begun||t-prior>ReplayTimeline.gapLimit(shot)){if(begun){gapPath.moveTo(priorX,priorY);gapPath.lineTo(xx,yy);}weightPath.moveTo(xx,yy);}else weightPath.lineTo(xx,yy);begun=true;prior=t;priorX=xx;priorY=yy;}
        Path pressurePath=new Path();double initial=NominalPressure.at(shot,0);if(Double.isFinite(initial)){pressurePath.moveTo(left,(float)(bottom-(bottom-top)*initial/10));JSONObject pi=shot.optJSONObject("pre_infusion");double duration=pi.optInt("enabled")==1?pi.optDouble("duration_setting_s"):0;if(duration>0&&start+duration<dataEnd){float transition=x(start+duration,start,end,left,right);pressurePath.lineTo(transition,(float)(bottom-(bottom-top)*initial/10));pressurePath.lineTo(transition,bottom-(bottom-top)*.9f);}double last=NominalPressure.at(shot,dataEnd-start);pressurePath.lineTo(x(dataEnd,start,end,left,right),(float)(bottom-(bottom-top)*last/10));}
        int save=c.save();c.clipRect(left,top,Double.isFinite(replayTime)?x(replayTime,start,end,left,right):right,bottom);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);if(mode!=1){p.setColor(0xffc4c8c0);p.setPathEffect(new DashPathEffect(new float[]{5*d,4*d},0));c.drawPath(pressurePath,p);p.setPathEffect(null);}if(mode!=2){p.setColor(0xffefb48d);c.drawPath(weightPath,p);drawGap(c,gapPath,d);}c.restoreToCount(save);p.setStyle(Paint.Style.FILL);
        if(Double.isFinite(replayTime)){p.setColor(0xfff2f3ef);float cursor=x(replayTime,start,end,left,right);c.drawLine(cursor,top,cursor,bottom,p);}
        p.setColor(0xffc2c7c2);for(int tick=0;tick<=2;tick++){double seconds=(end-start)*tick/2;String text=String.format(Locale.US,"%.0fs",seconds);float xx=x(start+seconds,start,end,left,right);if(tick==2)xx-=p.measureText(text);c.drawText(text,xx,bottom+20*d,p);}
    }
    private void drawGap(Canvas c,Path path,float d){p.setColor(0xffe8bb7c);p.setPathEffect(new DashPathEffect(new float[]{5*d,4*d},0));c.drawPath(path,p);p.setPathEffect(null);}
    private double value(JSONObject s,int panel){if(panel==0)return s.optDouble("weight_g");JSONArray r=s.optJSONArray("registers");return s.has("p1_w")?s.optDouble("p1_w"):r==null?Double.NaN:r.optDouble(51)/10.0;}
}
