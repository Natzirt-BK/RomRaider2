/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

public class RomRaiderCsvLogParserTest {
    private final RomRaiderCsvLogParser parser = new RomRaiderCsvLogParser();

    @Test
    public void parsesHeadersQuotedFieldsAndMissingSamples() throws Exception {
        String csv = "\ufeffTime (msec),Engine Speed (rpm),\"Fuel, Trim (%)\"\n"
                + "0,1496,0.00\n"
                + "102,1524,\n"
                + "204,1501,-1.25\n";

        LogDataset dataset = parser.parse("test.csv", new StringReader(csv));

        assertEquals("test.csv", dataset.getSourceName());
        assertEquals(3, dataset.getChannelCount());
        assertEquals(3, dataset.getRowCount());
        assertEquals("Time", dataset.getChannels().get(0).getName());
        assertEquals("msec", dataset.getChannels().get(0).getUnits());
        assertEquals("Fuel, Trim", dataset.getChannels().get(2).getName());
        assertTrue(Double.isNaN(dataset.getValue(1, 2)));
        assertEquals(-1.25, dataset.getValue(2, 2), 0.0);
    }

    @Test
    public void rejectsMisalignedRowsInsteadOfSilentlyShiftingChannels()
            throws Exception {
        try {
            parser.parse("bad.csv", new StringReader(
                    "Time (msec),RPM (rpm)\n0,1000\n100\n"));
            fail("Expected a row-width error");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Line 3"));
            assertTrue(e.getMessage().contains("expected 2"));
        }
    }

    @Test
    public void rejectsNonNumericSamplesWithLocation() throws Exception {
        try {
            parser.parse("bad.csv", new StringReader(
                    "Time (msec),RPM (rpm)\n0,running\n"));
            fail("Expected a numeric conversion error");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Line 2, column 2"));
        }
    }
}
