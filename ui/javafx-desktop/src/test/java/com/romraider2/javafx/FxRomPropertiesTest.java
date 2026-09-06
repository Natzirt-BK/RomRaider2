/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.swing.JProgressPane;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FxRomPropertiesTest {
    @Test void describesCurrentBufferWithoutChangingItOrClaimingChecksumApproval() {
        RomID id = new RomID(); id.setXmlid("SYNTHETIC");
        Rom rom = new Rom(id);
        rom.populateTables("abc".getBytes(StandardCharsets.UTF_8), new JProgressPane());
        String text = FxRomProperties.describe(rom, true);
        assertTrue(text.contains("Definition ID: SYNTHETIC"));
        assertTrue(text.contains("State: Unsaved changes"));
        assertTrue(text.contains("Image size: 3 bytes"));
        assertTrue(text.contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        assertTrue(text.contains("not ECU checksum validity"));
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), rom.getBinary());
    }
}
