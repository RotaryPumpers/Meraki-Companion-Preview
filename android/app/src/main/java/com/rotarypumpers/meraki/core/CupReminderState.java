package com.rotarypumpers.meraki.core;
/** One initial dosing reminder per connection; one brew reminder per observed grind. */
public final class CupReminderState {
 public enum Prompt { NONE, DOSE, BREW, STEAM }
 private boolean initial=true, pendingBrew;
 public void skipInitialReminder(){initial=false;}
 public Prompt accept(boolean ready, boolean grinding, boolean brewing) {
  if(grinding){initial=false;pendingBrew=true;}
  if(brewing){initial=false;pendingBrew=false;}
  if(!ready||grinding||brewing)return Prompt.NONE;
  if(pendingBrew){pendingBrew=false;initial=false;return Prompt.BREW;}
  if(initial){initial=false;return Prompt.DOSE;}
  return Prompt.NONE;
 }
}
