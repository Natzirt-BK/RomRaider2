/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

/** Terminal result from the mock-only live-tuning executor. */
public final class LiveTuneSimulationResult {
    private final boolean verified;
    private final int appliedChanges;
    private final String message;

    public LiveTuneSimulationResult(boolean verified, int appliedChanges,
            String message) {
        this.verified = verified;
        this.appliedChanges = appliedChanges;
        this.message = message;
    }

    public boolean isVerified() {
        return verified;
    }

    public int getAppliedChanges() {
        return appliedChanges;
    }

    public String getMessage() {
        return message;
    }
}
