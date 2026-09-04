/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Test;
import java.util.Arrays;
import java.util.Locale;

import com.romraider.maps.Rom;
import com.romraider.Settings;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table3D;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;

public final class CalibrationEditControllerTest {
    private Rom rom;

    @After
    public void clearHistory() {
        if (rom != null) RomEditHistory.getInstance().clear(rom);
    }

    @Test
    public void appliesValueThroughRomHistoryAndSupportsUndoRedo()
            throws Exception {
        Table1D table = line("Boost Target", 0, 3);
        rom = rom(table, new byte[] {10, 20, 30});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditResult result = controller.setCellValue(0, 1, "24");

        assertTrue(result.isChanged());
        assertEquals("20.0", result.getPreviousValue());
        assertEquals("24.0", result.getCurrentValue());
        assertEquals(24, rom.getBinary()[1] & 0xFF);
        assertEquals(1, result.getSnapshot().getChangedCellCount());
        assertTrue(controller.canUndo());
        assertFalse(controller.canRedo());

        CalibrationGridSnapshot undone = controller.undo();
        assertEquals(20, rom.getBinary()[1] & 0xFF);
        assertEquals(0, undone.getChangedCellCount());
        assertTrue(controller.canRedo());

        CalibrationGridSnapshot redone = controller.redo();
        assertEquals(24, rom.getBinary()[1] & 0xFF);
        assertEquals(1, redone.getChangedCellCount());
    }

    @Test
    public void rejectsPartialNumbersWithoutChangingRomOrHistory()
            throws Exception {
        Table1D table = line("Fuel Target", 0, 2);
        rom = rom(table, new byte[] {12, 14});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        try {
            controller.setCellValue(0, 1, "16 psi");
            fail("Partial value should be rejected");
        } catch (CalibrationEditException expected) {
            assertTrue(expected.getMessage().contains("complete"));
        }

        assertEquals(14, rom.getBinary()[1] & 0xFF);
        assertFalse(controller.canUndo());
    }

    @Test
    public void rejectsLockedTableBeforeStartingAHistoryStep()
            throws Exception {
        Table1D table = line("Locked Target", 0, 2);
        rom = rom(table, new byte[] {12, 14});
        table.setLocked(true);
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        try {
            controller.setCellValue(0, 0, "18");
            fail("Locked value should be rejected");
        } catch (CalibrationEditException expected) {
            assertEquals("This table is locked.", expected.getMessage());
        }

        assertEquals(12, rom.getBinary()[0] & 0xFF);
        assertFalse(controller.canUndo());
    }

    @Test
    public void resolvesThreeDimensionalCellsInVisualRowColumnOrder()
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
        rom = rom(table, new byte[] {1, 2, 3, 4, 10, 20, 30, 40});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditResult result = controller.setCellValue(1, 0, "9");

        assertTrue(result.isChanged());
        assertEquals("3.0", result.getPreviousValue());
        assertEquals("9.0", result.getCurrentValue());
        assertEquals(9, rom.getBinary()[2] & 0xFF);
        assertEquals("9.0", controller.getSnapshot()
                .cellAt(1, 0).getDisplayValue());
    }

    @Test
    public void editsThreeDimensionalAxesThroughRomHistory()
            throws Exception {
        Table3D table = new Table3D();
        table.setName("Fuel Target");
        table.setStorageAddress(0);
        table.setStorageType(1);
        table.setSizeX(1);
        table.setSizeY(1);
        table.getXAxis().setStorageAddress(1);
        table.getXAxis().setStorageType(1);
        table.getXAxis().setDataSize(1);
        table.getYAxis().setStorageAddress(2);
        table.getYAxis().setStorageType(1);
        table.getYAxis().setDataSize(1);
        rom = rom(table, new byte[] {5, 10, 20});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationAxisEditResult result = controller.setAxisValue(
                CalibrationAxis.COLUMN, 0, "15");

        assertTrue(result.isChanged());
        assertEquals("10.0", result.getPreviousValue());
        assertEquals("15.0", result.getCurrentValue());
        assertEquals(15, rom.getBinary()[1] & 0xFF);
        assertEquals(1, result.getSnapshot().getChangedAxisValueCount());
        assertEquals(1, result.getSnapshot().getChangedValueCount());
        assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));

        controller.undo();
        assertEquals(10, rom.getBinary()[1] & 0xFF);
        assertEquals("10.0", controller.getSnapshot()
                .getColumnLabels().get(0));
    }

    @Test
    public void editsTwoDimensionalColumnAxisAndRejectsRowAxis()
            throws Exception {
        Table2D table = new Table2D();
        table.setName("Boost Curve");
        table.setStorageAddress(0);
        table.setStorageType(1);
        table.setDataSize(2);
        table.getAxis().setStorageAddress(2);
        table.getAxis().setStorageType(1);
        table.getAxis().setDataSize(2);
        rom = rom(table, new byte[] {5, 6, 10, 20});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        controller.setAxisValue(CalibrationAxis.COLUMN, 1, "25");
        assertEquals(25, rom.getBinary()[3] & 0xFF);

        try {
            controller.setAxisValue(CalibrationAxis.ROW, 0, "4");
            fail("A 2D table should not expose a row axis");
        } catch (CalibrationEditException expected) {
            assertTrue(expected.getMessage().contains("row axis"));
        }
        assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));
    }

    @Test
    public void unchangedValueDoesNotCreateUndoHistory() throws Exception {
        Table1D table = line("Ignition Target", 0, 2);
        rom = rom(table, new byte[] {12, 14});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditResult result = controller.setCellValue(0, 1, "14");

        assertFalse(result.isChanged());
        assertFalse(controller.canUndo());
    }

    @Test
    public void listenersFollowClassicAndReplacementHistoryChanges()
            throws Exception {
        Table1D table = line("Fuel Target", 0, 2);
        rom = rom(table, new byte[] {12, 14});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);
        final CalibrationGridSnapshot[] latest = {null};
        final int[] updates = {0};
        CalibrationEditListener listener = snapshot -> {
            latest[0] = snapshot;
            updates[0]++;
        };
        controller.addListener(listener);

        controller.setCellValue(0, 1, "18");
        assertEquals("18.0", latest[0].cellAt(0, 1).getDisplayValue());
        controller.undo();
        assertEquals("14.0", latest[0].cellAt(0, 1).getDisplayValue());
        assertEquals(2, updates[0]);

        controller.removeListener(listener);
        controller.redo();
        assertEquals(2, updates[0]);
        controller.close();
    }

    @Test
    public void appliesDefinitionBackedFineAndCoarseAdjustments()
            throws Exception {
        Table1D table = line("Timing", 0, 2);
        rom = rom(table, new byte[] {12, 14});
        table.getCurrentScale().setFineIncrement(2.0);
        table.getCurrentScale().setCoarseIncrement(5.0);
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditResult fine = controller.adjustCellValue(0, 0,
                CalibrationAdjustment.FINE_INCREASE);
        assertEquals("14.0", fine.getCurrentValue());
        CalibrationEditResult coarse = controller.adjustCellValue(0, 0,
                CalibrationAdjustment.COARSE_DECREASE);
        assertEquals("9.0", coarse.getCurrentValue());
        assertEquals(2, RomEditHistory.getInstance().undoDepth(rom));
    }

    @Test(timeout = 1000)
    public void malformedScaleCannotOverflowTheEditorThread()
            throws Exception {
        Table1D table = line("Malformed scale", 0, 1);
        rom = rom(table, new byte[] {10});
        table.getCurrentScale().setByteExpression("10");
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditResult result = controller.adjustCellValue(0, 0,
                CalibrationAdjustment.FINE_INCREASE);

        assertFalse(result.isChanged());
        assertEquals(10, rom.getBinary()[0] & 0xFF);
        assertFalse(controller.canUndo());
    }

    @Test
    public void retryKeepsDirectionForNegativeScaleIncrements()
            throws Exception {
        Table1D table = line("Descending scale", 0, 1);
        rom = rom(table, new byte[] {10});
        table.getCurrentScale().setExpression("x/100");
        table.getCurrentScale().setByteExpression("x*100");
        table.getCurrentScale().setFineIncrement(0.001);
        table.getCurrentScale().setCoarseIncrement(-1.0);
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditResult result = controller.adjustCellValue(0, 0,
                CalibrationAdjustment.FINE_INCREASE);

        assertTrue(result.isChanged());
        assertEquals(9, rom.getBinary()[0] & 0xFF);
    }

    @Test
    public void adjustsASelectionAsOneUndoableChange() throws Exception {
        Table1D table = line("Timing", 0, 3);
        rom = rom(table, new byte[] {10, 20, 30});
        table.getCurrentScale().setFineIncrement(2.0);
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditBatchResult result = controller.adjustCellValues(
                Arrays.asList(new CalibrationCellCoordinate(0, 0),
                        new CalibrationCellCoordinate(0, 2)),
                CalibrationAdjustment.FINE_INCREASE);

        assertEquals(2, result.getChangedCellCount());
        assertEquals(12, rom.getBinary()[0] & 0xFF);
        assertEquals(20, rom.getBinary()[1] & 0xFF);
        assertEquals(32, rom.getBinary()[2] & 0xFF);
        assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));
        controller.undo();
        assertEquals(10, rom.getBinary()[0] & 0xFF);
        assertEquals(30, rom.getBinary()[2] & 0xFF);
    }

    @Test
    public void restoresASelectionAsOneUndoableChange() throws Exception {
        Table1D table = line("Fuel", 0, 3);
        rom = rom(table, new byte[] {10, 20, 30});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);
        controller.setCellValues(Arrays.asList(
                new CalibrationCellEdit(0, 0, "15"),
                new CalibrationCellEdit(0, 1, "25")));

        CalibrationEditBatchResult result = controller.restoreCellValues(
                Arrays.asList(new CalibrationCellCoordinate(0, 0),
                        new CalibrationCellCoordinate(0, 1)));

        assertEquals(2, result.getChangedCellCount());
        assertEquals(10, rom.getBinary()[0] & 0xFF);
        assertEquals(20, rom.getBinary()[1] & 0xFF);
        assertEquals(2, RomEditHistory.getInstance().undoDepth(rom));
        controller.undo();
        assertEquals(15, rom.getBinary()[0] & 0xFF);
        assertEquals(25, rom.getBinary()[1] & 0xFF);
    }

    @Test
    public void acceptsTheCurrentLocalesDecimalSeparator() throws Exception {
        Table1D table = line("Fuel", 0, 1);
        table.setStorageType(Settings.STORAGE_TYPE_FLOAT);
        rom = rom(table, new byte[] {0, 0, 0, 0});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            CalibrationEditResult result = controller.setCellValue(
                    0, 0, "12,5");
            assertEquals(12.5, table.getDataCell(0).getRealValue(), 0.0001);
            assertTrue(result.isChanged());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void restoresOneCellToItsLoadedValueThroughUndoHistory()
            throws Exception {
        Table1D table = line("Boost", 0, 2);
        rom = rom(table, new byte[] {12, 14});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);
        controller.setCellValue(0, 1, "19");

        CalibrationEditResult restored = controller.restoreCellValue(0, 1);

        assertEquals("14.0", restored.getCurrentValue());
        assertFalse(restored.getSnapshot().cellAt(0, 1).isChanged());
        assertEquals(2, RomEditHistory.getInstance().undoDepth(rom));
        controller.undo();
        assertEquals("19.0", controller.getSnapshot().cellAt(0, 1)
                .getDisplayValue());
    }

    @Test
    public void pastesAValueBlockAsOneUndoableOperation() throws Exception {
        Table1D table = line("Fuel Target", 0, 4);
        rom = rom(table, new byte[] {10, 20, 30, 40});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditBatchResult result = controller.setCellValues(
                Arrays.asList(new CalibrationCellEdit(0, 1, "21"),
                        new CalibrationCellEdit(0, 2, "32"),
                        new CalibrationCellEdit(0, 3, "43")));

        assertTrue(result.isChanged());
        assertEquals(3, result.getRequestedCellCount());
        assertEquals(3, result.getChangedCellCount());
        assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));
        assertEquals(21, rom.getBinary()[1] & 0xFF);
        assertEquals(32, rom.getBinary()[2] & 0xFF);
        assertEquals(43, rom.getBinary()[3] & 0xFF);

        controller.undo();
        assertEquals(20, rom.getBinary()[1] & 0xFF);
        assertEquals(30, rom.getBinary()[2] & 0xFF);
        assertEquals(40, rom.getBinary()[3] & 0xFF);
    }

    @Test
    public void rejectsAnOutOfBoundsBlockBeforeChangingAnyCell()
            throws Exception {
        Table1D table = line("Fuel Target", 0, 2);
        rom = rom(table, new byte[] {10, 20});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        try {
            controller.setCellValues(Arrays.asList(
                    new CalibrationCellEdit(0, 1, "25"),
                    new CalibrationCellEdit(0, 2, "35")));
            fail("Out-of-bounds block should be rejected");
        } catch (CalibrationEditException expected) {
            assertTrue(expected.getMessage().contains("do not fit"));
        }

        assertEquals(20, rom.getBinary()[1] & 0xFF);
        assertFalse(controller.canUndo());
    }

    @Test
    public void rejectsAMalformedBlockBeforeChangingEarlierValues()
            throws Exception {
        Table1D table = line("Fuel Target", 0, 3);
        rom = rom(table, new byte[] {10, 20, 30});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        try {
            controller.setCellValues(Arrays.asList(
                    new CalibrationCellEdit(0, 0, "15"),
                    new CalibrationCellEdit(0, 1, "not a number"),
                    new CalibrationCellEdit(0, 2, "35")));
            fail("Malformed block should be rejected");
        } catch (CalibrationEditException expected) {
            assertTrue(expected.getMessage().contains("complete"));
        }

        assertEquals(10, rom.getBinary()[0] & 0xFF);
        assertEquals(20, rom.getBinary()[1] & 0xFF);
        assertEquals(30, rom.getBinary()[2] & 0xFF);
        assertFalse(controller.canUndo());
    }

    @Test
    public void interpolatesASelectedLineAsOneUndoableChange()
            throws Exception {
        Table1D table = line("Fuel Target", 0, 4);
        rom = rom(table, new byte[] {10, 0, 0, 40});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditBatchResult result = controller.interpolate(
                0, 0, 0, 3, CalibrationInterpolation.HORIZONTAL);

        assertEquals(2, result.getRequestedCellCount());
        assertEquals(2, result.getChangedCellCount());
        assertEquals(20, rom.getBinary()[1] & 0xFF);
        assertEquals(30, rom.getBinary()[2] & 0xFF);
        assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));
        controller.undo();
        assertEquals(0, rom.getBinary()[1] & 0xFF);
        assertEquals(0, rom.getBinary()[2] & 0xFF);
    }

    @Test
    public void interpolatesAThreeDimensionalRangeAcrossBothAxes()
            throws Exception {
        Table3D table = new Table3D();
        table.setName("Fuel Surface");
        table.setStorageAddress(0);
        table.setStorageType(1);
        table.setSizeX(3);
        table.setSizeY(3);
        table.getXAxis().setStorageAddress(9);
        table.getXAxis().setStorageType(1);
        table.getXAxis().setDataSize(3);
        table.getYAxis().setStorageAddress(12);
        table.getYAxis().setStorageType(1);
        table.getYAxis().setDataSize(3);
        rom = rom(table, new byte[] {
                0, 30, 60, 0, 0, 0, 20, 50, 80,
                0, 10, 20, 0, 10, 20});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        CalibrationEditBatchResult result = controller.interpolate(
                0, 0, 2, 2, CalibrationInterpolation.BOTH);

        assertEquals(5, result.getRequestedCellCount());
        assertEquals(10, rom.getBinary()[3] & 0xFF);
        assertEquals(40, rom.getBinary()[4] & 0xFF);
        assertEquals(70, rom.getBinary()[5] & 0xFF);
        assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));
        controller.undo();
        assertEquals(0, rom.getBinary()[3] & 0xFF);
        assertEquals(0, rom.getBinary()[4] & 0xFF);
        assertEquals(0, rom.getBinary()[5] & 0xFF);
    }

    @Test
    public void rejectsInterpolationWithoutTwoBoundaryCells()
            throws Exception {
        Table1D table = line("Timing", 0, 3);
        rom = rom(table, new byte[] {10, 20, 30});
        TableCalibrationEditController controller =
                new TableCalibrationEditController(table);

        try {
            controller.interpolate(0, 0, 0, 1,
                    CalibrationInterpolation.HORIZONTAL);
            fail("Two cells do not contain an interpolation interior");
        } catch (CalibrationEditException expected) {
            assertTrue(expected.getMessage().contains("at least three"));
        }
        assertFalse(controller.canUndo());
    }

    private static Table1D line(String name, int address, int size) {
        Table1D table = new Table1D();
        table.setName(name);
        table.setStorageAddress(address);
        table.setStorageType(1);
        table.setDataSize(size);
        return table;
    }

    private static Rom rom(com.romraider.maps.Table table, byte[] binary) {
        Rom result = new Rom(new RomID());
        result.addTableByName(table);
        result.populateTables(binary, new JProgressPane());
        return result;
    }
}
