/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.gauge;

/** Bounded visual-only interpolation. Never used by recording, numbers or warnings. */
public final class GaugeMotion {
    public static final long DURATION_NANOS = 100_000_000L;
    private double from = Double.NaN;
    private double target = Double.NaN;
    private long started;
    private long lastUpdate;

    public void update(double value, long now) {
        if (!Double.isFinite(value)) { from = target = Double.NaN; started = lastUpdate = now; return; }
        if (value == target) { lastUpdate = now; return; }
        double current = valueAt(now);
        from = !Double.isFinite(current) || now - lastUpdate > 1_000_000_000L ? value : current;
        target = value;
        started = lastUpdate = now;
    }
    public double valueAt(long now) {
        if (!Double.isFinite(target)) return Double.NaN;
        double t = Math.max(0, Math.min(1, (now - started) / (double) DURATION_NANOS));
        double eased = t * t * (3 - 2 * t);
        // A weighted sum avoids overflowing target - from for extreme finite inputs.
        return from * (1 - eased) + target * eased;
    }
    public boolean isAnimating(long now) {
        return Double.isFinite(target) && from != target && now - started < DURATION_NANOS;
    }
}
