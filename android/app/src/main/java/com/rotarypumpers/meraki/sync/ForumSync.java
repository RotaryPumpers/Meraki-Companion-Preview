package com.rotarypumpers.meraki.sync;

import org.json.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Manual, HTTPS-only uploads. Credentials are never sent through redirects. */
public final class ForumSync {
    public static String fingerprint(String key)throws Exception {
        validateKey(key);
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(int i=0;i<8;i++){if(i>0&&i%2==0)out.append('-');out.append(String.format(java.util.Locale.ROOT,"%02X",digest[i]&255));}return out.toString();
    }
    public static String normalizeBase(String input)throws IOException {
        try {
            URI u=new URI(input.trim());String path=u.getRawPath();
            if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw new IOException("Enter the HTTPS forum address, without a page or query.");
            if(path==null)path="";while(path.endsWith("/"))path=path.substring(0,path.length()-1);
            if(path.contains("%")||path.contains("..")||path.contains("\\")||path.endsWith(".php"))throw new IOException("Use the forum home address.");
            return "https://"+u.getHost().toLowerCase(java.util.Locale.ROOT)+path;
        }catch(URISyntaxException e){throw new IOException("Invalid forum address");}
    }
    public static void validateKey(String key)throws IOException {if(!key.matches("rpj_[a-f0-9]{64}"))throw new IOException("Paste the complete connection key from My Shot Journal.");}
    public static JSONObject project(JSONObject in)throws JSONException {
        JSONObject out=new JSONObject();
        String[] keys={"start_utc","complete","finished","capture_quality","completion_reason","notes","source","dose_target_g","yield_target_g","brew_target_c","grind_setting","duration_estimate_s","settled_yield_g","start_bounds","stop_bounds","duration_bounds_s","pre_infusion","sampling_gaps","screen_capture"};
        for(String k:keys)if(in.has(k))out.put(k,in.get(k));
        if(out.optJSONObject("screen_capture")!=null){JSONObject c=new JSONObject(out.getJSONObject("screen_capture").toString());c.remove("original_png_base64");out.put("screen_capture",c);}
        JSONArray samples=in.getJSONArray("samples"),clean=new JSONArray();
        for(int i=0;i<samples.length();i++){JSONObject s=samples.getJSONObject(i),r=new JSONObject();for(String k:new String[]{"uptime","weight_g","p1_w","brew_active","flow_raw"})if(s.has(k))r.put(k,s.get(k));if(!r.has("p1_w")&&s.optJSONArray("registers")!=null&&s.getJSONArray("registers").length()>51)r.put("p1_w",s.getJSONArray("registers").getDouble(51)/10.0);clean.put(r);}out.put("samples",clean);return out;
    }
    public JSONObject request(String base,String key,JSONObject body)throws Exception {
        validateKey(key);URL url=new URL(normalizeBase(base)+"/rp-journal-sync.php");
        byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);if(data.length>2097152)throw new IOException("This shot exceeds the 2 MB sync limit.");
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        try {
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-RP-Journal-Key",key);c.setFixedLengthStreamingMode(data.length);
            try(OutputStream out=c.getOutputStream()){out.write(data);}
            int code=c.getResponseCode();
            if(code>=300&&code<400)throw new IOException("The forum redirected the request. Use its exact HTTPS address (including www if used).");
            if(code==429)throw new IOException("Forum is busy. Wait a few seconds and sync again; duplicates are safe.");
            InputStream stream=code>=400?c.getErrorStream():c.getInputStream();if(stream==null)throw new IOException("Forum returned HTTP "+code);
            String content;
            try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>65536)throw new IOException("Unexpected forum response");out.write(buf,0,n);}content=out.toString("UTF-8");}
            JSONObject result;try{result=new JSONObject(content);}catch(JSONException e){throw new IOException("Forum did not return sync data (HTTP "+code+"). Check the add-on and Cloudflare rules.");}
            if(code!=200)throw new IOException(result.optString("error","Forum returned HTTP "+code));
            if(result.optInt("protocol")!=1||result.optInt("user_id")<=0||result.optString("username").isEmpty())throw new IOException("Unrecognized journal server");
            return result;
        }finally{c.disconnect();}
    }
    public JSONObject whoami(String base,String key)throws Exception{return request(base,key,new JSONObject().put("protocol",1).put("action","whoami"));}
    public JSONObject upload(String base,String key,JSONObject shot)throws Exception{return request(base,key,new JSONObject().put("protocol",1).put("action","upsert").put("shot",project(shot)));}
}
