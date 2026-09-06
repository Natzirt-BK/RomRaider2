/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

/** Immutable, bounded index of exact accepted rows; never copies or rewrites CSV values. */
public final class FuelSampleReview {
    public static final int MAX_ACCEPTED_ROWS = 1_000_000;
    public static final int MAX_SCANNED_ROWS = 5_000_000;
    private final LogDataset dataset;
    private final int[] rows;
    private FuelSampleReview(LogDataset dataset, int[] rows) {
        this.dataset = dataset; this.rows = rows;
    }
    public static FuelSampleReview capture(FuelLogAnalysis.Result result) {
        if (result == null) throw new IllegalArgumentException("Complete a fuel analysis first.");
        validateSize(result.getAccepted(), result.getRange().size());
        int[] rows = new int[result.getAccepted()];
        int[] count = {0};
        result.forEachAcceptedRow(row -> {
            if (count[0] >= rows.length) throw new IllegalStateException("Accepted sample count changed.");
            rows[count[0]++] = row;
        });
        if (count[0] != rows.length) throw new IllegalStateException("Accepted sample count changed.");
        return new FuelSampleReview(result.getDataset(), rows);
    }
    static void validateSize(int accepted, int scanned) {
        if (accepted < 0 || scanned < accepted || accepted > MAX_ACCEPTED_ROWS || scanned > MAX_SCANNED_ROWS)
            throw new IllegalArgumentException("Sample review allows at most 1,000,000 accepted rows and 5,000,000 scanned rows. Narrow the sample range and analyze again; no rows were truncated.");
    }
    public LogDataset getDataset() { return dataset; }
    public int size() { return rows.length; }
    public int originalRow(int acceptedIndex) { return rows[acceptedIndex]; }
    /** Finite/missing counts are restricted to accepted rows, not the containing range. */
    public ChannelStatistics statistics(int channel) {
        if (channel < 0 || channel >= dataset.getChannelCount()) throw new IllegalArgumentException("Choose a current CSV channel.");
        return LogStatisticsService.analyzeSamples(dataset, rows.length, index -> rows[index], dataset.getChannels().get(channel));
    }
}
