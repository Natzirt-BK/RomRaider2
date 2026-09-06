/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import static org.junit.Assert.*;
import org.junit.Test;
import com.romraider.Settings;
import com.romraider.editor.calibration.TableCalibrationEditController;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;

public class DataCellAliasTest {
    private static Table1D table(String name, int address, int width, int size) {
        Table1D table = new Table1D(); table.setName(name);
        table.setStorageAddress(address); table.setStorageType(width);
        table.setDataSize(size); table.setEndian(Settings.Endian.BIG);
        return table;
    }
    @Test public void overlappingFloatCellsStaySynchronizedAcrossEditsUndoAndRedo() throws Exception {
        Table1D wide = table("Float array", 0, Settings.STORAGE_TYPE_FLOAT, 2);
        Table1D alias = table("Second float alias", 4, Settings.STORAGE_TYPE_FLOAT, 1);
        Rom rom = new Rom(new RomID()); rom.addTableByName(wide); rom.addTableByName(alias);
        byte[] original = new byte[] {0x3f, (byte) 0x80, 0, 0, 0x40, 0, 0, 0};
        rom.populateTables(original.clone(), new JProgressPane());
        try (TableCalibrationEditController edit = new TableCalibrationEditController(wide)) {
            assertEquals(4, DataCell.getMemoryStartAddress(wide.getDataCell(1)));
            edit.setCellValue(0, 1, "3");
            assertEquals(3, alias.getDataCell(0).getRealValue(), 0);
            edit.undo();
            assertEquals(2, alias.getDataCell(0).getRealValue(), 0);
            assertArrayEquals(original, rom.getBinary());
            edit.redo();
            assertEquals(3, alias.getDataCell(0).getRealValue(), 0);
        } finally { RomEditHistory.getInstance().clear(rom); }
    }
    @Test public void partialByteOverlapRefreshesBothDirectionsWithoutExtraHistory() throws Exception {
        Table1D word = table("Word", 0, 2, 1);
        Table1D byteAlias = table("Low byte", 1, 1, 1);
        Rom rom = new Rom(new RomID()); rom.addTableByName(word); rom.addTableByName(byteAlias);
        rom.populateTables(new byte[] {1, 2}, new JProgressPane());
        try {
            byteAlias.getDataCell(0).setBinValue(4);
            assertEquals(260, word.getDataCell(0).getBinValue(), 0);
            assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));
            RomEditHistory.getInstance().undo(rom);
            assertEquals(258, word.getDataCell(0).getBinValue(), 0);
            word.getDataCell(0).setBinValue(515);
            assertEquals(3, byteAlias.getDataCell(0).getBinValue(), 0);
            RomEditHistory.getInstance().undo(rom);
            assertArrayEquals(new byte[] {1, 2}, rom.getBinary());
            assertEquals(2, byteAlias.getDataCell(0).getBinValue(), 0);
        } finally { RomEditHistory.getInstance().clear(rom); }
    }
}
