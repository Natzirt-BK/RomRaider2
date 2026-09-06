/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
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
        for (int row = range.getStartInclusive();
                row < range.getEndExclusive(); row++) {
            double value = dataset.getValue(row, channel.getIndex());
            if (!Double.isFinite(value)) continue;
            finite[count++] = value;
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
                finite[0], finite[count - 1], mean(finite),
                percentile(finite, 0.50), standardDeviation(finite),
                percentile(finite, 0.05), percentile(finite, 0.95));
    }

    private static double mean(double[] values) {
        Sum sum = new Sum();
        for (double value : values) {
            sum.add(value);
            if (!Double.isFinite(sum.value())) {
                // Rare extreme-value fallback. Preserve the exact binary-double
                // inputs, including small residuals after huge cancellations.
                BigDecimal exact = BigDecimal.ZERO;
                for (double input : values) exact = exact.add(new BigDecimal(input));
                return exact.divide(BigDecimal.valueOf(values.length), MathContext.DECIMAL128).doubleValue();
            }
        }
        return sum.value() / values.length;
    }

    private static double standardDeviation(double[] sorted) {
        double minimum = sorted[0], maximum = sorted[sorted.length - 1];
        double span = maximum - minimum;
        if (span == 0.0) return 0.0;
        // Center before scaling when possible: subtracting a rounded original-
        // unit mean loses the spread of adjacent doubles at a huge offset.
        double origin = Double.isFinite(span) ? minimum : 0.0;
        double scale = Double.isFinite(span) ? span : Math.max(Math.abs(minimum), Math.abs(maximum));
        Sum normalized = new Sum();
        for (double value : sorted) normalized.add((value - origin) / scale);
        double center = normalized.value() / sorted.length;
        Sum squares = new Sum();
        for (double value : sorted) {
            double deviation = (value - origin) / scale - center;
            squares.add(deviation * deviation);
        }
        // Population SD cannot exceed half the finite span, or max(abs(value))
        // when the span overflows. Clamp only rounding at that mathematical bound.
        double bound = Double.isFinite(span) ? 0.5 : 1.0;
        return scale * Math.min(bound, Math.sqrt(squares.value() / sorted.length));
    }

    private static final class Sum {
        private double sum, correction;
        void add(double value) {
            double next = sum + value;
            correction += Math.abs(sum) >= Math.abs(value)
                    ? (sum - next) + value : (value - next) + sum;
            sum = next;
        }
        double value() { return sum + correction; }
    }

    private static double percentile(double[] sorted, double fraction) {
        if (sorted.length == 1) return sorted[0];
        double position = fraction * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted[lower];
        double weight = position - lower;
        double difference = sorted[upper] - sorted[lower];
        return Double.isFinite(difference) ? sorted[lower] + difference * weight
                : sorted[lower] * (1.0 - weight) + sorted[upper] * weight;
    }
}
