/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.romraider.maps.Table1D;
import com.romraider.maps.Table1DView;
import com.romraider.maps.Table1DView.Table1DType;

public final class SwingTableFrameRegistryTest {
    @Test
    public void compatibilityFrameIsOwnedOutsideCalibrationModel() {
        Table1D table = new Table1D();
        table.setName("Fuel Target");
        Table1DView view = new Table1DView(table, Table1DType.NO_AXIS);
        TableFrame frame = new TableFrame("Fuel Target", view);

        assertSame(frame, SwingTableFrameRegistry.find(table));
        assertSame(frame, new TableTreeNode(table).getFrame());
        assertSame(frame, view.getFrame());

        SwingTableFrameRegistry.unregister(table, frame);
        assertNull(SwingTableFrameRegistry.find(table));
        assertNull(new TableTreeNode(table).getFrame());
        frame.dispose();
    }
}
