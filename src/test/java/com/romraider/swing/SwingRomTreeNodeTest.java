/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;

public final class SwingRomTreeNodeTest {
    @Test
    public void compatibilityTreeMirrorsNeutralCatalogOnDemand() {
        Rom rom = new Rom(new RomID());
        Table1D fuel = table("Fuel Target", "Fuel");
        rom.addTableByName(fuel);
        SwingRomTreeNode node = SwingRomTreeRegistry.nodeFor(rom);

        assertSame(rom, node.getRom());
        assertSame(fuel, node.getTableNodeByName("fuel target").getTable());
        node.refreshDisplayedTables();
        assertEquals(1, node.getChildCount());

        rom.removeTableByName(fuel);
        assertNull(node.getTableNodeByName("Fuel Target"));
        SwingRomTreeRegistry.forget(rom);
    }

    private static Table1D table(String name, String category) {
        Table1D table = new Table1D();
        table.setName(name);
        table.setCategory(category);
        return table;
    }
}
