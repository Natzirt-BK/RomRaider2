/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Bounded, sample-weighted aggregation of original CSV values; never a tuning correction. */
public final class BinnedLogAnalysis {
    public static final int MAX_ROWS = 5_000_000, MAX_AXIS_BINS = 256, MAX_CELLS = 8192;
    public record Axis(int channel, double origin, double width) {
        public Axis {
            if (channel < 0 || !Double.isFinite(origin) || !Double.isFinite(width) || width <= 0)
                throw new IllegalArgumentException("Bin origins must be finite and widths must be finite and positive.");
        }
        private long index(double value) {
            double delta = value - origin;
            double position = Double.isFinite(delta) ? delta / width : value / width - origin / width;
            double key = Math.floor(Math.nextUp(position));
            if (!Double.isFinite(key) || Math.abs(key) > 1_000_000_000L) throw new IllegalArgumentException("A bin coordinate is not representable; adjust the origin or width.");
            long result = (long) key;
            if (!Double.isFinite(edge(result)) || !Double.isFinite(edge(result + 1)) || edge(result) >= edge(result + 1))
                throw new IllegalArgumentException("Bin edges overflow or collapse at this precision; adjust the origin or width.");
            return result;
        }
        public double edge(long index) { return Math.fma(index, width, origin); }
    }
    public record Cell(int row, int column, int count, double mean, double minimum, double maximum) { }
    public record Result(Axis x, Axis y, int valueChannel, long firstX, long firstY,
            int columns, int rows, int accepted, int invalid, List<Cell> cells) {
        public Result { cells = List.copyOf(cells); }
        public boolean isSurface() { return y != null; }
        public Cell cellAt(int row, int column) {
            if (row < 0 || row >= rows || column < 0 || column >= columns) throw new IndexOutOfBoundsException();
            return cells.get(row * columns + column);
        }
    }
    private record Key(long x, long y) { }
    private BinnedLogAnalysis() { }

    public static Result analyze(LogDataset data, LogRange range, Axis x, Axis y, int valueChannel) {
        if (data == null || range == null || x == null || range.getEndExclusive() > data.getRowCount()) throw new IllegalArgumentException("Choose a current log and valid applied range.");
        if (range.size() > MAX_ROWS) throw new IllegalArgumentException("Binned analysis allows at most 5,000,000 selected rows. Narrow the range; no samples were truncated.");
        channel(data, x.channel); if (y != null) channel(data, y.channel); channel(data, valueChannel);
        Map<Key, Accumulator> groups = new HashMap<>();
        long firstX = Long.MAX_VALUE, lastX = Long.MIN_VALUE, firstY = Long.MAX_VALUE, lastY = Long.MIN_VALUE;
        int accepted = 0, invalid = 0;
        for (int row = range.getStartInclusive(); row < range.getEndExclusive(); row++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            double vx = data.getValue(row, x.channel), vy = y == null ? 0 : data.getValue(row, y.channel), value = data.getValue(row, valueChannel);
            if (!Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(value)) { invalid++; continue; }
            long ix = x.index(vx), iy = y == null ? 0 : y.index(vy);
            firstX = Math.min(firstX, ix); lastX = Math.max(lastX, ix); firstY = Math.min(firstY, iy); lastY = Math.max(lastY, iy);
            long columns = lastX - firstX + 1, rows = lastY - firstY + 1;
            if (columns > MAX_AXIS_BINS || rows > MAX_AXIS_BINS || columns * rows > MAX_CELLS)
                throw new IllegalArgumentException("Observed bin span exceeds 256 bins per axis or 8,192 cells including gaps. Increase widths or narrow the range; no samples were truncated.");
            groups.computeIfAbsent(new Key(ix, iy), ignored -> new Accumulator()).add(value); accepted++;
        }
        if (accepted == 0) return new Result(x, y, valueChannel, 0, 0, 0, 0, 0, invalid, List.of());
        int columns = (int) (lastX - firstX + 1), rows = (int) (lastY - firstY + 1);
        List<Cell> cells = new ArrayList<>();
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Accumulator group = groups.get(new Key(firstX + column, firstY + row));
            cells.add(group == null ? new Cell(row, column, 0, Double.NaN, Double.NaN, Double.NaN)
                    : new Cell(row, column, group.count, group.mean(), group.minimum, group.maximum));
        }
        return new Result(x, y, valueChannel, firstX, firstY, columns, rows, accepted, invalid, cells);
    }
    private static void channel(LogDataset data, int channel) {
        if (channel < 0 || channel >= data.getChannelCount()) throw new IllegalArgumentException("Map channels from the current CSV.");
    }
    private static final class Accumulator {
        int count;
        double sum, correction, minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        BigDecimal wide;
        void add(double value) {
            count++; minimum = Math.min(minimum, value); maximum = Math.max(maximum, value);
            if (wide != null) { wide = wide.add(new BigDecimal(value)); return; }
            double next = sum + value;
            double adjusted = correction + (Math.abs(sum) >= Math.abs(value) ? (sum - next) + value : (value - next) + sum);
            if (!Double.isFinite(next) || !Double.isFinite(adjusted) || !Double.isFinite(next + adjusted)) {
                wide = new BigDecimal(sum).add(new BigDecimal(correction)).add(new BigDecimal(value));
            } else { sum = next; correction = adjusted; }
        }
        double mean() { return wide == null ? (sum + correction) / count : wide.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128).doubleValue(); }
    }
}
