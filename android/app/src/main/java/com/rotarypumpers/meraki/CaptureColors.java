package com.rotarypumpers.meraki;
import android.graphics.Bitmap;
import com.rotarypumpers.meraki.core.ResultsPalette;
import android.graphics.BitmapFactory;
import org.json.*;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** One-time correction for screenshots saved by the pre-0.2.3 RGB decoder. */
final class CaptureColors {
    static boolean upgrade(JSONArray shots) throws JSONException {
        boolean changed=false;
        for(int i=0;i<shots.length();i++)changed=upgradeCapture(shots.getJSONObject(i).optJSONObject("screen_capture"))||changed;
        return changed;
    }
    static boolean upgradeCapture(JSONObject capture) throws JSONException {
        if(capture==null||!capture.has("png_base64")||capture.optString("presentation").equals("meraki-results-v1"))return false;
        Bitmap original=null,corrected=null;
        try {
            byte[] bytes=Base64.getDecoder().decode(capture.getString("png_base64"));
            original=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            if(original==null||original.getWidth()!=480||original.getHeight()!=480)return false;
            int[] pixels=new int[480*480];original.getPixels(pixels,0,480,0,0,480,480);
            boolean legacy=!capture.has("pixel_format");
            if(legacy)for(int i=0;i<pixels.length;i++)pixels[i]=swapRedBlue(pixels[i]);
            boolean styled=ResultsPalette.apply(pixels,480,480);
            if(!legacy&&!styled)return false;
            corrected=Bitmap.createBitmap(pixels,480,480,Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(!corrected.compress(Bitmap.CompressFormat.PNG,100,out))return false;
            if(!capture.has("original_png_base64"))capture.put("original_png_base64",capture.getString("png_base64"));
            if(styled)capture.put("presentation","meraki-results-v1");
            capture.put("png_base64",Base64.getEncoder().encodeToString(out.toByteArray()));
            capture.put("pixel_format","argb8888-v2");
            return true;
        } catch(IllegalArgumentException e){return false;}
        finally {if(original!=null)original.recycle();if(corrected!=null)corrected.recycle();}
    }
    static int swapRedBlue(int c){return (c&0xff00ff00)|((c&255)<<16)|((c>>>16)&255);}
}
