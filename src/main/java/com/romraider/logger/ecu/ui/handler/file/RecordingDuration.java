/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.handler.file;

import java.util.function.LongSupplier;

/** Monotonic recording lifetime, independent of samples, UI refreshes and disk IO locks. */
final class RecordingDuration {
    private final LongSupplier clock;
    private boolean active;
    private long started, elapsed;

    RecordingDuration(LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }

    synchronized void start() {
        if (active) return;
        started = clock.getAsLong(); elapsed = 0; active = true;
    }

    synchronized void stop() {
        if (!active) return;
        elapsed = Math.max(0, clock.getAsLong() - started); active = false;
    }

    synchronized long elapsedMillis() {
        return (active ? Math.max(0, clock.getAsLong() - started) : elapsed) / 1_000_000;
    }
}
