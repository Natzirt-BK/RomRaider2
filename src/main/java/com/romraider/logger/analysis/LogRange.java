/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

/** Half-open sample range used by read-only log analysis services. */
public final class LogRange {
    private final int startInclusive;
    private final int endExclusive;

    private LogRange(int startInclusive, int endExclusive) {
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
    }

    public static LogRange all(LogDataset dataset) {
        if (dataset == null) throw new IllegalArgumentException("dataset");
        return of(0, dataset.getRowCount(), dataset.getRowCount());
    }

    public static LogRange of(int startInclusive, int endExclusive,
            int rowCount) {
        if (rowCount < 0 || startInclusive < 0
                || endExclusive > rowCount || startInclusive >= endExclusive) {
            throw new IllegalArgumentException("invalid log range");
        }
        return new LogRange(startInclusive, endExclusive);
    }

    public int getStartInclusive() {
        return startInclusive;
    }

    public int getEndExclusive() {
        return endExclusive;
    }

    public int size() {
        return endExclusive - startInclusive;
    }
}
