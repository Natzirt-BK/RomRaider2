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

    private static Set<String> ids(
            Collection<? extends EcuParameter> parameters) {
        Set<String> ids = new HashSet<String>();
        for (EcuParameter parameter : parameters) {
            ids.add(parameter.getId());
        }
        return ids;
    }
}
