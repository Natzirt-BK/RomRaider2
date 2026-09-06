/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Independent run ranges aligned by explicit common X bins, not sample index or elapsed time. */
public final class LogRunComparison {
    public record Run(LogDataset dataset, LogRange range, int xChannel, int valueChannel) {
        public Run {
            if (dataset == null || range == null || range.getEndExclusive() > dataset.getRowCount()
                    || xChannel < 0 || xChannel >= dataset.getChannelCount() || valueChannel < 0 || valueChannel >= dataset.getChannelCount())
                throw new IllegalArgumentException("Choose current channels and a valid sample range for each run.");
        }
    }
    public enum Coverage { BOTH, A_ONLY, B_ONLY, LOW_COUNT, EMPTY, DIFFERENCE_OVERFLOW }
    public record Row(long index, double lower, double upper, int countA, int countB,
            double meanA, double meanB, double difference, Coverage coverage) { }
    public record Result(List<Row> rows, int acceptedA, int invalidA, int acceptedB, int invalidB,
            String xUnits, String valueUnits, int minimumCount) {
        public Result { rows = List.copyOf(rows); }
    }
    private LogRunComparison() { }
    public static Result compare(Run a, Run b, double origin, double width, int minimumCount) {
        if (a == null || b == null || minimumCount < 1 || minimumCount > BinnedLogAnalysis.MAX_ROWS)
            throw new IllegalArgumentException("Both runs and a positive minimum count are required.");
        String xUnits = matchingUnits(a.dataset.getChannels().get(a.xChannel), b.dataset.getChannels().get(b.xChannel));
        String units = matchingUnits(a.dataset.getChannels().get(a.valueChannel), b.dataset.getChannels().get(b.valueChannel));
        var binsA = BinnedLogAnalysis.analyze(a.dataset, a.range, new BinnedLogAnalysis.Axis(a.xChannel, origin, width), null, a.valueChannel);
        var binsB = BinnedLogAnalysis.analyze(b.dataset, b.range, new BinnedLogAnalysis.Axis(b.xChannel, origin, width), null, b.valueChannel);
        if (binsA.columns() == 0 && binsB.columns() == 0) return new Result(List.of(), 0, binsA.invalid(), 0, binsB.invalid(), xUnits, units, minimumCount);
        long first = binsA.columns() == 0 ? binsB.firstX() : binsB.columns() == 0 ? binsA.firstX() : Math.min(binsA.firstX(), binsB.firstX());
        long last = Math.max(binsA.columns() == 0 ? first : binsA.firstX() + binsA.columns() - 1, binsB.columns() == 0 ? first : binsB.firstX() + binsB.columns() - 1);
        if (last - first + 1 > BinnedLogAnalysis.MAX_AXIS_BINS) throw new IllegalArgumentException("Combined run coverage exceeds 256 bin slots including gaps. Increase the width or narrow the ranges; neither run was truncated.");
        List<Row> rows = new ArrayList<>();
        for (long index = first; index <= last; index++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            var ca = cell(binsA, index); var cb = cell(binsB, index);
            int na = ca == null ? 0 : ca.count(), nb = cb == null ? 0 : cb.count();
            double va = na >= minimumCount ? ca.mean() : Double.NaN, vb = nb >= minimumCount ? cb.mean() : Double.NaN;
            double delta = Double.isFinite(va) && Double.isFinite(vb) ? vb - va : Double.NaN;
            Coverage coverage = na == 0 && nb == 0 ? Coverage.EMPTY
                    : (na > 0 && na < minimumCount) || (nb > 0 && nb < minimumCount) ? Coverage.LOW_COUNT
                    : na == 0 ? Coverage.B_ONLY : nb == 0 ? Coverage.A_ONLY
                    : Double.isFinite(delta) ? Coverage.BOTH : Coverage.DIFFERENCE_OVERFLOW;
            if (!Double.isFinite(delta)) delta = Double.NaN;
            rows.add(new Row(index, binsA.x().edge(index), binsA.x().edge(index + 1), na, nb, va, vb, delta, coverage));
        }
        return new Result(rows, binsA.accepted(), binsA.invalid(), binsB.accepted(), binsB.invalid(), xUnits, units, minimumCount);
    }
    private static BinnedLogAnalysis.Cell cell(BinnedLogAnalysis.Result result, long index) {
        return index < result.firstX() || index >= result.firstX() + result.columns() ? null : result.cellAt(0, (int) (index - result.firstX()));
    }
    private static String matchingUnits(LogChannel a, LogChannel b) {
        String first = a.getUnits().trim(), second = b.getUnits().trim();
        if (!first.equals(second)) throw new IllegalArgumentException("Run units differ (" + first + " versus " + second + "). Choose matching-unit channels; no conversion is inferred.");
        return first;
    }
}
