/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.dataflowSimulation;

import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class MissingDataflowValueTest {
    @Test public void invalidInputInvalidatesPreviousAndDownstreamOutputsThenRecovers() {
        DataflowSimulation simulation = new DataflowSimulation(null, "Synthetic");
        simulation.addInput("input", true);
        simulation.setUpdateFromLogger(true);
        simulation.addAction(new CalculationAction("first", "input*2"));
        simulation.addAction(new CalculationAction("second", "first+1"));
        simulation.updateVariableFromLogger("input", 5);
        assertEquals(10, simulation.simulate(0), 0);
        assertEquals(11, simulation.simulate(1), 0);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            simulation.updateVariableFromLogger("input", invalid);
            assertTrue(Double.isNaN(simulation.simulate(0)));
            assertTrue(Double.isNaN(simulation.simulate(1)));
            assertTrue(Double.isNaN(simulation.getVariableValue("first")));
            assertTrue(simulation.getAction(0).getOutputText().contains("NO VALID DATA"));
        }
        simulation.updateVariableFromLogger("input", 0);
        assertEquals(0, simulation.simulate(0), 0);
        assertEquals(1, simulation.simulate(1), 0);
    }

    @Test public void bitwiseOrComparisonCannotMaskInvalidReferencedInput() {
        HashMap<String, Double> values = new HashMap<>();
        values.put("input", Double.NaN);
        values.put("input2", 5.0);
        assertFalse(new CalculationAction("out", "input==1").isCurrentlyValid(values));
        assertTrue(new CalculationAction("out", "input2*2").isCurrentlyValid(values));
        assertTrue(new CalculationAction("out", "42").isCurrentlyValid(values));
    }

    @Test public void missingTableIsUnavailableNotZero() {
        TableAction action = new TableAction("out", "Missing synthetic table", "x", "");
        HashMap<String, Double> values = new HashMap<>();
        values.put("x", 0.0);
        assertFalse(action.isCurrentlyValid(values));
        assertTrue(Double.isNaN(action.calculate(values)));
        assertTrue(action.getInputs().isEmpty());
        assertTrue(action.getOutputText().contains("NO VALID DATA"));
    }

    @Test public void invalidTableInputNeverReachesLookupAndRecoveryClearsOldInputs() throws Exception {
        TableAction action = new TableAction("out", "Synthetic", "x", "y");
        final int[] lookups = {0};
        com.romraider.maps.Table1D table = new com.romraider.maps.Table1D() {
            @Override public double queryTable(Double x, Double y) {
                assertTrue(Double.isFinite(x) && Double.isFinite(y));
                lookups[0]++;
                return x + y;
            }
        };
        java.lang.reflect.Field field = TableAction.class.getDeclaredField("resolvedTable");
        field.setAccessible(true);
        field.set(action, table);
        HashMap<String, Double> values = new HashMap<>();
        values.put("x", 1.0);
        values.put("y", 2.0);
        assertEquals(3, action.calculate(values), 0);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            values.put("y", invalid);
            assertFalse(action.isCurrentlyValid(values));
            assertTrue(Double.isNaN(action.calculate(values)));
            assertTrue(action.getInputs().isEmpty());
        }
        assertEquals(1, lookups[0]);
        values.put("y", 0.0);
        assertEquals(1, action.calculate(values), 0);
        assertEquals(2, action.getInputs().size());
        assertEquals(2, lookups[0]);
    }
}
