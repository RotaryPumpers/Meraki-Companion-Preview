package com.rotarypumpers.meraki.core;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import org.json.*;
/** HTTPS only in production; injectable request seam for isolated HTTP simulations. No redirects or POST retries. */
public final class VisualizerHttp {
 public interface Wire {Reply request(String path,String token,String type,byte[] body)throws Exception;}
 public static final class Reply {public final int status;public final JSONObject json;public Reply(int status,JSONObject json){this.status=status;this.json=json;}}
 public static final class Rejected extends IOException {public final int status;public Rejected(int s){super(s==401?"Sign in to Visualizer again":s==403?"Visualizer did not grant access; check account confirmation and permissions":s==429?"Visualizer rate limit reached; try later":s==422?"Visualizer could not accept this shot; check graph format or account upload limit":"Visualizer request failed ("+s+")");status=s;}}
 private final Wire wire;public VisualizerHttp(){this(new Https());}public VisualizerHttp(Wire w){wire=w;}
 public JSONObject exchange(String form)throws Exception{return require(wire.request("/oauth/token",null,"application/x-www-form-urlencoded",form.getBytes(StandardCharsets.UTF_8)));}
 public JSONObject account(String token)throws Exception{return require(wire.request("/api/me",token,null,null));}
 public String upload(String token,JSONObject payload)throws Exception{byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>VisualizerExport.MAX_BYTES)throw new IllegalArgumentException("Shot exceeds 10 MB");JSONObject result=require(wire.request("/api/shots/upload",token,"application/json",bytes));String id=result.getString("id");VisualizerExport.requireId(id);return id;}
 public void revoke(String client,String token)throws Exception{Reply r=wire.request("/oauth/revoke",null,"application/x-www-form-urlencoded",("client_id="+VisualizerOAuth.encode(client)+"&token="+VisualizerOAuth.encode(token)).getBytes(StandardCharsets.UTF_8));require(r);}
 private static JSONObject require(Reply r)throws Exception{if(r.status<200||r.status>=300)throw new Rejected(r.status);return r.json;}
 private static final class Https implements Wire {
  public Reply request(String path,String token,String type,byte[] body)throws Exception{
   if(!java.util.Arrays.asList("/oauth/token","/api/me","/api/shots/upload","/oauth/revoke").contains(path))throw new IllegalArgumentException("Unexpected Visualizer endpoint");
   javax.net.ssl.HttpsURLConnection c=(javax.net.ssl.HttpsURLConnection)new URL("https://visualizer.coffee"+path).openConnection();
   try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","MerakiCompanion/0.4.41");if(token!=null){if(token.contains("\r")||token.contains("\n"))throw new IllegalArgumentException("Invalid access token");c.setRequestProperty("Authorization","Bearer "+token);}if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type",type);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
    int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null)try(InputStream stream=in){byte[] b=new byte[4096];int n;while((n=stream.read(b))!=-1){if(out.size()+n>1024*1024)throw new IOException("Visualizer response too large");out.write(b,0,n);}}
    JSONObject json=new JSONObject();if(code>=200&&code<300&&out.size()>0)json=new JSONObject(out.toString("UTF-8"));return new Reply(code,json);
   }finally{c.disconnect();}
  }
 }
}
