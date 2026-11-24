package com.rotarypumpers.meraki.core;
import org.json.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.*;
/** Separate durable receipt journal. In-flight records remain uncertain after process death. */
public final class VisualizerJobs {
 private final File file;public VisualizerJobs(File dir){file=new File(dir,"visualizer-jobs.json");}
 private JSONObject load()throws Exception{return file.exists()?new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)):new JSONObject();}
 private void save(JSONObject j)throws Exception{File tmp=new File(file.getParentFile(),"visualizer-jobs.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(j.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
 private static String key(String account,String local)throws Exception{byte[] h=MessageDigest.getInstance("SHA-256").digest((account+"\n"+local).getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:h)s.append(String.format("%02x",b&255));return s.toString();}
 public synchronized JSONObject get(String account,String local)throws Exception{JSONObject j=load().optJSONObject(key(account,local));return j==null?new JSONObject().put("state","new"):j;}
 public synchronized boolean begin(String account,String local)throws Exception{JSONObject all=load();String key=key(account,local);JSONObject prior=all.optJSONObject(key);if(prior!=null&&!"rejected".equals(prior.optString("state")))return false;all.put(key,new JSONObject().put("state","sending"));save(all);return true;}
 public synchronized void accepted(String account,String local,String id)throws Exception{VisualizerExport.requireId(id);put(account,local,new JSONObject().put("state","uploaded").put("id",id));}
 public synchronized void failure(String account,String local,Exception error)throws Exception{boolean known=error instanceof VisualizerHttp.Rejected&&((VisualizerHttp.Rejected)error).status>=400&&((VisualizerHttp.Rejected)error).status<500;put(account,local,new JSONObject().put("state",known?"rejected":"uncertain"));}
 public synchronized void checkedMissing(String account,String local)throws Exception{JSONObject j=get(account,local);if(!java.util.Arrays.asList("sending","uncertain").contains(j.getString("state")))throw new IllegalArgumentException("No uncertain upload to clear");put(account,local,new JSONObject().put("state","rejected"));}
 private void put(String account,String local,JSONObject row)throws Exception{JSONObject all=load();all.put(key(account,local),row);save(all);}
}
