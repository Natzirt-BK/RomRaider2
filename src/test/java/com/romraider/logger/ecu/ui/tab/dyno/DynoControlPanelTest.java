/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.tab.dyno;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DynoControlPanelTest {
    @Test
    public void missingCarProfilesCannotRestoreAGearSelection() {
        assertFalse(DynoControlPanel.hasGearSelection(
                false, null, 0, 0));
        assertFalse(DynoControlPanel.hasGearSelection(
                false, new String[][]{{"1", "3.45"}}, 0, 0));
    }

    @Test
    public void validatesIndexesAndRatioBeforeRestoringSelection() {
        String[][] ratios = {{"2", "3.45", "1.95"}};
        assertFalse(DynoControlPanel.hasGearSelection(true, ratios, -1, 0));
        assertFalse(DynoControlPanel.hasGearSelection(true, ratios, 0, -1));
        assertFalse(DynoControlPanel.hasGearSelection(true, ratios, 0, 2));
        assertTrue(DynoControlPanel.hasGearSelection(true, ratios, 0, 1));
    }
}
