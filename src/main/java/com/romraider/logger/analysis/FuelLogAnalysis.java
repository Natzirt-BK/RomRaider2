/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Read-only projections of the legacy MAF/injector logger calculations. */
public final class FuelLogAnalysis {
    public static final int MAX_BINS = 2000;
    private FuelLogAnalysis() { }

    @FunctionalInterface public interface PointConsumer { void accept(double x, double y); }
    @FunctionalInterface private interface PointSource { void forEach(PointConsumer consumer); }

    /** Inclusive filter limits; an unavailable value rejects the sample. */
    public static final class Filter {
        private final int channel;
        private final double minimum, maximum;
        public Filter(int channel, double minimum, double maximum) {
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
                throw new IllegalArgumentException("Filter limits must be finite and ordered");
            }
            this.channel = channel; this.minimum = minimum; this.maximum = maximum;
        }
    }

    public static final class Bin {
        private final double lower, upper, meanX, mean, minimum, maximum;
        private final int count;
        private Bin(double lower, double upper, Accumulator values) {
            this.lower = lower; this.upper = upper;
            meanX = values.meanX; mean = values.mean; minimum = values.minimum; maximum = values.maximum;
            count = values.count;
        }
        public double getLower() { return lower; }
        public double getUpper() { return upper; }
        public double getMean() { return mean; }
        public double getMeanX() { return meanX; }
        public double getMinimum() { return minimum; }
        public double getMaximum() { return maximum; }
        public int getCount() { return count; }
    }

    public static final class Result {
        private final List<Bin> bins;
        private final int accepted, invalid, filtered;
        private final PointSource points;
        private Result(List<Bin> bins, int accepted, int invalid, int filtered, PointSource points) {
            this.bins = Collections.unmodifiableList(new ArrayList<Bin>(bins));
            this.accepted = accepted; this.invalid = invalid; this.filtered = filtered;
            this.points = points;
        }
        public List<Bin> getBins() { return bins; }
        public int getAccepted() { return accepted; }
        public int getInvalid() { return invalid; }
        public int getFiltered() { return filtered; }
        /** Replays the exact accepted raw projections without retaining a second sample array. */
        public void forEachAccepted(PointConsumer consumer) {
            if (consumer == null) throw new IllegalArgumentException("Point consumer is required");
            points.forEach(consumer);
        }
    }

    /** Inputs must be volts and percent. Output is learning + correction (%). */
    public static Result maf(LogDataset data, LogRange range, int voltage,
            int learning, int correction, double binWidth, List<Filter> filters) {
        return maf(data, range, voltage, learning, correction, binWidth, filters, null);
    }
    public static Result maf(LogDataset data, LogRange range, int voltage,
            int learning, int correction, double binWidth, List<Filter> filters, FuelRateFilter rate) {
        validateChannel(data, correction);
        if (voltage == learning || voltage == correction || learning == correction) {
            throw new IllegalArgumentException("Choose distinct MAF, learning and correction channels");
        }
        return analyze(data, range, voltage, learning, correction,
                binWidth, filters, false, 1, 1, rate);
    }

    /**
     * Legacy four-cylinder projection: load(g/rev) / 2 / stoich AFR * 1000 /
     * density(g/L). Output is estimated fuel cc per combustion event, not a
     * measured injector flow rate or a correction ready to apply to a ROM.
     */
    public static Result injector(LogDataset data, LogRange range, int pulseWidth,
            int load, double stoichAfr, double density, double binWidth, List<Filter> filters) {
        return injector(data, range, pulseWidth, load, stoichAfr, density, binWidth, filters, null);
    }
    public static Result injector(LogDataset data, LogRange range, int pulseWidth,
            int load, double stoichAfr, double density, double binWidth, List<Filter> filters, FuelRateFilter rate) {
        positive(stoichAfr, "Stoichiometric AFR"); positive(density, "Fuel density");
        if (pulseWidth == load) throw new IllegalArgumentException("Choose distinct pulse-width and load channels");
        return analyze(data, range, pulseWidth, load, -1,
                binWidth, filters, true, stoichAfr, density, rate);
    }

    private static Result analyze(LogDataset data, LogRange range, int xChannel,
            int yChannel, int correction, double width, List<Filter> filters,
            boolean injector, double stoich, double density, FuelRateFilter rate) {
        validateChannel(data, xChannel); validateChannel(data, yChannel);
        positive(width, "Bin width");
        if (range == null || range.getEndExclusive() > data.getRowCount()) {
            throw new IllegalArgumentException("Range exceeds the loaded log");
        }
        if (filters == null) throw new IllegalArgumentException("Filters are required");
        for (Filter filter : filters) {
            if (filter == null) throw new IllegalArgumentException("Missing filter");
            validateChannel(data, filter.channel);
        }
        List<Filter> capturedFilters = List.copyOf(filters);
        if (rate != null) rate.validate(data);
        Map<Long, Accumulator> groups = new TreeMap<Long, Accumulator>();
        int[] counts = visitAccepted(data, range, xChannel, yChannel, correction, width, capturedFilters,
                injector, stoich, density, rate, (x, y) -> {
                    long key = (long) Math.floor(Math.nextUp(x / width));
                    Accumulator bin = groups.get(key);
                    if (bin == null) {
                        if (groups.size() >= MAX_BINS) throw new IllegalArgumentException("Too many bins; increase bin width or narrow the sample range");
                        bin = new Accumulator(); groups.put(key, bin);
                    }
                    bin.add(x, y);
                });
        List<Bin> bins = new ArrayList<Bin>();
        for (Map.Entry<Long, Accumulator> entry : groups.entrySet()) {
            bins.add(new Bin(entry.getKey() * width, (entry.getKey() + 1) * width, entry.getValue()));
        }
        return new Result(bins, counts[0], counts[1], counts[2], consumer -> visitAccepted(data, range,
                xChannel, yChannel, correction, width, capturedFilters, injector, stoich, density, rate, consumer));
    }

    private static int[] visitAccepted(LogDataset data, LogRange range, int xChannel,
            int yChannel, int correction, double width, List<Filter> filters,
            boolean injector, double stoich, double density, FuelRateFilter rate, PointConsumer consumer) {
        int accepted = 0, invalid = 0, filtered = 0;
        for (int row = range.getStartInclusive(); row < range.getEndExclusive(); row++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            double x = data.getValue(row, xChannel), y = data.getValue(row, yChannel);
            if (injector) y = y / 2 / stoich * 1000 / density;
            else y += data.getValue(row, correction);
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || (injector && (x == 0 || y <= 0))) {
                invalid++; continue;
            }
            boolean missing = false, excluded = false;
            for (Filter filter : filters) {
                double value = data.getValue(row, filter.channel);
                missing |= !Double.isFinite(value);
                excluded |= value < filter.minimum || value > filter.maximum;
            }
            if (rate != null) {
                double slope = rate.rateAt(data, row, range.getStartInclusive());
                missing |= !Double.isFinite(slope);
                excluded |= slope > rate.maximumRate();
            }
            if (missing) { invalid++; continue; }
            if (excluded) { filtered++; continue; }
            // Snap a single rounding ULP at a decimal bin boundary (e.g. 2.3/0.1).
            double keyValue = Math.floor(Math.nextUp(x / width));
            if (!Double.isFinite(keyValue) || keyValue > 1_000_000_000L
                    || !Double.isFinite((keyValue + 1) * width)) {
                invalid++; continue;
            }
            consumer.accept(x, y); accepted++;
        }
        return new int[] {accepted, invalid, filtered};
    }

    private static void validateChannel(LogDataset data, int channel) {
        if (data == null || channel < 0 || channel >= data.getChannelCount()
                || data.getChannels().get(channel).isTimeChannel()) {
            throw new IllegalArgumentException("Choose a numeric data channel, not the time column");
        }
    }
    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be finite and positive");
    }
    private static final class Accumulator {
        private int count;
        private double meanX, mean, minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        private void add(double x, double value) {
            count++;
            // Accepted x values are nonnegative, so this difference cannot overflow.
            meanX = count == 1 ? x : meanX + (x - meanX) / count;
            double delta = value - mean;
            mean = count == 1 ? value : Double.isFinite(delta)
                    ? mean + delta / count : mean * (1.0 - 1.0 / count) + value / count;
            minimum = Math.min(minimum, value); maximum = Math.max(maximum, value);
        }
    }
}
