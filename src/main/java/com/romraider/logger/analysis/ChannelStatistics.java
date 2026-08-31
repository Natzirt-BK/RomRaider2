/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

/** Deterministic descriptive statistics for one channel and sample range. */
public final class ChannelStatistics {
    private final LogChannel channel;
    private final int sampleCount;
    private final int missingCount;
    private final double minimum;
    private final double maximum;
    private final double mean;
    private final double median;
    private final double standardDeviation;
    private final double percentile05;
    private final double percentile95;

    ChannelStatistics(LogChannel channel, int sampleCount, int missingCount,
            double minimum, double maximum, double mean, double median,
            double standardDeviation, double percentile05,
            double percentile95) {
        this.channel = channel;
        this.sampleCount = sampleCount;
        this.missingCount = missingCount;
        this.minimum = minimum;
        this.maximum = maximum;
        this.mean = mean;
        this.median = median;
        this.standardDeviation = standardDeviation;
        this.percentile05 = percentile05;
        this.percentile95 = percentile95;
    }

    public LogChannel getChannel() { return channel; }
    public int getSampleCount() { return sampleCount; }
    public int getMissingCount() { return missingCount; }
    public double getMinimum() { return minimum; }
    public double getMaximum() { return maximum; }
    public double getMean() { return mean; }
    public double getMedian() { return median; }
    public double getStandardDeviation() { return standardDeviation; }
    public double getPercentile05() { return percentile05; }
    public double getPercentile95() { return percentile95; }
}
