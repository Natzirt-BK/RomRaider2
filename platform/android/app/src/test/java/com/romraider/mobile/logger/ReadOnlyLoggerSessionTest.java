/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.PortableLogSession;
import com.romraider.portable.logger.*;
import com.romraider.portable.logger.definition.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

/** Real session loop with a deterministic transport; no Android stubs or ECU. */
public class ReadOnlyLoggerSessionTest {
    private static final String CONFIG = "type=mut2\nparamname=RPM\nparamid=0x21\nscalingrpn=x,31.25,*\n"
            + "paramname=Battery\nparamid=0x14\nscalingrpn=x,0.0733,*\n";

    @Test public void mut2RecordsConvertedCycleAndClosesOnce() throws Exception {
        Harness h = new Harness();
        h.session.run();
        assertEquals(PortableLoggerProtocol.MUT2, h.transport.protocol);
        assertEquals(List.of(0x21, 0x14), h.transport.pids);
        assertEquals(2, h.log.size());
        assertEquals(2500.0, h.log.snapshot().get(0).getValue(), 0);
        assertEquals(13.194, h.log.snapshot().get(1).getValue(), 0.000001);
        assertEquals(1, h.stopped);
        assertEquals(1, h.transport.closed);
        assertFalse(h.session.isRunning());
    }

    @Test public void stopBeforeRunDoesNotProbeOrRead() throws Exception {
        Harness h = new Harness();
        h.session.stop();
        h.session.run();
        assertEquals(0, h.transport.identifies);
        assertTrue(h.transport.pids.isEmpty());
        assertEquals(1, h.stopped);
    }

    @Test public void ssmStillUsesItsAddressBatchCodec() throws Exception {
        Harness h = new Harness("SSM", new PortableLogSession(), "SSM");
        h.session.run();
        assertEquals(PortableLoggerProtocol.SSM, h.transport.protocol);
        assertEquals(2, h.log.size());
        assertEquals(2500.0, h.log.snapshot().get(0).getValue(), 0);
        assertEquals(1, h.stopped);
    }

    @Test public void stoppingInReadDoesNotPollRemainingPidOrRecordPartialCycle() throws Exception {
        Harness h = new Harness();
        h.transport.afterRead = h.session::stop;
        h.session.run();
        assertEquals(1, h.transport.pids.size());
        assertEquals(0, h.log.size());
        assertEquals("Read-only logger stopped.", h.message);
    }

    @Test public void stoppingAfterIdentificationDoesNotRead() throws Exception {
        Harness h = new Harness();
        h.onIdentity = h.session::stop;
        h.session.run();
        assertTrue(h.transport.pids.isEmpty());
        assertEquals(1, h.stopped);
    }

    @Test public void failureMidCycleDoesNotPublishPartialValues() throws Exception {
        Harness h = new Harness();
        h.transport.failAt = 2;
        h.session.run();
        assertEquals(0, h.log.size());
        assertEquals(0, h.cycles);
        assertEquals("USB detached", h.message);
        assertEquals(1, h.transport.closed);
    }

    @Test public void disconnectPreservesCompletedCycles() throws Exception {
        Harness h = new Harness();
        h.stopAfter = 100;
        h.transport.failAt = 3;
        h.session.run();
        assertEquals(2, h.log.size());
        assertEquals(1, h.cycles);
        assertEquals("USB detached", h.message);
    }

    @Test public void protocolMismatchRejectedBeforeVehicleAccess() throws Exception {
        Harness h = new Harness("SSM", new PortableLogSession());
        h.session.run();
        assertEquals(0, h.transport.identifies);
        assertTrue(h.message.contains("protocol does not match"));
    }

    @Test public void legacyProfileWithoutProtocolUsesExplicitDefinitionProtocol() throws Exception {
        Harness h = new Harness("", new PortableLogSession());
        h.session.run();
        assertEquals(2, h.log.size());
        assertEquals(PortableLoggerProtocol.MUT2, h.transport.protocol);
    }

    @Test public void sessionCannotRunTwice() throws Exception {
        Harness h = new Harness();
        h.session.run();
        assertThrows(IllegalStateException.class, h.session::run);
        assertEquals(1, h.stopped);
    }

    @Test public void cleanupFailureStillFinishesRecordingAndNotifiesUi() throws Exception {
        Harness h = new Harness();
        h.transport.failClose = true;
        h.session.run();
        assertEquals(2, h.log.size());
        assertEquals(1, h.stopped);
        assertTrue(h.message.contains("Adapter cleanup failed"));
    }

    @Test public void badResponseLengthIsReportedWithoutRecording() throws Exception {
        Harness h = new Harness();
        h.transport.badLength = true;
        h.session.run();
        assertEquals(0, h.log.size());
        assertTrue(h.message.contains("expected 1"));
    }

    @Test public void shortRecordingFlushedOnStopAndStillExportable() throws Exception {
        File file = Files.createTempFile("rr2-session-test-", ".part").toFile();
        PortableLogSession log = PortableLogSession.streaming(file, 1);
        try {
            Harness h = new Harness("MUT2", log);
            h.session.run();
            assertTrue(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains("2500.0"));
            StringWriter csv = new StringWriter();
            log.writeLongFormCsv(csv);
            assertEquals(3, csv.toString().lines().count());
            assertEquals(1, log.snapshot().size());
        } finally { log.discard(); }
    }

    @Test public void longSyntheticRunRetainsFullCsvWithBoundedMemory() throws Exception {
        File file = Files.createTempFile("rr2-session-long-test-", ".part").toFile();
        PortableLogSession log = PortableLogSession.streaming(file, 10);
        try {
            Harness h = new Harness("MUT2", log);
            h.stopAfter = 20_000;
            h.session.run();
            assertEquals(40_000, log.size());
            assertEquals(10, log.snapshot().size());
            try (java.util.stream.Stream<String> lines = Files.lines(file.toPath())) {
                assertEquals(40_000, lines.count());
            }
            assertEquals(1, h.stopped);
        } finally { log.discard(); }
    }

    private static final class FakeTransport implements ReadOnlyLoggerTransport {
        PortableLoggerProtocol protocol;
        int identifies;
        int closed;
        int failAt = -1;
        boolean badLength;
        boolean failClose;
        Runnable afterRead = () -> { };
        final List<Integer> pids = new ArrayList<>();
        public String identifyEcu(PortableLoggerProtocol protocol) {
            this.protocol = protocol;
            identifies++;
            return ReadOnlyMut2Protocol.GENERIC_ECU_ID;
        }
        public byte[] read(PortableLoggerQueryBatch batch) throws IOException {
            assertEquals(protocol, batch.getProtocol());
            if (protocol == PortableLoggerProtocol.SSM) {
                assertEquals(0x80, batch.request()[0] & 255);
                byte[] response = new byte[batch.getAddresses().length];
                for (int index = 0; index < response.length; index++) {
                    response[index] = (byte) (batch.getAddresses()[index] == 0x21 ? 80 : 180);
                }
                return response;
            }
            assertEquals(1, batch.request().length);
            int pid = batch.getAddresses()[0];
            pids.add(pid);
            if (pids.size() == failAt) throw new IOException("USB detached");
            afterRead.run();
            return badLength ? new byte[0] : new byte[] {(byte) (pid == 0x21 ? 80 : 180)};
        }
        public void closeReadOnlyKLine() {
            closed++;
            if (failClose) throw new IllegalStateException("permission lost");
        }
    }

    private static final class Harness implements ReadOnlyLoggerSession.Listener {
        final FakeTransport transport = new FakeTransport();
        final PortableLogSession log;
        final ReadOnlyLoggerSession session;
        int stopAfter = 1;
        int cycles;
        int stopped;
        String message;
        Runnable onIdentity = () -> { };
        Harness() throws IOException { this("MUT2", new PortableLogSession()); }
        Harness(String profileProtocol, PortableLogSession log) throws IOException {
            this(profileProtocol, log, "MUT2");
        }
        Harness(String profileProtocol, PortableLogSession log, String definitionProtocol) throws IOException {
            this.log = log;
            PortableLoggerDefinition imported = PortableMut2LogConfigReader.read(
                    new ByteArrayInputStream(CONFIG.getBytes(StandardCharsets.UTF_8)));
            PortableLoggerDefinition definition = new PortableLoggerDefinition("test",
                    definitionProtocol, imported.parameters());
            PortableLoggerProfile profile = new PortableLoggerProfile(profileProtocol,
                    List.of(new PortableLoggerProfile.Selection("RPM", "scaled"),
                            new PortableLoggerProfile.Selection("Battery", "scaled")), List.of());
            session = new ReadOnlyLoggerSession(transport, definition, profile, log, this);
        }
        public void onIdentified(String ecuId, int ready, int unavailable) {
            assertEquals("MUT2_GENERIC", ecuId);
            assertEquals(2, ready);
            assertEquals(0, unavailable);
            onIdentity.run();
        }
        public void onValues(String ecuId, long timestamp, List<PortableLoggerValue> values, int samples) {
            assertTrue(timestamp >= 0);
            assertEquals(2, values.size());
            if (++cycles >= stopAfter) session.stop();
        }
        public void onStopped(String message) { stopped++; this.message = message; }
    }
}
