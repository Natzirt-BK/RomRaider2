/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.romraider.maps.Table1D;
import com.romraider.maps.Table1DView;
import com.romraider.maps.Table1DView.Table1DType;

public final class SwingTableViewRegistryTest {
    @Test
    public void viewRegistersWithoutBeingOwnedByCalibrationTable() {
        Table1D table = new Table1D();
        table.setName("Fuel Target");
        Table1DView view = new Table1DView(table, Table1DType.NO_AXIS);

        assertSame(view, SwingTableViewRegistry.find(table));
        view.setTable(null);
        assertNull(SwingTableViewRegistry.find(table));
    }
}
