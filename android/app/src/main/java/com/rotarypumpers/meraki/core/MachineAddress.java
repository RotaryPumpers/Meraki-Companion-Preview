package com.rotarypumpers.meraki.core;

public final class MachineAddress {
    private MachineAddress() {}

    public static String privateIpv4(String host){
        if(!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IllegalArgumentException("Enter the Meraki's private IPv4 address");
        String[] p=host.split("\\.");int[] a=new int[4];for(int i=0;i<4;i++){a[i]=Integer.parseInt(p[i]);if(a[i]>255)throw new IllegalArgumentException("Invalid address");}
        if(!(a[0]==10||a[0]==172&&a[1]>=16&&a[1]<=31||a[0]==192&&a[1]==168)||a[3]==0||a[3]==255)throw new IllegalArgumentException("Private device address required");
        return a[0]+"."+a[1]+"."+a[2]+"."+a[3];
    }
}
