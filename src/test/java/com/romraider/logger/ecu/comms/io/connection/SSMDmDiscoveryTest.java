/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.io.connection;

import com.romraider.io.connection.ConnectionManager;
import com.romraider.logger.ecu.comms.io.protocol.LoggerProtocol;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native protocol framing with an in-memory manager; no adapter or vehicle access. */
public class SSMDmDiscoveryTest {
    private enum Fault { NONE, EMPTY, OVERSIZED, WRONG_TYPE, BAD_ID, BAD_CHECKSUM, BAD_LENGTH, TRUNCATED }

    @Test(timeout = 10000)
    public void readsExactMultiChunkBlocksOnBothTransports() throws Exception {
        for (boolean can : new boolean[] {false, true}) {
            Fixture f = new Fixture(can);
            byte[] expected = new byte[205];
            for (int i = 0; i < expected.length; i++) expected[i] = (byte) i;
            f.block = expected;
            assertArrayEquals(expected, f.connection.readDmDiscovery(f.module, 0x1000, expected.length));
            assertEquals(can ? 7 : 3, f.requests.size());
            assertEquals(expected.length, f.readOffset);
            for (byte[] request : f.requests) assertEquals(can ? 0xa8 : 0xa0, request[4] & 0xff);
        }
    }

    @Test(timeout = 5000)
    public void shortPositiveMemoryRepliesAdvanceByActualPayload() throws Exception {
        Fixture f = new Fixture(false);
        f.block = new byte[19];
        for (int i = 0; i < f.block.length; i++) f.block[i] = (byte) (i + 1);
        f.memoryLimit = 7;
        assertArrayEquals(f.block, f.connection.readDmDiscovery(f.module, 0x1000, 19));
        assertEquals(3, f.requests.size());
        assertEquals(0x1007, address(f.requests.get(1), 6));
        assertEquals(0x100e, address(f.requests.get(2), 6));
    }

    @Test(timeout = 10000)
    public void rejectsMalformedOrNonProgressingChunksWithoutAnotherRequest() throws Exception {
        for (boolean can : new boolean[] {false, true}) for (Fault fault : Fault.values()) {
            if (fault == Fault.NONE || can && (fault == Fault.BAD_CHECKSUM || fault == Fault.BAD_LENGTH)) continue;
            Fixture f = new Fixture(can);
            f.fault = fault;
            try {
                f.connection.readDmDiscovery(f.module, 0x1000, 20);
                fail("Accepted " + fault + " on " + (can ? "CAN" : "K-line"));
            } catch (InvalidResponseException expected) { }
            assertEquals("Invalid response caused another read", 1, f.requests.size());
        }
        Fixture shortCan = new Fixture(true);
        shortCan.memoryLimit = 3;
        try { shortCan.connection.readDmDiscovery(shortCan.module, 0x1000, 20); fail("Accepted incomplete CAN address results"); }
        catch (InvalidResponseException expected) { }
        assertEquals(1, shortCan.requests.size());
    }

    @Test
    public void invalidAdvertisedRangeIsRejectedBeforeSending() throws Exception {
        for (int[] range : new int[][] {{0x1000, 0}, {0x1000, 3}, {0x1000, 65536},
                {-1, 4}, {0xfffffe, 4}, {Integer.MAX_VALUE, 4}}) {
            Fixture f = new Fixture(false);
            try { f.connection.readDmDiscovery(f.module, range[0], range[1]); fail("Accepted invalid range"); }
            catch (InvalidResponseException expected) { }
            assertTrue(f.requests.isEmpty());
        }
    }

    @Test(timeout = 5000)
    public void finalFourBytesOfAddressSpaceDoNotWrap() throws Exception {
        Fixture f = new Fixture(true);
        f.startAddress = 0xfffffc;
        f.block = new byte[] {3, 1, 0, 1};
        assertArrayEquals(f.block, f.connection.readDmDiscovery(f.module, f.startAddress, 4));
        assertEquals(0xffffff, address(f.requests.get(0), 15));
    }

    @Test(timeout = 5000)
    public void interruptionStopsBeforeAnyRead() throws Exception {
        Fixture f = new Fixture(false);
        Thread.currentThread().interrupt();
        try { f.connection.readDmDiscovery(f.module, 0x1000, 4); fail("Ignored interruption"); }
        catch (InterruptedException expected) { assertTrue(f.requests.isEmpty()); }
        finally { Thread.interrupted(); }
    }

    @Test(timeout = 10000)
    public void publicDiscoveryPathPreservesHandshakeAndRuntimeParsing() throws Exception {
        for (boolean can : new boolean[] {false, true}) for (int minor : new int[] {0, 3}) {
            Fixture f = new Fixture(can);
            f.handshake = true;
            f.block = discovery(minor);
            Callback callback = new Callback(null);
            f.connection.dmInit(callback, f.module);
            assertEquals(1, callback.calls);
            assertEquals(2, callback.value.getMajorVer());
            assertEquals(minor, callback.value.getMinorVer());
            assertEquals(minor == 0 ? 1 : 8, callback.value.getRuntimeCurrentErrors().length);
            assertArrayEquals(f.block, callback.value.getDmInitBytes());
            assertEquals(2, f.writes); // Existing negotiation only; this test never sends real I/O.
            assertEquals(1, f.runtimeReads);
        }
    }

    @Test(timeout = 10000)
    public void badResetReadNeverStartsNegotiation() throws Exception {
        for (boolean can : new boolean[] {false, true}) for (Fault fault :
                new Fault[] {Fault.EMPTY, Fault.OVERSIZED, Fault.BAD_ID, Fault.TRUNCATED, Fault.BAD_CHECKSUM}) {
            if (can && fault == Fault.BAD_CHECKSUM) continue;
            Fixture f = new Fixture(can);
            f.handshake = true;
            f.fault = fault;
            Callback callback = new Callback(null);
            try { f.connection.dmInit(callback, f.module); fail("Accepted bad reset-state frame " + fault); }
            catch (InvalidResponseException expected) { }
            assertEquals(1, f.requests.size());
            assertEquals(0, f.writes);
            assertEquals(0, callback.calls);
        }
    }

    @Test(timeout = 10000)
    public void laterChunkFailureNeverPublishesPartialDiscovery() throws Exception {
        for (boolean can : new boolean[] {false, true}) {
            Fixture f = new Fixture(can);
            f.handshake = true;
            f.block = discovery(3);
            f.fault = Fault.EMPTY;
            f.faultOnRequest = 10; // Six scalar reads, two existing writes, then the second data chunk.
            Callback callback = new Callback(null);
            try { f.connection.dmInit(callback, f.module); fail("Published partial discovery"); }
            catch (InvalidResponseException expected) { }
            assertEquals(10, f.requests.size());
            assertEquals(2, f.writes);
            assertEquals(0, f.runtimeReads);
            assertEquals(0, callback.calls);
            assertNull(callback.value);
        }
    }

    @Test(timeout = 10000)
    public void cachedRuntimeRejectsInvalidFramesWithoutPublishingOrMutating() throws Exception {
        for (boolean can : new boolean[] {false, true}) for (int minor : new int[] {0, 3}) {
            for (Fault fault : new Fault[] {Fault.EMPTY, Fault.OVERSIZED, Fault.BAD_ID, Fault.TRUNCATED,
                    Fault.BAD_CHECKSUM, Fault.BAD_LENGTH, Fault.WRONG_TYPE}) {
                if (can && (fault == Fault.BAD_CHECKSUM || fault == Fault.BAD_LENGTH)) continue;
                Fixture f = new Fixture(can);
                f.fault = fault;
                f.runtimeOnly = true;
                DmInit cached = new DmInit(discovery(minor));
                int[] errors = minor == 0 ? new int[] {1} : new int[] {1, 0, 0, 0, 0, 0, 0, 0};
                cached.updateRuntimeData(0, 1, errors, errors);
                Callback callback = new Callback(cached);
                try { f.connection.dmInit(callback, f.module); fail("Accepted invalid runtime " + fault); }
                catch (InvalidResponseException expected) { }
                assertEquals(0, callback.calls);
                // Public error arrays are defensive snapshots, not aliases of
                // caller-owned storage. Invalid frames must preserve contents.
                assertArrayEquals(errors, cached.getRuntimeCurrentErrors());
                assertArrayEquals(errors, cached.getRuntimeMemErrors());
                assertNotSame(errors, cached.getRuntimeCurrentErrors());
                assertEquals(1, cached.getRuntimeActiveInputs());
                assertEquals(1, f.requests.size());
                assertEquals(0, f.writes);
            }
        }
    }

    @Test
    public void runtimeOnlyRejectsMissingUnsupportedMetadataAndModuleBeforeIo() throws Exception {
        for (boolean can : new boolean[] {false, true}) {
            Fixture f = new Fixture(can);
            for (DmInit cached : new DmInit[] {null, new DmInit(new byte[] {3, 1, 0, 1})}) {
                try { f.connection.readDmRuntime(cached, f.module); fail("Accepted missing/unsupported cache"); }
                catch (IllegalArgumentException expected) { }
            }
            try { f.connection.readDmRuntime(new DmInit(discovery(3)), null); fail("Accepted missing module"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(f.requests.isEmpty());
        }
    }

    @Test(timeout = 5000)
    public void runtimeOnlyReadsFreshSnapshotWithoutNegotiationOrCacheMutation() throws Exception {
        for (boolean can : new boolean[] {false, true}) for (int minor : new int[] {0, 3}) {
            Fixture f = new Fixture(can);
            f.runtimeOnly = true;
            f.runtimeData = new byte[minor == 0 ? 14 : 38];
            ByteBuffer data = ByteBuffer.wrap(f.runtimeData);
            data.putInt(0).putShort((short) 2);
            if (minor == 0) data.putInt(0x12345678).putInt(0x23456789);
            else {
                for (int i = 0; i < 8; i++) data.putShort((short) (0x8000 + i));
                for (int i = 0; i < 8; i++) data.putShort((short) (0x9000 + i));
            }
            DmInit cached = new DmInit(discovery(minor));
            int[] oldErrors = new int[minor == 0 ? 1 : 8];
            oldErrors[0] = 1;
            cached.updateRuntimeData(0, 1, oldErrors, oldErrors);
            int channelCount = cached.getEcuParams().size();
            DmInit fresh = f.connection.readDmRuntime(cached, f.module);
            assertNotSame(cached, fresh);
            assertArrayEquals(cached.getDmInitBytes(), fresh.getDmInitBytes());
            assertArrayEquals(oldErrors, cached.getRuntimeCurrentErrors());
            assertArrayEquals(oldErrors, cached.getRuntimeMemErrors());
            assertEquals(1, cached.getRuntimeActiveInputs());
            assertEquals(channelCount, cached.getEcuParams().size());
            assertEquals(2, fresh.getRuntimeActiveInputs());
            int[] current = new int[minor == 0 ? 1 : 8];
            int[] memorized = new int[current.length];
            for (int i = 0; i < current.length; i++) {
                current[i] = minor == 0 ? 0x12345678 : 0x8000 + i;
                memorized[i] = minor == 0 ? 0x23456789 : 0x9000 + i;
            }
            assertArrayEquals(current, fresh.getRuntimeCurrentErrors());
            assertArrayEquals(memorized, fresh.getRuntimeMemErrors());
            assertEquals(1, f.requests.size());
            assertEquals(0xa8, f.requests.get(0)[4] & 0xff);
            assertEquals(0x2020, address(f.requests.get(0), 6));
            assertEquals(0, f.writes);
            assertEquals(0, f.scalar);
        }
    }

    @Test(timeout = 10000)
    public void runtimeOnlyFailuresNeverReturnStaleOrFabricatedErrors() throws Exception {
        for (boolean can : new boolean[] {false, true}) for (int minor : new int[] {0, 3}) {
            for (Fault fault : Fault.values()) {
                if (fault == Fault.NONE || can && (fault == Fault.BAD_CHECKSUM || fault == Fault.BAD_LENGTH)) continue;
                Fixture f = new Fixture(can);
                f.runtimeOnly = true;
                f.fault = fault;
                DmInit cached = new DmInit(discovery(minor));
                int[] errors = new int[minor == 0 ? 1 : 8];
                errors[0] = 1;
                cached.updateRuntimeData(0, 1, errors, errors);
                try { f.connection.readDmRuntime(cached, f.module); fail("Accepted invalid runtime " + fault); }
                catch (InvalidResponseException expected) { }
                assertArrayEquals(errors, cached.getRuntimeCurrentErrors());
                assertArrayEquals(errors, cached.getRuntimeMemErrors());
                assertEquals(1, cached.getRuntimeActiveInputs());
                assertEquals(1, f.requests.size());
                assertEquals(0, f.writes);
            }
        }
    }

    @Test(timeout = 5000)
    public void runtimeOnlyCancellationStopsBeforeIo() throws Exception {
        for (boolean can : new boolean[] {false, true}) {
            Fixture f = new Fixture(can);
            Thread.currentThread().interrupt();
            try { f.connection.readDmRuntime(new DmInit(discovery(3)), f.module); fail("Ignored cancellation"); }
            catch (InterruptedException expected) { assertTrue(f.requests.isEmpty()); }
            finally { Thread.interrupted(); }
        }
    }

    @Test
    public void runtimeOnlyRejectsNonSsmProtocolBeforeIo() throws Exception {
        Fixture f = new Fixture(false);
        LoggerProtocol nonSsm = (LoggerProtocol) java.lang.reflect.Proxy.newProxyInstance(
                LoggerProtocol.class.getClassLoader(), new Class<?>[] {LoggerProtocol.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getProtocol")) return null;
                    throw new AssertionError("Unexpected protocol call: " + method.getName());
                });
        try {
            new SSMLoggerConnection(f, nonSsm).readDmRuntime(new DmInit(discovery(3)), f.module);
            fail("Accepted non-SSM protocol");
        } catch (UnsupportedOperationException expected) { }
        assertTrue(f.requests.isEmpty());
    }

    @Test
    public void unsupportedCachedMajorNeverReadsDefaultRuntimeAddresses() throws Exception {
        Fixture f = new Fixture(false);
        DmInit unsupported = new DmInit(new byte[] {3, 1, 0, 1});
        Callback callback = new Callback(unsupported);
        f.connection.dmInit(callback, f.module);
        assertEquals(1, callback.calls);
        assertSame(unsupported, callback.value);
        assertTrue(f.requests.isEmpty());
    }

    @Test(timeout = 5000)
    public void unsupportedDiscoveredMajorNeverReadsDefaultRuntimeAddresses() throws Exception {
        Fixture f = new Fixture(true);
        f.handshake = true;
        f.block = new byte[] {3, 1, 0, 1};
        Callback callback = new Callback(null);
        f.connection.dmInit(callback, f.module);
        assertEquals(1, callback.calls);
        assertEquals(3, callback.value.getMajorVer());
        assertEquals(0, f.runtimeReads);
        assertEquals(9, f.requests.size());
    }

    private static byte[] discovery(int minor) {
        ByteBuffer bytes = ByteBuffer.allocate(minor >= 3 ? 112 : 96);
        bytes.put((byte) 2).put((byte) minor).putShort((short) 100).putInt(0x20000).putInt(0).putInt(0xDEAD0001);
        for (int i = 0; i < (minor >= 3 ? 24 : 20); i++) bytes.putInt(0x2000 + i * 0x10);
        return bytes.array();
    }

    private static final class Callback implements DmInitCallback {
        DmInit value;
        int calls;
        Callback(DmInit value) { this.value = value; }
        public DmInit getDmInit() { return value; }
        public boolean needToInit() { return value == null; }
        public void callback(DmInit next, boolean forceUpdate) { value = next; calls++; }
    }

    private static int address(byte[] request, int offset) {
        return (request[offset] & 0xff) << 16 | (request[offset + 1] & 0xff) << 8 | request[offset + 2] & 0xff;
    }

    private static final class Fixture implements ConnectionManager {
        final boolean can;
        final Module module;
        final SSMLoggerConnection connection;
        final List<byte[]> requests = new ArrayList<>();
        byte[] block = new byte[256];
        byte[] runtimeData;
        int readOffset, writes, runtimeReads, scalar, memoryLimit = 256;
        int startAddress = 0x1000, faultOnRequest = 1;
        boolean handshake, runtimeOnly;
        Fault fault = Fault.NONE;
        Fixture(boolean can) {
            this.can = can;
            module = can ? new Module("ECU", new byte[] {0, 0, 7, (byte) 0xe8}, "Synthetic CAN", new byte[] {0, 0, 7, (byte) 0xe0}, false)
                    : new Module("ECU", new byte[] {0x10}, "Synthetic K-line", new byte[] {(byte) 0xf0}, false);
            LoggerProtocol protocol = can ? new com.romraider.io.protocol.ssm.iso15765.SSMLoggerProtocol()
                    : new com.romraider.io.protocol.ssm.iso9141.SSMLoggerProtocol();
            connection = new SSMLoggerConnection(this, protocol);
        }
        public byte[] send(byte[] request) {
            requests.add(request.clone());
            Fault fault = requests.size() >= faultOnRequest ? this.fault : Fault.NONE;
            assertTrue("Discovery did not make bounded progress", requests.size() < 30);
            int command = request[4] & 0xff;
            byte[] data;
            int responseCode = command == 0xa0 ? 0xe0 : 0xe8;
            if (command == 0xb8) {
                assertTrue("Unexpected write", handshake);
                int target = address(request, 5);
                assertEquals(writes == 0 ? 0x60 : 0, target);
                assertEquals(writes == 0 ? 0xde : 0, request[8] & 0xff);
                data = new byte[] {(byte) (writes++ == 0 ? 0xad : 0)};
                responseCode = 0xf8;
            } else {
                assertTrue("Non-read request", command == 0xa0 || command == 0xa8);
                int target = address(request, 6);
                int requested = command == 0xa0 ? (request[9] & 0xff) + 1 : (request.length - (can ? 6 : 7)) / 3;
                if (handshake && target == 0x60) {
                    int[] scalars = {0, block.length >> 8, block.length & 0xff,
                            startAddress >> 16, startAddress >> 8 & 0xff, startAddress & 0xff};
                    assertEquals(1, requested);
                    data = new byte[] {(byte) scalars[scalar++]};
                } else if (runtimeOnly || target == 0x2020) {
                    runtimeReads++;
                    assertTrue(requested == 14 || requested == 38);
                    data = runtimeData == null ? new byte[requested] : runtimeData.clone();
                } else {
                    assertEquals(startAddress + readOffset, target);
                    assertTrue(requested <= (can ? 32 : 96));
                    if (can) for (int i = 0; i < requested; i++) assertEquals(target + i, address(request, 6 + i * 3));
                    int size = Math.min(requested, memoryLimit);
                    data = Arrays.copyOfRange(block, readOffset, readOffset + size);
                    readOffset += size;
                }
            }
            if (fault == Fault.EMPTY) data = new byte[0];
            if (fault == Fault.OVERSIZED) data = Arrays.copyOf(data, data.length + 1);
            if (fault == Fault.WRONG_TYPE) responseCode = 0xf8;
            byte[] frame = new byte[data.length + (can ? 5 : 6)];
            if (can) System.arraycopy(module.getAddress(), 0, frame, 0, 4);
            else { frame[0] = (byte) 0x80; frame[1] = (byte) 0xf0; frame[2] = 0x10; frame[3] = (byte) (data.length + 1); }
            frame[4] = (byte) responseCode;
            System.arraycopy(data, 0, frame, 5, data.length);
            if (fault == Fault.BAD_ID) frame[can ? 3 : 2] ^= 1;
            if (fault == Fault.BAD_LENGTH) frame[3]++;
            if (!can) {
                for (int i = 0; i < frame.length - 1; i++) frame[frame.length - 1] += frame[i];
                if (fault == Fault.BAD_CHECKSUM) frame[frame.length - 1] ^= 1;
            }
            if (fault == Fault.TRUNCATED) frame = Arrays.copyOf(frame, 4);
            if (can) return frame;
            byte[] echoAndFrame = Arrays.copyOf(request, request.length + frame.length);
            System.arraycopy(frame, 0, echoAndFrame, request.length, frame.length);
            return echoAndFrame;
        }
        public void open(byte[] start, byte[] stop) { throw new AssertionError("No device open allowed"); }
        public void send(byte[] request, byte[] response, PollingState state) { throw new AssertionError("Unexpected polling API"); }
        public void clearLine() { throw new AssertionError("No real line allowed"); }
        public void close() { }
    }
}
