/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.concurrent.CancellationException;
import org.junit.Test;

public class BinnedLogAnalysisTest {
    private LogDataset data(String rows) throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader("X,Y,Value\n" + rows));
    }
    private BinnedLogAnalysis.Result analyze(LogDataset data, boolean surface) {
        return BinnedLogAnalysis.analyze(data, LogRange.all(data), new BinnedLogAnalysis.Axis(0, 0, 1), surface ? new BinnedLogAnalysis.Axis(1, 0, 1) : null, 2);
    }
    @Test public void binsOriginalValuesWithSampleCountsAndUnweightedSampleMeans() throws Exception {
        var result = analyze(data("0,0,10\n0.5,0,20\n1,0,40\n3,0,80\n"), false);
        assertEquals(4, result.accepted()); assertEquals(4, result.columns()); assertFalse(result.isSurface());
        assertEquals(2, result.cellAt(0, 0).count()); assertEquals(15, result.cellAt(0, 0).mean(), 0);
        assertEquals(10, result.cellAt(0, 0).minimum(), 0); assertEquals(20, result.cellAt(0, 0).maximum(), 0);
        assertEquals(0, result.cellAt(0, 2).count()); assertTrue(Double.isNaN(result.cellAt(0, 2).mean()));
    }
    @Test public void gridKeepsEmptyInteriorCellsRatherThanFillingThem() throws Exception {
        var result = analyze(data("0,0,10\n2,0,20\n0,2,30\n2,2,40\n"), true);
        assertTrue(result.isSurface()); assertEquals(3, result.rows()); assertEquals(3, result.columns());
        assertEquals(9, result.cells().size()); assertEquals(0, result.cellAt(1, 1).count());
        assertTrue(Double.isNaN(result.cellAt(1, 1).minimum())); assertEquals(40, result.cellAt(2, 2).mean(), 0);
    }
    @Test public void originNegativeCoordinatesAndOneUlpDecimalBoundarySnappingAreExplicit() throws Exception {
        LogDataset data = data("-0.1,0,1\n0,0,2\n0.3,0,3\n");
        var result = BinnedLogAnalysis.analyze(data, LogRange.all(data), new BinnedLogAnalysis.Axis(0, 0, .1), null, 2);
        assertEquals(-1, result.firstX()); assertEquals(5, result.columns()); assertEquals(3, result.cellAt(0, 4).mean(), 0);
        var shifted = BinnedLogAnalysis.analyze(data, LogRange.all(data), new BinnedLogAnalysis.Axis(0, -.1, .1), null, 2);
        assertEquals(0, shifted.firstX()); assertEquals(-.1, shifted.x().edge(0), 0);
    }
    @Test public void missingRequiredValuesRejectRowsButAnUnusedYDoesNot() throws Exception {
        LogDataset data = data("0,,10\n1,2,\n,2,20\n2,2,30\n");
        assertEquals(2, analyze(data, false).accepted()); assertEquals(2, analyze(data, false).invalid());
        assertEquals(1, analyze(data, true).accepted()); assertEquals(3, analyze(data, true).invalid());
        var empty = analyze(data(",,\n0,0,NaN\n"), true); assertEquals(0, empty.accepted()); assertEquals(2, empty.invalid()); assertTrue(empty.cells().isEmpty());
    }
    @Test public void selectedRangeAndImmutableOutputsPreserveTheOriginalDataset() throws Exception {
        LogDataset data = data("0,0,10\n1,0,20\n2,0,30\n");
        var result = BinnedLogAnalysis.analyze(data, LogRange.of(1, 2, 3), new BinnedLogAnalysis.Axis(0, 0, 1), null, 2);
        assertEquals(1, result.accepted()); assertEquals(1, result.firstX()); assertEquals(20, result.cellAt(0, 0).mean(), 0);
        try { result.cells().clear(); fail("Mutable result"); } catch (UnsupportedOperationException expected) { }
        assertEquals(10, data.getValue(0, 2), 0);
    }
    @Test public void invalidAndOversizedGeometryRejectsWithoutPublishingTruncatedResults() throws Exception {
        rejects(() -> new BinnedLogAnalysis.Axis(0, 0, 0)); rejects(() -> new BinnedLogAnalysis.Axis(0, Double.NaN, 1));
        LogDataset wide = data("0,0,1\n256,0,2\n"); rejects(() -> analyze(wide, false));
        LogDataset grid = data("0,0,1\n255,32,2\n"); rejects(() -> analyze(grid, true));
        LogDataset maximum = data("0,0,1\n255,31,2\n"); assertEquals(8192, analyze(maximum, true).cells().size());
        rejects(() -> BinnedLogAnalysis.analyze(wide, LogRange.all(wide), new BinnedLogAnalysis.Axis(3, 0, 1), null, 2));
        LogDataset tiny = data("1,0,2\n"); rejects(() -> BinnedLogAnalysis.analyze(tiny, LogRange.all(tiny), new BinnedLogAnalysis.Axis(0, 0, Double.MIN_VALUE), null, 2));
    }
    @Test public void extremeFiniteValuesRetainFiniteMeansAndSmallCancellationResiduals() throws Exception {
        String max = Double.toString(Double.MAX_VALUE);
        var result = analyze(data("0,0," + max + "\n0,0," + max + "\n0,0,-" + max + "\n0,0,-" + max + "\n0,0,1\n"), false);
        assertEquals(.2, result.cellAt(0, 0).mean(), 0); assertEquals(5, result.cellAt(0, 0).count());
        assertEquals(1.0 / 3, analyze(data("0,0,1e100\n0,0,1\n0,0,-1e100\n"), false).cellAt(0, 0).mean(), 0);
    }
    @Test public void cancellationStopsAggregation() throws Exception {
        LogDataset data = data("0,0,1\n");
        try { Thread.currentThread().interrupt(); try { analyze(data, false); fail("Ignored cancellation"); } catch (CancellationException expected) { } }
        finally { Thread.interrupted(); }
    }
    private static void rejects(Runnable action) { try { action.run(); fail("Expected rejection"); } catch (IllegalArgumentException expected) { } }
}
