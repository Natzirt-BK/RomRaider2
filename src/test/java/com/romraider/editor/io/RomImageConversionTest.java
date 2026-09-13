package com.romraider.editor.io;

import static org.junit.Assert.*;
import org.junit.Test;
import com.romraider.Settings;

public class RomImageConversionTest {
    @Test public void originalLayoutRoundTripsWithoutChangingInput() {
        byte[] source = new byte[Settings.SIXTEENBIT_SMALL_SIZE];
        for (int i = 0; i < source.length; i++) source[i] = (byte) (i * 31 + i / 256);
        byte[] original = source.clone();
        byte[] expanded = RomImageConversion.convert(source, true);
        assertEquals(Settings.SIXTEENBIT_LARGE_SIZE, expanded.length);
        for (int i = 0; i < Settings.SIXTEENBIT_START_ADDRESS; i++) assertEquals(source[i], expanded[i]);
        for (int i = Settings.SIXTEENBIT_START_ADDRESS; i < Settings.SIXTEENBIT_END_ADDRESS; i++)
            assertEquals(0, expanded[i]);
        assertArrayEquals(source, RomImageConversion.convert(expanded, false));
        assertArrayEquals(original, source);
    }

    @Test public void wrongSizesAreRejected() {
        for (boolean expand : new boolean[] {true, false}) {
            try { RomImageConversion.convert(new byte[1024], expand); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
