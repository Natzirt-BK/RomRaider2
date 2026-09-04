/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Scale;
import com.romraider.maps.Table1D;
import com.romraider.maps.Table3D;
import com.romraider.swing.JProgressPane;

public class CalibrationGridProjectionServiceTest {
    @Test
    public void projectsChangedLineWithoutCreatingAView() throws Exception {
        Table1D table = line("Boost Target", 2, 3);
        Rom rom = rom(table, new byte[] {0, 0, 10, 20, 30, 0});
        table.getDataCell(1).setBinValue(24);

        CalibrationGridSnapshot grid =
                CalibrationGridProjectionService.project(table);

        assertEquals("Boost Target", grid.getTableName());
        assertEquals(1, grid.getRows());
        assertEquals(3, grid.getColumns());
        assertEquals("24.0", grid.cellAt(0, 1).getDisplayValue());
        assertTrue(grid.cellAt(0, 1).isChanged());
        assertFalse(grid.cellAt(0, 0).isChanged());
        assertEquals(1, grid.getChangedCellCount());
        assertEquals(24, rom.getBinary()[3]);
    }

    @Test
    public void projectsThreeDimensionalCellsInVisualRowOrder() throws Exception {
        Table3D table = new Table3D();
        table.setName("Fuel Target");
        table.setStorageAddress(0);
        table.setStorageType(1);
        table.setSizeX(2);
        table.setSizeY(2);
        table.getXAxis().setName("Load");
        table.getXAxis().setStorageAddress(4);
        table.getXAxis().setStorageType(1);
        table.getXAxis().setDataSize(2);
        table.getYAxis().setName("RPM");
        table.getYAxis().setStorageAddress(6);
        table.getYAxis().setStorageType(1);
        table.getYAxis().setDataSize(2);
        rom(table, new byte[] {1, 2, 3, 4, 10, 20, 30, 40});

        CalibrationGridSnapshot grid =
                CalibrationGridProjectionService.project(table);

        assertEquals(2, grid.getRows());
        assertEquals(2, grid.getColumns());
        assertEquals("1.0", grid.cellAt(0, 0).getDisplayValue());
        assertEquals("2.0", grid.cellAt(0, 1).getDisplayValue());
        assertEquals("3.0", grid.cellAt(1, 0).getDisplayValue());
        assertEquals("4.0", grid.cellAt(1, 1).getDisplayValue());
        assertEquals("10.0", grid.getColumnLabels().get(0));
        assertEquals("30.0", grid.getRowLabels().get(0));
        assertEquals("Load", grid.getColumnAxisName());
        assertEquals("RPM", grid.getRowAxisName());
    }

    @Test
    public void usesDefinitionFormatsInsteadOfBinaryFloatingPointNoise()
            throws Exception {
        Table3D table = new Table3D();
        table.setName("Fuel Target");
        table.setStorageAddress(0);
        table.setStorageType(1);
        table.setSizeX(2);
        table.setSizeY(2);
        table.getXAxis().setStorageAddress(4);
        table.getXAxis().setStorageType(1);
        table.getXAxis().setDataSize(2);
        table.getYAxis().setStorageAddress(6);
        table.getYAxis().setStorageType(1);
        table.getYAxis().setDataSize(2);
        rom(table, new byte[] {1, 2, 3, 4, 10, 20, 30, 40});
        Scale valueScale = scale("0.00");
        table.addScale(valueScale);
        table.getXAxis().addScale(scale("0.00"));
        table.getYAxis().addScale(scale("#"));
        table.setCurrentScale(valueScale);

        CalibrationGridSnapshot grid =
                CalibrationGridProjectionService.project(table);

        assertEquals("1.00", grid.cellAt(0, 0).getDisplayValue());
        assertEquals(1.0, grid.cellAt(0, 0).getRealValue(), 0.0);
        assertEquals("10.00", grid.getColumnLabels().get(0));
        assertEquals("30", grid.getRowLabels().get(0));
    }

    private static Table1D line(String name, int address, int size) {
        Table1D table = new Table1D();
        table.setName(name);
        table.setStorageAddress(address);
        table.setStorageType(1);
        table.setDataSize(size);
        return table;
    }

    private static Scale scale(String format) {
        Scale scale = new Scale();
        scale.setCategory("Default");
        scale.setFormat(format);
        return scale;
    }

    private static Rom rom(com.romraider.maps.Table table, byte[] binary) {
        Rom rom = new Rom(new RomID());
        rom.addTableByName(table);
        rom.populateTables(binary, new JProgressPane());
        return rom;
    }
}
