package com.rotarypumpers.meraki.core;
/** Observed steam >=8s, then 2s idle. Suppress cleanup purges for 120s. */
public final class SteamReminderState {
 private double began=Double.NaN,ended=Double.NaN,last=Double.NaN,cooldown=-1;
 private boolean active=false,pending=false;
 public boolean accept(double now,boolean steam,boolean busy){
  if(!Double.isFinite(now))return false;
  if(!Double.isNaN(last)&&(now<last||now-last>2)){began=Double.NaN;ended=Double.NaN;active=false;pending=false;if(now<last)cooldown=-1;}
  last=now;
  if(steam){if(!active){began=now;active=true;}ended=Double.NaN;pending=false;return false;}
  if(active){active=false;ended=now;pending=now-began>=8&&now>=cooldown;}
  if(pending&&!busy&&now-ended>=2){pending=false;cooldown=now+120;return true;}
  return false;
 }
}
