/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;

import org.junit.jupiter.api.Test;

class FxDialogsTest {
    @Test
    void removesNativeChooserDuplicateRomExtension() {
        assertEquals(new File("/tmp/tune.bin"),
                FxDialogs.normalizeRomSaveTarget(
                        new File("/tmp/tune.bin.bin")));
        assertEquals(new File("/tmp/tune.HEX"),
                FxDialogs.normalizeRomSaveTarget(
                        new File("/tmp/tune.HEX.HEX")));
    }

    @Test
    void preservesOrdinaryTargetsAndCancel() {
        assertEquals(new File("/tmp/tune.bin"),
                FxDialogs.normalizeRomSaveTarget(new File("/tmp/tune.bin")));
        assertNull(FxDialogs.normalizeRomSaveTarget(null));
    }

    @Test
    void normalizesParentlessTarget() {
        assertEquals(new File("tune.bin"),
                FxDialogs.normalizeRomSaveTarget(new File("tune.bin.bin")));
    }
}
