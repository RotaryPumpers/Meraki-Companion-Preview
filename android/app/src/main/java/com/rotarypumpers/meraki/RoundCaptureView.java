package com.rotarypumpers.meraki;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Presentation only: the original PNG remains unchanged. */
final class RoundCaptureView extends View {
    private final Bitmap bitmap;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final Path clip=new Path();
    RoundCaptureView(Context c,Bitmap b){super(c);bitmap=b;setContentDescription("Saved round Meraki results screen");}
    @Override protected void onMeasure(int w,int h){int size=MeasureSpec.getSize(w);if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)size=Math.min(size,MeasureSpec.getSize(h));setMeasuredDimension(size,size);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=Math.min(getWidth(),getHeight());float pad=8*getResources().getDisplayMetrics().density;RectF r=new RectF(pad,pad,size-pad,size-pad);clip.reset();clip.addOval(r,Path.Direction.CW);int save=canvas.save();canvas.clipPath(clip);canvas.drawColor(Color.BLACK);canvas.drawBitmap(bitmap,null,r,paint);canvas.restoreToCount(save);}
}
