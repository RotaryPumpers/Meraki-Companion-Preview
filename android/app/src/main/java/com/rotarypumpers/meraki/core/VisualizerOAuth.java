package com.rotarypumpers.meraki.core;
import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import org.json.*;
public final class VisualizerOAuth {
 public static final String REDIRECT="com.rotarypumpers.meraki:/visualizer/oauth",SCOPE="read upload";
 public static String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){throw new IllegalArgumentException(e);}}
 public static String random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 public static String challenge(String verifier)throws Exception{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}
 public static JSONObject begin(String client,long now)throws Exception{if(client==null||!client.matches("[A-Za-z0-9_-]{8,256}"))throw new IllegalArgumentException("Enter the public OAuth client ID");return new JSONObject().put("client_id",client).put("state",random()).put("verifier",random()).put("created",now);}
 public static String authorization(JSONObject pending)throws Exception{return "https://visualizer.coffee/oauth/authorize?response_type=code&client_id="+encode(pending.getString("client_id"))+"&redirect_uri="+encode(REDIRECT)+"&scope="+encode(SCOPE)+"&state="+encode(pending.getString("state"))+"&code_challenge_method=S256&code_challenge="+encode(challenge(pending.getString("verifier")));}
 public static String callback(String callback,JSONObject pending,long now)throws Exception{
  if(pending==null||now<pending.getLong("created")||now-pending.getLong("created")>600)throw new IllegalArgumentException("Sign-in expired; connect again");
  URI u=new URI(callback),expected=new URI(REDIRECT);
  if(!expected.getScheme().equals(u.getScheme())||u.getRawAuthority()!=null||!expected.getPath().equals(u.getPath())||u.getFragment()!=null)throw new IllegalArgumentException("Unexpected sign-in return address");
  Map<String,String> q=new HashMap<>();if(u.getRawQuery()==null)throw new IllegalArgumentException("Missing sign-in result");for(String pair:u.getRawQuery().split("&")){String[] v=pair.split("=",2);String k=URLDecoder.decode(v[0],"UTF-8"),val=v.length==2?URLDecoder.decode(v[1],"UTF-8"):"";if(q.put(k,val)!=null)throw new IllegalArgumentException("Duplicate sign-in field");}
  if(!MessageDigest.isEqual(pending.getString("state").getBytes(StandardCharsets.UTF_8),q.getOrDefault("state","").getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("Sign-in state did not match");
  if(q.containsKey("error"))throw new IllegalArgumentException("Visualizer sign-in was not approved");String code=q.get("code");if(code==null||code.isEmpty()||code.length()>2048)throw new IllegalArgumentException("Missing authorization code");return code;
 }
 public static String codeForm(JSONObject p,String code)throws Exception{return "grant_type=authorization_code&client_id="+encode(p.getString("client_id"))+"&redirect_uri="+encode(REDIRECT)+"&code="+encode(code)+"&code_verifier="+encode(p.getString("verifier"));}
 public static String refreshForm(String client,String refresh){return "grant_type=refresh_token&client_id="+encode(client)+"&refresh_token="+encode(refresh);}
 public static JSONObject tokens(JSONObject response,String client,long now)throws Exception{
  String access=response.getString("access_token"),refresh=response.getString("refresh_token");long ttl=response.getLong("expires_in");String type=response.getString("token_type");
  Set<String> scopes=new HashSet<>(Arrays.asList(response.getString("scope").split(" +")));
  if(!"bearer".equalsIgnoreCase(type)||access.isEmpty()||refresh.isEmpty()||access.contains("\n")||access.contains("\r")||ttl<=0||ttl>31536000||!scopes.contains("read")||!scopes.contains("upload"))throw new IllegalArgumentException("Visualizer did not grant the required access");
  return new JSONObject().put("access_token",access).put("refresh_token",refresh).put("expires_at",now+ttl).put("client_id",client);
 }
}
