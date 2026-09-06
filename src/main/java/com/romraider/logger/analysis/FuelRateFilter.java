/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

/** Absolute adjacent-row signal slope, using explicitly scaled recorded time. */
public record FuelRateFilter(int signal, int time, double secondsPerTimeUnit,
        double maximumRate, double maximumGapSeconds) {
    public FuelRateFilter {
        if (signal < 0 || time < 0 || signal == time) throw new IllegalArgumentException("Choose distinct signal and recorded-time channels");
        if (!Double.isFinite(secondsPerTimeUnit) || secondsPerTimeUnit <= 0
                || !Double.isFinite(maximumGapSeconds) || maximumGapSeconds <= 0
                || !Double.isFinite(maximumRate) || maximumRate < 0)
            throw new IllegalArgumentException("Rate limit must be finite and nonnegative; time scale and maximum gap must be finite and positive");
    }
    public void validate(LogDataset data) {
        if (data == null || signal >= data.getChannelCount() || time >= data.getChannelCount()
                || data.getChannels().get(signal).isTimeChannel() || !data.getChannels().get(time).isTimeChannel())
            throw new IllegalArgumentException("Map the rate signal and recognized recorded-time column from this log");
    }
    /** NaN means unavailable, never a zero slope. The selection's first row has no predecessor. */
    public double rateAt(LogDataset data, int row, int firstRow) {
        if (row <= firstRow) return Double.NaN;
        double now = data.getValue(row, time), before = data.getValue(row - 1, time);
        double current = data.getValue(row, signal), previous = data.getValue(row - 1, signal);
        if (!Double.isFinite(now) || !Double.isFinite(before) || !Double.isFinite(current) || !Double.isFinite(previous)) return Double.NaN;
        double elapsed = (now - before) * secondsPerTimeUnit;
        if (!Double.isFinite(elapsed) || elapsed <= 0 || elapsed > maximumGapSeconds) return Double.NaN;
        double rate = Math.abs((current - previous) / elapsed);
        return Double.isFinite(rate) ? rate : Double.NaN;
    }
}
