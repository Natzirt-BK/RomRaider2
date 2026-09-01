/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

/** State holder for one immutable staged plan and its latest preflight. */
public final class LiveTuneSession {
    private final LiveTunePlan plan;
    private LiveTuneSessionState state = LiveTuneSessionState.DRAFT;
    private LiveTunePreflight preflight;

    public LiveTuneSession(LiveTunePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Live-tune plan is required");
        }
        this.plan = plan;
    }

    public synchronized LiveTunePreflight preflight(
            LiveTuneSafetyContext context) {
        if (state == LiveTuneSessionState.APPLYING) {
            throw new IllegalStateException(
                    "Preflight cannot run while a simulation is applying");
        }
        preflight = LiveTunePreflightEvaluator.evaluate(plan, context);
        state = preflight.isReady()
                ? LiveTuneSessionState.READY : LiveTuneSessionState.BLOCKED;
        return preflight;
    }

    public LiveTunePlan getPlan() {
        return plan;
    }

    public synchronized LiveTuneSessionState getState() {
        return state;
    }

    public synchronized LiveTunePreflight getPreflight() {
        return preflight;
    }

    synchronized void beginSimulation() {
        if (state != LiveTuneSessionState.READY) {
            throw new IllegalStateException(
                    "A passing preflight is required before simulation");
        }
        state = LiveTuneSessionState.APPLYING;
    }

    synchronized void finishSimulation(boolean verified) {
        if (state != LiveTuneSessionState.APPLYING) {
            throw new IllegalStateException("No simulation is applying");
        }
        state = verified
                ? LiveTuneSessionState.VERIFIED : LiveTuneSessionState.FAILED;
    }
}
