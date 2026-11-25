import com.rotarypumpers.meraki.core.SecureRecorderTransport;
public class TransportCheck {
 public static void main(String[] args)throws Exception{
  try(SecureRecorderTransport t=new SecureRecorderTransport(args[1])){
   t.connect("127.0.0.1",Integer.parseInt(args[0]),args[2]);
   String row=t.readLine();if(!row.equals("{\"type\":\"sample\",\"seq\":0}"))throw new Exception("unexpected record");
   t.acknowledge(0);
   if(!t.readLine().equals("ACK_SAVED"))throw new Exception("ack failed");
  }
  System.out.println("JAVA_TLS_PIN_AND_ACK_OK");
 }
}
