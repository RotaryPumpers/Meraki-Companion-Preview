package com.rotarypumpers.meraki;
import android.content.Context;import android.graphics.*;import android.view.View;import android.animation.ValueAnimator;
/** Native vector; no WebView, network asset or recording-thread animation. */
public final class WaterDropView extends View {
 private final Paint paint=new Paint(3);private final Path drop=new Path();private ValueAnimator animator;private boolean pulse;private float glow=.65f;
 public WaterDropView(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 public void pulse(boolean on){if(pulse==on)return;pulse=on;update();invalidate();}
 private void update(){if(animator!=null){animator.cancel();animator=null;}glow=.65f;
  if(pulse&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&getVisibility()==VISIBLE&&ValueAnimator.areAnimatorsEnabled()){
   animator=ValueAnimator.ofFloat(.45f,1f);animator.setDuration(1100);animator.setRepeatMode(ValueAnimator.REVERSE);animator.setRepeatCount(ValueAnimator.INFINITE);animator.addUpdateListener(a->{glow=(float)a.getAnimatedValue();invalidate();});animator.start();}}
 protected void onAttachedToWindow(){super.onAttachedToWindow();update();}
 protected void onDetachedFromWindow(){if(animator!=null){animator.cancel();animator=null;}super.onDetachedFromWindow();}
 protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);update();}
 protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/64f,getHeight()/72f);
  paint.setShader(null);paint.setColor(pulse?0xff55bfff:0xff9d9b98);paint.setAlpha((int)(30*glow));c.drawCircle(32,43,29,paint);
  drop.reset();drop.moveTo(32,5);drop.cubicTo(28,21,10,34,10,47);drop.cubicTo(10,74,54,74,54,47);drop.cubicTo(54,34,36,21,32,5);drop.close();
  paint.setAlpha(255);paint.setShader(new LinearGradient(15,18,50,65,pulse?0xffa9ecff:0xffbbb9b5,pulse?0xff1588d0:0xff777571,Shader.TileMode.CLAMP));c.drawPath(drop,paint);
  paint.setShader(null);paint.setColor(0xccf1fbff);paint.setStrokeWidth(3);paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(20,40,17,49,paint);paint.setColor(pulse?0xff75d4ff:0xffaaa8a4);paint.setAlpha((int)(180*glow));c.drawCircle(7,29,2,paint);c.drawCircle(57,36,2.5f,paint);c.restore();}
}
