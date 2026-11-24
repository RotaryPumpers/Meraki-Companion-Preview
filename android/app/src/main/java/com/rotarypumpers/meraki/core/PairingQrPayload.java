package com.rotarypumpers.meraki.core;
import java.util.*;
/** Bounded, strict, flat pairing JSON. It never interprets a code as a URL. */
public final class PairingQrPayload {
 public final String host; private final String pin,token;
 private PairingQrPayload(String host,String pin,String token){this.host=host;this.pin=pin;this.token=token;}
 public static PairingQrPayload parse(String raw)throws Exception{
  if(raw==null||raw.length()>2048)throw bad();Map<String,Object> m=new Reader(raw).read();
  if(!"rp-secure-stream-v1".equals(m.get("protocol"))||!Integer.valueOf(9443).equals(m.get("port")))throw bad();
  if(!(m.get("certificate_sha256") instanceof String)||!(m.get("token") instanceof String))throw bad();
  String host=null;if(m.containsKey("host")){if(!(m.get("host") instanceof String))throw bad();host=MachineAddress.privateIpv4(((String)m.get("host")).trim());}
  String pin=(String)m.get("certificate_sha256"),token=(String)m.get("token");
  // Existing Secure Link validation remains authoritative for the credential fields.
  return new PairingQrPayload(host,SecurePairing.checkedPin(pin),SecurePairing.checkedToken(token));
 }
 public SecurePairing pairing(String address)throws Exception{return new SecurePairing(host==null?address:host,pin,token);}
 public SecurePairing withConfirmedAddress(String address)throws Exception{return new SecurePairing(address,pin,token);}
 private static IllegalArgumentException bad(){return new IllegalArgumentException("Use a Companion phone-pairing code or file.");}
 private static final class Reader{
  final String s;int i;Reader(String s){this.s=s;}
  void ws(){while(i<s.length()&&" \r\n\t".indexOf(s.charAt(i))>=0)i++;}
  boolean take(char c){ws();if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
  void need(char c){if(!take(c))throw bad();}
  String string(){ws();if(i>=s.length()||s.charAt(i++)!='"')throw bad();StringBuilder b=new StringBuilder();boolean closed=false;
   while(i<s.length()){char c=s.charAt(i++);if(c=='"'){closed=true;break;}if(c<32)throw bad();
    if(c=='\\'){if(i>=s.length())throw bad();char e=s.charAt(i++);switch(e){case '"':case '\\':case '/':c=e;break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;case 'u':if(i+4>s.length())throw bad();try{c=(char)Integer.parseInt(s.substring(i,i+4),16);}catch(Exception x){throw bad();}i+=4;break;default:throw bad();}}
    b.append(c);
   }if(!closed)throw bad();return b.toString();
  }
  Object value(){ws();if(i<s.length()&&s.charAt(i)=='"')return string();int start=i;while(i<s.length()&&s.charAt(i)>='0'&&s.charAt(i)<='9')i++;if(i==start||(i-start>1&&s.charAt(start)=='0'))throw bad();try{return Integer.valueOf(s.substring(start,i));}catch(Exception e){throw bad();}}
  Map<String,Object> read(){Map<String,Object> m=new LinkedHashMap<>();need('{');if(take('}'))throw bad();while(true){String k=string();if(!Arrays.asList("protocol","port","certificate_sha256","token","host").contains(k)||m.containsKey(k))throw bad();need(':');m.put(k,value());if(take('}'))break;need(',');}ws();if(i!=s.length()||m.size()<4)throw bad();return m;}
 }
}
