/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.concurrent.CancellationException;
import org.junit.Test;

public class LogRunComparisonTest {
    private LogRunComparison.Run run(String rows) throws Exception { return run("X (rpm),Value (AFR)\n", rows); }
    private LogRunComparison.Run run(String header, String rows) throws Exception {
        var data = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(header + rows));
        return new LogRunComparison.Run(data, LogRange.all(data), 0, 1);
    }
    @Test public void alignsByCommonBinNotSampleNumberAndKeepsUnequalCounts() throws Exception {
        var a = run("0,10\n.5,20\n1,30\n"); var b = run("1,50\n0,40\n");
        var result = LogRunComparison.compare(a, b, 0, 1, 1);
        assertEquals(2, result.rows().size()); var row = result.rows().get(0);
        assertEquals(2, row.countA()); assertEquals(1, row.countB()); assertEquals(15, row.meanA(), 0); assertEquals(25, row.difference(), 0);
        assertEquals(20, result.rows().get(1).difference(), 0); assertEquals(3, result.acceptedA()); assertEquals(2, result.acceptedB());
    }
    @Test public void gapsAndLowCountsNeverCreateFalseDifferences() throws Exception {
        var result = LogRunComparison.compare(run("0,10\n0,20\n2,30\n4,40\n4,40\n"), run("0,40\n2,50\n2,50\n6,60\n6,60\n"), 0, 1, 2);
        assertEquals(LogRunComparison.Coverage.LOW_COUNT, result.rows().get(0).coverage()); assertTrue(Double.isNaN(result.rows().get(0).difference()));
        assertEquals(15, result.rows().get(0).meanA(), 0); assertTrue(Double.isNaN(result.rows().get(0).meanB()));
        assertEquals(LogRunComparison.Coverage.EMPTY, result.rows().get(1).coverage());
        assertEquals(LogRunComparison.Coverage.A_ONLY, result.rows().get(4).coverage());
        assertEquals(LogRunComparison.Coverage.B_ONLY, result.rows().get(6).coverage());
    }
    @Test public void eachRunCanSelectADifferentRangeOfTheSameCapture() throws Exception {
        var whole = run("0,10\n1,20\n0,30\n1,40\n"); var data = whole.dataset();
        var result = LogRunComparison.compare(new LogRunComparison.Run(data, LogRange.of(0, 2, 4), 0, 1), new LogRunComparison.Run(data, LogRange.of(2, 4, 4), 0, 1), 0, 1, 1);
        assertEquals(20, result.rows().get(0).difference(), 0); assertEquals(20, result.rows().get(1).difference(), 0);
    }
    @Test public void unitMatchingIsExplicitAndCaseSensitiveForSiPrefixes() throws Exception {
        var a = run("X (rpm),Value (mV)\n", "0,10\n"); var b = run("Other (rpm),Sensor (MV)\n", "0,10\n");
        rejects(() -> LogRunComparison.compare(a, b, 0, 1, 1));
        var same = run("Other (rpm),Sensor (mV)\n", "0,11\n"); assertEquals(1, LogRunComparison.compare(a, same, 0, 1, 1).rows().get(0).difference(), 0);
    }
    @Test public void missingRowsAndUnrepresentableDifferencesRemainUnavailable() throws Exception {
        String max = Double.toString(Double.MAX_VALUE);
        var result = LogRunComparison.compare(run("0,-" + max + "\n1,\n"), run("0," + max + "\n"), 0, 1, 1);
        assertEquals(1, result.invalidA()); assertEquals(LogRunComparison.Coverage.DIFFERENCE_OVERFLOW, result.rows().get(0).coverage()); assertTrue(Double.isNaN(result.rows().get(0).difference()));
        assertEquals(Double.MAX_VALUE, result.rows().get(0).meanB(), 0);
        assertTrue(LogRunComparison.compare(run("0,\n"), run("0,\n"), 0, 1, 1).rows().isEmpty());
        assertEquals(LogRunComparison.Coverage.B_ONLY, LogRunComparison.compare(run("0,\n"), run("-2,10\n"), 0, 1, 1).rows().get(0).coverage());
    }
    @Test public void combinedCoverageIsBoundedAndResultIsImmutable() throws Exception {
        var a = run("0,10\n"); var b = run("256,10\n");
        rejects(() -> LogRunComparison.compare(a, b, 0, 1, 1)); rejects(() -> LogRunComparison.compare(a, a, 0, 1, 0));
        var result = LogRunComparison.compare(a, run("255,10\n"), 0, 1, 1); assertEquals(256, result.rows().size());
        try { result.rows().clear(); fail("Mutable comparison"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void cancellationIsPreserved() throws Exception {
        var a = run("0,10\n");
        try { Thread.currentThread().interrupt(); try { LogRunComparison.compare(a, a, 0, 1, 1); fail("Ignored cancellation"); } catch (CancellationException expected) { } }
        finally { Thread.interrupted(); }
    }
    private static void rejects(Runnable action) { try { action.run(); fail("Expected rejection"); } catch (IllegalArgumentException expected) { } }
}
