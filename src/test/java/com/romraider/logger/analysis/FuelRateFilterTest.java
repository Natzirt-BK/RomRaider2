/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class FuelRateFilterTest {
    private static void rejects(Runnable action) {
        try { action.run(); fail("Invalid rate filter was accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private LogDataset data(String rows) throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader("Time (msec),MAF (V),Learning (%),Correction (%),State\n" + rows));
    }
    private FuelLogAnalysis.Result analyze(LogDataset data, LogRange range, double maximum, List<FuelLogAnalysis.Filter> filters) {
        return FuelLogAnalysis.maf(data, range, 1, 2, 3, 1, filters, new FuelRateFilter(1, 0, .001, maximum, 1));
    }
    @Test public void usesRecordedMillisecondsAndInclusiveAbsoluteRateLimit() throws Exception {
        LogDataset data = data("0,1,2,0,8\n500,2,4,0,8\n1000,1,6,0,8\n1500,3,8,0,8\n");
        var result = analyze(data, LogRange.all(data), 2, List.of());
        assertEquals(2, result.getAccepted()); assertEquals(1, result.getInvalid()); assertEquals(1, result.getFiltered());
        assertEquals(2, new FuelRateFilter(1, 0, .001, 2, 1).rateAt(data, 2, 0), 0);
    }
    @Test public void firstSelectedRowNeverBorrowsOutsideItsRange() throws Exception {
        LogDataset data = data("0,1,2,0,8\n500,1,4,0,8\n1000,1,6,0,8\n");
        var result = analyze(data, LogRange.of(1, 3, 3), 0, List.of());
        assertEquals(1, result.getAccepted()); assertEquals(1, result.getInvalid());
    }
    @Test public void missingSignalAndBadTimestampsDoNotBridgeToEarlierGoodRows() throws Exception {
        LogDataset data = data("0,1,2,0,8\n100,NaN,2,0,8\n200,1,2,0,8\n200,1,2,0,8\n150,1,2,0,8\n3000,1,2,0,8\n3500,1,2,0,8\n");
        var result = analyze(data, LogRange.all(data), 1, List.of());
        assertEquals(1, result.getAccepted()); assertEquals(6, result.getInvalid()); assertEquals(0, result.getFiltered());
    }
    @Test public void rejectedNumericFilterRowsRemainTheTemporalPredecessor() throws Exception {
        LogDataset data = data("0,1,2,0,8\n500,10,2,0,0\n1000,1,2,0,8\n1500,1,2,0,8\n");
        var result = analyze(data, LogRange.all(data), 1, List.of(new FuelLogAnalysis.Filter(4, 8, 8)));
        assertEquals(1, result.getAccepted()); assertEquals(2, result.getFiltered()); assertEquals(1, result.getInvalid());
    }
    @Test public void rawSampleReplayRetainsTheSameImmutableRateCondition() throws Exception {
        LogDataset data = data("0,1,2,0,8\n500,2,4,0,8\n1000,3,6,0,8\n1500,8,100,0,8\n");
        var result = analyze(data, LogRange.all(data), 2, List.of());
        List<Double> samples = new ArrayList<>(); result.forEachAccepted((x, y) -> samples.add(y));
        assertEquals(List.of(4.0, 6.0), samples);
        var fit = FuelCurveAnalysis.fit(result, 1); assertEquals(2, fit.getSamples()); assertEquals(2, fit.linearSlope(), 1e-12);
    }
    @Test public void invalidProjectionDoesNotEraseTheAdjacentRecordedSignal() throws Exception {
        LogDataset data = data("0,1,2,0,8\n500,2,NaN,0,8\n1000,3,6,0,8\n");
        var result = analyze(data, LogRange.all(data), 2, List.of());
        assertEquals(1, result.getAccepted()); assertEquals(2, result.getInvalid());
    }
    @Test public void injectorUsesTheSameRateConditionOnItsOwnMappedSignal() throws Exception {
        LogDataset data = data("0,1,2,0,8\n500,2,4,0,8\n1000,5,6,0,8\n");
        var result = FuelLogAnalysis.injector(data, LogRange.all(data), 1, 2, 14.7, 732, 1, List.of(), new FuelRateFilter(1, 0, .001, 2, 1));
        assertEquals(1, result.getAccepted()); assertEquals(1, result.getFiltered()); assertEquals(1, result.getInvalid());
    }
    @Test public void invalidMappingsLimitsAndNonfiniteArithmeticFailExplicitly() throws Exception {
        LogDataset data = data("0,1,2,0,8\nInfinity,2,4,0,8\n1000,3,6,0,8\n");
        var result = analyze(data, LogRange.all(data), 2, List.of()); assertEquals(3, result.getInvalid());
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            rejects(() -> new FuelRateFilter(1, 0, invalid, 2, 1));
            rejects(() -> new FuelRateFilter(1, 0, .001, 2, invalid));
        }
        rejects(() -> new FuelRateFilter(1, 0, .001, -1, 1));
        rejects(() -> new FuelRateFilter(1, 2, 1, 2, 1).validate(data));
        rejects(() -> new FuelRateFilter(20, 0, 1, 2, 1).validate(data));
    }
    @Test public void disabledRatePreservesPreviousAnalysisIncludingFirstRow() throws Exception {
        LogDataset data = data("0,1,2,0,8\n0,1,2,0,8\n");
        assertEquals(2, FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, List.of()).getAccepted());
    }
}
