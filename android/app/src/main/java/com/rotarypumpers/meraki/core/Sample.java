package com.rotarypumpers.meraki.core;

public final class Sample {
    public final double uptime, weight, p1;
    public final int[] registers, preferences;
    public final boolean active, drive;
    public final String utc;
    public Sample(double uptime,int[] r,int[] p,String utc) {
        if(!Double.isFinite(uptime)||uptime<0||(r.length!=72&&r.length!=112)||p.length!=13) throw new IllegalArgumentException("Invalid snapshot size or uptime");
        for(int x:r) if(x<0||x>65535) throw new IllegalArgumentException("Invalid register");
        for(int x:p) if(x<0||x>255) throw new IllegalArgumentException("Invalid preference");
        if(r[22]>1||r[41]>1800||r[42]>1600||r[47]>2000||((short)r[37]<-3||(short)r[37]>200)||p[0]>1||u32(p,1)>10||u32(p,5)>60) throw new IllegalArgumentException("Unsupported telemetry values");
        this.uptime=uptime;registers=r.clone();preferences=p.clone();this.utc=utc;
        active=r[22]==1||(r[33]&0x1000)!=0;drive=(r[33]&0x200)!=0;
        if(active&&r[16]!=0) throw new IllegalArgumentException("Conflicting brew/water state");
        weight=(r[39]>=32768?r[39]-65536:r[39])/10.0;p1=r[51]/10.0;
    }
    public static long u32(int[] p,int i){return (long)p[i]+((long)p[i+1]<<8)+((long)p[i+2]<<16)+((long)p[i+3]<<24);}
    public boolean idle(){return !active&&!drive&&registers[16]==0&&registers[52]==0;}
}
