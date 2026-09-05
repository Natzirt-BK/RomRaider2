/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FxCalibrationPaneTest {
    @Test
    void tableScaleIsRoundedAndLimitedToUsefulDesktopSizes() {
        assertEquals(.6, FxCalibrationPane.normalizeTableScale(.1));
        assertEquals(.8, FxCalibrationPane.normalizeTableScale(.76));
        assertEquals(1.0, FxCalibrationPane.normalizeTableScale(1.04));
        assertEquals(1.6, FxCalibrationPane.normalizeTableScale(2.0));
    }

    @Test
    void tableScaleIsPresentedAsAnUnambiguousPercentage() {
        assertEquals("60%", FxCalibrationPane.formatTableScale(.6));
        assertEquals("100%", FxCalibrationPane.formatTableScale(1.0));
        assertEquals("160%", FxCalibrationPane.formatTableScale(1.6));
    }
}
