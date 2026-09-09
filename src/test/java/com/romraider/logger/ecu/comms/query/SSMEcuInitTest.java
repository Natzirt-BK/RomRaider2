/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.query;

import org.junit.Test;
import static org.junit.Assert.*;

public class SSMEcuInitTest {
    @Test public void inputAndReturnedBytesCannotChangeCapturedIdentity() {
        byte[] wire = {0, 0, 0, 0x12, 0x34, 0x56, 0x78, (byte) 0x90, 7};
        byte[] expected = wire.clone();
        SSMEcuInit captured = new SSMEcuInit(wire);
        String id = captured.getEcuId();
        wire[3] = 0;
        captured.getEcuInitBytes()[8] = 0;
        assertEquals(id, captured.getEcuId());
        assertArrayEquals(expected, captured.getEcuInitBytes());
    }

    @Test public void explicitIdentityAlsoOwnsItsBytes() {
        byte[] input = {1, 2, 3};
        SSMEcuInit captured = new SSMEcuInit(input, "CUSTOM-ID");
        input[0] = 0;
        captured.getEcuInitBytes()[1] = 0;
        assertArrayEquals(new byte[] {1, 2, 3}, captured.getEcuInitBytes());
        assertEquals("CUSTOM-ID", captured.getEcuId());
    }

    @Test public void truncatedIdentificationRepliesAreRejectedExplicitly() {
        for (int length = 1; length < 8; length++) {
            try { new SSMEcuInit(new byte[length]); fail("Truncated reply accepted"); }
            catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("ECU ID"));
            }
        }
    }
}
