package com.rotarypumpers.meraki.core;
import java.io.*;
import java.time.Instant;
import org.json.*;
/** Decodes paired TLS samples and acknowledges each sample after storage. */
public final class SecureArmStream {
 private final SecureRecorderTransport transport;
 public SecureArmStream(SecureRecorderTransport transport){this.transport=transport;}
 public void run(Sink sink)throws Exception{
  Decoder decoder=new Decoder();boolean metadata=false;
  long previousDelivery=0,previousProcessing=0,lastReceived=0;
  try {
   while(true){
    String line;
    try{line=transport.readLine();}catch(EOFException e){if(!metadata)throw new SecureLinkSetupException();throw e;}
    JSONObject row=decoder.decode(line);
    if(row.getString("type").equals("metadata")){
     sink.metadata(row);metadata=true;
    }else{
     Sample sample=decoder.sample(row);long received=System.nanoTime();
     row.put("previous_phone_delivery_ns",previousDelivery);
     row.put("previous_phone_processing_ns",previousProcessing);
     row.put("phone_interarrival_ns",lastReceived==0?0:received-lastReceived);lastReceived=received;
     long start=System.nanoTime();sink.sample(sample,row);
     transport.acknowledge(row.getLong("seq"));previousDelivery=System.nanoTime()-start;
     start=System.nanoTime();sink.afterAcknowledged(sample,row);previousProcessing=System.nanoTime()-start;
    }
   }
  } finally {transport.close();}
 }
    private static final String DISPLAY_SHA = "df0c10a7c72a710ff5fb703c39e97a47e69f4fc2032e0fb6be54000784665473";

    public interface Sink {
        void metadata(JSONObject metadata)throws Exception;
        void sample(Sample sample,JSONObject nativeRow)throws Exception;
        default void afterAcknowledged(Sample sample,JSONObject nativeRow)throws Exception {}
    }

    static final class Decoder {
        private boolean metadata;private long sequence,previousNs=-1;
        public JSONObject decode(String line)throws Exception{
            if(line.length()>4096)throw new IOException("Oversized ARM record");
            if(line.equals("RP_CAPTURE_ERROR: phone acknowledgement timed out"))throw new ArmAcknowledgementTimeout();
            if(!line.startsWith("{"))throw new IOException("ARM recorder: "+line);
            JSONObject row=new JSONObject(line);String type=row.getString("type");
            if(type.equals("metadata")){
                if(metadata||!row.getString("format").equals("rp-display-cache-v1")||row.getInt("stream_protocol")!=2||!row.getString("display_sha256").equals(DISPLAY_SHA)||!validIdentity(row)||row.getInt("register_count")!=112||row.getInt("period_ms")!=500||!row.getString("source").equals("display_cache")||row.getBoolean("sensor_synchronous"))throw new IOException("ARM identity or format mismatch");
                metadata=true;
            }else if(type.equals("sample")){
                if(!metadata||row.getLong("seq")!=sequence++)throw new IOException("ARM sample sequence mismatch");
                long uptime=row.getLong("uptime_ns"),gap=row.getLong("gap_ns"),duration=row.getLong("read_duration_ns");
                if(uptime<0||duration<0||duration>1_000_000_000L||gap!=(previousNs<0?0:uptime-previousNs)||(previousNs>=0&&uptime<=previousNs))throw new IOException("ARM timing mismatch");previousNs=uptime;
                if(row.getJSONArray("registers").length()!=112||row.getJSONArray("preferences").length()!=13)throw new IOException("ARM snapshot size mismatch");
            }else throw new IOException("Unexpected ARM record");
            return row;
        }
        private boolean validIdentity(JSONObject row)throws Exception{
            long pid=row.getLong("pid"),start=row.getLong("start_ticks"),pointer=row.getLong("app_pointer");
            return pid>=1&&pid<=Integer.MAX_VALUE&&start>=0&&pointer>=65536&&pointer<=0x7fffffffL&&pointer%4==0;
        }
        public Sample sample(JSONObject row)throws Exception{
            return new Sample(row.getLong("uptime_ns")/1e9,integers(row.getJSONArray("registers"),65535),integers(row.getJSONArray("preferences"),255),Instant.now().toString());
        }
        private static int[] integers(JSONArray values,int max)throws Exception{
            int[] result=new int[values.length()];for(int i=0;i<result.length;i++){double value=values.getDouble(i);if(value<0||value>max||value!=Math.floor(value))throw new IOException("Invalid ARM integer");result[i]=(int)value;}return result;
        }
    }
}
