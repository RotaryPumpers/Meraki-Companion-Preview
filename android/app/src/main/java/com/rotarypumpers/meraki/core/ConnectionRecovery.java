package com.rotarypumpers.meraki.core;
import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
/** Finite retry budget per user connection request, never reset by a brief success. */
public final class ConnectionRecovery {
 private int attempts;
 private boolean secure;
 private boolean startupRetryUsed;
 public void reset(boolean encrypted){attempts=0;secure=encrypted;startupRetryUsed=false;}
 public int limit(){return secure?6:3;}
 public void reset(){reset(false);}
 public int attempts(){return attempts;}
 public static boolean transientFailure(Throwable error){
  return error instanceof ArmAcknowledgementTimeout || error instanceof SocketException || error instanceof SocketTimeoutException || error instanceof EOFException;
 }
 public int nextDelaySeconds(Throwable error,boolean requested,boolean foreground){
  if(!requested||!foreground||attempts>=limit())return 0;
  // A pre-metadata EOF cannot distinguish a temporary startup failure from rejected
  // credentials on the legacy listener. One bounded probe only; never retry SSL,
  // decoding or persistence failures, and do not reset after a brief success.
  if(error instanceof SecureLinkSetupException){
   if(!secure||startupRetryUsed)return 0;
   startupRetryUsed=true;attempts++;return 6;
  }
  if(!transientFailure(error))return 0;
  return new int[]{6,12,24,30,30,30}[attempts++];
 }
 public static String message(Throwable error){
  if(error instanceof javax.net.ssl.SSLException)return "Secure connection verification failed. Check the paired machine fingerprint in Tools. No unencrypted connection was attempted.";
  if(error instanceof SecureLinkSetupException)return error.getMessage();
  if(error instanceof ArmAcknowledgementTimeout)return "Recording link stalled while waiting for this phone. The interrupted recording was retained.";
  if(error instanceof SocketTimeoutException)return "Machine did not respond. Wake its screen if asleep, then check power, Wi-Fi and its address.";
  if(error instanceof EOFException)return "Machine connection closed. Wake its screen if asleep; it may also have restarted.";
  if(error instanceof SocketException)return "Machine connection unavailable. Wake its screen if asleep and keep your phone on the same home Wi-Fi.";
  String detail=error==null?"Unknown error":error.getMessage();
  return "Recording stopped: "+(detail==null?error.getClass().getSimpleName():detail)+". Review the problem before reconnecting.";
 }
}
