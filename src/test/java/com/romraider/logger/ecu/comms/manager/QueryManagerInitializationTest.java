/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.manager;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.ui.MessageListener;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real initialization orchestration with synthetic connections; no worker is started. */
public class QueryManagerInitializationTest {
    @Test
    public void successfulAttemptClosesCallbacksBeforeConnectionCleanup() throws Exception {
        Fixture f = new Fixture();
        f.connection.onClose = () -> {
            f.connection.ecu.callback(ecu("late"));
            f.connection.dime.callback(null, true);
        };
        assertTrue(f.initialize());
        assertEquals("current", f.identity.getEcuId());
        assertSame(f.metadata, f.cached);
        assertEquals(1, f.ecuResults);
        assertEquals(1, f.dimeResults);
        assertEquals(1, f.connection.closes);
        assertEquals(4, f.messages.size()); // Both initialization stages started and completed.
        try { f.connection.dime.getDmInit(); fail("Completed cache lookup succeeded"); }
        catch (IllegalStateException expected) { }
        try { f.connection.dime.needToInit(); fail("Completed attempt requested discovery"); }
        catch (IllegalStateException expected) { }
    }

    @Test
    public void oldAttemptCannotOverwriteAnotherAttemptOnTheSameOwner() throws Exception {
        Fixture f = new Fixture();
        assertTrue(f.initialize());
        EcuInitCallback oldEcu = f.connection.ecu;
        DmInitCallback oldDime = f.connection.dime;
        f.connection.onEcu = () -> {
            oldEcu.callback(ecu("old"));
            oldDime.callback(null, true);
        };
        assertTrue(f.initialize());
        assertEquals("current", f.identity.getEcuId());
        assertSame(f.metadata, f.cached);
        assertEquals(2, f.ecuResults);
        assertEquals(2, f.dimeResults);
    }

    @Test
    public void stopDuringEcuReadRejectsResultAndSkipsDimeInitialization() throws Exception {
        Fixture f = new Fixture();
        f.connection.onEcu = f.manager::stop;
        assertFalse(f.initialize());
        assertNull(f.identity);
        assertEquals(0, f.ecuResults);
        assertEquals(0, f.connection.dimeCalls);
        assertEquals(1, f.connection.closes);
        assertEquals(1, f.messages.size()); // Started, but no success/error message after Stop.
    }

    @Test
    public void alreadyStoppedManagerNeverCreatesAConnection() throws Exception {
        Fixture f = new Fixture();
        f.manager.stop();
        assertFalse(f.initialize());
        assertEquals(0, f.factories.get());
        assertTrue(f.messages.isEmpty());
    }

    @Test
    public void preexistingInterruptionNeverCreatesAConnection() throws Exception {
        Fixture f = new Fixture();
        Thread.currentThread().interrupt();
        try {
            assertFalse(f.initialize());
            assertEquals(0, f.factories.get());
            assertTrue(f.messages.isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test
    public void stopDuringConnectionCreationClosesItWithoutSendingInitialization() throws Exception {
        Fixture f = new Fixture();
        f.onCreate = f.manager::stop;
        assertFalse(f.initialize());
        assertNull(f.connection.ecu);
        assertEquals(0, f.connection.dimeCalls);
        assertEquals(1, f.connection.closes);
        assertEquals(1, f.messages.size());
    }

    @Test
    public void ecuFailureExpiresTheCallbackAndClosesConnection() throws Exception {
        Fixture f = new Fixture();
        f.connection.onEcu = () -> { throw new IllegalStateException("synthetic ECU failure"); };
        assertFalse(f.initialize());
        f.connection.ecu.callback(ecu("late"));
        assertNull(f.identity);
        assertEquals(0, f.connection.dimeCalls);
        assertEquals(1, f.connection.closes);
    }

    @Test
    public void optionalDimeFailureStillExpiresItsCallbacks() throws Exception {
        Fixture f = new Fixture();
        f.connection.failDime = true;
        assertTrue(f.initialize()); // Standard logging remains available as before.
        f.connection.dime.callback(f.metadata, true);
        assertNull(f.cached);
        assertEquals(0, f.dimeResults);
        assertEquals(1, f.connection.closes);
    }

    @Test
    public void interruptedEcuAttemptStopsRetriesAndPreservesTheInterrupt() throws Exception {
        Fixture f = new Fixture();
        f.connection.onEcu = () -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("synthetic interrupted ECU failure");
        };
        try {
            assertFalse(f.initialize());
            assertTrue("ECU interruption must stop retrying", f.stopped());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, f.connection.dimeCalls);
            assertEquals(1, f.connection.closes);
            assertEquals(1, f.messages.size());
            f.connection.ecu.callback(ecu("late"));
            assertNull(f.identity);
            // Even cleanup that consumes the interrupt must not re-enable retries.
            Thread.interrupted();
            assertFalse(f.initialize());
            assertEquals(1, f.factories.get());
            assertEquals(1, f.messages.size());
        } finally { Thread.interrupted(); }
    }

    @Test
    public void interruptedFactoryReturnClosesWithoutSendingEcuCommands() throws Exception {
        Fixture f = new Fixture();
        f.onCreate = () -> Thread.currentThread().interrupt();
        try {
            assertFalse("Interrupted factory return must not initialize", f.initialize());
            assertTrue(f.stopped());
            assertTrue(Thread.currentThread().isInterrupted());
            assertNull(f.connection.ecu);
            assertEquals(0, f.connection.dimeCalls);
            assertEquals(1, f.connection.closes);
            assertEquals(1, f.messages.size());
        } finally { Thread.interrupted(); }
    }

    @Test
    public void interruptedEcuReturnNeverStartsDimeDiscovery() throws Exception {
        Fixture f = new Fixture();
        f.connection.onEcu = () -> Thread.currentThread().interrupt();
        try {
            assertFalse("Interrupted ECU return must not succeed", f.initialize());
            assertTrue(f.stopped());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, f.connection.dimeCalls);
            assertEquals(1, f.connection.closes);
            assertEquals(1, f.messages.size());
        } finally { Thread.interrupted(); }
    }

    @Test
    public void interruptedDimeReturnDoesNotReportSuccess() throws Exception {
        Fixture f = new Fixture();
        f.connection.onDime = () -> Thread.currentThread().interrupt();
        try {
            assertFalse("Interrupted DimeMod return must not succeed", f.initialize());
            assertTrue(f.stopped());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, f.connection.closes);
            assertEquals(3, f.messages.size());
            // Cancellation does not clear state accepted before the manager observes it.
            assertSame(f.metadata, f.cached);
        } finally { Thread.interrupted(); }
    }

    @Test
    public void interruptedDimeFailureDoesNotFallBackToStandardLogging() throws Exception {
        Fixture f = new Fixture();
        f.connection.onDime = () -> Thread.currentThread().interrupt();
        f.connection.failDime = true;
        try {
            assertFalse("Cancellation is not an optional DimeMod failure", f.initialize());
            assertTrue(f.stopped());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, f.connection.closes);
            assertEquals(3, f.messages.size());
            assertNull(f.cached);
        } finally { Thread.interrupted(); }
    }

    @Test
    public void interruptedDimeAttemptDoesNotReportSuccessAndPreservesCancellation() throws Exception {
        Fixture f = new Fixture();
        f.connection.interruptDime = true;
        try {
            assertFalse(f.initialize());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(f.stopped());
            f.connection.dime.callback(f.metadata, true);
            assertNull(f.cached);
            assertEquals(1, f.connection.closes);
            assertEquals(3, f.messages.size()); // ECU completed; DimeMod started but was cancelled.
        } finally { Thread.interrupted(); }
    }

    private static EcuInit ecu(String id) {
        return new EcuInit() {
            public String getEcuId() { return id; }
            public byte[] getEcuInitBytes() { return new byte[128]; }
        };
    }

    private static final class Fixture {
        final DmInit metadata = new DmInit(new byte[] {3, 1, 0, 1});
        final FakeConnection connection = new FakeConnection(metadata);
        final AtomicInteger factories = new AtomicInteger();
        final List<String> messages = new ArrayList<>();
        final QueryManagerImpl manager;
        EcuInit identity;
        DmInit cached;
        int ecuResults, dimeResults;
        Runnable onCreate = () -> { };
        Fixture() throws Exception {
            manager = new QueryManagerImpl(value -> { identity = value; ecuResults++; }, new DmInitCallback() {
                public void callback(DmInit value, boolean force) { cached = value; dimeResults++; }
                public boolean needToInit() { return cached == null; }
                public DmInit getDmInit() { return cached; }
            }, new MessageListener() {
                public void reportStats(String text) { }
                public void reportMessage(String text) { messages.add(text); }
                public void reportMessageInTitleBar(String text) { }
                public void reportError(String text) { }
                public void reportError(Exception error) { }
                public void reportError(String text, Exception error) { }
            }, Runnable::run, () -> { factories.incrementAndGet(); onCreate.run(); return connection; });
            // Enable only the isolated orchestration method, never run()/start().
            java.lang.reflect.Field stop = QueryManagerImpl.class.getDeclaredField("stop");
            stop.setAccessible(true);
            stop.setBoolean(manager, false);
        }
        boolean initialize() {
            return manager.initConnection(new Module("ECU", new byte[] {0x10}, "Synthetic",
                    new byte[] {(byte) 0xf0}, false), "synthetic connection");
        }
        boolean stopped() throws Exception {
            java.lang.reflect.Field stop = QueryManagerImpl.class.getDeclaredField("stop");
            stop.setAccessible(true);
            return stop.getBoolean(manager);
        }
    }

    private static final class FakeConnection implements LoggerConnection {
        final DmInit metadata;
        EcuInitCallback ecu;
        DmInitCallback dime;
        Runnable onEcu = () -> { }, onDime = () -> { }, onClose = () -> { };
        int dimeCalls, closes;
        boolean failDime, interruptDime;
        FakeConnection(DmInit metadata) { this.metadata = metadata; }
        public void ecuInit(EcuInitCallback callback, Module module) {
            ecu = callback;
            onEcu.run();
            callback.callback(ecu("current"));
        }
        public void dmInit(DmInitCallback callback, Module module) throws InterruptedException {
            dimeCalls++;
            dime = callback;
            onDime.run();
            if (interruptDime) throw new InterruptedException("synthetic cancellation");
            if (failDime) throw new IllegalStateException("synthetic DimeMod failure");
            callback.getDmInit();
            callback.callback(metadata, false);
        }
        public void close() { closes++; onClose.run(); }
        public void open(Module module) { throw new AssertionError("open"); }
        public void ecuReset(Module module, int code) { throw new AssertionError("reset"); }
        public void sendAddressReads(Collection<EcuQuery> queries, Module module, PollingState state) { throw new AssertionError("poll"); }
        public void clearLine() { throw new AssertionError("clear"); }
        public void sendAddressWrites(Map<EcuQuery, byte[]> queries, Module module) { throw new AssertionError("write"); }
    }
}
