/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FxGaugeConfigurationDialogTest {
    @Test void bindsParsedLimitsToTheActiveConversion() {
        var config = FxGaugeConfigurationDialog.parse("0", "120", "", "100", "5", "celsius");
        assertEquals(100, config.getHighWarning());
        assertEquals(5, config.getHysteresis());
        assertTrue(config.matchesConversion("celsius"));
        assertFalse(config.matchesConversion("fahrenheit"));
    }
    @Test void emptyFieldsClearLimits() {
        assertNull(FxGaugeConfigurationDialog.parse("", "", "", "", "", "celsius"));
    }
    @Test void rejectsInvalidOrIncompleteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> FxGaugeConfigurationDialog.parse("0", "", "", "100", "5", "C"));
        assertThrows(IllegalArgumentException.class, () -> FxGaugeConfigurationDialog.parse("", "", "", "NaN", "5", "C"));
        assertThrows(IllegalArgumentException.class, () -> FxGaugeConfigurationDialog.parse("", "", "", "100", "-5", "C"));
        assertThrows(IllegalArgumentException.class, () -> FxGaugeConfigurationDialog.parse("", "", "", "100", "5", ""));
        assertThrows(IllegalArgumentException.class, () -> FxGaugeConfigurationDialog.parse("", "", "", "text", "5", "C"));
    }
}
