/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.junit.Test;

public class RomTableCatalogTest {
    @Test
    public void neutralCatalogAndSwingTreeMirrorShareTableIdentity() {
        Rom rom = new Rom(new RomID());
        Table1D timing = table("Timing");
        Table1D boost = table("Boost");

        rom.addTableByName(timing);
        rom.addTableByName(boost);

        assertSame(boost, rom.getTableByName("BOOST"));
        assertSame(boost, rom.getTableNodeByName("boost").getTable());
        assertEquals(Arrays.asList("Boost", "Timing"), names(rom.getTables()));
        assertEquals(Arrays.asList("Timing", "Boost"),
                catalogNames(rom.getTableCatalog()));

        try {
            rom.getTableCatalog().clear();
            fail("neutral table catalog must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Callers cannot alter the ROM's source-of-truth table catalog.
        }

        Vector<Table> snapshot = rom.getTables();
        snapshot.clear();
        assertEquals(2, rom.getTables().size());

        rom.removeTableByName(boost);
        assertNull(rom.getTableByName("boost"));
        assertNull(rom.getTableNodeByName("boost"));
        assertEquals(Arrays.asList("Timing"), names(rom.getTables()));
    }

    private static Table1D table(String name) {
        Table1D table = new Table1D();
        table.setName(name);
        return table;
    }

    private static List<String> names(Vector<Table> tables) {
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        for (Table table : tables) names.add(table.getName());
        return names;
    }

    private static List<String> catalogNames(List<Table> tables) {
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        for (Table table : tables) names.add(table.getName());
        return names;
    }
}
