/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

/** Normalizes captured timestamps to a monotonic millisecond timeline. */
final class LogTimeline {
    private static final double FALLBACK_SAMPLE_MILLIS = 100.0;
    private final double[] elapsedMillis;

    LogTimeline(LogDataset dataset) {
        if (dataset == null) throw new IllegalArgumentException("dataset");
        elapsedMillis = new double[dataset.getRowCount()];
        LogChannel time = dataset.getTimeChannel();
        if (time == null) {
            fillFallback();
            return;
        }

        double scale = timeScale(time.getUnits());
        double origin = Double.NaN;
        double previous = 0.0;
        for (int row = 0; row < elapsedMillis.length; row++) {
            double raw = dataset.getValue(row, time.getIndex());
            if (Double.isFinite(raw) && !Double.isFinite(origin)) origin = raw;
            double normalized = Double.isFinite(raw) && Double.isFinite(origin)
                    ? Math.max(0.0, (raw - origin) * scale) : previous;
            elapsedMillis[row] = Math.max(previous, normalized);
            previous = elapsedMillis[row];
        }
    }

    double getElapsedMillis(int sampleIndex) {
        return elapsedMillis[sampleIndex];
    }

    int sampleAtOrBefore(double elapsed, LogRange range) {
        int low = range.getStartInclusive();
        int high = range.getEndExclusive() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (elapsedMillis[middle] <= elapsed) low = middle + 1;
            else high = middle - 1;
        }
        return Math.max(range.getStartInclusive(), Math.min(high,
                range.getEndExclusive() - 1));
    }

    private void fillFallback() {
        for (int row = 0; row < elapsedMillis.length; row++) {
            elapsedMillis[row] = row * FALLBACK_SAMPLE_MILLIS;
        }
    }

    private static double timeScale(String units) {
        if ("sec".equalsIgnoreCase(units) || "s".equalsIgnoreCase(units)
                || "second".equalsIgnoreCase(units)
                || "seconds".equalsIgnoreCase(units)) return 1000.0;
        return 1.0;
    }
}
