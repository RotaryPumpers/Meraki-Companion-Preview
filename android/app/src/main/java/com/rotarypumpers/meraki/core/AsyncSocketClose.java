package com.rotarypumpers.meraki.core;
import java.io.Closeable;
import java.io.IOException;
/** TLS close may write close_notify: never execute it on an Android UI caller. */
final class AsyncSocketClose {
 static Thread close(Closeable resource){
  if(resource==null)return null;
  Thread closer=new Thread(()->{try{resource.close();}catch(IOException ignored){}},"rp-secure-close");
  closer.setDaemon(true);closer.start();return closer;
 }
}
