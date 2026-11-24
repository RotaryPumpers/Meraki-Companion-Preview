package com.rotarypumpers.meraki.core;
import org.json.*;
/** No toString override: credentials must never appear in diagnostics. */
public final class SecurePairing {
 public final String host,pin,token;public final int port;
 public SecurePairing(String host,String pin,String token)throws Exception{
  this.host=MachineAddress.privateIpv4(host);this.port=9443;
  this.pin=checkedPin(pin);this.token=checkedToken(token);
 }
 static String checkedPin(String pin){
  String normalized=pin.trim().toLowerCase(java.util.Locale.ROOT);
  if(!normalized.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Certificate fingerprint must contain 64 hexadecimal characters");
  return normalized;
 }
 static String checkedToken(String token){
  String normalized=token.trim();
  if(!normalized.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Pairing key must contain 64 lowercase hexadecimal characters");
  return normalized;
 }
 public String encode()throws Exception{return new JSONObject().put("host",host).put("certificate_sha256",pin).put("token",token).put("port",port).toString();}
 public static SecurePairing decode(String raw)throws Exception{
  if(raw.length()>2048)throw new IllegalArgumentException("Pairing data too large");
  JSONObject o=new JSONObject(raw);if(o.getInt("port")!=9443)throw new IllegalArgumentException("Unsupported Secure Link port");
  return new SecurePairing(o.getString("host"),o.getString("certificate_sha256"),o.getString("token"));
 }
 /** Import only the phone file, never the device private-key file. */
 public static SecurePairing fromPhoneFile(String host,String raw)throws Exception{
  if(raw==null||raw.length()>2048)throw new IllegalArgumentException("Invalid pairing block");
  JSONObject o=new JSONObject(raw);
  if(o.has("private_key_pem")||o.has("certificate_pem"))throw new IllegalArgumentException("Use phone-pairing.json only");
  if(!"rp-secure-stream-v1".equals(o.getString("protocol"))||o.getInt("port")!=9443)throw new IllegalArgumentException("Unsupported pairing protocol or port");
  return new SecurePairing(host,o.getString("certificate_sha256"),o.getString("token"));
 }
 public void requireHost(String target)throws Exception{
  if(!host.equals(MachineAddress.privateIpv4(target)))throw new IllegalArgumentException("Machine address differs from its pairing. Update Secure Link in Tools.");
 }
}
