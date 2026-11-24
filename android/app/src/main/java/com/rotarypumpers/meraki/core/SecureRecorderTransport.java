package com.rotarypumpers.meraki.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Experimental TLS-only transport. Exact paired certificate pin; no trust-on-first-use. */
public final class SecureRecorderTransport implements AutoCloseable {
 private volatile SSLSocket socket;
 private boolean closed,started;
 private BufferedInputStream input;
 private final byte[] pin;
 public SecureRecorderTransport(String certificateSha256) {
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
   s.getOutputStream().write(("RP/1 "+token+"\n").getBytes(StandardCharsets.US_ASCII));s.getOutputStream().flush();
   s.setSoTimeout(20000);input=new BufferedInputStream(s.getInputStream());
  }catch(Exception e){close();throw e;}
 }
 public String readLine()throws IOException {
  if(input==null)throw new IOException("Not connected");ByteArrayOutputStream b=new ByteArrayOutputStream();
  while(b.size()<=4096){int c=input.read();if(c<0)throw new EOFException("Secure recorder disconnected");if(c=='\n')return new String(b.toByteArray(),StandardCharsets.UTF_8);b.write(c);}
  throw new IOException("Oversized recorder message");
 }
 /** Call only after durable storage succeeds. */
 public void acknowledge(long sequence)throws IOException {
  SSLSocket current=socket;
  if(sequence<0||current==null)throw new IOException("Invalid acknowledgement");
  current.getOutputStream().write(("ACK "+sequence+"\n").getBytes(StandardCharsets.US_ASCII));current.getOutputStream().flush();
 }
 public void close(){SSLSocket s; synchronized(this){closed=true;s=socket;socket=null;}AsyncSocketClose.close(s);}
}
