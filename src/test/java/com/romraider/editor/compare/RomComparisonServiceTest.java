/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.compare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;

public class RomComparisonServiceTest {
    @Test
    public void indexesNamesAndRanksModifiedAndMissingBeforeUnchanged() {
        Rom left = rom("left.bin",
                table("Boost Target", 10.0),
                table("Fuel Target", 12.0),
                table("Left Only", 1.0));
        Rom right = rom("right.bin",
                table("boost target", 11.0),
                table("Fuel Target", 12.0),
                table("Right Only", 2.0));

        RomComparisonResult result = RomComparisonService.compare(left, right);

        assertEquals(1, result.getDifferentCount());
        assertEquals(1, result.getEqualCount());
        assertEquals(2, result.getMissingCount());
        assertFalse(result.isIdentical());
        assertEquals(TableComparisonStatus.DIFFERENT,
                result.getTables().get(0).getStatus());
        assertEquals(TableComparisonStatus.EQUAL,
                result.getTables().get(result.getTables().size() - 1).getStatus());
    }

    private static Rom rom(String file, Table1D... tables) {
        Rom rom = new Rom(new RomID());
        rom.setFileName(file);
        for (Table1D table : tables) rom.addTableByName(table);
        return rom;
    }

    private static Table1D table(String name, double value) {
        Table1D table = new Table1D();
        table.setName(name);
        table.setData(new DataCell[] {new ValueCell(table, value)});
        return table;
    }

    private static final class ValueCell extends DataCell {
        private final double value;
        private ValueCell(Table1D table, double value) {
            super(table, (Rom) null);
            this.value = value;
        }
        public boolean equals(Object other) {
            return other instanceof ValueCell
                    && Double.compare(value, ((ValueCell) other).value) == 0;
        }
    }
}
