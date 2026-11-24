package com.rotarypumpers.meraki.core;

/** Presentation correction for the known black-field results capture only. */
public final class ResultsPalette {
    public static final int BROWN=0xff6d3212, WHITE=0xfff6f6f6;
    public static boolean apply(int[] p,int w,int h){
        if(w!=480||h!=480||p.length!=w*h)return false;
        int black=0,brown=0,inside=0,minX=w,minY=h,maxX=0,maxY=0,gray=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            if(!circle(x,y,240,240,238))continue;
            int c=p[y*w+x],r=c>>>16&255,g=c>>>8&255,b=c&255;inside++;
            if(r<3&&g<3&&b<3)black++;
            if(r>=90&&r<=120&&g>=40&&g<=65&&b>=10&&b<=35)brown++;
            if(y>350&&x>150&&x<330&&r==76&&g==76&&b==76){gray++;minX=Math.min(x,minX);maxX=Math.max(x,maxX);minY=Math.min(y,minY);maxY=Math.max(y,maxY);}
        }
        // Require the black field, brown lettering and the known gray round OK button.
        if(black<inside*.85||brown<100||gray<1000||maxX-minX<65||maxX-minX>90||maxY-minY<65||maxY-minY>90)return false;
        double cx=(minX+maxX)/2.0,cy=(minY+maxY)/2.0,rad=(maxX-minX+maxY-minY+2)/4.0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x,c=p[i],r=c>>>16&255,g=c>>>8&255,b=c&255;
            if(!circle(x,y,240,240,240))continue;
            boolean button=circle(x,y,cx,cy,rad-1);
            int background=button?0xff4c230d:BROWN;
            if(r==g&&g==b){
                if(button){p[i]=background;continue;}
                // Preserve the button's antialiased boundary on the new brown field.
                if(circle(x,y,cx,cy,rad+2)){p[i]=mix(BROWN,0xff4c230d,r/76.0);continue;}
                p[i]=mix(BROWN,WHITE,r/127.0);continue;
            }
            // Recover glyph coverage against its old background, preserving its original shape.
            double base=button?76:0;
            int fr=button?109:115,fg=button?50:59,fb=button?18:29;
            double dr=fr-base,dg=fg-base,db=fb-base;
            double a=((r-base)*dr+(g-base)*dg+(b-base)*db)/(dr*dr+dg*dg+db*db);
            p[i]=mix(background,WHITE,a);
        }
        return true;
    }
    private static boolean circle(double x,double y,double cx,double cy,double r){return (x-cx)*(x-cx)+(y-cy)*(y-cy)<=r*r;}
    private static int mix(int a,int b,double t){t=Math.max(0,Math.min(1,t));int c=0xff000000;for(int s=0;s<=16;s+=8)c|=((int)Math.round((a>>>s&255)*(1-t)+(b>>>s&255)*t))<<s;return c;}
}
