/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Calculates finite-value statistics without depending on Swing or ECU state. */
public final class LogStatisticsService {
    private LogStatisticsService() {
    }

    public static List<ChannelStatistics> analyze(LogDataset dataset,
            LogRange range) {
        if (dataset == null || range == null) {
            throw new IllegalArgumentException("dataset and range are required");
        }
        if (range.getEndExclusive() > dataset.getRowCount()) {
            throw new IllegalArgumentException("range exceeds dataset");
        }
        List<ChannelStatistics> result =
                new ArrayList<ChannelStatistics>(dataset.getChannelCount());
        for (LogChannel channel : dataset.getChannels()) {
            result.add(analyzeChannel(dataset, range, channel));
        }
        return Collections.unmodifiableList(result);
    }

    private static ChannelStatistics analyzeChannel(LogDataset dataset,
            LogRange range, LogChannel channel) {
        double[] finite = new double[range.size()];
        int count = 0;
        double mean = 0.0;
        double m2 = 0.0;
        for (int row = range.getStartInclusive();
                row < range.getEndExclusive(); row++) {
            double value = dataset.getValue(row, channel.getIndex());
            if (!Double.isFinite(value)) continue;
            finite[count++] = value;
            double delta = value - mean;
            mean += delta / count;
            m2 += delta * (value - mean);
        }

        int missing = range.size() - count;
        if (count == 0) {
            return new ChannelStatistics(channel, 0, missing,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN);
        }

        finite = Arrays.copyOf(finite, count);
        Arrays.sort(finite);
        return new ChannelStatistics(channel, count, missing,
                finite[0], finite[count - 1], mean,
                percentile(finite, 0.50), Math.sqrt(m2 / count),
                percentile(finite, 0.05), percentile(finite, 0.95));
    }

    private static double percentile(double[] sorted, double fraction) {
        if (sorted.length == 1) return sorted[0];
        double position = fraction * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted[lower];
        double weight = position - lower;
        return sorted[lower] + (sorted[upper] - sorted[lower]) * weight;
    }
}
