/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class FuelLogAnalysisTest {
    private static void assertThrows(Class<? extends RuntimeException> expected, Runnable action) {
        try { action.run(); fail("Expected " + expected.getSimpleName()); }
        catch (RuntimeException failure) { assertTrue(failure.toString(), expected.isInstance(failure)); }
    }
    private LogDataset log(String rows) throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time (msec),MAF (V),Learning (%),Correction (%),Pulse (ms),Load (g/rev),State\n" + rows));
    }
    @Test public void mafMatchesLegacyAdditionWithOrderedCountedBins() throws Exception {
        LogDataset data = log("0,2.31,4,-1,2,1,8\n1,2.32,2,3,2,1,8\n2,1.1,-2,1,2,1,8\n");
        FuelLogAnalysis.Result result = FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, .1, Collections.emptyList());
        assertEquals(3, result.getAccepted()); assertEquals(0, result.getInvalid());
        assertEquals(2, result.getBins().size());
        assertEquals(1.1, result.getBins().get(0).getLower(), 1e-12);
        FuelLogAnalysis.Bin bin = result.getBins().get(1);
        assertEquals(2, bin.getCount()); assertEquals(4, bin.getMean(), 1e-12);
        assertEquals(3, bin.getMinimum(), 0); assertEquals(5, bin.getMaximum(), 0);
        assertEquals(2.3, bin.getLower(), 1e-12); assertEquals(2.4, bin.getUpper(), 1e-12);
        assertEquals(4, data.getValue(0, 2), 0); // Source data never changes.
        assertThrows(UnsupportedOperationException.class, () -> result.getBins().clear());
    }
    @Test public void injectorMatchesLegacyFourCylinderFuelProjection() throws Exception {
        LogDataset data = log("0,2,0,0,2.25,1.2,8\n1,2,0,0,2.26,1.4,8\n");
        FuelLogAnalysis.Result result = FuelLogAnalysis.injector(data, LogRange.all(data), 4, 5, 14.7, 732, .1, Collections.emptyList());
        assertEquals(2, result.getAccepted());
        assertEquals(1.3 / 2 / 14.7 * 1000 / 732, result.getBins().get(0).getMean(), 1e-12);
    }
    @Test public void rangeAndInclusiveFiltersSeparateMissingFromExcluded() throws Exception {
        LogDataset data = log("0,2,1,1,2,1,8\n1,2,2,2,2,1,8\n2,2,3,3,2,1,10\n3,2,4,4,2,1,\n4,,5,5,2,1,8\n");
        FuelLogAnalysis.Result result = FuelLogAnalysis.maf(data, LogRange.of(1, 5, 5), 1, 2, 3, .1,
                Arrays.asList(new FuelLogAnalysis.Filter(6, 8, 8)));
        assertEquals(1, result.getAccepted()); assertEquals(1, result.getFiltered()); assertEquals(2, result.getInvalid());
        assertEquals(4, result.getBins().get(0).getMean(), 0);
    }
    @Test public void invalidRequiredValuesAndZeroPulseWidthDoNotBecomeZeros() throws Exception {
        LogDataset data = log("0,NaN,1,1,0,1,8\n1,2,Infinity,1,2,NaN,8\n2,-1,1,1,-1,1,8\n3,2,1,1,2,-1,8\n");
        FuelLogAnalysis.Result maf = FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, .1, Collections.emptyList());
        assertEquals(3, maf.getInvalid()); assertEquals(1, maf.getAccepted());
        FuelLogAnalysis.Result injector = FuelLogAnalysis.injector(data, LogRange.all(data), 4, 5, 14.7, 732, .1, Collections.emptyList());
        assertEquals(4, injector.getInvalid()); assertEquals(0, injector.getAccepted()); assertTrue(injector.getBins().isEmpty());
    }
    @Test public void decimalBoundariesAreStableAndLegitimateZeroIsRetained() throws Exception {
        LogDataset data = log("0,2.3,0,0,2,1,8\n1,2.299,0,0,2,1,8\n2,0,0,0,2,1,8\n");
        FuelLogAnalysis.Result result = FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, .1, Collections.emptyList());
        assertEquals(3, result.getBins().size()); assertEquals(0, result.getBins().get(0).getMean(), 0);
        assertEquals(2.2, result.getBins().get(1).getLower(), 1e-12);
        assertEquals(2.3, result.getBins().get(2).getLower(), 1e-12);
    }
    @Test public void invalidConfigurationFailsInsteadOfReturningPlausibleResults() throws Exception {
        LogDataset data = log("0,2,1,1,2,1,8\n"); LogRange all = LogRange.all(data);
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.maf(data, all, 0, 2, 3, .1, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.maf(data, all, 1, 2, 2, .1, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.maf(data, all, 1, 2, 3, 0, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.maf(data, all, 1, 2, 3, Double.NaN, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.maf(data, LogRange.of(0, 2, 2), 1, 2, 3, .1, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.injector(data, all, 4, 5, 0, 732, .1, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.injector(data, all, 4, 5, 14.7, Double.POSITIVE_INFINITY, .1, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.injector(data, all, 4, 4, 14.7, 732, .1, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new FuelLogAnalysis.Filter(1, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.maf(data, all, 1, 2, 3, .1, Arrays.asList(new FuelLogAnalysis.Filter(99, 0, 1))));
    }
    @Test public void excessiveBinCountsAreBounded() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int row = 0; row <= FuelLogAnalysis.MAX_BINS; row++) rows.append(row + "," + row + ",0,0,2,1,8\n");
        LogDataset data = log(rows.toString());
        assertThrows(IllegalArgumentException.class, () -> FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, Collections.emptyList()));
    }
}
