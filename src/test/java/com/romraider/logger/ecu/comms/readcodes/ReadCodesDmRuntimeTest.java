/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the production read-codes cache boundary without a Swing owner or device. */
public class ReadCodesDmRuntimeTest {
    private static DmInit metadata() {
        ByteBuffer bytes = ByteBuffer.allocate(112);
        bytes.put((byte) 2).put((byte) 3).putShort((short) 100)
                .putInt(0x20000).putInt(0).putInt(0xDEAD0001);
        for (int i = 0; i < 24; i++) bytes.putInt(0x2000 + i * 0x10);
        return new DmInit(bytes.array());
    }

    @Test
    public void missingCacheSkipsDimeWithoutCallingAnyConnectionMethod() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        assertNull(ReadCodesManagerImpl.readDmRuntime(() -> {
            lookups.incrementAndGet();
            return null;
        }, new NoIoConnection(), null));
        assertEquals(1, lookups.get());
    }

    @Test
    public void cacheClearedOrReplacedDuringReadCannotStartDiscoveryOrReplaceResult() throws Exception {
        for (DmInit replacement : new DmInit[] {null, metadata()}) {
            DmInit original = metadata();
            DmInit refreshed = metadata();
            AtomicReference<DmInit> owner = new AtomicReference<>(original);
            AtomicInteger lookups = new AtomicInteger();
            AtomicInteger reads = new AtomicInteger();
            LoggerConnection connection = new NoIoConnection() {
                @Override public DmInit readDmRuntime(DmInit cached, Module module) {
                    assertSame(original, cached);
                    reads.incrementAndGet();
                    owner.set(replacement);
                    return refreshed;
                }
            };
            DmInit result = ReadCodesManagerImpl.readDmRuntime(() -> {
                assertEquals("Owner cache was looked up again", 1, lookups.incrementAndGet());
                return owner.get();
            }, connection, null);
            assertSame(refreshed, result);
            assertSame(replacement, owner.get());
            assertEquals(1, reads.get());
        }
    }

    @Test
    public void unsupportedConnectionDefaultFailsWithoutLegacyFallback() throws Exception {
        try {
            ReadCodesManagerImpl.readDmRuntime(ReadCodesDmRuntimeTest::metadata, new NoIoConnection(), null);
            fail("Unsupported read-only refresh silently succeeded");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test
    public void failedAndCancelledReadsPropagateInsteadOfReturningCachedState() throws Exception {
        for (boolean cancel : new boolean[] {false, true}) {
            LoggerConnection connection = new NoIoConnection() {
                @Override public DmInit readDmRuntime(DmInit cached, Module module) throws InterruptedException {
                    if (cancel) throw new InterruptedException("synthetic cancellation");
                    throw new InvalidResponseException("synthetic invalid frame");
                }
            };
            try {
                ReadCodesManagerImpl.readDmRuntime(ReadCodesDmRuntimeTest::metadata, connection, null);
                fail("Failed refresh returned state");
            } catch (InterruptedException expected) { assertTrue(cancel); }
            catch (InvalidResponseException expected) { assertFalse(cancel); }
        }
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
