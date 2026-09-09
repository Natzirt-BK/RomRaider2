/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real diagnostic request with a fail-on-write connection and synthetic replies. */
public class ReadCodesDmRuntimeTest {
    private static final Module MODULE = new Module("ecu", new byte[] {0x10}, "Engine", new byte[] {(byte) 0xf0}, false);
    private static EcuInit identity(String id, byte[] bytes) {
        return new EcuInit() {
            public String getEcuId() { return id; }
            public byte[] getEcuInitBytes() { return bytes; }
        };
    }
    private static EcuInit identity() { return identity("1234567890", new byte[] {1, 2, 3, 4}); }
    private static DmInit metadata() {
        ByteBuffer bytes = ByteBuffer.allocate(112);
        bytes.put((byte) 2).put((byte) 3).putShort((short) 100)
                .putInt(0x20000).putInt(0).putInt(0xDEAD0001);
        for (int i = 0; i < 24; i++) bytes.putInt(0x2000 + i * 0x10);
        return new DmInit(bytes.array());
    }
    private static DmRuntimeReadRequest request() {
        return new DmRuntimeReadRequest(identity(), metadata(), () -> true);
    }

    @Test public void missingCacheSkipsAllConnectionMethods() throws Exception {
        assertNull(new DmRuntimeReadRequest(null, null, () -> true).read(new NoIoConnection(), null));
    }

    @Test public void matchingFreshIdentificationPrecedesRuntimeRead() throws Exception {
        List<String> calls = new ArrayList<>();
        DmInit cached = metadata();
        DmInit refreshed = metadata();
        LoggerConnection connection = new NoIoConnection() {
            public void ecuInit(EcuInitCallback callback, Module module) {
                assertSame(MODULE, module);
                calls.add("identify"); callback.callback(identity());
            }
            public DmInit readDmRuntime(DmInit snapshot, Module module) {
                assertEquals(List.of("identify"), calls);
                assertSame(MODULE, module);
                assertNotSame(cached, snapshot);
                assertArrayEquals(cached.getDmInitBytes(), snapshot.getDmInitBytes());
                calls.add("runtime"); return refreshed;
            }
        };
        assertSame(refreshed, new DmRuntimeReadRequest(identity(), cached, () -> true).read(connection, MODULE));
        assertEquals(List.of("identify", "runtime"), calls);
    }

    @Test public void changedIdOrInitializationBytesRejectBeforeAnyDynamicRead() throws Exception {
        for (EcuInit changed : new EcuInit[] {
                identity("9999999999", new byte[] {1, 2, 3, 4}),
                identity("1234567890", new byte[] {1, 2, 3, 5}),
                identity("1234567890", null), null}) {
            try {
                request().read(new NoIoConnection() {
                    public void ecuInit(EcuInitCallback callback, Module module) { callback.callback(changed); }
                }, MODULE);
                fail("Changed identity accepted");
            } catch (InvalidResponseException expected) { }
        }
    }

    @Test public void missingOrDuplicateRepliesCannotAuthorizeRuntimeReads() throws Exception {
        for (int count : new int[] {0, 2}) {
            try {
                request().read(new NoIoConnection() {
                    public void ecuInit(EcuInitCallback callback, Module module) {
                        for (int i = 0; i < count; i++) callback.callback(identity());
                    }
                }, MODULE);
                fail("Invalid reply count accepted");
            } catch (InvalidResponseException expected) { }
        }
    }

    @Test public void staleOwnerRejectedBeforeIoAfterIdentificationAndAfterRuntime() throws Exception {
        for (int expireAt = 0; expireAt < 3; expireAt++) {
            AtomicBoolean current = new AtomicBoolean(expireAt != 0);
            final int stage = expireAt;
            try {
                new DmRuntimeReadRequest(identity(), metadata(), current::get).read(new NoIoConnection() {
                    public void ecuInit(EcuInitCallback callback, Module module) {
                        assertNotEquals(0, stage);
                        callback.callback(identity());
                        if (stage == 1) current.set(false);
                    }
                    public DmInit readDmRuntime(DmInit cached, Module module) {
                        assertEquals(2, stage);
                        current.set(false); return metadata();
                    }
                }, MODULE);
                fail("Stale owner result returned");
            } catch (IllegalStateException expected) { }
        }
    }

    @Test public void capturedIdentityIsFrozen() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        DmRuntimeReadRequest request = new DmRuntimeReadRequest(identity("1234567890", bytes), metadata(), () -> true);
        bytes[0] = 9;
        assertNotNull(request.read(new MatchingConnection(), MODULE));
    }

    @Test public void lateIdentificationReplyCannotReviveAnExpiredRead() throws Exception {
        AtomicReference<EcuInitCallback> callback = new AtomicReference<>();
        try {
            request().read(new NoIoConnection() {
                public void ecuInit(EcuInitCallback next, Module module) { callback.set(next); }
            }, MODULE);
            fail("Missing identity accepted");
        } catch (InvalidResponseException expected) { }
        callback.get().callback(identity());
    }

    @Test public void missingIdentityUnsupportedMetadataAndMissingModuleFailBeforeIo() throws Exception {
        for (DmRuntimeReadRequest request : new DmRuntimeReadRequest[] {
                new DmRuntimeReadRequest(null, metadata(), () -> true),
                new DmRuntimeReadRequest(identity("", new byte[0]), metadata(), () -> true),
                new DmRuntimeReadRequest(identity(), new DmInit(new byte[] {3, 0, 0, 0}), () -> true)}) {
            try { request.read(new NoIoConnection(), MODULE); fail("Invalid capture accepted"); }
            catch (IllegalStateException expected) { }
        }
        try { request().read(new NoIoConnection(), null); fail("Missing module accepted"); }
        catch (IllegalStateException expected) { }
    }

    @Test public void cancellationNeverStartsDiscoveryOrReturnsStaleRuntime() throws Exception {
        for (int interruptAt = 0; interruptAt < 3; interruptAt++) {
            final int stage = interruptAt;
            try {
                if (stage == 0) Thread.currentThread().interrupt();
                request().read(new NoIoConnection() {
                    public void ecuInit(EcuInitCallback callback, Module module) {
                        assertNotEquals(0, stage); callback.callback(identity());
                        if (stage == 1) Thread.currentThread().interrupt();
                    }
                    public DmInit readDmRuntime(DmInit cached, Module module) {
                        assertEquals(2, stage); Thread.currentThread().interrupt(); return metadata();
                    }
                }, MODULE);
                fail("Cancelled read succeeded");
            } catch (InterruptedException expected) { assertTrue(Thread.currentThread().isInterrupted()); }
            finally { Thread.interrupted(); }
        }
    }

    @Test public void runtimeFailureAndMissingResultPropagateWithoutDiscovery() throws Exception {
        for (boolean missing : new boolean[] {false, true}) {
            try {
                request().read(new MatchingConnection() {
                    public DmInit readDmRuntime(DmInit cached, Module module) {
                        if (missing) return null;
                        throw new InvalidResponseException("synthetic invalid frame");
                    }
                }, MODULE);
                fail("Bad runtime result accepted");
            } catch (InvalidResponseException expected) { }
        }
    }

    @Test public void unsupportedRuntimeConnectionNeverFallsBackToDiscovery() throws Exception {
        try {
            request().read(new NoIoConnection() {
                public void ecuInit(EcuInitCallback callback, Module module) { callback.callback(identity()); }
            }, MODULE);
            fail("Unsupported runtime connection accepted");
        } catch (UnsupportedOperationException expected) { }
    }

    private static class MatchingConnection extends NoIoConnection {
        public void ecuInit(EcuInitCallback callback, Module module) { callback.callback(identity()); }
        public DmInit readDmRuntime(DmInit cached, Module module) { return metadata(); }
    }

    private static class NoIoConnection implements LoggerConnection {
        public void open(Module module) { throw new AssertionError("open"); }
        public void ecuReset(Module module, int code) { throw new AssertionError("reset"); }
        public void ecuInit(EcuInitCallback callback, Module module) { throw new AssertionError("identify"); }
        public void dmInit(DmInitCallback callback, Module module) { throw new AssertionError("legacy discovery"); }
        public void sendAddressReads(Collection<EcuQuery> queries, Module module, PollingState state) { throw new AssertionError("poll"); }
        public void clearLine() { throw new AssertionError("clear"); }
        public void close() { throw new AssertionError("close"); }
        public void sendAddressWrites(Map<EcuQuery, byte[]> queries, Module module) { throw new AssertionError("write"); }
    }
}
