package com.rotarypumpers.meraki;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.SystemClock;
import android.view.View;
import android.animation.ValueAnimator;
import java.io.InputStream;
/** Small local animation only; never runs on the recording worker or opens a WebView. */
public final class InlineReminderView extends View {
 private Movie movie;private String kind="";private boolean playing;private long start;
 public InlineReminderView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 public void show(String next,boolean animate){
  if(!kind.equals(next)){kind=next;movie=null;start=SystemClock.uptimeMillis();
   if(!next.isEmpty())try(InputStream in=getContext().getAssets().open("inline-"+next+".gif")){movie=Movie.decodeStream(in);}catch(java.io.IOException ignored){}
   invalidate();
  }
  if(playing!=animate){playing=animate;invalidate();}
 }
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(movie==null)return;
  boolean active=playing&&isShown()&&getWindowVisibility()==VISIBLE&&ValueAnimator.areAnimatorsEnabled();
  int duration=Math.max(1,movie.duration());movie.setTime(active?(int)((SystemClock.uptimeMillis()-start)%duration):0);
  float scale=Math.min(getWidth()/(float)movie.width(),getHeight()/(float)movie.height());
  canvas.save();canvas.translate((getWidth()-movie.width()*scale)/2,(getHeight()-movie.height()*scale)/2);canvas.scale(scale,scale);movie.draw(canvas,0,0);canvas.restore();
  if(active)postInvalidateDelayed(40);
 }
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate();}
 @Override protected void onDetachedFromWindow(){playing=false;super.onDetachedFromWindow();}
}
