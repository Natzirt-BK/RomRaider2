/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.PortableLogSample;
import com.romraider.portable.PortableLogSession;
import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.PortableLoggerQueryBatch;
import com.romraider.portable.logger.ReadOnlyLoggerTransport;
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import com.romraider.portable.logger.definition.PortableMut2LogConfigReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real session/CSV tests with deterministic transports; no Android framework or ECU. */
public class ReadOnlyRecordingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final int END = -1;
    private static final String CONFIG = "XXRR2-MUT-IIXX\ntype=mut2\nparamname=RPM\nparamid=0x21\nscalingrpn=x,31.25,*\n"
            + "paramname=Battery\nparamid=0x14\nscalingrpn=x,0.0733,*\n";

    @Test public void constructionAndRepeatedSnapshotsNeverAcquireOrStart() throws Exception {
        try (Harness h = new Harness()) {
            assertEquals(PortableLoggerProtocol.MUT2, h.recording.protocol());
            for (int i = 0; i < 1000; i++) {
                assertEquals(ReadOnlyRecording.Phase.NEW, h.recording.snapshot().phase());
                assertFalse(h.recording.snapshot().active());
                assertNull(h.recording.completedLog());
            }
            assertEquals(0, h.opens.get());
            assertEquals(0, h.transport.identifies.get());
            h.recording.close();
            assertTrue(h.recording.awaitStopped(0, TimeUnit.MILLISECONDS));
            assertThrows(IllegalStateException.class, h.recording::start);
        }
    }

    @Test public void queuedStartCanBeStoppedWithoutAcquiringAnything() throws Exception {
        List<Runnable> pending = new ArrayList<>();
        AtomicInteger opens = new AtomicInteger();
        ReadOnlyRecording recording = new ReadOnlyRecording(cancelled -> {
            opens.incrementAndGet(); throw new IOException("Must not acquire");
        }, definition(), profile(), pending::add, () -> 10);
        recording.start();
        assertEquals(ReadOnlyRecording.Phase.PREPARING, recording.snapshot().phase());
        recording.stop();
        assertEquals(ReadOnlyRecording.Phase.STOPPING, recording.snapshot().phase());
        assertFalse(recording.awaitStopped(0, TimeUnit.MILLISECONDS));
        pending.remove(0).run();
        assertTrue(recording.awaitStopped(0, TimeUnit.MILLISECONDS));
        assertEquals(0, opens.get());
        assertNull(recording.completedLog());
        assertFalse(recording.snapshot().active());
        assertThrows(IllegalStateException.class, recording::start);
    }

    @Test public void stopDuringPreparationCleansUpWithoutIdentifying() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Harness h = new Harness()) {
            h.prepare = () -> { entered.countDown(); gate(release); };
            h.recording.start();
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                h.recording.stop();
                assertTrue(h.cancelled.getAsBoolean());
                assertNull(h.recording.completedLog());
                assertEquals(ReadOnlyRecording.Phase.STOPPING, h.recording.snapshot().phase());
            } finally { release.countDown(); }
            await(h.recording);
            assertEquals(0, h.transport.identifies.get());
            assertEquals(0, h.transport.reads.get());
            assertEquals(1, h.releases.get());
            assertSame(h.log, h.recording.completedLog());
            assertThrows(IllegalStateException.class, () -> h.log.append(sample()));
        }
    }

    @Test public void immutableLatestCycleKeepsReceiptTimeAcrossReplacementReaders() throws Exception {
        try (Harness h = new Harness()) {
            h.recording.start();
            h.cycle();
            waitUntil(() -> h.recording.snapshot().samples() == 2);
            ReadOnlyRecording.Snapshot firstScreen = h.recording.snapshot();
            assertEquals(ReadOnlyRecording.Phase.RECORDING, firstScreen.phase());
            assertEquals("MUT2_GENERIC", firstScreen.ecuId());
            assertEquals(2, firstScreen.ready());
            assertEquals(0, firstScreen.unavailable());
            assertTrue(firstScreen.timestampMillis() >= 0);
            assertEquals(77, firstScreen.receivedAtNanos());
            assertEquals(2500, firstScreen.values().get(0).getValue(), 0);
            assertThrows(UnsupportedOperationException.class, () -> firstScreen.values().clear());
            h.clock.set(3_000_000_077L);
            for (int reader = 0; reader < 1000; reader++) {
                assertSame(firstScreen, h.recording.snapshot());
                assertEquals(77, h.recording.snapshot().receivedAtNanos());
            }
            assertNull(h.recording.completedLog());
            assertEquals(1, h.opens.get());
            assertEquals(1, h.transport.identifies.get());
            h.cycle();
            waitUntil(() -> h.recording.snapshot().samples() == 4);
            assertEquals(2, firstScreen.samples());
            assertEquals(4, h.recording.snapshot().samples());
            assertEquals(3_000_000_077L, h.recording.snapshot().receivedAtNanos());
            assertEquals(2, h.recording.snapshot().values().size());
        }
    }

    @Test public void stoppedReadPublishesNeitherPartialCycleNorLateLiveState() throws Exception {
        try (Harness h = new Harness()) {
            h.recording.start();
            waitUntil(() -> h.transport.reads.get() == 1);
            assertEquals(ReadOnlyRecording.Phase.CONNECTING, h.recording.snapshot().phase());
            assertTrue(h.recording.snapshot().values().isEmpty());
            h.recording.stop();
            h.transport.responses.add(80);
            await(h.recording);
            assertEquals(1, h.transport.reads.get());
            assertEquals(0, h.recording.snapshot().samples());
            assertTrue(h.recording.snapshot().values().isEmpty());
            assertEquals(ReadOnlyRecording.Phase.STOPPED, h.recording.snapshot().phase());
            assertEquals(1, h.transport.closes.get());
            assertEquals(1, h.releases.get());
        }
    }

    @Test public void peaksIncludeUnobservedCyclesAndResetWithoutChangingFreshnessOrCsv() throws Exception {
        try (Harness h = new Harness()) {
            h.cycle(); h.recording.start();
            waitUntil(() -> h.recording.snapshot().samples() == 2);
            ReadOnlyRecording.Snapshot first = h.recording.snapshot();
            h.transport.responses.add(160); h.transport.responses.add(180);
            waitUntil(() -> h.recording.snapshot().samples() == 4);
            assertEquals(2500, first.maximum(0), 0);
            assertEquals(5000, h.recording.snapshot().maximum(0), 0);
            assertEquals(2500, h.recording.snapshot().minimum(0), 0);
            long received = h.recording.snapshot().receivedAtNanos();
            h.clock.set(received + 4_000_000_000L);
            h.recording.resetPeaks();
            assertEquals(received, h.recording.snapshot().receivedAtNanos());
            assertEquals(4, h.recording.snapshot().samples());
            assertEquals(5000, h.recording.snapshot().minimum(0), 0);
            h.transport.responses.add(40); h.transport.responses.add(180);
            waitUntil(() -> h.recording.snapshot().samples() == 6);
            assertEquals(1250, h.recording.snapshot().minimum(0), 0);
            assertEquals(5000, h.recording.snapshot().maximum(0), 0);
            h.recording.stop(); h.transport.responses.add(END); await(h.recording);
            assertEquals(1250, h.recording.snapshot().minimum(0), 0);
            assertEquals(5000, h.recording.snapshot().maximum(0), 0);
            assertEquals(6, h.recording.completedLog().size());
        }
    }

    @Test public void disconnectRetainsCompletedCyclesAndNormalRomRaiderExport() throws Exception {
        try (Harness h = new Harness()) {
            h.cycle();
            h.transport.responses.add(80); // Second cycle fails before Battery.
            h.transport.responses.add(END);
            h.recording.start();
            await(h.recording);
            assertEquals(2, h.recording.snapshot().samples());
            assertEquals(2, h.recording.snapshot().values().size());
            assertEquals("Synthetic USB detached", h.recording.snapshot().message());
            assertSame(h.log, h.recording.completedLog());
            assertEquals(1, h.log.snapshot().size()); // Disk spool retains more than memory.
            try (java.util.stream.Stream<String> lines = Files.lines(h.spool.toPath())) {
                assertEquals(2, lines.count());
            }
            StringWriter csv = new StringWriter(); h.log.writeRomRaiderCsv(csv);
            String[] rows = csv.toString().split("\n");
            assertEquals("Time (msec),RPM (scaled),Battery (scaled)", rows[0]);
            assertEquals(2, rows.length);
            String[] cells = rows[1].split(",");
            assertEquals(2500, Double.parseDouble(cells[1]), 0);
            assertEquals(13.194, Double.parseDouble(cells[2]), 1e-9);
            assertThrows(IllegalStateException.class, () -> h.log.append(sample()));
            assertThrows(IllegalStateException.class, h.recording::start);
        }
    }

    @Test public void stopIsNonblockingAndCompletionWaitsForFullUsbRelease() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Harness h = new Harness()) {
            h.release = () -> { entered.countDown(); gate(release); };
            h.cycle(); h.transport.responses.add(END);
            h.recording.start();
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                h.recording.stop(); h.recording.close();
                assertEquals(ReadOnlyRecording.Phase.STOPPING, h.recording.snapshot().phase());
                assertTrue(h.recording.snapshot().active());
                assertNull(h.recording.completedLog());
                assertFalse(h.recording.awaitStopped(0, TimeUnit.MILLISECONDS));
                assertTrue(h.spool.isFile());
            } finally { release.countDown(); }
            await(h.recording);
            assertSame(h.log, h.recording.completedLog());
            h.recording.close(); h.recording.stop();
            assertEquals(1, h.releases.get());
            assertEquals(1, h.transport.closes.get());
            assertTrue(h.spool.isFile()); // Closing an owner never deletes data.
        }
    }

    @Test public void acquisitionFailureAndWorkerRejectionCannotAutoRetry() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        ReadOnlyRecording recording = new ReadOnlyRecording(cancelled -> {
            opens.incrementAndGet(); throw new IOException("USB permission denied");
        }, definition(), profile());
        recording.start(); await(recording);
        assertEquals(1, opens.get());
        assertTrue(recording.snapshot().message().contains("USB permission denied"));
        assertNull(recording.completedLog());
        assertThrows(IllegalStateException.class, recording::start);

        ReadOnlyRecording rejected = new ReadOnlyRecording(cancelled -> {
            opens.incrementAndGet(); throw new IOException("Must not acquire");
        }, definition(), profile(), task -> { throw new RejectedExecutionException("closed"); }, () -> 0);
        rejected.start(); await(rejected);
        assertEquals(1, opens.get());
        assertTrue(rejected.snapshot().message().contains("worker could not start"));
        assertFalse(rejected.snapshot().active());
        assertThrows(IllegalStateException.class, rejected::start);
    }

    @Test public void usbReleaseFailureDoesNotHideReadFailureOrDeleteCsv() throws Exception {
        try (Harness h = new Harness()) {
            h.release = () -> { throw new IOException("Synthetic release failure"); };
            h.cycle(); h.transport.responses.add(END);
            h.recording.start(); await(h.recording);
            assertTrue(h.recording.snapshot().message().contains("Synthetic USB detached"));
            assertTrue(h.recording.snapshot().message().contains("Synthetic release failure"));
            assertEquals(2, h.recording.completedLog().size());
            StringWriter csv = new StringWriter(); h.recording.completedLog().writeRomRaiderCsv(csv);
            assertTrue(csv.toString().startsWith("Time (msec),RPM (scaled),Battery (scaled)"));
            assertEquals(1, h.releases.get());
        }
    }

    @Test public void kLineCleanupFailureStillReleasesUsbAndRetainsFinishedRecording() throws Exception {
        try (Harness h = new Harness()) {
            h.transport.failClose = true;
            h.cycle(); h.transport.responses.add(END);
            h.recording.start(); await(h.recording);
            assertTrue(h.recording.snapshot().message().contains("Adapter cleanup failed"));
            assertTrue(h.recording.snapshot().message().contains("Synthetic USB detached"));
            assertEquals(1, h.releases.get());
            assertEquals(2, h.recording.completedLog().size());
            assertThrows(IllegalStateException.class, () -> h.log.append(sample()));
        }
    }

    @Test public void allResourceOperationsRunOnOwnedWorkerNotCallingThread() throws Exception {
        try (Harness h = new Harness(true)) {
            Thread caller = Thread.currentThread();
            h.cycle(); h.transport.responses.add(END);
            h.recording.start(); await(h.recording);
            assertNotSame(caller, h.openThread);
            assertEquals("rr2-read-only-recording", h.openThread.getName());
            assertSame(h.openThread, h.transport.readThread);
            assertSame(h.openThread, h.releaseThread);
            assertEquals(1, h.opens.get());
        }
    }

    @Test public void longUnobservedRecordingHasBoundedLatestSnapshotAndCompleteSpool() throws Exception {
        try (Harness h = new Harness()) {
            h.transport.fastCycles = 20_000;
            h.recording.start(); await(h.recording);
            assertEquals(40_000, h.recording.snapshot().samples());
            assertEquals(2, h.recording.snapshot().values().size());
            assertEquals(1, h.recording.completedLog().snapshot().size());
            try (java.util.stream.Stream<String> lines = Files.lines(h.spool.toPath())) {
                assertEquals(40_000, lines.count());
            }
            assertEquals(1, h.opens.get());
            assertEquals(1, h.transport.identifies.get());
            assertEquals(1, h.releases.get());
        }
    }

    @Test public void mismatchedProtocolIsRejectedBeforeResourceFactory() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        PortableLoggerProfile wrong = new PortableLoggerProfile("SSM", profile().selections(), List.of());
        PortableLoggerDefinition definition = definition();
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyRecording(cancelled -> {
            opens.incrementAndGet(); throw new IOException("Must not acquire");
        }, definition, wrong));
        assertEquals(0, opens.get());
    }

    @Test public void concurrentStartsAcquireExactlyOnce() throws Exception {
        try (Harness h = new Harness()) {
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger rejected = new AtomicInteger();
            List<Thread> callers = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                Thread caller = new Thread(() -> {
                    try {
                        gate(go);
                        h.recording.start();
                    } catch (IllegalStateException expected) { rejected.incrementAndGet(); }
                    catch (IOException failure) { throw new AssertionError(failure); }
                });
                callers.add(caller); caller.start();
            }
            go.countDown();
            for (Thread caller : callers) { caller.join(5000); assertFalse(caller.isAlive()); }
            waitUntil(() -> h.transport.identifies.get() == 1);
            assertEquals(19, rejected.get());
            assertEquals(1, h.opens.get());
        }
    }

    @Test public void storageFailureStopsAndReleasesWithoutClaimingARecordedCycle() throws Exception {
        try (Harness h = new Harness()) {
            Files.createDirectory(h.spool.toPath()); // No writer can open this synthetic spool.
            h.cycle(); h.recording.start(); await(h.recording);
            assertTrue(h.recording.snapshot().message().contains("could not be written to storage"));
            assertEquals(0, h.recording.snapshot().samples());
            assertTrue(h.recording.snapshot().values().isEmpty());
            assertEquals(1, h.releases.get());
            assertFalse(h.recording.snapshot().active());
        }
    }

    @Test public void lateIdentificationAfterStopCannotReappearAsConnected() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        PortableLogSession log = new PortableLogSession();
        ReadOnlyLoggerTransport transport = new ReadOnlyLoggerTransport() {
            public String identifyEcu(PortableLoggerProtocol protocol) throws IOException {
                entered.countDown(); gate(release); return "MUT2_GENERIC";
            }
            public byte[] read(PortableLoggerQueryBatch batch) throws IOException {
                reads.incrementAndGet(); throw new IOException("Must not read after stop");
            }
            public void closeReadOnlyKLine() { closes.incrementAndGet(); }
        };
        ReadOnlyRecording recording = new ReadOnlyRecording(cancelled ->
                new ReadOnlyRecording.Resources(transport, log, () -> releases.incrementAndGet()),
                definition(), profile());
        recording.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            recording.stop();
        } finally { recording.close(); release.countDown(); }
        await(recording);
        assertEquals("", recording.snapshot().ecuId());
        assertEquals(0, recording.snapshot().ready());
        assertEquals(0, reads.get());
        assertEquals(1, closes.get());
        assertEquals(1, releases.get());
        assertEquals(0, recording.completedLog().size());
    }

    @Test public void ssmUsesSameOwnerWithoutChangingAddressBatchesOrCsvOrder() throws Exception {
        PortableLogSession log = PortableLogSession.streaming(temporary.newFile(), 1);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        ReadOnlyLoggerTransport transport = new ReadOnlyLoggerTransport() {
            public String identifyEcu(PortableLoggerProtocol protocol) throws IOException {
                if (protocol != PortableLoggerProtocol.SSM) throw new IOException("Wrong protocol");
                return "SYNTHETIC_SSM";
            }
            public byte[] read(PortableLoggerQueryBatch batch) throws IOException {
                if (reads.incrementAndGet() > 1) throw new IOException("Synthetic USB detached");
                if (batch.getProtocol() != PortableLoggerProtocol.SSM) throw new IOException("Wrong batch protocol");
                byte[] values = new byte[batch.getAddresses().length];
                for (int i = 0; i < values.length; i++) {
                    values[i] = (byte) (batch.getAddresses()[i] == 0x21 ? 80 : 180);
                }
                return values;
            }
            public void closeReadOnlyKLine() { }
        };
        PortableLoggerDefinition definition = new PortableLoggerDefinition("test", "SSM", definition().parameters());
        PortableLoggerProfile profile = new PortableLoggerProfile("SSM", List.of(
                new PortableLoggerProfile.Selection("Battery", "scaled"),
                new PortableLoggerProfile.Selection("RPM", "scaled")), List.of());
        ReadOnlyRecording recording = new ReadOnlyRecording(cancelled ->
                new ReadOnlyRecording.Resources(transport, log, () -> releases.incrementAndGet()), definition, profile);
        recording.start(); await(recording);
        assertEquals("SYNTHETIC_SSM", recording.snapshot().ecuId());
        assertEquals(2, recording.snapshot().samples());
        assertEquals(1, releases.get());
        StringWriter output = new StringWriter(); recording.completedLog().writeRomRaiderCsv(output);
        assertTrue(output.toString().startsWith("Time (msec),Battery (scaled),RPM (scaled)\n"));
        String[] cells = output.toString().split("\n")[1].split(",");
        assertEquals(13.194, Double.parseDouble(cells[1]), 1e-9);
        assertEquals(2500, Double.parseDouble(cells[2]), 0);
    }

    private static PortableLoggerDefinition definition() throws IOException {
        return PortableMut2LogConfigReader.read(new ByteArrayInputStream(CONFIG.getBytes(StandardCharsets.UTF_8)));
    }

    private static PortableLoggerProfile profile() {
        return new PortableLoggerProfile("MUT2", List.of(new PortableLoggerProfile.Selection("RPM", "scaled"),
                new PortableLoggerProfile.Selection("Battery", "scaled")), List.of());
    }

    private static PortableLogSample sample() { return new PortableLogSample(0, "RPM", "RPM", 0, "scaled"); }

    private static void await(ReadOnlyRecording recording) throws InterruptedException {
        assertTrue("Recording failed to finish", recording.awaitStopped(10, TimeUnit.SECONDS));
        assertEquals(ReadOnlyRecording.Phase.STOPPED, recording.snapshot().phase());
    }

    private static void gate(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IOException("Test gate timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IOException(failure);
        }
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(1_000_000);
        assertTrue("Recording did not reach expected state", condition.getAsBoolean());
    }

    private interface Step { void run() throws IOException; }

    private final class Harness implements AutoCloseable {
        final File spool = temporary.newFile();
        final PortableLogSession log = PortableLogSession.streaming(spool, 1);
        final FakeTransport transport = new FakeTransport();
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger releases = new AtomicInteger();
        final AtomicLong clock = new AtomicLong(77);
        final ReadOnlyRecording recording;
        volatile BooleanSupplier cancelled;
        volatile Thread openThread, releaseThread;
        Step prepare = () -> { };
        Step release = () -> { };

        Harness() throws Exception { this(false); }
        Harness(boolean defaultWorker) throws Exception {
            ReadOnlyRecording.ResourceFactory factory = cancellation -> {
                cancelled = cancellation;
                openThread = Thread.currentThread(); opens.incrementAndGet(); prepare.run();
                return new ReadOnlyRecording.Resources(transport, log, () -> {
                    releaseThread = Thread.currentThread(); releases.incrementAndGet(); release.run();
                });
            };
            recording = defaultWorker ? new ReadOnlyRecording(factory, definition(), profile())
                    : new ReadOnlyRecording(factory, definition(), profile(), task -> {
                        Thread thread = new Thread(task, "recording-test-worker");
                        thread.setDaemon(true); thread.start();
                    }, clock::get);
        }

        void cycle() { transport.responses.add(80); transport.responses.add(180); }

        @Override public void close() throws Exception {
            recording.close(); transport.responses.add(END); await(recording);
            log.finish(); // A never-started fixture still owns this test-created log.
        }
    }

    private static final class FakeTransport implements ReadOnlyLoggerTransport {
        final BlockingQueue<Integer> responses = new LinkedBlockingQueue<>();
        final AtomicInteger identifies = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        volatile Thread readThread;
        int fastCycles;
        boolean failClose;
        public String identifyEcu(PortableLoggerProtocol protocol) throws IOException {
            if (protocol != PortableLoggerProtocol.MUT2) throw new IOException("Unexpected protocol");
            identifies.incrementAndGet(); return "MUT2_GENERIC";
        }
        public byte[] read(PortableLoggerQueryBatch batch) throws IOException {
            readThread = Thread.currentThread();
            int count = reads.incrementAndGet();
            if (fastCycles > 0) {
                if (count > fastCycles * 2) throw new IOException("Synthetic USB detached");
                return new byte[] {(byte) (count % 2 == 1 ? 80 : 180)};
            }
            try {
                Integer value = responses.poll(10, TimeUnit.SECONDS);
                if (value == null) throw new IOException("Test response timed out");
                if (value == END) throw new IOException("Synthetic USB detached");
                return new byte[] {value.byteValue()};
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt(); throw new IOException(failure);
            }
        }
        public void closeReadOnlyKLine() {
            closes.incrementAndGet();
            if (failClose) throw new IllegalStateException("Synthetic K-line cleanup failure");
        }
    }
}
