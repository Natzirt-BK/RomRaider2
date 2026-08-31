/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ECUEditorToolBarTest {
    @Test
    public void responsiveActionsUseCompactMediumAndWideLayouts() {
        assertEquals(0, ECUEditorToolBar.responsiveActionLevel(899));
        assertEquals(1, ECUEditorToolBar.responsiveActionLevel(900));
        assertEquals(1, ECUEditorToolBar.responsiveActionLevel(1249));
        assertEquals(2, ECUEditorToolBar.responsiveActionLevel(1250));
    }
}
