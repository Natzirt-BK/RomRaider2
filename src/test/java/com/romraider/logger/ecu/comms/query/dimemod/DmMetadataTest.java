/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.query.dimemod;

import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.Test;
import com.romraider.logger.ecu.definition.EcuParameter;

/** Generated metadata only; no private ROM, adapter, handshake or writes. */
public class DmMetadataTest {
    private static final int[][] VERSIONS = {{0, 0}, {0, 1}, {0, 2}, {1, 0}, {1, 300},
            {2, 0}, {3, 0}, {3, 100}, {4, 0}};

    @Test public void rejectsMissingHeaderAndOversizedMetadata() {
        rejects(() -> new DmInit(null));
        for (int size = 0; size < 4; size++) {
            byte[] bytes = new byte[size];
            rejects(() -> new DmInit(bytes));
        }
        rejects(() -> new DmInit(new byte[65536]));
    }

    @Test public void everyTruncatedSupportedLayoutHasAMetadataDiagnostic() {
        int prefixes = 0;
        for (int[] version : VERSIONS) {
            byte[] full = fixture(version[0], version[1]);
            assertEquals(version[0], new DmInit(full).getMinorVer());
            for (int size = 4; size < full.length; size++) {
                byte[] truncated = Arrays.copyOf(full, size);
                IllegalStateException failure = rejects(() -> new DmInit(truncated));
                assertTrue("Missing metadata context at " + size + ": " + failure, failure.getMessage().contains("metadata"));
                assertTrue("Missing offset at " + size + ": " + failure, failure.getMessage().contains("byte"));
                prefixes++;
            }
        }
        byte[] full = fixture(3, 100);
        assertTrue(rejects(() -> new DmInit(Arrays.copyOf(full, 8))).getMessage().contains("HEADER"));
        assertTrue(rejects(() -> new DmInit(Arrays.copyOf(full, 16))).getMessage().contains("INPUTS_CONFIG"));
        assertTrue(rejects(() -> new DmInit(Arrays.copyOf(full, full.length - 1))).getMessage().contains("VALET_MODE"));
        System.out.println("DimeMod metadata truncation checks: " + prefixes + " prefixes across " + VERSIONS.length + " layouts");
    }

    @Test public void everyAdvertisedSectionSignatureIsChecked() {
        byte[] full = fixture(3, 100);
        int sections = 0;
        for (int offset = 12; offset < full.length; offset += 4) {
            if ((ByteBuffer.wrap(full).getInt(offset) & 0xFFFF0000) != 0xDEAD0000) continue;
            byte[] invalid = full.clone(); invalid[offset] = 0;
            rejects(() -> new DmInit(invalid)); sections++;
        }
        assertEquals(11, sections);
    }

    @Test public void retainedMetadataIsIndependentOfCallerBuffers() {
        byte[] input = fixture(3, 100), original = input.clone();
        DmInit discovery = new DmInit(input);
        Arrays.fill(input, (byte) 0);
        assertArrayEquals(original, discovery.getDmInitBytes());
        byte[] exported = discovery.getDmInitBytes();
        Arrays.fill(exported, (byte) 0);
        assertArrayEquals(original, discovery.getDmInitBytes());
        assertEquals(discovery.getDimeModVersion(), new DmInit(discovery.getDmInitBytes()).getDimeModVersion());
    }

    @Test public void runtimeArraysAndChannelCollectionsAreSnapshots() {
        DmInit discovery = new DmInit(fixture(3, 100));
        int[] current = {0x800d, 0}, memorized = {0x800e, 0};
        discovery.updateRuntimeData(0, 1, current, memorized);
        current[0] = 0; memorized[0] = 0;
        assertEquals(0x800d, discovery.getRuntimeCurrentErrors()[0]);
        assertEquals(0x800e, discovery.getRuntimeMemErrors()[0]);
        discovery.getRuntimeCurrentErrors()[0] = 0;
        discovery.getRuntimeMemErrors()[0] = 0;
        assertEquals(0x800d, discovery.getRuntimeCurrentErrors()[0]);
        assertEquals(0x800e, discovery.getRuntimeMemErrors()[0]);
        Collection<? extends EcuParameter> channels = discovery.getEcuParams();
        assertTrue(has(channels, "DM902"));
        discovery.updateRuntimeData(0, 0, new int[8], new int[8]);
        assertTrue("Previously published channel list changed", has(channels, "DM902"));
        assertFalse(has(discovery.getEcuParams(), "DM902"));
        try { channels.clear(); fail("Caller could mutate the channel collection"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void atmosphericChannelUsesTheSameVersionGateAsItsAddress() {
        for (int[] version : VERSIONS) {
            byte[] bytes = fixture(version[0], version[1]);
            DmInit discovery = new DmInit(bytes);
            discovery.updateRuntimeData(0x800000, 0, new int[8], new int[8]);
            boolean expected = version[0] > 0 || version[1] > 0;
            assertEquals("Atmospheric input at 2." + version[0] + "." + version[1], expected,
                    has(discovery.getEcuParams(), "DM02C"));
            if (expected) {
                ByteBuffer metadata = ByteBuffer.wrap(bytes);
                int speedSection = 12;
                while (metadata.getInt(speedSection) != 0xDEAD0009) speedSection += 4;
                long address = metadata.getInt(speedSection + 4 + 12 * 4) & 0xFFFFFF;
                for (EcuParameter channel : discovery.getEcuParams()) if ("DM02C".equals(channel.getId())) {
                    assertEquals(address, Long.decode(channel.getAddress().getAddresses()[0]).longValue());
                    assertEquals(4, channel.getAddress().getLength());
                }
            }
            discovery.updateRuntimeData(0, 0, new int[8], new int[8]);
            assertFalse(has(discovery.getEcuParams(), "DM02C"));
        }
    }

    @Test public void runtimeWireSpansCannotWrapButUpperAddressAliasesRemainSupported() {
        for (int minor : new int[] {0, 1, 3}) {
            int[] widths = {minor == 0 ? 13 : 38, minor == 0 ? 4 : 16, 4, 2};
            for (int i = 0; i < widths.length; i++) {
                byte[] bytes = fixture(minor, 100);
                int offset = 16 + i * 4;
                ByteBuffer.wrap(bytes).putInt(offset, 0x1000000 - widths[i] + 1);
                rejects(() -> new DmInit(bytes));
                int valid = 0x1000000 - widths[i];
                for (int alias : new int[] {0, 0xFF000000}) {
                    ByteBuffer.wrap(bytes).putInt(offset, alias | valid);
                    DmInit discovery = new DmInit(bytes);
                    discovery.updateRuntimeData(0, 0, new int[8], new int[8]);
                    if (i == 0) assertEquals(alias | valid, discovery.getCurrentErrorCodesAddress());
                }
            }
        }
    }

    @Test public void unknownVersionsAndBoundedTrailingBytesRemainCompatible() {
        for (int major : new int[] {0, 1, 3, 255}) {
            DmInit unsupported = new DmInit(new byte[] {(byte) major, 1, 0, 1});
            assertEquals(major, unsupported.getMajorVer());
            assertTrue(unsupported.getEcuParams().isEmpty());
        }
        byte[] full = fixture(3, 100);
        assertEquals("2.3 build 100", new DmInit(Arrays.copyOf(full, 65535)).getDimeModVersion());
    }

    private static boolean has(Collection<? extends EcuParameter> channels, String id) {
        for (EcuParameter channel : channels) if (id.equals(channel.getId())) return true;
        return false;
    }
    private static IllegalStateException rejects(Runnable action) {
        try { action.run(); fail("Invalid DimeMod metadata accepted"); }
        catch (IllegalStateException expected) { return expected; }
        throw new AssertionError("Unreachable");
    }

    private static byte[] fixture(int minor, int build) {
        ByteBuffer data = ByteBuffer.allocate(1024);
        data.put((byte) 2).put((byte) minor).putShort((short) build).putInt(0x20000);
        data.put((byte) 0xff).put((byte) 0xfc).putShort((short) 0);
        block(data, 0xDEAD0001, minor >= 3 ? 24 : 20);
        data.putInt(0xDEAD0020).putInt(0x4000).putInt(96);
        block(data, 0xDEAD0002, 4);
        block(data, 0xDEAD0004, 1);
        block(data, 0xDEAD0006, 1);
        data.putInt(0xDEAD0007).putInt(4).putInt(0x4100);
        if (minor < 3) addresses(data, 2);
        addresses(data, 5);
        if (minor > 0 || build > 1) {
            addresses(data, 4);
            if (minor > 3 || minor == 1 && build >= 300 || minor == 3 && build >= 100) addresses(data, 2);
        }
        block(data, 0xDEAD0009, minor > 0 || build > 0 ? 17 : 16);
        block(data, 0xDEAD000C, 11);
        block(data, 0xDEAD000D, 1);
        block(data, 0xDEAD000A, minor > 3 || minor == 3 && build > 0 ? 6 : 1);
        block(data, 0xDEAD000E, 1);
        return Arrays.copyOf(data.array(), data.position());
    }
    private static void block(ByteBuffer data, int signature, int count) {
        data.putInt(signature); addresses(data, count);
    }
    private static void addresses(ByteBuffer data, int count) {
        for (int i = 0; i < count; i++) data.putInt(0x1000 + data.position() * 4);
    }
}
