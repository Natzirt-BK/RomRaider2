/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import Jama.Matrix;
import org.junit.Test;

public class FuelCurveAnalysisTest {
    private FuelLogAnalysis.Result data(double[] x, double[] y, double width) throws Exception {
        StringBuilder csv = new StringBuilder("Time (msec),Input (V),Learning (%),Correction (%)\n");
        for (int i = 0; i < x.length; i++) csv.append(i).append(',').append(x[i]).append(',').append(y[i]).append(",0\n");
        LogDataset log = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(csv.toString()));
        return FuelLogAnalysis.maf(log, LogRange.all(log), 1, 2, 3, width, List.of());
    }
    private void rejects(Runnable action) {
        try { action.run(); fail("Expected invalid fit to be rejected"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void linearFitUsesActualAcceptedCoordinatesNotBinCenters() throws Exception {
        FuelLogAnalysis.Result result = data(new double[] {1.1, 1.2, 1.4, 1.8}, new double[] {1.7, 1.9, 2.3, 3.1}, 1);
        assertEquals(1, result.getBins().size());
        FuelCurveAnalysis.Polynomial fit = FuelCurveAnalysis.fit(result, 1);
        assertEquals(2, fit.linearSlope(), 1e-12); assertEquals(-.5, fit.linearIntercept(), 1e-12);
        assertEquals(2.5, fit.predict(1.5), 1e-12); assertEquals(4, fit.getSamples());
        assertEquals(1, fit.getRSquared(), 1e-12); assertEquals(0, fit.getRmse(), 1e-12);
        assertEquals(1.375, result.getBins().get(0).getMeanX(), 1e-12);
        double[] coefficients = fit.getNormalizedCoefficients(); coefficients[0] = 1e99;
        assertEquals(2.5, fit.predict(1.5), 1e-12);
    }

    @Test public void streamedQrMatchesIndependentHouseholderSolve() throws Exception {
        Random random = new Random(703);
        for (int degree = 1; degree <= 6; degree++) {
            double[] x = new double[200], y = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = 1 + 4.0 * i / (x.length - 1);
                y[i] = 3 - 2 * x[i] + .3 * x[i] * x[i] + random.nextGaussian() * .01;
            }
            FuelCurveAnalysis.Polynomial fit = FuelCurveAnalysis.fit(data(x, y, .1), degree);
            double[][] design = new double[x.length][degree + 1], observations = new double[x.length][1];
            for (int i = 0; i < x.length; i++) {
                double z = (x[i] - 3) / 2; design[i][0] = 1; observations[i][0] = y[i];
                for (int j = 1; j <= degree; j++) design[i][j] = design[i][j - 1] * z;
            }
            Matrix reference = new Matrix(design).qr().solve(new Matrix(observations));
            double sum = 0;
            for (int i = 0; i < x.length; i++) {
                double expected = 0;
                for (int j = degree; j >= 0; j--) expected = expected * ((x[i] - 3) / 2) + reference.get(j, 0);
                assertEquals(expected, fit.predict(x[i]), 1e-10);
                sum += Math.pow(y[i] - expected, 2);
            }
            assertEquals(Math.sqrt(sum / x.length), fit.getRmse(), 1e-12);
            assertEquals(Math.sqrt(sum / (x.length - degree - 1)), fit.getResidualDeviation(), 1e-12);
        }
    }

    @Test public void normalizedBasisHandlesLargeOffsetsAndWideOutputScales() throws Exception {
        double[] x = {1e12, 1e12 + 1, 1e12 + 2, 1e12 + 3};
        double[] y = {2e200, 4e200, 6e200, 8e200};
        FuelCurveAnalysis.Polynomial fit = FuelCurveAnalysis.fit(data(x, y, 1e10), 1);
        assertEquals(5, fit.predict(1e12 + 1.5) / 1e200, 1e-12);
        assertEquals(2, fit.linearSlope() / 1e200, 1e-12);
        assertTrue(Double.isFinite(fit.getRmse()));
    }

    @Test public void degreeTwentyIsAvailableWhenTheObservedDesignIsResolved() throws Exception {
        double[] x = new double[100], y = new double[100];
        for (int i = 0; i < x.length; i++) { x[i] = i / 99.0; y[i] = 1 + x[i] + x[i] * x[i]; }
        FuelCurveAnalysis.Polynomial fit = FuelCurveAnalysis.fit(data(x, y, .01), 20);
        assertEquals(20, fit.getDegree()); assertEquals(1.75, fit.predict(.5), 1e-8);
    }

    @Test public void rankAndSampleLimitsRejectPlausibleButUnsupportedFits() throws Exception {
        FuelLogAnalysis.Result same = data(new double[] {2, 2, 2}, new double[] {1, 2, 3}, 1);
        rejects(() -> FuelCurveAnalysis.fit(same, 1));
        FuelLogAnalysis.Result two = data(new double[] {1, 2}, new double[] {1, 2}, 1);
        for (int degree : new int[] {0, 2, 21}) rejects(() -> FuelCurveAnalysis.fit(two, degree));
        FuelLogAnalysis.Result repeated = data(new double[] {1, 1, 2, 2}, new double[] {1, 1, 2, 2}, 1);
        rejects(() -> FuelCurveAnalysis.fit(repeated, 2));
        FuelCurveAnalysis.Polynomial exact = FuelCurveAnalysis.fit(two, 1);
        assertTrue(Double.isNaN(exact.getResidualDeviation())); // zero residual degrees of freedom
    }

    @Test public void constantOutputAndMissingPredictionsAreNotInventedStatistics() throws Exception {
        FuelCurveAnalysis.Polynomial fit = FuelCurveAnalysis.fit(data(new double[] {1, 2, 3}, new double[] {0, 0, 0}, 1), 1);
        assertEquals(0, fit.predict(2), 0); assertTrue(Double.isNaN(fit.getRSquared()));
        for (double x : new double[] {0, 4, Double.NaN, Double.POSITIVE_INFINITY}) assertTrue(Double.isNaN(fit.predict(x)));
    }

    @Test public void interpolationUsesObservedMeansAndDoesNotBridgeEmptyBins() throws Exception {
        FuelLogAnalysis.Result result = data(new double[] {.2, 1.2, 3.2}, new double[] {2, 4, 8}, 1);
        assertEquals(3, FuelCurveAnalysis.interpolate(result, .7), 1e-12);
        assertEquals(8, FuelCurveAnalysis.interpolate(result, 3.2), 0);
        for (double x : new double[] {0, 2, 4, Double.NaN}) assertTrue(Double.isNaN(FuelCurveAnalysis.interpolate(result, x)));
    }

    @Test public void acceptedReplayPreservesRangeFiltersAndRejectsInvalidRows() throws Exception {
        LogDataset log = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time (msec),Input (V),Learning (%),Correction (%),State\n"
                + "0,0,99,0,8\n1,1,2,0,8\n2,2,4,0,8\n3,3,900,0,9\n4,4,900,0,NaN\n5,NaN,0,0,8\n"));
        List<FuelLogAnalysis.Filter> filters = new ArrayList<>(List.of(new FuelLogAnalysis.Filter(4, 8, 8)));
        FuelLogAnalysis.Result result = FuelLogAnalysis.maf(log, LogRange.of(1, 6, 6), 1, 2, 3, 1, filters);
        filters.clear(); // Later caller edits cannot alter the replayed selection.
        assertEquals(2, result.getAccepted()); assertEquals(1, result.getFiltered()); assertEquals(2, result.getInvalid());
        List<Double> replay = new ArrayList<>(); result.forEachAccepted((x, y) -> { replay.add(x); replay.add(y); });
        assertEquals(List.of(1.0, 2.0, 2.0, 4.0), replay);
        assertEquals(2, FuelCurveAnalysis.fit(result, 1).linearSlope(), 1e-12);
    }

    @Test public void replayAndFitHonorWorkerCancellation() throws Exception {
        FuelLogAnalysis.Result result = data(new double[] {1, 2, 3}, new double[] {2, 4, 6}, 1);
        Thread.currentThread().interrupt();
        try {
            try { FuelCurveAnalysis.fit(result, 1); fail("Expected cancellation"); }
            catch (CancellationException expected) { }
        } finally { Thread.interrupted(); }
    }

    @Test public void excessiveRawSampleWorkIsRejectedBeforeFactorization() {
        LogDataset log = new LogDataset("synthetic.csv", List.of(new LogChannel(0, "Time"),
                new LogChannel(1, "Input"), new LogChannel(2, "Learning"), new LogChannel(3, "Correction")),
                java.util.Collections.nCopies(230000, new double[] {0, 1.1, 2, 0}));
        FuelLogAnalysis.Result result = FuelLogAnalysis.maf(log, LogRange.all(log), 1, 2, 3, 1, List.of());
        assertEquals(1.1, result.getBins().get(0).getMeanX(), 0); // Identical inputs must not drift.
        try { FuelCurveAnalysis.fit(result, 20); fail("Expected bounded-work rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("work limit")); }
    }

    @Test public void injectorProjectionRecoversTheSyntheticLineWithoutUsingBinnedMidpoints() throws Exception {
        StringBuilder csv = new StringBuilder("Time (msec),Pulse (ms),Load (g/rev)\n");
        for (int i = 0; i < 12; i++) {
            double pulse = 1.1 + i * .02, fuel = .02 * (pulse - .5);
            csv.append(i).append(',').append(pulse).append(',').append(fuel * 2 * 14.7 * 732 / 1000).append('\n');
        }
        LogDataset log = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(csv.toString()));
        FuelCurveAnalysis.Polynomial fit = FuelCurveAnalysis.fit(
                FuelLogAnalysis.injector(log, LogRange.all(log), 1, 2, 14.7, 732, 1, List.of()), 1);
        assertEquals(1200, fit.linearSlope() * 60000, 1e-8);
        assertEquals(.5, -fit.linearIntercept() / fit.linearSlope(), 1e-12);
    }

    @Test public void finiteExtremeBinMeansDoNotOverflowOrDrift() throws Exception {
        FuelLogAnalysis.Result constant = data(new double[] {1.1, 1.1, 1.1},
                new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, 1);
        assertEquals(Double.MAX_VALUE, FuelCurveAnalysis.interpolate(constant, 1.1), 0);
        FuelLogAnalysis.Result opposite = data(new double[] {1, 1},
                new double[] {Double.MAX_VALUE, -Double.MAX_VALUE}, 1);
        assertEquals(0, opposite.getBins().get(0).getMean(), 0);
    }
}
