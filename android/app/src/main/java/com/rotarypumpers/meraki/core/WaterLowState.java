package com.rotarypumpers.meraki.core;
/** Firmware GetE3tate(1): app+0x1030 = registers[36], mask 1. */
public final class WaterLowState {
 private Boolean previous;
 public static boolean low(int[] r){return (r[36]&1)!=0;}
 public int accept(int[] r){boolean low=low(r);int event=previous==null?(low?1:0):(previous!=low?(low?1:-1):0);previous=low;return event;}
}
