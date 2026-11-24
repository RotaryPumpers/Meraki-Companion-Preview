package com.rotarypumpers.meraki;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Light surfaces for app-owned dialogs; does not change the recording screens. */
final class LightDialogs {
    private static final int INK=0xff20261e, MUTED=0xff586052, GREEN=0xff35542d;
    static Context context(Context owner){return new ContextThemeWrapper(owner,R.style.CompanionLightDialog);}
    static AlertDialog.Builder builder(Context owner){return new Builder(owner);}
    private static final class Builder extends AlertDialog.Builder {
        Builder(Context owner){super(owner,R.style.CompanionLightDialog);setIcon(owner.getApplicationInfo().loadIcon(owner.getPackageManager()));}
        @Override public AlertDialog.Builder setItems(CharSequence[] items,android.content.DialogInterface.OnClickListener listener){
            return super.setAdapter(new MenuRows(getContext(),items),listener);
        }
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();ListView list=dialog.getListView();
            if(list!=null){list.setDivider(null);list.setDividerHeight(0);
                list.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{
                    for(int i=0;i<list.getChildCount();i++){
                        View row=list.getChildAt(i);
                        if(row instanceof CheckedTextView && !(row.getBackground() instanceof android.graphics.drawable.InsetDrawable)){
                            CheckedTextView checked=(CheckedTextView)row;checked.setTextColor(GREEN);checked.setTextSize(16);
                            checked.setBackground(new android.graphics.drawable.InsetDrawable(rowSurface(),dp(getContext(),12),dp(getContext(),3),dp(getContext(),12),dp(getContext(),3)));
                            checked.setMinHeight(dp(getContext(),52));
                        }
                    }
                });
            }
            dialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                public void onViewAttachedToWindow(View v){
                    int id=getContext().getResources().getIdentifier("alertTitle","id","android");
                    TextView title=dialog.findViewById(id);if(title!=null)title.setTextSize(18);
                }
                public void onViewDetachedFromWindow(View v){}
            });
            return dialog;
        }
        @Override public AlertDialog.Builder setView(View view){prepare(view);return super.setView(view);}
    }
    private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    private static GradientDrawable rowSurface(){GradientDrawable s=new GradientDrawable();s.setColor(0xffe8efdf);s.setCornerRadius(12);return s;}
    private static final class MenuRows extends BaseAdapter {
        private final Context owner;private final CharSequence[] items;
        MenuRows(Context c,CharSequence[] values){owner=c;items=values.clone();}
        public int getCount(){return items.length;}
        public Object getItem(int i){return items[i];}
        public long getItemId(int i){return i;}
        public View getView(int i,View recycled,ViewGroup parent){
            FrameLayout frame=new FrameLayout(owner);frame.setPadding(dp(owner,16),dp(owner,3),dp(owner,16),dp(owner,3));
            TextView label=new TextView(owner);label.setText(items[i]);label.setTextColor(GREEN);label.setTextSize(16);
            label.setGravity(android.view.Gravity.CENTER_VERTICAL);label.setMinHeight(dp(owner,48));
            label.setPadding(dp(owner,16),dp(owner,10),dp(owner,16),dp(owner,10));label.setBackground(rowSurface());
            frame.addView(label,new FrameLayout.LayoutParams(-1,-2));return frame;
        }
    }
    private static void prepare(View view){
        restyle(view);
        // Some menus append their action buttons after create() and before show().
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){restyle(v);}
            public void onViewDetachedFromWindow(View v){}
        });
    }
    private static void restyle(View view){
        if(view instanceof LinearLayout || view instanceof ScrollView || view instanceof FrameLayout)
            view.setBackgroundColor(Color.TRANSPARENT);
        if(view instanceof TextView){
            TextView text=(TextView)view;text.setTextColor(INK);text.setHintTextColor(MUTED);
        }
        if(view instanceof EditText){view.setBackgroundTintList(ColorStateList.valueOf(GREEN));}
        if(view instanceof CompoundButton){((CompoundButton)view).setButtonTintList(ColorStateList.valueOf(GREEN));}
        if(view instanceof Button && !(view instanceof CompoundButton)){
            GradientDrawable surface=new GradientDrawable();surface.setColor(0xffe8efdf);
            surface.setCornerRadius(12*view.getResources().getDisplayMetrics().density);
            view.setBackground(surface);((Button)view).setTextColor(GREEN);((Button)view).setTextSize(16);
            if(view.getLayoutParams() instanceof LinearLayout.LayoutParams){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)view.getLayoutParams();lp.topMargin=dp(view.getContext(),3);lp.bottomMargin=dp(view.getContext(),3);view.setLayoutParams(lp);}
        }
        if(view instanceof NumberPicker && android.os.Build.VERSION.SDK_INT>=29)
            ((NumberPicker)view).setTextColor(INK);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)restyle(group.getChildAt(i));}
    }
    private LightDialogs(){}
}
