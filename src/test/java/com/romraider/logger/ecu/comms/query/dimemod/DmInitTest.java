/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.query.dimemod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.romraider.logger.ecu.definition.EcuParameter;

public class DmInitTest {
    @Test public void dynamicChannelAddressesAlwaysUseThreeWireBytes() {
        for (int minor : new int[] {0, 1, 3}) {
            DmInit metadata = new DmInit(discovery(minor, 300, false));
            metadata.updateRuntimeData(-1, 0x3ff, new int[8], new int[8]);
            for (EcuParameter parameter : metadata.getEcuParams()) {
                assertEquals(parameter.getId(), parameter.getAddress().getAddresses().length * 3,
                        parameter.getAddress().getBytes().length);
                org.junit.Assert.assertSame(metadata, parameter.getSourceIdentity());
            }
        }
    }

    @Test
    public void parsesDm20DiscoveryAndBuildsRuntimeParameters() {
        DmInit discovery = new DmInit(dm20Discovery());

        assertEquals(2, discovery.getMajorVer());
        assertEquals(0, discovery.getMinorVer());
        assertEquals(42, discovery.getBuildNum());
        assertEquals("2.0 build 042", discovery.getDimeModVersion());
        assertEquals(0x1000, discovery.getCurrentErrorCodesAddress());
        assertEquals(0x1010, discovery.getMemorizedErrorCodesAddress());
        assertEquals(0x1030, discovery.getActiveInputsAddress());
        assertFalse(discovery.isRamTuneEnabled());
        assertEquals(0, discovery.getRamTuneSignatureAddress());
        assertEquals(0, discovery.getRamTuneLutSize());

        assertTrue(discovery.updateRuntimeData(0, 0x01,
                new int[] {0x00010001}, new int[] {0x80000000}));
        assertFalse(discovery.updateRuntimeData(0, 0x01,
                new int[] {0x00010001}, new int[] {0x80000000}));

        Set<String> parameterIds = ids(discovery.getEcuParams());
        assertTrue(parameterIds.contains("DM900"));
        assertTrue(parameterIds.contains("DM901"));
        assertTrue(parameterIds.contains("DM902"));
        assertTrue(parameterIds.contains("DM903"));
        assertFalse(parameterIds.contains("DM904"));

        assertTrue(discovery.decodeDmCurrentErrors().contains(
                "DM0001: Table Metadata Buffer Overflow"));
        assertTrue(discovery.decodeDmCurrentErrors().contains(
                "DM0030: AFR Related Problem"));
        assertTrue(discovery.decodeDmMemorizedErrors().contains(
                "DM9999: Internal Logic Error"));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDiscoveryWithWrongStructureSignature() {
        byte[] discovery = dm20Discovery();
        discovery[12] = 0;
        new DmInit(discovery);
    }

    @Test
    public void newerUnknownMajorVersionIsNotParsedAsDm20() {
        DmInit discovery = new DmInit(new byte[] {3, 1, 0, 1});
        assertEquals("3.1 build 001", discovery.getDimeModVersion());
        assertTrue(discovery.getEcuParams().isEmpty());
    }

    @Test
    public void retainsAdvertisedRamTuneSignatureAndLookupMetadata() {
        DmInit discovery = new DmInit(dm20RamTuneDiscovery());

        assertTrue(discovery.isRamTuneEnabled());
        assertEquals(0x123456, discovery.getRamTuneSignatureAddress());
        assertEquals(96, discovery.getRamTuneLutSize());
    }

    @Test
    public void absentFeatureBlocksNeverCreateZeroAddressChannels() {
        for (int minor : new int[] {0, 1, 3}) {
            DmInit discovery = new DmInit(discovery(minor, 300, false));
            discovery.updateRuntimeData(-1, 0x3ff, new int[4], new int[4]);
            Set<String> selected = ids(discovery.getEcuParams());
            for (String missing : new String[] {"DM001", "DM010", "DM017", "DM018", "DM019",
                    "DM01A", "DM01B", "DM01C", "DM01D", "DM020", "DMA00", "DM030"}) {
                assertFalse("Unadvertised block exposed " + missing + " on 2." + minor, selected.contains(missing));
            }
            assertTrue(selected.contains("DM911")); // An input address is independent of MapSwitch.
            assertAddress(discovery, "DM911", minor >= 3 ? 0x10b0 : 0x1090, 4);
            assertEquals(minor >= 3, selected.contains("DM920"));
            assertEquals(minor >= 3, selected.contains("DM922"));
            for (EcuParameter parameter : discovery.getEcuParams())
                assertTrue("Default zero address for " + parameter.getId(),
                        Long.decode(parameter.getAddress().getAddresses()[0]) != 0);
        }
    }

    @Test
    public void compensationChannelsRequireTheirDiscoveryBlock() {
        DmInit discovery = new DmInit(discovery(3, 100, false));
        discovery.updateRuntimeData(0, 0, new int[4], new int[4]);
        Set<String> selected = ids(discovery.getEcuParams());
        for (String missing : new String[] {"DM017", "DM019", "DM01A", "DM01C", "DM01D"})
            assertFalse("Missing compensation address exposed " + missing, selected.contains(missing));
    }

    @Test
    public void olderInputLayoutsDoNotInventOilSensorAddresses() {
        for (int minor : new int[] {0, 1, 2, 3}) {
            DmInit discovery = new DmInit(discovery(minor, 100, false));
            discovery.updateRuntimeData(0, 0x300, new int[4], new int[4]);
            Set<String> selected = ids(discovery.getEcuParams());
            for (String oil : new String[] {"DM920", "DM921", "DM922", "DM923"})
                assertEquals("Wrong oil-input layout for 2." + minor, minor >= 3, selected.contains(oil));
        }
    }

    @Test
    public void ffsAndFailsafeTriggersUseTheirOwnInputBits() {
        DmInit discovery = new DmInit(discovery(3, 100, false));
        discovery.updateRuntimeData(0, 0x20, new int[4], new int[4]);
        Set<String> selected = ids(discovery.getEcuParams());
        assertTrue(selected.contains("DM916"));
        assertTrue(selected.contains("DM917"));
        assertFalse(selected.contains("DM913"));
        discovery.updateRuntimeData(0, 0x80, new int[4], new int[4]);
        selected = ids(discovery.getEcuParams());
        assertTrue(selected.contains("DM913"));
        assertTrue(selected.contains("DM914"));
        assertFalse(selected.contains("DM916"));
        assertFalse(selected.contains("DM917"));
    }

    @Test
    public void advertisedMapSwitchCompensationsHonorVersionBoundaries() {
        for (int[] version : new int[][] {{0, 1}, {0, 2}, {1, 299}, {1, 300}, {3, 99}, {3, 100}, {4, 0}}) {
            DmInit discovery = new DmInit(discovery(version[0], version[1], true));
            discovery.updateRuntimeData(0x02000000, 0x15, new int[4], new int[4]);
            Set<String> selected = ids(discovery.getEcuParams());
            assertTrue(selected.contains("DM010"));
            assertTrue(selected.contains("DM017"));
            assertAddress(discovery, "DM017", 0x2140, 4);
            boolean compensation = version[0] > 0 || version[1] > 1;
            assertEquals(compensation, selected.contains("DM019"));
            if (compensation) assertAddress(discovery, "DM019", 0x2220, 4);
            assertEquals(compensation, selected.contains("DM01A"));
            boolean multipliers = version[0] > 3 || version[0] == 1 && version[1] >= 300
                    || version[0] == 3 && version[1] >= 100;
            assertEquals(multipliers, selected.contains("DM01C"));
            assertEquals(multipliers, selected.contains("DM01D"));
            discovery.updateRuntimeData(0, 0x15, new int[4], new int[4]);
            selected = ids(discovery.getEcuParams());
            assertFalse(selected.contains("DM010"));
            assertTrue(selected.contains("DM017")); // Known compensation address, even if switching is inactive.
            assertEquals(compensation, selected.contains("DM019"));
        }
    }

    @Test
    public void advertisedFeaturesRemainAvailableAndFollowRuntimeActivation() {
        ByteBuffer data = ByteBuffer.allocate(256);
        data.put(discovery(3, 100, false));
        data.put(8, (byte) 4); // Per-cylinder knock sums.
        data.put(9, (byte) 0xc4); // Speed density, ALS, valet.
        data.putInt(0xDEAD0006).putInt(0x3100);
        data.putInt(0xDEAD0009);
        for (int i = 0; i < 17; i++) data.putInt(0x3200 + i * 0x10);
        data.putInt(0xDEAD000A);
        for (int i = 0; i < 6; i++) data.putInt(0x3400 + i * 0x10);
        data.putInt(0xDEAD000E).putInt(0x3500);
        DmInit discovery = new DmInit(java.util.Arrays.copyOf(data.array(), data.position()));
        discovery.updateRuntimeData(0x04c40000, 0, new int[4], new int[4]);
        assertAddress(discovery, "DM001", 0x3100, 1);
        assertAddress(discovery, "DM002", 0x3102, 1);
        assertAddress(discovery, "DM003", 0x3101, 1);
        assertAddress(discovery, "DM004", 0x3103, 1);
        assertAddress(discovery, "DM020", 0x3200, 4);
        assertAddress(discovery, "DMA00", 0x3400, 1);
        assertAddress(discovery, "DM030", 0x3500, 2);
        discovery.updateRuntimeData(0, 0, new int[4], new int[4]);
        Set<String> selected = ids(discovery.getEcuParams());
        for (String inactive : new String[] {"DM001", "DM020", "DMA00", "DM030"})
            assertFalse(selected.contains(inactive));
    }

    private static void assertAddress(DmInit discovery, String id, int expected, int length) {
        for (EcuParameter parameter : discovery.getEcuParams()) if (parameter.getId().equals(id)) {
            assertEquals(id, expected, Long.decode(parameter.getAddress().getAddresses()[0]).longValue());
            assertEquals(id, length, parameter.getAddress().getLength());
            return;
        }
        throw new AssertionError("Missing channel " + id);
    }

    private static byte[] discovery(int minor, int build, boolean mapSwitch) {
        ByteBuffer data = ByteBuffer.allocate(256);
        data.put((byte) 2).put((byte) minor).putShort((short) build).putInt(0x20000);
        data.put((byte) (mapSwitch ? 2 : 0)).put((byte) 0).put((byte) 0).put((byte) 0);
        data.putInt(0xDEAD0001);
        for (int index = 0; index < (minor >= 3 ? 24 : 20); index++) data.putInt(0x1000 + index * 0x10);
        if (mapSwitch) {
            data.putInt(0xDEAD0007).putInt(4).putInt(0x2000);
            if (minor < 3) data.putInt(0x2010).putInt(0x2020);
            for (int index = 0; index < 5; index++) data.putInt(0x2100 + index * 0x10);
            if (minor > 0 || build > 1) {
                for (int index = 0; index < 4; index++) data.putInt(0x2200 + index * 0x10);
                if (minor > 3 || minor == 1 && build >= 300 || minor == 3 && build >= 100)
                    data.putInt(0x2300).putInt(0x2310);
            }
        }
        return java.util.Arrays.copyOf(data.array(), data.position());
    }

    private static byte[] dm20Discovery() {
        ByteBuffer data = ByteBuffer.allocate(100);
        data.put((byte) 2);
        data.put((byte) 0);
        data.putShort((short) 42);
        data.putInt(0x20000);
        data.putInt(0);
        data.putInt(0xDEAD0001);
        for (int index = 0; index < 21; index++) {
            data.putInt(0x1000 + index * 0x10);
        }
        return data.array();
    }

    private static byte[] dm20RamTuneDiscovery() {
        ByteBuffer data = ByteBuffer.allocate(120);
        data.put((byte) 2);
        data.put((byte) 0);
        data.putShort((short) 43);
        data.putInt(0x20000);
        data.put((byte) 0x80);
        data.put((byte) 0);
        data.put((byte) 0);
        data.put((byte) 0);
        data.putInt(0xDEAD0001);
        for (int index = 0; index < 20; index++) {
            data.putInt(0x1000 + index * 0x10);
        }
        data.putInt(0xDEAD0020);
        data.putInt(0x123456);
        data.putInt(96);
        return data.array();
    }

    private static Set<String> ids(
            Collection<? extends EcuParameter> parameters) {
        Set<String> ids = new HashSet<String>();
        for (EcuParameter parameter : parameters) {
            ids.add(parameter.getId());
        }
        return ids;
    }
}
