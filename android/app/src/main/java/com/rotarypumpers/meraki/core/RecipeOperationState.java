package com.rotarypumpers.meraki.core;
/** A background interruption permanently ends permission for this operation. */
public final class RecipeOperationState {
 private boolean active,interrupted;
 public synchronized boolean begin(){if(active)return false;active=true;interrupted=false;return true;}
 public synchronized void interrupt(){if(active)interrupted=true;}
 public synchronized void finish(){active=false;}
 public synchronized boolean active(){return active;}
 public synchronized boolean permits(boolean foreground){return active&&!interrupted&&foreground;}
}
