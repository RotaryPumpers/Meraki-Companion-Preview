package com.rotarypumpers.meraki.core;
import org.json.*;import java.io.IOException;
/** Serialized OAuth refresh, shared by Android and simulation. Rotation saved before use. */
public final class VisualizerSession {
 public interface Store {JSONObject load()throws Exception;void save(JSONObject t)throws Exception;}
 private final VisualizerHttp http;private final Store store;
 public VisualizerSession(VisualizerHttp http,Store store){this.http=http;this.store=store;}
 public synchronized JSONObject valid(long now)throws Exception{
  JSONObject t=store.load();if(t==null)throw new IOException("Connect Visualizer in App tools first");
  if(t.getLong("expires_at")<=now+60){
   JSONObject next=VisualizerOAuth.tokens(http.exchange(VisualizerOAuth.refreshForm(t.getString("client_id"),t.getString("refresh_token"))),t.getString("client_id"),now);
   next.put("account_id",t.getString("account_id")).put("account_name",t.optString("account_name"));store.save(next);t=next;
  }
  return t;
 }
}
