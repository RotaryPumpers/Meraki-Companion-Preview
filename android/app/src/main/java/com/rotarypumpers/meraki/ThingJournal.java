package com.rotarypumpers.meraki;

import org.json.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;
import javax.net.ssl.*;
import com.rotarypumpers.meraki.core.MachineAddress;

final class ThingJournal {
    static final String[] FIELDS={"recipe","notes","taste","rating"};
    static JSONObject pair(String text) throws Exception {
        JSONObject c=new JSONObject(text);
        if(!"MerakiThingJournal".equals(c.getString("format")) || !c.getString("pin").matches("[0-9a-f]{64}")
                || !c.getString("token").matches("[A-Za-z0-9_-]{40,64}")
                || c.getInt("port")!=8743) throw new IOException("Choose the QR code on the Thing's Pi menu");
        c.put("host", MachineAddress.privateIpv4(c.getString("host")));
        return c;
    }
    static JSONObject request(JSONObject c, JSONObject packet) throws Exception {
        final String pin=c.getString("pin");
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException();}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{
                try{
                    StringBuilder hex=new StringBuilder();
                    for(byte b:MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))hex.append(String.format(Locale.ROOT,"%02x",b&255));
                    if(!pin.contentEquals(hex))throw new CertificateException("Pi pairing changed");
                }catch(NoSuchAlgorithmException e){throw new CertificateException(e);}
            }
        };
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,new TrustManager[]{trust},null);
        String host=c.getString("host");
        HttpsURLConnection conn=(HttpsURLConnection)new URL("https://"+host+":8743/journal").openConnection();
        try{
            conn.setSSLSocketFactory(ssl.getSocketFactory());conn.setHostnameVerifier((name,session)->host.equals(name));
            conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(8000);conn.setReadTimeout(60000);
            conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Authorization","Bearer "+c.getString("token"));
            conn.setRequestProperty("Content-Type","application/json");byte[] body=packet.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);try(OutputStream out=conn.getOutputStream()){out.write(body);}
            if(conn.getResponseCode()!=200)throw new IOException("Pi: "+conn.getResponseMessage());
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;
                while((n=in.read(b))!=-1){if(out.size()+n>64*1024*1024)throw new IOException("Journal exceeds size limit");out.write(b,0,n);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{conn.disconnect();}
    }
    interface Transport { JSONObject exchange(JSONObject packet) throws Exception; }
    static JSONArray sync(JSONObject config,JSONArray current)throws Exception{
        return reconcile(current,packet->request(config,packet));
    }
    static JSONArray reconcile(JSONArray current,Transport transport)throws Exception{
        JSONArray rows=new JSONArray(current.toString()), outgoing=new JSONArray();
        Map<String,JSONObject> byDate=new HashMap<>(),byId=new HashMap<>();
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);String date=r.getString("start_utc");byDate.put(date,r);
            if(r.has("thing_id"))byId.put(r.getString("thing_id"),r);
            JSONObject small=new JSONObject();
            for(String key:new String[]{"start_utc","thing_id","complete","duration_estimate_s","settled_yield_g","recipe","taste","notes","rating","shot_detail"})
                if(r.has(key))small.put(key,r.get(key));
            if(!small.has("shot_detail")){
                JSONArray samples=r.optJSONArray("samples"),points=new JSONArray(),bounds=r.optJSONArray("start_bounds");
                if(samples!=null&&samples.length()>0){
                    double start=bounds==null?samples.getJSONObject(0).optDouble("uptime"):bounds.optDouble(1);
                    for(int j=0;j<samples.length();j++){JSONObject sample=samples.getJSONObject(j);
                        double t=sample.optDouble("uptime")-start,w=sample.optDouble("weight_g");
                        if(Double.isFinite(t)&&Double.isFinite(w)&&t>=0&&t<=r.optDouble("duration_estimate_s",Double.MAX_VALUE))
                            points.put(new JSONArray().put(t).put(w));}
                }
                JSONObject detail=new JSONObject().put("points",points),g=r.optJSONObject("grind_workflow");
                if(g!=null)detail.put("grind",new JSONObject().put("duration_s",g.opt("duration_estimate_s")).put("settled_dose_g",g.opt("settled_dose_g")));
                small.put("shot_detail",detail);
            }
            outgoing.put(new JSONObject().put("shot",small).put("base",r.optJSONObject("thing_sync_base")));
        }
        JSONObject result=transport.exchange(new JSONObject().put("version",1).put("shots",outgoing));
        JSONObject aliases=result.getJSONObject("aliases");JSONArray reply=result.getJSONArray("shots");
        for(Iterator<String> it=aliases.keys();it.hasNext();){String date=it.next();byId.put(aliases.getString(date),byDate.get(date));}
        for(int i=0;i<reply.length();i++){
            JSONObject pi=reply.getJSONObject(i);String id=pi.getString("thing_id");
            JSONObject r=byId.get(id);
            if(r==null){
                r=new JSONObject(pi.toString());JSONArray samples=new JSONArray(),points=r.optJSONObject("shot_detail")==null?null:r.getJSONObject("shot_detail").optJSONArray("points");
                if(points!=null)for(int j=0;j<points.length();j++){JSONArray p=points.getJSONArray(j);samples.put(new JSONObject().put("uptime",p.getDouble(0)).put("weight_g",p.getDouble(1)));}
                r.put("samples",samples).put("source","MerakiThing").put("start_bounds",new JSONArray().put(0).put(0));
                JSONObject grind=r.optJSONObject("shot_detail")==null?null:r.getJSONObject("shot_detail").optJSONObject("grind");
                if(grind!=null)r.put("grind_workflow",new JSONObject().put("duration_estimate_s",grind.opt("duration_s")).put("settled_dose_g",grind.opt("settled_dose_g")));
                if(r.has("duration_estimate_s")){double duration=r.getDouble("duration_estimate_s");r.put("stop_bounds",new JSONArray().put(duration).put(duration));}
                rows.put(r);
            }
            JSONObject base=new JSONObject();
            for(String key:FIELDS){Object value=pi.opt(key);if(value==null||value==JSONObject.NULL){r.remove(key);}else{r.put(key,value);base.put(key,value);}}
            r.put("thing_id",id).put("thing_sync_base",base);
        }
        List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<rows.length();i++)sorted.add(rows.getJSONObject(i));
        sorted.sort(Comparator.comparing(r->Instant.parse(r.optString("start_utc"))));
        return new JSONArray(sorted);
    }
}
