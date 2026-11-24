package com.rotarypumpers.meraki;
import org.json.*;
public class ReplayTest{
 static JSONObject row(double t,double w)throws Exception{return new JSONObject().put("uptime",t).put("weight_g",w).put("p1_w",w*10);}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args)throws Exception{
 JSONObject s=new JSONObject().put("samples",new JSONArray().put(row(10,0)).put(row(11,10)).put(row(16,20)).put(row(17,30))).put("start_bounds",new JSONArray("[9,10]")).put("stop_bounds",new JSONArray("[16,17]"));String original=s.toString();ReplayTimeline t=new ReplayTimeline(s);
 check(t.duration()==7);check(Double.isNaN(t.at(0)[0]));check(t.at(1)[0]==5);check(Double.isNaN(t.at(4)[0]));check(t.at(7)[0]==25);check(t.at(100)[0]==25);check(s.toString().equals(original));
 JSONObject partial=new JSONObject().put("samples",s.getJSONArray("samples"));check(new ReplayTimeline(partial).end==17);
 JSONObject old=new JSONObject().put("registers",new JSONArray());for(int i=0;i<52;i++)old.getJSONArray("registers").put(i==51?1234:0);check(ReplayTimeline.power(old)==123.4);
 boolean rejected=false;try{new ReplayTimeline(new JSONObject().put("samples",new JSONArray().put(row(1,1))));}catch(IllegalArgumentException e){rejected=true;}check(rejected);System.out.println("10 replay timing, gap, legacy-power and data-preservation checks passed");
 }
}
