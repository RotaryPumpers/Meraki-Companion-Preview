package com.rotarypumpers.meraki.core;

/** Presentation only: never alters recorded samples or grinder/brew association. */
public final class RecordScaleState {
    public static final class Display {
        public final String label,message;
        public final Double weight;
        Display(String label,String message,Double weight){this.label=label;this.message=message;this.weight=weight;}
    }
    private boolean grinding,hold,finalized,moved;
    private Double dose;
    private double stoppedAt,previousTime=Double.NaN;

    public void finished(Double settledDose){
        if(hold){dose=settledDose;finalized=true;}
    }
    public Display accept(Sample sample,Recorder.State state,boolean steaming){
        boolean on=GrindWorkflow.grinding(sample);
        if(Double.isFinite(previousTime)&&(sample.uptime<=previousTime||sample.uptime-previousTime>.75)){
            hold=false;dose=null;grinding=false;finalized=false;moved=false;
        }
        previousTime=sample.uptime;
        if(on){
            if(!grinding){finalized=false;moved=false;}
            dose=GrindWorkflow.dose(sample);hold=true;grinding=true;
            return new Display("Grinder scale","Grinding · keep dosing cup in place",dose);
        }
        if(grinding){stoppedAt=sample.uptime;grinding=false;}
        if(state==Recorder.State.TARING){hold=false;dose=null;return new Display("Brew scale","Waiting for automatic tare",null);}
        if(sample.active){hold=false;dose=null;return new Display("Brew scale","Brewing · keep cup in place",sample.weight);}
        if(state==Recorder.State.SETTLING){hold=false;return new Display("Brew scale","Brew finished · wait for final drips",sample.weight);}
        if(hold){
            if(!finalized&&sample.uptime-stoppedAt<=3.5){
                double reading=GrindWorkflow.dose(sample);
                if(reading<0||dose!=null&&reading<dose-2)moved=true;
                if(!moved)dose=reading;
            }
            return new Display(finalized?"Recorded grind dose":"Grinder scale",
                steaming?"Steaming · grind dose retained":finalized?
                (dose==null?"Grind dose unavailable · ready to brew":"Grind finished · dose held until brewing"):
                "Grind finished · let dose settle",dose);
        }
        return new Display("Brew scale",steaming?"Steaming":state==Recorder.State.READY?
            "Ready · grind or brew normally":"Waiting for machine to be ready",sample.weight);
    }
}
