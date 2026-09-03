/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RomUserInteractionServiceTest {
    @Test
    public void routesRomMessagesAndDecisionsWithoutSwing() {
        Rom rom = new Rom(new RomID());
        Table1D table = new Table1D();
        table.setName("Checksum Fix");
        final int[] events = new int[3];
        RomUserInteraction handler = new RomUserInteraction() {
            public void definitionError(Rom target, Table failed,
                    String title, String message, Throwable failure) {
                events[0]++;
            }
            public boolean confirmChecksumFix(Rom target, Table checksum,
                    String title, String message) {
                events[1]++;
                return true;
            }
            public void checksumUpdated(Rom target, String message) {
                events[2]++;
            }
        };
        RomUserInteractionService.addHandler(handler);
        try {
            RomUserInteractionService.definitionError(rom, table, "Error",
                    "Bad definition", new IllegalStateException());
            assertTrue(RomUserInteractionService.confirmChecksumFix(rom,
                    table, "Checksum", "Repair?"));
            RomUserInteractionService.checksumUpdated(rom, "Updated");
            assertEquals(1, events[0]);
            assertEquals(1, events[1]);
            assertEquals(1, events[2]);
        } finally {
            RomUserInteractionService.removeHandler(handler);
        }
    }
}
