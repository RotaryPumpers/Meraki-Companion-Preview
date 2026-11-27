package com.rotarypumpers.meraki.core;
import java.util.*;

public final class Recorder {
    public enum State { WAITING, READY, TARING, RECORDING, SETTLING }
    public static final class Shot {
        public GrindWorkflow.Result grind;
        public Sample observedStart;public double autoTareWait;public int preTareSamples;
        public double gapLimit=3.5;
        public final List<Sample> samples=new ArrayList<>();
        public final List<double[]> gaps=new ArrayList<>();
        public double startLo,startHi,stopLo,stopHi;public boolean stopped,complete,finished;public String reason;public Double yield;
        public Double duration(){return stopped?(stopLo+stopHi-startLo-startHi)/2:null;}
    }
    public interface Listener {void saved(Shot s);default void stopped(Shot s){} default void ground(GrindWorkflow.Result g){} default void startSkipped(String reason){}}
    private final GrindWorkflow workflow=new GrindWorkflow();
    private final double gapLimit;private final Listener listener;private Sample previous;private int idleCount;private double unchanged;private Shot shot;private boolean tarePending;
    private Sample pendingStart;private double pendingLo;private int preTareSamples;
    public static final double AUTO_TARE_LIMIT_S=1.5;
    public double startTime(){return shot==null?Double.NaN:(shot.startLo+shot.startHi)/2;}
    public double grindElapsed(){return workflow.elapsed();}
    private void skipStart(String reason){pendingStart=null;preTareSamples=0;state=State.WAITING;idleCount=0;listener.startSkipped(reason);}
    private void begin(Sample first,Sample start,double lo,int omitted){shot=new Shot();shot.observedStart=start;shot.autoTareWait=first.uptime-start.uptime;shot.preTareSamples=omitted;shot.gapLimit=gapLimit;shot.grind=workflow.peek(start.uptime);shot.startLo=lo;shot.startHi=start.uptime;shot.samples.add(first);tarePending=first.weight< -1;pendingStart=null;preTareSamples=0;state=State.RECORDING;}
    public State state=State.WAITING;
    public Recorder(Listener listener){this(listener,3.5);}
    public Recorder(Listener listener,double gapLimit){if(!Double.isFinite(gapLimit)||gapLimit<=0)throw new IllegalArgumentException("Invalid gap limit");this.listener=listener;this.gapLimit=gapLimit;}
    public boolean hasGaps(){return shot!=null&&!shot.gaps.isEmpty();}
    public void abort(String reason){workflow.reset();if(shot!=null)finish(false,reason);state=State.WAITING;previous=null;idleCount=0;unchanged=0;tarePending=false;pendingStart=null;preTareSamples=0;}
    private void finish(boolean finished,String reason){Shot done=shot;shot=null;done.finished=finished;done.complete=finished&&done.gaps.isEmpty();done.reason=reason+(done.gaps.isEmpty()?"":"; sampling gaps present — missing data was not reconstructed");if(finished){int n=done.samples.size();double[] tail={done.samples.get(n-3).weight,done.samples.get(n-2).weight,done.samples.get(n-1).weight};Arrays.sort(tail);done.yield=tail[1];}state=State.WAITING;idleCount=0;listener.saved(done);}
    public void accept(Sample x){
        if(previous!=null){double dt=x.uptime-previous.uptime;
            if(dt<=0)throw new IllegalArgumentException("Machine uptime stopped or moved backwards");
            if(dt>12)throw new IllegalArgumentException("Sample gap exceeded 12-second recovery limit");
            if(dt>gapLimit){
                if(shot!=null)shot.gaps.add(new double[]{previous.uptime,x.uptime});
                else{idleCount=0;state=State.WAITING;}
            }
            unchanged=Arrays.equals(x.registers,previous.registers)?unchanged+dt:0;if(unchanged>=10)throw new IllegalArgumentException("Telemetry freshness uncertain");}
        GrindWorkflow.Result ground=workflow.accept(x);if(ground!=null)listener.ground(ground);
        // The display can publish Brew On one sample before its automatic tare.
        // Keep the start edge, but never put a pre-tare cup weight in a shot curve.
        if(pendingStart!=null){
            if(previous==null||x.uptime-previous.uptime>gapLimit){skipStart("Brew not recorded: data gap while waiting for automatic tare");}
            else if(!x.active){skipStart("Brew not recorded: stopped before automatic tare settled");}
            else if(x.uptime-pendingStart.uptime>AUTO_TARE_LIMIT_S){skipStart("Brew not recorded: automatic tare did not settle within 1.5 seconds");}
            else if(x.weight>=-1&&x.weight<=1){begin(x,pendingStart,pendingLo,preTareSamples);}
            else if(x.weight< -2||x.weight>500){skipStart("Brew not recorded: invalid scale reading during automatic tare");}
            else preTareSamples++;
            previous=x;return;
        }
        if(shot==null){
            if(x.idle()){if(++idleCount>=3)state=State.READY;}else if(!x.active){idleCount=0;state=State.WAITING;}
            if(x.active){if(state==State.READY&&previous!=null&&!previous.active){if(x.weight< -2||x.weight>500)throw new IllegalArgumentException("Invalid scale reading at brew start");if(x.weight>5){pendingStart=x;pendingLo=previous.uptime;preTareSamples=1;state=State.TARING;}else begin(x,x,previous.uptime,0);}else{idleCount=0;state=State.WAITING;}}
        }else{
            Sample last=shot.samples.get(shot.samples.size()-1);
            // Preserve the observed start edge while allowing a short, bounded negative tare.
            if(tarePending){
                if(!x.active||x.uptime-shot.startHi>1.5||x.weight< -2||x.weight>5)throw new IllegalArgumentException("Brew tare did not settle near zero");
                if(x.weight>=-1)tarePending=false;
            }
            if((x.weight< -1&&!tarePending)||x.weight<last.weight-2||x.weight>500){
                // A stopped short event must not disconnect the recorder when its cup is removed.
                // Keep normal extraction and active-brew weight checks unchanged.
                if(shot.stopped&&!x.active&&shot.duration()!=null&&shot.duration()<15){
                    finish(false,"Short event ended before weight settled");previous=x;return;
                }
                throw new IllegalArgumentException("Cup movement or invalid weight");
            }
            if(shot.stopped&&x.active)throw new IllegalArgumentException("New brew before settling");
            shot.samples.add(x);
            if(!x.active&&!shot.stopped){shot.stopped=true;shot.stopLo=previous.uptime;shot.stopHi=x.uptime;if(shot.duration()!=null&&shot.duration()>=15)workflow.consume(shot.startHi);state=State.SETTLING;listener.stopped(shot);}
            if(shot.stopped){double elapsed=x.uptime-shot.stopHi;int n=shot.samples.size();
                if(n>=3){List<Sample> tail=shot.samples.subList(n-3,n);double lo=tail.stream().mapToDouble(s->s.weight).min().orElse(0),hi=tail.stream().mapToDouble(s->s.weight).max().orElse(0);
                    boolean recent=tail.get(1).uptime-tail.get(0).uptime<=gapLimit&&tail.get(2).uptime-tail.get(1).uptime<=gapLimit;
                    if(elapsed>=5&&recent&&hi-lo<=.200001&&tail.stream().allMatch(Sample::idle)){finish(lo>=2,lo>=2?"Final weight settled":"Brew event below 2 g");}
                    else if(elapsed>=10)finish(false,"Weight did not settle within 10 seconds");
                }
            }else if(x.uptime-shot.startLo>180)throw new IllegalArgumentException("Brew exceeded recording limit");
        }
        previous=x;
    }
}
