/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** Immutable status update; progress is measured or explicitly indeterminate. */
public final class FlashProgress {
    private final FlashState state;
    private final String message;
    private final long completedUnits;
    private final long totalUnits;
    private final boolean measured;

    private FlashProgress(FlashState state, String message, long completedUnits,
            long totalUnits, boolean measured) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (measured && (completedUnits < 0 || totalUnits <= 0
                || completedUnits > totalUnits)) {
            throw new IllegalArgumentException("invalid measured progress");
        }
        this.state = state;
        this.message = message == null ? "" : message;
        this.completedUnits = completedUnits;
        this.totalUnits = totalUnits;
        this.measured = measured;
    }

    public static FlashProgress indeterminate(FlashState state, String message) {
        return new FlashProgress(state, message, 0L, 0L, false);
    }

    public static FlashProgress measured(FlashState state, String message,
            long completedUnits, long totalUnits) {
        return new FlashProgress(state, message, completedUnits, totalUnits, true);
    }

    public FlashState getState() { return state; }
    public String getMessage() { return message; }
    public boolean isMeasured() { return measured; }
    public long getCompletedUnits() { return completedUnits; }
    public long getTotalUnits() { return totalUnits; }

    public double getFraction() {
        if (!measured) throw new IllegalStateException("progress is indeterminate");
        return completedUnits / (double) totalUnits;
    }
}
