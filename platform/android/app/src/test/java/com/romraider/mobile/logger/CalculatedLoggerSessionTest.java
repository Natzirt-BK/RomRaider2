/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import static org.junit.Assert.*;
import com.romraider.portable.PortableLogSession;
import com.romraider.portable.logger.*;
import com.romraider.portable.logger.definition.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CalculatedLoggerSessionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String XML = "<logger><protocol id='MUT2'>"
            + "<parameter id='P8' name='RPM'><address>33</address><conversions><conversion units='rpm' expr='x*31.25' format='0'/></conversions></parameter>"
            + "<parameter id='P21' name='Hidden pulse'><address>50</address><conversions><conversion units='ms' expr='x/10' format='0.00'/></conversions></parameter>"
            + "<parameter id='P201' name='Injector duty'><depends><ref parameter='P8'/><ref parameter='P21'/></depends><conversions><conversion units='%' expr='P8*[P21:ms]/1200' format='0.00'/></conversions></parameter>"
            + "</protocol></logger>";

    @Test(timeout = 5000) public void calculatedCsvKeepsSelectionOrderAndHidesDependencies() throws Exception {
        Harness h = new Harness(0); h.session.run();
        assertEquals(2, h.cycles); assertEquals(4, h.reads); assertEquals(1, h.identifies); assertEquals(1, h.closes);
        StringWriter output = new StringWriter(); h.log.writeRomRaiderCsv(output);
        String[] rows = output.toString().split("\n");
        assertEquals("Time (msec),Injector duty (%),RPM (rpm)", rows[0]);
        assertEquals(3, rows.length);
        for (int i = 1; i < rows.length; i++) {
            String[] cells = rows[i].split(","); assertEquals(3, cells.length);
            assertEquals(6.25, Double.parseDouble(cells[1]), 1e-12); assertEquals(2500, Double.parseDouble(cells[2]), 0);
        }
        assertFalse(output.toString().contains("Hidden pulse"));
    }

    @Test(timeout = 5000) public void failedDependencyBatchPublishesNoPartialCalculatedCycle() throws Exception {
        Harness h = new Harness(2); h.session.run();
        assertEquals(0, h.cycles); assertEquals(0, h.log.size()); assertEquals("Synthetic disconnect", h.message); assertEquals(1, h.closes);
    }

    @Test(timeout = 5000) public void failedLaterCycleRetainsOnlyTheCompleteEarlierCsvRow() throws Exception {
        Harness h = new Harness(4); h.session.run();
        assertEquals(1, h.cycles); assertEquals(1, h.closes);
        StringWriter output = new StringWriter(); h.log.writeRomRaiderCsv(output);
        assertEquals(2, output.toString().split("\n").length);
        assertEquals("Synthetic disconnect", h.message);
    }

    private final class Harness implements ReadOnlyLoggerTransport, ReadOnlyLoggerSession.Listener {
        final PortableLogSession log;
        final ReadOnlyLoggerSession session;
        final int failAt;
        int cycles, identifies, reads, closes;
        String message;
        Harness(int failAt) throws Exception {
            this.failAt = failAt;
            log = PortableLogSession.streaming(temporary.newFile("synthetic-" + failAt + ".part"), 1);
            PortableLoggerDefinition definition = PortableLoggerDefinitionReader.read(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)), "MUT2");
            PortableLoggerProfile profile = new PortableLoggerProfile("MUT2", List.of(new PortableLoggerProfile.Selection("P201", "%"),
                    new PortableLoggerProfile.Selection("P8", "rpm")), List.of());
            session = new ReadOnlyLoggerSession(this, definition, profile, log, this);
        }
        public String identifyEcu(PortableLoggerProtocol protocol) { assertEquals(PortableLoggerProtocol.MUT2, protocol); identifies++; return "MUT2_GENERIC"; }
        public byte[] read(PortableLoggerQueryBatch batch) throws IOException {
            if (++reads == failAt) throw new IOException("Synthetic disconnect");
            assertEquals(1, batch.getAddresses().length); assertEquals(1, batch.request().length);
            int pid = batch.getAddresses()[0]; assertTrue(pid == 33 || pid == 50);
            return new byte[] {(byte) (pid == 33 ? 80 : 30)};
        }
        public void closeReadOnlyKLine() { closes++; }
        public void onIdentified(String id, int ready, int unavailable) { assertEquals(2, ready); assertEquals(0, unavailable); }
        public void onValues(String id, long time, List<PortableLoggerValue> values, int samples) {
            assertEquals(2, values.size()); assertEquals("P201", values.get(0).getSelection().getParameter().getId());
            if (++cycles == 2) session.stop();
        }
        public void onStopped(String message) { this.message = message; }
    }
}
