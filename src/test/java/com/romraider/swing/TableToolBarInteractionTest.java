/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.romraider.maps.Table1D;

public class TableToolBarInteractionTest {
    @Test
    public void tableKeystrokePopulatesTheValueField() {
        TableToolBar toolbar = new TableToolBar();
        Table1D table = new Table1D();
        toolbar.updateTableToolBar(table);

        assertEquals("No cells selected", toolbar.getSelectionStatusForTesting());
        assertFalse(toolbar.isSetValueEnabledForTesting());
        assertFalse(toolbar.isRevertSelectedEnabledForTesting());

        toolbar.focusSetValue('7');

        assertEquals("7", toolbar.getSetValueTextForTesting());
        assertTrue(toolbar.hasPendingValueEditForTesting());
    }
}
