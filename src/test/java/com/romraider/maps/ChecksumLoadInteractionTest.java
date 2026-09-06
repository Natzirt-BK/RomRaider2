/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import static org.junit.Assert.*;
import com.romraider.Settings;
import com.romraider.swing.JProgressPane;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ChecksumLoadInteractionTest {
    @Test(timeout = 3000) public void invalidAndDisabledChecksumsUseTheUiNeutralBoundaryWithoutChangingBytes() {
        for (boolean disabled : new boolean[] {false, true}) {
            byte[] bytes = new byte[256];
            ByteBuffer.wrap(bytes).putInt(10).putInt(0).putInt(disabled ? 0 : 4)
                    .putInt(disabled ? Settings.CHECK_TOTAL : 0);
            byte[] before = bytes.clone();
            List<String> messages = new ArrayList<String>();
            RomUserInteraction handler = new RomUserInteraction() {
                public void checksumValidationFailed(Rom rom, String title, String message) {
                    messages.add(message);
                }
            };
            RomUserInteractionService.addHandler(handler);
            try {
                TableSwitch table = new TableSwitch(); table.setName("Checksum Fix");
                table.setStorageAddress(4); table.setDataSize(12); table.setLocked(true);
                Rom rom = new Rom(new RomID()); rom.addTableByName(table);
                rom.populateTables(bytes, new JProgressPane());
                assertEquals(1, messages.size());
                assertTrue(table.isLocked());
                assertArrayEquals(before, rom.getBinary());
            } finally { RomUserInteractionService.removeHandler(handler); }
        }
    }
}
