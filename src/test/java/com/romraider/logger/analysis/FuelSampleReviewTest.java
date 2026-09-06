/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.Test;

public class FuelSampleReviewTest {
    private LogDataset data() throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time (msec),V (V),Learning (%),Correction (%),State,Pulse (ms),Load (g/rev),Aux\n"
                + "0,1,10,1,8,1,1,100\n100,2,20,1,0,0,2,200\n200,3,30,1,8,3,3,\n"
                + "300,,40,1,8,4,,400\n400,5,50,1,8,5,5,500\n500,6,60,1,8,6,6,600\n"));
    }
    @Test public void capturesNoncontiguousOriginalRowsAndExactChannelStatistics() throws Exception {
        LogDataset data = data();
        FuelLogAnalysis.Result result = FuelLogAnalysis.maf(data, LogRange.of(1, 5, 6), 1, 2, 3, 1,
                List.of(new FuelLogAnalysis.Filter(4, 8, 8)));
        FuelSampleReview review = FuelSampleReview.capture(result);
        assertSame(data, review.getDataset()); assertSame(data, result.getDataset());
        assertEquals(2, review.size()); assertEquals(2, review.originalRow(0)); assertEquals(4, review.originalRow(1));
        ChannelStatistics stats = review.statistics(7);
        assertEquals(1, stats.getSampleCount()); assertEquals(1, stats.getMissingCount()); assertEquals(500, stats.getMean(), 0);
        assertEquals(4, review.statistics(1).getMean(), 0);
        assertEquals(300, review.statistics(0).getMean(), 0);
        assertEquals(3, LogStatisticsService.analyze(data, result.getRange()).get(7).getSampleCount());
    }
    @Test public void replaysCapturedFiltersEvenIfCallersChangeTheirList() throws Exception {
        LogDataset data = data(); List<FuelLogAnalysis.Filter> filters = new ArrayList<>();
        filters.add(new FuelLogAnalysis.Filter(4, 8, 8));
        FuelLogAnalysis.Result result = FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, filters);
        filters.clear(); filters.add(new FuelLogAnalysis.Filter(4, 99, 99));
        List<Integer> rows = new ArrayList<>(); result.forEachAcceptedRow(rows::add);
        assertEquals(List.of(0, 2, 4, 5), rows);
        List<Double> values = new ArrayList<>(); result.forEachAccepted((x, y) -> values.add(y));
        assertEquals(List.of(11.0, 31.0, 51.0, 61.0), values);
        assertEquals(result.getAccepted(), FuelSampleReview.capture(result).size());
    }
    @Test public void rateReviewUsesAdjacentRecordedRowsNotPreviousAcceptedRows() throws Exception {
        LogDataset data = data();
        FuelLogAnalysis.Result result = FuelLogAnalysis.maf(data, LogRange.of(1, 6, 6), 1, 2, 3, 1,
                List.of(new FuelLogAnalysis.Filter(4, 8, 8)), new FuelRateFilter(1, 0, .001, 20, 1));
        FuelSampleReview review = FuelSampleReview.capture(result);
        assertEquals(2, review.size()); assertEquals(2, review.originalRow(0)); assertEquals(5, review.originalRow(1));
    }
    @Test public void injectorReviewIncludesOnlyValidFilteredProjections() throws Exception {
        LogDataset data = data();
        FuelSampleReview review = FuelSampleReview.capture(FuelLogAnalysis.injector(data, LogRange.all(data), 5, 6, 14.7, 732, 1,
                List.of(new FuelLogAnalysis.Filter(4, 8, 8))));
        assertEquals(4, review.size()); assertEquals(0, review.originalRow(0)); assertEquals(2, review.originalRow(1));
        assertEquals(3.75, review.statistics(5).getMean(), 0);
    }
    @Test public void emptyAcceptedSetDoesNotFallBackToAllRows() throws Exception {
        LogDataset data = data();
        FuelSampleReview review = FuelSampleReview.capture(FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1,
                List.of(new FuelLogAnalysis.Filter(4, 99, 99))));
        assertEquals(0, review.size()); ChannelStatistics stats = review.statistics(1);
        assertEquals(0, stats.getSampleCount()); assertEquals(0, stats.getMissingCount()); assertTrue(Double.isNaN(stats.getMean()));
    }
    @Test public void preflightLimitsRejectInsteadOfTruncatingAndValidateChannels() throws Exception {
        FuelSampleReview.validateSize(FuelSampleReview.MAX_ACCEPTED_ROWS, FuelSampleReview.MAX_SCANNED_ROWS);
        rejects(() -> FuelSampleReview.validateSize(FuelSampleReview.MAX_ACCEPTED_ROWS + 1, FuelSampleReview.MAX_SCANNED_ROWS));
        rejects(() -> FuelSampleReview.validateSize(1, FuelSampleReview.MAX_SCANNED_ROWS + 1));
        rejects(() -> FuelSampleReview.capture(null));
        LogDataset data = data(); FuelSampleReview review = FuelSampleReview.capture(FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, List.of()));
        rejects(() -> review.statistics(-1)); rejects(() -> review.statistics(data.getChannelCount()));
    }
    @Test public void replayAndStatisticsRespectCancellation() throws Exception {
        LogDataset data = data(); FuelLogAnalysis.Result result = FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, List.of());
        FuelSampleReview review = FuelSampleReview.capture(result);
        try {
            Thread.currentThread().interrupt();
            try { FuelSampleReview.capture(result); fail("Capture ignored cancellation"); } catch (CancellationException expected) { }
            try { review.statistics(1); fail("Statistics ignored cancellation"); } catch (CancellationException expected) { }
        } finally { Thread.interrupted(); }
    }
    private static void rejects(Runnable action) {
        try { action.run(); fail("Expected input rejection"); } catch (IllegalArgumentException expected) { }
    }
}
