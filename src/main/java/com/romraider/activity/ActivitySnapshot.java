/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.activity;

/** Immutable activity state shared without a Swing dependency. */
public final class ActivitySnapshot {
    private final ActivityState state;
    private final String message;
    private final int progressPercent;
    private final long startedAtMillis;
    private final long updatedAtMillis;

    ActivitySnapshot(ActivityState state, String message, int progressPercent,
            long startedAtMillis, long updatedAtMillis) {
        this.state = state;
        this.message = message == null || message.trim().isEmpty()
                ? "Ready" : message.trim();
        this.progressPercent = progressPercent < 0 ? -1
                : Math.min(100, progressPercent);
        this.startedAtMillis = startedAtMillis;
        this.updatedAtMillis = updatedAtMillis;
    }

    public ActivityState getState() { return state; }
    public String getMessage() { return message; }
    public int getProgressPercent() { return progressPercent; }
    public boolean hasMeasuredProgress() { return progressPercent >= 0; }
    public long getStartedAtMillis() { return startedAtMillis; }
    public long getUpdatedAtMillis() { return updatedAtMillis; }
    public long getElapsedMillis(long nowMillis) {
        long end = state == ActivityState.RUNNING ? nowMillis : updatedAtMillis;
        return Math.max(0L, end - startedAtMillis);
    }
}
