package com.rotarypumpers.meraki.core;
/** Exactly one deliberate recorder pause; no control retries or background resume. */
public final class RecordActionGate{
 public enum Next{WAIT,START,CANCEL}private int phase;private boolean resume;private long deadline;
 public boolean active(){return phase!=0;}public boolean waiting(){return phase==1;}
 public boolean begin(boolean idle,long now,boolean resume){if(!idle||active())return false;phase=1;deadline=now+5000;this.resume=resume;return true;}
 public Next poll(long now,boolean busy,boolean foreground){if(phase!=1)return Next.CANCEL;if(!foreground||now>=deadline){cancel();return Next.CANCEL;}if(busy)return Next.WAIT;phase=2;return Next.START;}
 public boolean complete(boolean verified,boolean foreground){boolean yes=phase==2&&resume&&verified&&foreground;cancel();return yes;}
 public void cancel(){phase=0;resume=false;}
}
