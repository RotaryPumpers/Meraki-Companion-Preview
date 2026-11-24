package com.rotarypumpers.meraki;
import android.content.*;import android.net.Uri;import android.graphics.*;import android.graphics.drawable.Drawable;import android.media.ExifInterface;import org.json.*;import java.io.*;
import com.rotarypumpers.meraki.core.RecipeBackground;
final class RecordCardAppearance {
 static byte[] photo(Context context,Uri uri)throws Exception{
  File tmp=File.createTempFile("recipe-photo-",".image",context.getCacheDir());Bitmap decoded=null,oriented=null;
  try{
   try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(tmp)){if(in==null)throw new IOException("Cannot open this photo");byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>20*1024*1024)throw new IOException("Choose a photo smaller than 20 MB");out.write(b,0,n);}}
   BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(tmp.toString(),bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>30000||bounds.outHeight>30000||(long)bounds.outWidth*bounds.outHeight>250000000)throw new IOException("Unsupported photo size");
   BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1200)options.inSampleSize*=2;decoded=BitmapFactory.decodeFile(tmp.toString(),options);if(decoded==null)throw new IOException("Could not read that photo");
   int rotation=ExifInterface.ORIENTATION_NORMAL;try{rotation=new ExifInterface(tmp.toString()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
   Matrix m=new Matrix();switch(rotation){case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:m.setScale(-1,1);break;case ExifInterface.ORIENTATION_ROTATE_180:m.setRotate(180);break;case ExifInterface.ORIENTATION_FLIP_VERTICAL:m.setScale(1,-1);break;case ExifInterface.ORIENTATION_TRANSPOSE:m.setRotate(90);m.postScale(-1,1);break;case ExifInterface.ORIENTATION_ROTATE_90:m.setRotate(90);break;case ExifInterface.ORIENTATION_TRANSVERSE:m.setRotate(270);m.postScale(-1,1);break;case ExifInterface.ORIENTATION_ROTATE_270:m.setRotate(270);break;}
   oriented=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),m,true);
   for(int attempts=0;attempts<5;attempts++){for(int quality=85;quality>=45;quality-=10){ByteArrayOutputStream out=new ByteArrayOutputStream();if(!oriented.compress(Bitmap.CompressFormat.JPEG,quality,out))throw new IOException("Cannot prepare photo");if(out.size()<=RecipeMediaStore.MAX_BYTES)return out.toByteArray();}Bitmap smaller=Bitmap.createScaledBitmap(oriented,Math.max(1,oriented.getWidth()*3/4),Math.max(1,oriented.getHeight()*3/4),true);if(oriented!=decoded)oriented.recycle();oriented=smaller;}
   throw new IOException("Choose a simpler or smaller product photo");
  }finally{tmp.delete();if(oriented!=null&&oriented!=decoded)oriented.recycle();if(decoded!=null)decoded.recycle();}
 }
 static Drawable background(Context context,JSONObject recipe){
  String mode=RecipeBackground.mode(recipe);if(mode.equals("standard"))return null;
  Bitmap bitmap=null;if(mode.equals("photo")){try{RecipeMediaStore media=new RecipeMediaStore(context.getFilesDir());byte[] bytes=media.read(recipe.optString("record_photo"));BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(o.outWidth>0&&o.outHeight>0&&o.outWidth<=1600&&o.outHeight<=1600)bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Exception|OutOfMemoryError ignored){}}
  return new CardDrawable(bitmap,recipe!=null&&!recipe.optString("home_color","standard").equals("standard")?RecipeBackground.homeColor(recipe):CompanionPalette.CARD,context.getResources().getDisplayMetrics().density);
 }
 static final class CardDrawable extends Drawable{
  private final Bitmap photo;private final int color;private final float d;private final Paint paint=new Paint(3);private final Path clip=new Path();
  CardDrawable(Bitmap b,int c,float density){photo=b;color=c;d=density;}
  public void draw(Canvas c){Rect r=getBounds();RectF box=new RectF(r);clip.reset();clip.addRoundRect(box,10*d,10*d,Path.Direction.CW);int save=c.save();c.clipPath(clip);paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawRect(r,paint);
   if(photo!=null){float scale=Math.max((float)r.width()/photo.getWidth(),(float)r.height()/photo.getHeight()),w=photo.getWidth()*scale,h=photo.getHeight()*scale;paint.setColor(Color.WHITE);c.drawBitmap(photo,null,new RectF(r.centerX()-w/2,r.centerY()-h/2,r.centerX()+w/2,r.centerY()+h/2),paint);paint.setColor(0xc9000000);c.drawRect(r,paint);}
   c.restoreToCount(save);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(0xff69766d);c.drawRoundRect(new RectF(r.left+d/2,r.top+d/2,r.right-d/2,r.bottom-d/2),10*d,10*d,paint);paint.setStyle(Paint.Style.FILL);
  }
  public void setAlpha(int alpha){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
