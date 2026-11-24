package com.rotarypumpers.meraki.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Home appearance channel; existing certificate pin, no command retries. */
public final class HomeControlTransport implements AutoCloseable {
 private volatile SSLSocket socket;
 private boolean closed,started;
 private BufferedInputStream input;
 private final byte[] pin;
 public HomeControlTransport(String certificateSha256) {
  if(certificateSha256==null||!certificateSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid certificate fingerprint");
  pin=new byte[32];for(int i=0;i<32;i++)pin[i]=(byte)Integer.parseInt(certificateSha256.substring(2*i,2*i+2),16);
 }
 private static InetAddress privateAddress(String host)throws IOException {
  if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IOException("Private IPv4 address required");
  String[] p=host.split("\\.");byte[] b=new byte[4];for(int i=0;i<4;i++){int v=Integer.parseInt(p[i]);if(v>255)throw new IOException("Invalid address");b[i]=(byte)v;}
  InetAddress a=InetAddress.getByAddress(b);if(!a.isSiteLocalAddress()&&!a.isLoopbackAddress())throw new IOException("Private IPv4 address required");return a;
 }
 public void connect(String host,int port,String token)throws Exception {
  if(token==null||!token.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid pairing token");
  if(port<1||port>65535)throw new IllegalArgumentException("Invalid port");
  X509TrustManager pinned=new X509TrustManager(){
   public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
   public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Client role only");}
   public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException {
    try{if(c==null||c.length==0||!MessageDigest.isEqual(pin,MessageDigest.getInstance("SHA-256").digest(c[0].getEncoded())))throw new CertificateException("Machine certificate does not match pairing");}
    catch(NoSuchAlgorithmException e){throw new CertificateException(e);}
   }
  };
  SSLContext context=SSLContext.getInstance("TLS");context.init(null,new TrustManager[]{pinned},new SecureRandom());
  SSLSocket s=(SSLSocket)context.getSocketFactory().createSocket();
  synchronized(this){if(closed||started){s.close();throw new IOException("Secure connection cancelled or already used");}started=true;socket=s;}
  try{
   s.setEnabledProtocols(new String[]{"TLSv1.2"}); // Android API26-compatible baseline, no plaintext fallback.
   s.connect(new InetSocketAddress(privateAddress(host),port),5000);s.setTcpNoDelay(true);s.setSoTimeout(10000);s.startHandshake();
   s.getOutputStream().write(("RP-TEMP/1 "+token+"\n").getBytes(StandardCharsets.US_ASCII));s.getOutputStream().flush();
   s.setSoTimeout(45000);input=new BufferedInputStream(s.getInputStream());
  }catch(Exception e){close();throw e;}
 }
 public String readLine()throws IOException {
  if(input==null)throw new IOException("Not connected");ByteArrayOutputStream b=new ByteArrayOutputStream();
  while(b.size()<=4096){int c=input.read();if(c<0)throw new EOFException("Dose control disconnected");if(c=='\n')return new String(b.toByteArray(),StandardCharsets.UTF_8);b.write(c);}
  throw new IOException("Oversized recorder message");
 }
 public org.json.JSONObject capabilities()throws Exception{
  org.json.JSONObject c=new org.json.JSONObject(readLine());if(!"capabilities".equals(c.optString("type"))||!"rp-temperature-v1".equals(c.optString("protocol"))||!c.optBoolean("home_appearance")||!c.optString("epoch").matches("[0-9a-f]{32}"))throw new IOException("Home appearance needs the updated machine service");return c;
 }
 public org.json.JSONObject exchange(org.json.JSONObject request)throws Exception{
  SSLSocket current=socket;if(current==null)throw new IOException("Home connection closed");byte[] bytes=(request.toString()+"\n").getBytes(StandardCharsets.UTF_8);if(bytes.length>512)throw new IOException("Oversized Home request");current.getOutputStream().write(bytes);current.getOutputStream().flush();org.json.JSONObject r=new org.json.JSONObject(readLine());
  if(!"result".equals(r.optString("type"))||!request.getString("id").equals(r.optString("id")))throw new IOException("Home response did not match request");String status=r.optString("status");if(!java.util.Arrays.asList("applied","deferred","unchanged","standard","error","busy","rejected","pending","unknown").contains(status))throw new IOException("Unknown Home result");
  if(request.has("home")&&java.util.Arrays.asList("applied","deferred","unchanged").contains(status)){org.json.JSONObject want=request.getJSONObject("home"),got=r.optJSONObject("home");if(got==null||!want.getString("color").equals(got.optString("color"))||!want.getString("name").equals(got.optString("name")))throw new IOException("Home result differs from request");}return r;
 }
 public org.json.JSONObject setHome(String id,String epoch,org.json.JSONObject home)throws Exception{return exchange(new org.json.JSONObject().put("type","set_home").put("id",id).put("epoch",epoch).put("home",home));}
 public org.json.JSONObject getHome(String id,String epoch)throws Exception{return exchange(new org.json.JSONObject().put("type","get_home").put("id",id).put("epoch",epoch));}
 public org.json.JSONObject result(String id,String epoch)throws Exception{return exchange(new org.json.JSONObject().put("type","get_result").put("id",id).put("epoch",epoch));}
 public void close(){SSLSocket s;synchronized(this){closed=true;s=socket;socket=null;}AsyncSocketClose.close(s);}
}
