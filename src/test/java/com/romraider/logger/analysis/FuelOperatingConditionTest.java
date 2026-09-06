/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.*;
import org.junit.Test;

public class FuelOperatingConditionTest {
    private static final double[] BASE = {0, 1.5, 2, 0, 8, 14.7, 2000, 30, 30, 80, 0};
    private static final int[] CHANNELS = {4, 5, 6, 7, 1, 8, 9, 10};
    private static final Double[] MIN = {8.0, 13.0, 0.0, 20.0, 1.2, null, 70.0, 0.0};
    private static final Double[] MAX = {8.0, 16.0, 4500.0, 100.0, 2.6, 45.0, null, 0.0};
    private static void rejects(Runnable action) {
        try { action.run(); fail("Invalid condition accepted"); } catch (IllegalArgumentException expected) { }
    }
    private LogDataset data(List<double[]> rows) throws Exception {
        StringBuilder csv = new StringBuilder("Time (msec),MAF (V),Learning (%),Correction (%),State,AFR,RPM,Flow (g/s),IAT (C),ECT (C),Tip-in\n");
        for (double[] row : rows) { for (int i = 0; i < row.length; i++) csv.append(i == 0 ? "" : ",").append(row[i]); csv.append('\n'); }
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(csv.toString()));
    }
    private List<FuelLogAnalysis.Filter> filters() {
        var filters = new ArrayList<FuelLogAnalysis.Filter>();
        for (var kind : FuelOperatingCondition.values()) filters.add(kind.filter(CHANNELS[kind.ordinal()], MIN[kind.ordinal()], MAX[kind.ordinal()]));
        return filters;
    }
    @Test public void allEightConditionsApplyTogetherAndRejectEachIndependentViolation() throws Exception {
        List<double[]> rows = new ArrayList<>(); rows.add(BASE.clone());
        double[] invalid = {8.9, 18, 5000, 101, 3, 46, 69, 1};
        for (int i = 0; i < CHANNELS.length; i++) { double[] row = BASE.clone(); row[CHANNELS[i]] = invalid[i]; rows.add(row); }
        LogDataset data = data(rows); var result = FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, filters());
        assertEquals(1, result.getAccepted()); assertEquals(8, result.getFiltered()); assertEquals(0, result.getInvalid());
    }
    @Test public void allEnabledMissingValuesRejectRatherThanBypassConditions() throws Exception {
        List<double[]> rows = new ArrayList<>(); rows.add(BASE.clone());
        for (int channel : CHANNELS) { double[] row = BASE.clone(); row[channel] = Double.NaN; rows.add(row); }
        LogDataset data = data(rows); var result = FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, filters());
        assertEquals(1, result.getAccepted()); assertEquals(8, result.getInvalid()); assertEquals(0, result.getFiltered());
    }
    @Test public void inclusiveBoundsAndOneSidedLimitsDoNotInventOtherBounds() throws Exception {
        for (var kind : FuelOperatingCondition.values()) {
            int i = kind.ordinal();
            for (double value : new double[] {MIN[i] == null ? -10 : MIN[i], MAX[i] == null ? 200 : MAX[i]}) {
                double[] row = BASE.clone(); row[CHANNELS[i]] = value;
                LogDataset data = data(List.of(row));
                var result = FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, List.of(kind.filter(CHANNELS[i], MIN[i], MAX[i])));
                assertEquals(kind.name() + " at " + value, 1, result.getAccepted());
            }
        }
    }
    @Test public void disabledConditionsLeaveUnrelatedMissingChannelsUnused() throws Exception {
        double[] row = BASE.clone(); row[4] = Double.NaN; LogDataset data = data(List.of(row));
        assertEquals(1, FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, List.of()).getAccepted());
    }
    @Test public void conditionShapesRejectMissingExtraNonfiniteAndReversedBounds() {
        rejects(() -> FuelOperatingCondition.LOOP_STATE.validate(8.0, 9.0));
        rejects(() -> FuelOperatingCondition.AFR.validate(16.0, 13.0));
        rejects(() -> FuelOperatingCondition.RPM.validate(null, 100.0));
        rejects(() -> FuelOperatingCondition.COOLANT_TEMPERATURE.validate(70.0, 100.0));
        rejects(() -> FuelOperatingCondition.INTAKE_TEMPERATURE.validate(0.0, 45.0));
        rejects(() -> FuelOperatingCondition.TIP_IN.validate(Double.NaN, Double.NaN));
        rejects(() -> FuelOperatingCondition.MAF_VOLTAGE.validate(0.0, Double.POSITIVE_INFINITY));
    }
    @Test public void injectorAndRawReplayRetainAllNamedConditions() throws Exception {
        double[] invalid = BASE.clone(); invalid[4] = 0;
        LogDataset data = data(List.of(BASE.clone(), invalid));
        var result = FuelLogAnalysis.injector(data, LogRange.all(data), 1, 2, 14.7, 732, 1, filters());
        assertEquals(1, result.getAccepted()); assertEquals(1, result.getFiltered());
        int[] replayed = {0}; result.forEachAccepted((x, y) -> replayed[0]++); assertEquals(1, replayed[0]);
    }
}
