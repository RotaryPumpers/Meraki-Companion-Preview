package com.rotarypumpers.meraki;
import android.app.*;import android.widget.*;import android.view.*;
/** UI only: no connection, cancellation, retry or machine commands. */
final class RecipeProgressDialog {
 private final Activity owner;private AlertDialog dialog;private TextView step;
 RecipeProgressDialog(Activity activity){owner=activity;}
 void show(String message){
  if(owner.isFinishing()||owner.isDestroyed())return;
  if(dialog!=null){step.setText(message);return;}
  android.content.Context context=LightDialogs.context(owner);
  LinearLayout panel=new LinearLayout(context);panel.setOrientation(1);int pad=Math.round(18*owner.getResources().getDisplayMetrics().density);panel.setPadding(pad,pad/2,pad,pad);
  LinearLayout status=new LinearLayout(context);status.setGravity(Gravity.CENTER_VERTICAL);ProgressBar spinner=new ProgressBar(context);spinner.setIndeterminate(true);int size=Math.round(30*owner.getResources().getDisplayMetrics().density);status.addView(spinner,new LinearLayout.LayoutParams(size,size));
  step=new TextView(context);step.setTextSize(16);step.setText(message);step.setPadding(pad,0,0,0);status.addView(step,new LinearLayout.LayoutParams(0,-2,1));panel.addView(status);
  TextView hint=new TextView(context);hint.setTextSize(12);hint.setText("Keep Companion open while the machine updates.");hint.setPadding(0,pad,0,0);panel.addView(hint);
  dialog=LightDialogs.builder(owner).setTitle("Applying recipe").setView(panel).setCancelable(false).create();dialog.setCanceledOnTouchOutside(false);dialog.show();
 }
 void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;step=null;}}
}
