package com.rotarypumpers.meraki;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.*;
import com.rotarypumpers.meraki.core.*;

/** Reviewed edits to past brand attribution; no recording or machine operations. */
final class BrandAssignmentController {
 private final Activity owner;private final UsageTotals usage;private final Supplier<JSONArray> journal;
 private final BooleanSupplier locked;private final Executor worker;
 BrandAssignmentController(Activity owner,UsageTotals usage,Supplier<JSONArray> journal,BooleanSupplier locked,Executor worker){this.owner=owner;this.usage=usage;this.journal=journal;this.locked=locked;this.worker=worker;}
 private boolean ready(){if(locked.getAsBoolean()){message("Disconnect recording before assigning brands");return false;}return true;}
 private void message(String text){Toast.makeText(owner,text,Toast.LENGTH_LONG).show();}
 void month(YearMonth month,Runnable done){
  if(!ready())return;
  LightDialogs.builder(owner).setTitle("Assign brand")
   .setItems(new String[]{"Whole month…","Choose shots…"},(d,w)->{if(w==0)entry(month,null,done);else chooseShots(month,done);})
   .setNegativeButton("Cancel",null).show();
 }
 void shot(JSONObject shot,Runnable done){if(!ready())return;entry(null,Collections.singleton(shot.optString("start_utc")),done);}
 private void chooseShots(YearMonth month,Runnable done){
  if(!ready())return;
  List<JSONObject> shots=new ArrayList<>();ZoneId zone=ZoneId.systemDefault();
  JSONArray source=journal.get();for(int i=0;i<source.length();i++){
   JSONObject s=source.optJSONObject(i);if(s==null||s.optBoolean("demo")||!ShotInsights.countedBrew(s))continue;
   try{if(YearMonth.from(Instant.parse(s.getString("start_utc")).atZone(zone)).equals(month))shots.add(s);}catch(Exception ignored){}
  }
  shots.sort((a,b)->b.optString("start_utc").compareTo(a.optString("start_utc")));
  if(shots.isEmpty()){message("No saved shots in this month. Whole month can still label counted history.");return;}
  String[] labels=new String[shots.size()];boolean[] selected=new boolean[shots.size()];
  for(int i=0;i<shots.size();i++){
   JSONObject s=shots.get(i);String brand="";try{brand=usage.shotBrand(s);}catch(Exception ignored){}
   labels[i]=JournalBrowse.localTime(s.optString("start_utc"))+"\n"+JournalBrowse.name(s)+" · "+(brand.isEmpty()?"No brand":brand);
  }
  AlertDialog dialog=LightDialogs.builder(owner).setTitle("Choose shots")
   .setMultiChoiceItems(labels,selected,(d,w,on)->selected[w]=on)
   .setPositiveButton("Continue",null).setNegativeButton("Cancel",null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
   Set<String> ids=new LinkedHashSet<>();for(int i=0;i<selected.length;i++)if(selected[i])ids.add(shots.get(i).optString("start_utc"));
   if(ids.isEmpty()){message("Select at least one shot");return;}dialog.dismiss();entry(null,ids,done);
  }));dialog.show();
 }
 private void entry(YearMonth month,Set<String> ids,Runnable done){
  if(!ready())return;
  Context context=LightDialogs.context(owner);LinearLayout form=new LinearLayout(context);form.setOrientation(1);int pad=Math.round(18*owner.getResources().getDisplayMetrics().density);form.setPadding(pad,pad/2,pad,pad/2);
  TextView scope=new TextView(context);scope.setText(month!=null?month.format(DateTimeFormatter.ofPattern("MMMM yyyy")):ids.size()+" selected shot"+(ids.size()==1?"":"s"));form.addView(scope);
  EditText name=new EditText(context);name.setSingleLine(true);name.setHint("Brand / roaster");name.setContentDescription("Brand / roaster");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});form.addView(name);
  if(ids!=null&&ids.size()==1){try{for(int i=0;i<journal.get().length();i++){JSONObject s=journal.get().getJSONObject(i);if(ids.contains(s.optString("start_utc"))){name.setText(usage.shotBrand(s));break;}}}catch(Exception ignored){}}
  CheckBox missing=new CheckBox(context);missing.setText("Only records without a brand");missing.setChecked(true);if(month!=null)form.addView(missing);
  TextView hint=new TextView(context);hint.setText(month!=null?"Includes counted shots and measured grinds dated in this month, including history no longer in the journal. Uncheck to replace existing brands too.":"Also labels each selected shot’s linked measured grind, even if it occurred in the previous month.");form.addView(hint);
  TextView future=new TextView(context);future.setText("This updates past records. For future recordings, set the brand in the recipe’s Bean / bag section.");future.setPadding(0,pad/2,0,0);form.addView(future);
  ScrollView scroll=new ScrollView(context);scroll.addView(form);
  AlertDialog dialog=LightDialogs.builder(owner).setTitle("Assign brand").setView(scroll)
   .setPositiveButton("Review",null).setNegativeButton("Cancel",null).create();
  dialog.setOnShowListener(v->{dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
   if(!ready())return;
   try{
    UsageTotals.BrandPlan plan=month!=null?usage.planMonth(month,ZoneId.systemDefault(),name.getText().toString(),missing.isChecked()):usage.planShots(journal.get(),ids,name.getText().toString());
    dialog.dismiss();review(plan,month,done);
   }catch(Exception e){name.setError(e.getMessage());}
  });});dialog.show();
 }
 private void review(UsageTotals.BrandPlan plan,YearMonth month,Runnable done){
  String scope=month==null?"Selected shots and linked grinds":month.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
  String note=scope+"\n\nBrand: "+plan.brand+"\n"+plan.summary()+"\n\n"+(plan.replaced>0?plan.replaced+" existing brand labels will be replaced.\n\n":"")+"Shot counts, weights, graphs and recorded bag dates stay unchanged.";
  AlertDialog dialog=LightDialogs.builder(owner).setTitle("Confirm brand assignment").setMessage(note)
   .setPositiveButton("Assign brand",null).setNegativeButton("Cancel",null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
   if(!ready())return;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);dialog.setCancelable(false);
   worker.execute(()->{
    try{if(locked.getAsBoolean())throw new IllegalStateException("Disconnect recording first");usage.assign(plan);owner.runOnUiThread(()->{dialog.dismiss();message("Brand assigned: "+plan.summary());if(!owner.isFinishing())done.run();});}
    catch(Exception e){owner.runOnUiThread(()->{dialog.dismiss();LightDialogs.builder(owner).setTitle("Brand not changed").setMessage(e.getMessage()).setPositiveButton("Close",null).show();});}
   });
  }));dialog.show();
 }
}
