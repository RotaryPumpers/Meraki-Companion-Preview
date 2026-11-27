package com.rotarypumpers.meraki.core;

public final class GrindTimerChecks {
    private static Sample sample(double time, boolean grinding, boolean brewing) {
        int[] registers = new int[112];
        if (grinding) registers[33] = 0x40;
        if (brewing) registers[22] = 1;
        registers[38] = 178;
        return new Sample(time, registers, new int[13], "2026-11-26T00:00:00Z");
    }

    private static void seconds(double expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError("Expected " + expected + " s, got " + actual);
        }
    }

    public static void main(String[] args) {
        RecordScaleState scale = new RecordScaleState();
        Recorder recorder = new Recorder(new Recorder.Listener() {
            @Override public void saved(Recorder.Shot shot) {}
            @Override public void ground(GrindWorkflow.Result grind) {
                seconds(1.5, grind.duration());
                scale.finished(grind.settledDose);
            }
        }, .75);

        for (double t = 0; t <= 1; t += .5) {
            Sample idle = sample(t, false, false);
            recorder.accept(idle);
            scale.accept(idle, recorder.state, false);
        }
        for (double t = 1.5; t <= 6.5; t += .5) {
            boolean running = t < 3;
            Sample event = sample(t, running, false);
            recorder.accept(event);
            RecordScaleState.Display display = scale.accept(event, recorder.state, false);
            if (!display.label.toLowerCase(java.util.Locale.ROOT).contains("grind")) {
                throw new AssertionError("Grind duration should accompany the grinder scale");
            }
            seconds(running ? t - 1.25 : 1.5, recorder.grindElapsed());
        }
        Sample brew = sample(7, false, true);
        recorder.accept(brew);
        if (!"Brew scale".equals(scale.accept(brew, recorder.state, false).label)) {
            throw new AssertionError("Brew start should switch back to the brew timer");
        }
        seconds(6.75, recorder.startTime());
        recorder.abort("Disconnected");
        recorder.accept(sample(10, true, false));
        if (!Double.isNaN(recorder.grindElapsed())) {
            throw new AssertionError("A session beginning mid-grind has an unknown start time");
        }
        System.out.println("Grind timer checks passed");
    }
}
