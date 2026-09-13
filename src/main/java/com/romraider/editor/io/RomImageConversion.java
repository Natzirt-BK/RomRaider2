/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.io;

import java.util.Arrays;
import com.romraider.Settings;

/** Original 16-bit ROM image layouts; never mutates the supplied image. */
public final class RomImageConversion {
    private RomImageConversion() { }

    public static byte[] convert(byte[] source, boolean expand) {
        int expected = expand ? Settings.SIXTEENBIT_SMALL_SIZE : Settings.SIXTEENBIT_LARGE_SIZE;
        if (source == null || source.length != expected)
            throw new IllegalArgumentException("Conversion requires a " + expected / 1024 + " KB image");
        byte[] output = new byte[expand ? Settings.SIXTEENBIT_LARGE_SIZE : Settings.SIXTEENBIT_SMALL_SIZE];
        System.arraycopy(source, 0, output, 0, Settings.SIXTEENBIT_START_ADDRESS);
        if (expand) {
            Arrays.fill(output, Settings.SIXTEENBIT_START_ADDRESS,
                    Settings.SIXTEENBIT_END_ADDRESS, (byte) Settings.SIXTEENBIT_SEGMENT_VALUE);
        }
        System.arraycopy(source, expand ? Settings.SIXTEENBIT_START_ADDRESS : Settings.SIXTEENBIT_END_ADDRESS,
                output, expand ? Settings.SIXTEENBIT_END_ADDRESS : Settings.SIXTEENBIT_START_ADDRESS,
                Settings.SIXTEENBIT_SEGMENT_SIZE);
        return output;
    }
}
