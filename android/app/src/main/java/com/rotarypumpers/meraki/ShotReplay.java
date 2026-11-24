package com.rotarypumpers.meraki;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import android.graphics.Typeface;import android.graphics.drawable.*;import org.json.*;import java.util.Locale;
final class ShotReplay extends Dialog {
 private final ReplayTimeline timeline;private ReplayTimeline referenceTimeline;private ComparisonTimeline comparison;private JSONObject reference;private ShotChart referenceChart;private TextView referenceNumbers;private final JSONObject shot;private final Handler handler=new Handler(Looper.getMainLooper());private boolean playing;private double position,speed=1;private long tickTime;private TextView numbers,clock,phase;private Button play;private SeekBar seek;private ShotChart chart;
 ShotReplay(Activity activity,JSONObject s){super(activity);shot=s;timeline=new ReplayTimeline(s);}
 ShotReplay(Activity activity,JSONObject s,JSONObject r){this(activity,s);reference=r;comparison=new ComparisonTimeline(s,r);referenceTimeline=comparison.reference;}
 private double playbackDuration(){return referenceTimeline==null?timeline.duration():comparison.duration();}
 private int dp(int n){return (int)(n*getContext().getResources().getDisplayMetrics().density);}
 private TextView label(String s,int size){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(0xfff2f3ef);t.setTypeface(getContext().getResources().getFont(R.font.space_grotesk));t.setPadding(0,dp(6),0,dp(6));return t;}
 private Button button(String s){Button b=new Button(getContext());b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setSingleLine();b.setTextColor(0xffefb48d);GradientDrawable bg=new GradientDrawable();bg.setColor(0xff242a27);bg.setCornerRadius(dp(10));bg.setStroke(dp(1),0xff505954);b.setBackground(bg);return b;}
 protected void onCreate(Bundle b){super.onCreate(b);requestWindowFeature(Window.FEATURE_NO_TITLE);ScrollView scroll=new ScrollView(getContext());scroll.setBackgroundColor(0xff343a36);LinearLayout root=new LinearLayout(getContext());root.setOrientation(1);root.setPadding(dp(18),dp(12),dp(18),dp(18));scroll.addView(root);setContentView(scroll);
  root.addView(label(reference==null?"Shot replay":"Compare shots",24));if(shot.optJSONObject("recipe")!=null)root.addView(label(shot.optJSONObject("recipe").optString("name"),16));root.addView(label((reference==null?"Saved shot":"Current shot · "+shot.optString("start_utc"))+" · drag the timeline to explore",12));
  numbers=label("",28);root.addView(numbers);clock=label("",14);root.addView(clock);phase=label("",12);root.addView(phase);
  chart=new ShotChart(getContext());chart.shot(shot);root.addView(chart,new LinearLayout.LayoutParams(-1,dp(reference==null?280:220)));
  if(reference!=null){
   root.addView(label("Reference · "+(reference.optJSONObject("recipe")==null?"Saved shot":reference.optJSONObject("recipe").optString("name")),16));
   root.addView(label(reference.optString("start_utc")+" · "+ShotAssessment.result(reference),12));
   referenceNumbers=label("",24);root.addView(referenceNumbers);referenceChart=new ShotChart(getContext());referenceChart.shot(reference);root.addView(referenceChart,new LinearLayout.LayoutParams(-1,dp(220)));
   double commonMax=Math.max(ShotChart.weightScale(shot),ShotChart.weightScale(reference));chart.comparisonScale(playbackDuration(),commonMax);referenceChart.comparisonScale(playbackDuration(),commonMax);
   root.addView(label("Current minus reference: "+ShotAssessment.difference(shot,reference),13));
   root.addView(label("Both graphs use the same scales and start at brew start. The shorter shot stops at its own end.",12));
  }
  String assessment=ShotAssessment.summary(shot);if(!assessment.isEmpty())root.addView(label(assessment,14));
  seek=new SeekBar(getContext());seek.setMax(1000);seek.setContentDescription("Shot replay timeline");root.addView(seek);
  LinearLayout controls=new LinearLayout(getContext());play=button("Play");play.setTextColor(0xffa9c397);Button restart=button("Restart"),rate=button("1×");for(Button v:new Button[]{play,restart,rate}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(3),dp(6),dp(3),dp(6));controls.addView(v,lp);}root.addView(controls);
  play.setOnClickListener(v->{if(playing)pause();else{if(position>=playbackDuration())position=0;playing=true;play.setText("Pause");tickTime=SystemClock.uptimeMillis();handler.post(tick);}});
  restart.setOnClickListener(v->{pause();position=0;update();});rate.setOnClickListener(v->{speed=speed==1?2:speed==2?.5:1;rate.setText(speed==.5?"0.5×":speed==1?"1×":"2×");tickTime=SystemClock.uptimeMillis();});
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){pause();}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(user){position=playbackDuration()*p/1000;update();}}});
  root.addView(label(shot.has("settled_yield_g")?String.format(Locale.US,"Settled final yield: %.1f g",shot.optDouble("settled_yield_g")):"Settled final yield unavailable",14));
  root.addView(label("Amber dashes mark missing data; replay values stay blank there. Playback ends at brew stop.",12));
  Button close=button("Close");close.setOnClickListener(v->dismiss());root.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));update();
 }
 private final Runnable tick=new Runnable(){public void run(){if(!playing)return;long now=SystemClock.uptimeMillis();position=Math.min(playbackDuration(),position+(now-tickTime)/1000.0*speed);tickTime=now;update();if(position>=playbackDuration())pause();else handler.postDelayed(this,33);}};
 void pause(){playing=false;handler.removeCallbacks(tick);if(play!=null)play.setText(position>=playbackDuration()?"Replay":"Play");}
 private void update(){double[] values=timeline.at(position);numbers.setText((Double.isFinite(values[0])?String.format(Locale.US,"%.1f g",values[0]):"— g")+"  /  "+(Double.isFinite(NominalPressure.at(shot,position))?String.format(Locale.US,"%.0f bar",NominalPressure.at(shot,position)):"— bar"));clock.setText(String.format(Locale.US,"%.1f / %.1f sec",position,playbackDuration()));JSONObject pi=shot.optJSONObject("pre_infusion");phase.setText(position>=timeline.duration()?"Brew ended":!Double.isFinite(values[0])?"No sample at this time":pi!=null&&pi.optInt("enabled")==1&&position<pi.optDouble("duration_setting_s")?"Pre-infusion":"Brewing");chart.replayAt(timeline.start+Math.min(position,timeline.duration()));
 if(referenceTimeline!=null){double[] r=referenceTimeline.at(Math.min(position,referenceTimeline.duration()));referenceNumbers.setText(position>=referenceTimeline.duration()?"Brew ended · "+ShotAssessment.result(reference):Double.isFinite(r[0])?String.format(Locale.US,"%.1f g · %.1f s",r[0],position):"Missing sample · "+String.format(Locale.US,"%.1f s",position));referenceChart.replayAt(referenceTimeline.start+Math.min(position,referenceTimeline.duration()));if(position>=timeline.duration())numbers.setText("Brew ended · "+ShotAssessment.result(shot));}
 seek.setProgress((int)(1000*position/playbackDuration()));}
 protected void onStart(){super.onStart();getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(!playing&&position==0){playing=true;play.setText("Pause");tickTime=SystemClock.uptimeMillis();handler.post(tick);}}
 protected void onStop(){pause();super.onStop();}
}
