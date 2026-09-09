/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.dimemod.*;
import com.romraider.portable.logger.definition.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic SSM frames only. Never opens a USB device. */
public class DimeModDiscoveryTest {
    public static byte[] metadata(int minor) {
        ByteBuffer out = ByteBuffer.allocate(minor >= 3 ? 112 : 96);
        out.put((byte) 2).put((byte) minor).putShort((short) 100).putInt(0x20000).putInt(0).putInt(0xDEAD0001);
        for (int i = 0; i < (minor >= 3 ? 24 : 20); i++) out.putInt(0x2000 + i * 0x10);
        return out.array();
    }

    @Test public void authorHandshakeProducesEthanolAndVoltageForBothLayouts() throws Exception {
        for (int minor : new int[]{0, 1, 3}) {
            Wire wire = new Wire(); wire.bytes = metadata(minor);
            PortableDimeModMetadata result = DimeModDiscovery.discover(wire, () -> false);
            assertEquals(List.of(0x60, 0), wire.writeAddresses);
            assertEquals(List.of(0xDE, 0), wire.writeValues);
            assertEquals(5, wire.headerReads);
            assertEquals(minor >= 3 ? 2 : 1, wire.memoryReads);
            PortableLoggerParameter ethanol = result.parameters().stream().filter(p -> p.getId().equals("DM911")).findFirst().orElseThrow();
            assertEquals("%", ethanol.getConversions().get(0).getUnits());
            assertEquals("float", ethanol.getConversions().get(0).getStorageType());
            assertEquals("big", ethanol.getConversions().get(0).getEndian());
            assertTrue(result.parameters().stream().anyMatch(p -> p.getId().equals("DM912") && p.getConversions().get(0).getUnits().equals("V")));
        }
    }

    @Test public void stockEcuRestoresOnlySavedStateAndReturnsNoDimeCatalog() throws Exception {
        Wire wire = new Wire(); wire.stock = true;
        assertNull(DimeModDiscovery.discover(wire, () -> false));
        assertEquals(List.of(0x60, 0x60), wire.writeAddresses);
        assertEquals(List.of(0xDE, 0x12), wire.writeValues);
        assertEquals(0, wire.memoryReads);
    }

    @Test public void cancelledBeforeEntryNeverWrites() {
        Wire wire = new Wire();
        assertThrows(IOException.class, () -> DimeModDiscovery.discover(wire, () -> true));
        assertEquals(0, wire.requests);
    }

    @Test public void cancellationAfterEntryAlwaysExitsWithoutReadingMetadata() {
        Wire wire = new Wire();
        assertThrows(IOException.class, () -> DimeModDiscovery.discover(wire, () -> !wire.writeAddresses.isEmpty()));
        assertEquals(List.of(0x60, 0), wire.writeAddresses);
        assertEquals(0, wire.memoryReads);
    }

    @Test public void everyHeaderFailureExitsOnce() {
        for (int at = 1; at <= 5; at++) {
            Wire wire = new Wire(); wire.failHeader = at;
            assertThrows(IOException.class, () -> DimeModDiscovery.discover(wire, () -> false));
            assertEquals(List.of(0x60, 0), wire.writeAddresses);
            assertEquals(0, wire.memoryReads);
        }
    }

    @Test public void unconfirmedEntryNeverRetriesOrGuessesCleanup() {
        Wire wire = new Wire(); wire.failEntry = true;
        IOException error = assertThrows(IOException.class, () -> DimeModDiscovery.discover(wire, () -> false));
        assertTrue(error.getMessage().contains("not confirmed"));
        assertEquals(List.of(0x60), wire.writeAddresses);
    }

    @Test public void exitFailureStopsBeforeMetadataAndKeepsActionableMessage() {
        Wire wire = new Wire(); wire.failExit = true;
        IOException error = assertThrows(IOException.class, () -> DimeModDiscovery.discover(wire, () -> false));
        assertTrue(error.getMessage().contains("exit was not confirmed"));
        assertEquals(0, wire.memoryReads);
    }

    @Test public void invalidRangesAreRejectedAfterNegotiationExit() {
        for (int start : new int[]{0x100000, 0xFFFFF0}) {
            Wire wire = new Wire(); wire.start = start;
            assertThrows(IOException.class, () -> DimeModDiscovery.discover(wire, () -> false));
            assertEquals(List.of(0x60, 0), wire.writeAddresses);
            assertEquals(0, wire.memoryReads);
        }
    }

    @Test public void badPayloadChecksumAndUnknownVersionNeverPublish() {
        Wire wire = new Wire(); wire.corruptMemory = true;
        assertThrows(IOException.class, () -> DimeModDiscovery.discover(wire, () -> false));
        Wire unknown = new Wire(); unknown.bytes[1] = 4;
        assertThrows(IOException.class, () -> DimeModDiscovery.discover(unknown, () -> false));
    }

    @Test public void missingInputFlagsNeverInventFlexFuelOrAbsentFeatureChannels() throws Exception {
        Wire wire = new Wire(); wire.inputs = 0;
        PortableDimeModMetadata result = DimeModDiscovery.discover(wire, () -> false);
        assertFalse(result.parameters().stream().anyMatch(p -> p.getId().equals("DM911")));
        assertFalse(result.parameters().stream().anyMatch(p -> p.getId().equals("DM017")));
    }

    @Test public void boundedDeadlineAndTruncationRejectWithoutPublishing() {
        Wire wire = new Wire(); wire.pauseMillis = 10_000;
        assertThrows(IOException.class, () -> DimeModDiscovery.discover(wire, () -> false));
        assertEquals(List.of(0x60, 0), wire.writeAddresses);
        for (int length = 0; length < 112; length++) {
            byte[] bytes = Arrays.copyOf(metadata(3), length);
            assertThrows(IllegalStateException.class, () -> new PortableDimeModMetadata(bytes));
        }
    }

    @Test public void full23100MetadataCoversFeatureBlocksAndTypedChannels() {
        ByteBuffer out = ByteBuffer.allocate(1024);
        out.put((byte) 2).put((byte) 3).putShort((short) 100).putInt(0x20000).putInt(0xD6DC0000);
        block(out, 0xDEAD0001, 24);
        block(out, 0xDEAD0020, 2); // RAM tune metadata is skipped, never used for writes.
        block(out, 0xDEAD0002, 4);
        block(out, 0xDEAD0004, 1);
        block(out, 0xDEAD0006, 1);
        block(out, 0xDEAD0007, 13);
        block(out, 0xDEAD0009, 17);
        block(out, 0xDEAD000C, 11);
        block(out, 0xDEAD000D, 1);
        block(out, 0xDEAD000A, 6);
        block(out, 0xDEAD000E, 1);
        byte[] bytes = Arrays.copyOf(out.array(), out.position());
        PortableDimeModMetadata parsed = new PortableDimeModMetadata(bytes);
        parsed.updateRuntimeData(-1, 0x3FF, null, null);
        Set<String> ids = new HashSet<>();
        for (PortableLoggerParameter channel : parsed.parameters()) {
            assertTrue("Duplicate " + channel.getId(), ids.add(channel.getId()));
            assertFalse(channel.addressesFor(null).isEmpty());
        }
        assertTrue(ids.containsAll(List.of("DM911", "DM912", "DM920", "DM922", "DM001", "DM004",
                "DM204", "DM010", "DM013", "DM01D", "DM02C", "DMA05", "DM030")));
        assertTrue(ids.size() > 50);
        for (int length = 0; length < bytes.length; length++) {
            byte[] truncated = Arrays.copyOf(bytes, length);
            assertThrows(IllegalStateException.class, () -> new PortableDimeModMetadata(truncated));
        }
    }

    private static void block(ByteBuffer out, int signature, int fields) {
        out.putInt(signature);
        for (int i = 0; i < fields; i++) out.putInt(0x4000 + out.position() * 4);
    }

    public static final class Wire implements DimeModDiscovery.Link {
        public byte[] bytes = metadata(3);
        public int start = 0x1000, inputs = 0x10;
        int headerReads, memoryReads, requests, failHeader, pauseMillis = 100;
        long time;
        boolean stock, failEntry, failExit, corruptMemory;
        final List<Integer> writeAddresses = new ArrayList<>(), writeValues = new ArrayList<>();
        public long millis() { return time; }
        public void pause() { time += pauseMillis; }
        public byte[] exchange(byte[] request, boolean cleanup) throws IOException {
            assertTrue(++requests < 100);
            int command = request[4] & 255;
            int sum = 0; for (int i = 0; i < request.length - 1; i++) sum += request[i] & 255;
            assertEquals((byte) sum, request[request.length - 1]);
            if (command == 0xB8) {
                int address = address(request, 5), value = request[8] & 255;
                writeAddresses.add(address); writeValues.add(value);
                assertTrue(address == 0 || address == 0x60);
                if (writeAddresses.size() == 1) {
                    assertFalse(cleanup); assertEquals(0xDE, value);
                    if (failEntry) throw new IOException("Lost entry reply");
                    return response(0xF8, new byte[]{(byte) (stock ? value : 0xAD)});
                }
                assertTrue(cleanup);
                if (failExit) throw new IOException("Lost exit reply");
                return response(0xF8, new byte[]{(byte) value});
            }
            assertFalse(cleanup);
            if (command == 0xA0) {
                assertEquals(List.of(0x60, 0), writeAddresses);
                memoryReads++;
                int offset = address(request, 6) - start;
                int count = (request[9] & 255) + 1;
                assertTrue(count <= 96);
                byte[] frame = response(0xE0, Arrays.copyOfRange(bytes, offset, offset + count));
                if (corruptMemory) frame[frame.length - 1]++;
                return frame;
            }
            assertEquals(0xA8, command);
            int count = (request.length - 7) / 3;
            if (count == 1 && address(request, 6) == 0x60) {
                if (writeAddresses.isEmpty()) return response(0xE8, new byte[]{0x12});
                byte[] header = {(byte) (bytes.length >>> 8), (byte) bytes.length,
                        (byte) (start >>> 16), (byte) (start >>> 8), (byte) start};
                if (++headerReads == failHeader) throw new IOException("Lost header reply");
                return response(0xE8, new byte[]{header[headerReads - 1]});
            }
            assertEquals(6, count);
            assertEquals(0x2020, address(request, 6));
            assertEquals(0x2030, address(request, 18));
            return response(0xE8, new byte[]{0, 0, 0, 0, (byte) (inputs >>> 8), (byte) inputs});
        }
    }

    public static int address(byte[] bytes, int index) {
        return ((bytes[index] & 255) << 16) | ((bytes[index + 1] & 255) << 8) | (bytes[index + 2] & 255);
    }

    public static byte[] response(int command, byte[] values) {
        byte[] frame = new byte[values.length + 6];
        frame[0] = (byte) 0x80; frame[1] = (byte) 0xF0; frame[2] = 0x10;
        frame[3] = (byte) (values.length + 1); frame[4] = (byte) command;
        System.arraycopy(values, 0, frame, 5, values.length);
        for (int i = 0; i < frame.length - 1; i++) frame[frame.length - 1] += frame[i];
        return frame;
    }
}
