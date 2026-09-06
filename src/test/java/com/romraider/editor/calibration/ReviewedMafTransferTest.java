/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import static org.junit.Assert.*;
import com.romraider.Settings;
import com.romraider.maps.*;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;
import com.romraider.xml.RomAttributeParser;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;

public class ReviewedMafTransferTest {
    private Rom rom;
    @After public void cleanup() { if (rom != null) RomEditHistory.getInstance().clear(rom); }
    private Table2D target(int storage, byte[] values) throws Exception {
        Table2D table = new Table2D(); table.setName("Synthetic MAF"); table.setStorageType(storage);
        table.setStorageAddress(0); table.setDataSize(3);
        Table1D axis = table.getAxis(); axis.setStorageType(1); axis.setStorageAddress(values.length); axis.setDataSize(3);
        byte[] bytes = Arrays.copyOf(values, values.length + 4); bytes[values.length] = 1; bytes[values.length + 1] = 2; bytes[values.length + 2] = 3;
        rom = new Rom(new RomID()); rom.addTableByName(table); rom.populateTables(bytes, new JProgressPane());
        table.getCurrentScale().setUnit("g/s"); axis.getCurrentScale().setUnit("V");
        return table;
    }
    private Table2D target() throws Exception { return target(1, new byte[] {10, 20, 30}); }
    private static void rejected(Runnable action, String message) {
        try { action.run(); fail("Expected refusal: " + message); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }
    @Test public void reviewIsReadOnlyAndApplyIsOneUndoableRoundedBatch() throws Exception {
        Table2D table = target(); byte[] original = rom.getBinary().clone();
        ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> 15, "Synthetic fit");
        assertArrayEquals(original, rom.getBinary()); assertEquals(0, RomEditHistory.getInstance().undoDepth(rom));
        assertEquals(11.5, review.getRows().get(0).requested(), 1e-12); assertEquals(12, review.getRows().get(0).stored(), 0);
        assertEquals(3, review.getChangedCount()); review.apply(() -> true);
        assertArrayEquals(new byte[] {12, 23, 35, 1, 2, 3, 0}, rom.getBinary());
        assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));
        RomEditHistory.getInstance().undo(rom); assertArrayEquals(original, rom.getBinary());
        RomEditHistory.getInstance().redo(rom); assertEquals(35, table.getDataCell(2).getRealValue(), 0);
        rejected(() -> review.apply(() -> true), "already");
    }
    @Test public void uncoveredValuesAreExplicitAndRemainUnchanged() throws Exception {
        Table2D table = target(); ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> v == 2 ? Double.NaN : 10, "Sparse bins");
        assertEquals(1, review.getUncoveredCount()); assertFalse(review.getRows().get(1).covered());
        review.apply(() -> true); assertEquals(20, table.getDataCell(1).getRealValue(), 0); assertEquals(33, table.getDataCell(2).getRealValue(), 0);
    }
    @Test public void rangeOverflowInfinityNegativeFlowAndNoChangesAreRejectedWithoutEdits() throws Exception {
        Table2D table = target(); byte[] original = rom.getBinary().clone();
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10000, "Overflow"), "clipping");
        rejected(() -> ReviewedMafTransfer.preview(table, v -> Double.POSITIVE_INFINITY, "Infinity"), "infinite");
        rejected(() -> ReviewedMafTransfer.preview(table, v -> -100, "Zero multiplier"), "invalid flow");
        rejected(() -> ReviewedMafTransfer.preview(table, v -> Double.NaN, "No coverage"), "No covered");
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 0, "Identity"), "No covered");
        assertArrayEquals(original, rom.getBinary()); assertFalse(RomEditHistory.getInstance().canUndo(rom));
    }
    @Test public void documentGuardAndUnrelatedByteChangesInvalidateReview() throws Exception {
        Table2D table = target(); ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> 10, "Fit");
        rejected(() -> review.apply(() -> false), "context changed");
        rom.getBinary()[6] = 42; rejected(() -> review.apply(() -> true), "changed after review");
        assertEquals(42, rom.getBinary()[6]); assertEquals(10, table.getDataCell(0).getRealValue(), 0);
    }
    @Test public void scalingChangesInvalidateReviewEvenWithoutByteChanges() throws Exception {
        Table2D table = target(); ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> 10, "Fit");
        table.getCurrentScale().setExpression("x*2"); rejected(() -> review.apply(() -> true), "changed after review");
        assertEquals(10, rom.getBinary()[0]);
    }
    @Test public void equalValuedReplacementCellsStillInvalidateReview() throws Exception {
        Table2D table = target(); ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> 10, "Fit");
        DataCell replacement = new DataCell(table, 0, rom); assertEquals(table.getDataCell(0), replacement);
        table.getData()[0] = replacement; rejected(() -> review.apply(() -> true), "changed after review");
    }
    @Test public void staleCellCachesAreRejectedBeforeReview() throws Exception {
        Table2D table = target(); rom.getBinary()[0] = 12;
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "Cached table");
        rom.getBinary()[0] = 10; rom.getBinary()[3] = 4;
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "Cached axis");
    }
    @Test public void wrongUnitsLockedAndOverlappingAxisAreRejected() throws Exception {
        Table2D table = target(); table.getAxis().getCurrentScale().setUnit("rpm");
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "units");
        table.getAxis().getCurrentScale().setUnit("V"); table.setLocked(true);
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "editable");
        table.setLocked(false); table.getAxis().setStorageAddress(0);
        for (DataCell cell : table.getAxis().getData()) cell.updateBinValueFromMemory();
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "overlap");
    }
    @Test public void explicitInverseAndStoredScaledValuesAreReviewed() throws Exception {
        Table2D table = target(); table.getCurrentScale().setExpression("x/2"); table.getCurrentScale().setByteExpression("x*2");
        ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> 15, "Fit");
        assertEquals(5.75, review.getRows().get(0).requested(), 0); assertEquals(6, review.getRows().get(0).stored(), 0);
        review.apply(() -> true); assertEquals(6, table.getDataCell(0).getRealValue(), 0);
        table.getCurrentScale().setByteExpression("");
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "explicit inverse");
    }
    @Test public void floatReviewMatchesActualStoredPrecision() throws Exception {
        Table2D template = new Table2D(); byte[] values = new byte[12];
        for (int i = 0; i < 3; i++) System.arraycopy(RomAttributeParser.floatToByte(i + 1, template.getEndian(), template.getMemModelEndian()), 0, values, i * 4, 4);
        Table2D table = target(Settings.STORAGE_TYPE_FLOAT, values);
        ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> 11.1111111, "Fit");
        review.apply(() -> true);
        for (ReviewedMafTransfer.Row row : review.getRows()) assertEquals(row.stored(), table.getDataCell(row.index()).getRealValue(), 0);
    }
    @Test public void presentationFailureRollsBackBytesAliasesAndHistory() throws Exception {
        Table2D table = target(); Table1D alias = new Table1D(); alias.setName("Alias"); alias.setStorageType(1); alias.setStorageAddress(0); alias.setDataSize(3);
        rom.addTableByName(alias); alias.populateTable(rom);
        ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> 10, "Fit"); byte[] original = rom.getBinary().clone();
        TablePresentationListener broken = new TablePresentationListener() {
            @Override public void cellChanged(Table t, DataCell cell) { throw new IllegalStateException("Synthetic render failure"); }
        };
        TablePresentationService.addListener(alias, broken);
        try { rejected(() -> review.apply(() -> true), "original ROM bytes were restored"); }
        finally { TablePresentationService.removeListener(alias, broken); }
        assertArrayEquals(original, rom.getBinary()); assertFalse(RomEditHistory.getInstance().canUndo(rom));
        for (int i = 0; i < 3; i++) { assertEquals(original[i], table.getDataCell(i).getBinValue(), 0); assertEquals(original[i], alias.getDataCell(i).getBinValue(), 0); }
    }
    @Test public void callbackCannotMutateTheProposalTargetUndetected() throws Exception {
        Table2D table = target();
        rejected(() -> ReviewedMafTransfer.preview(table, v -> { rom.getBinary()[6] = 42; return 10; }, "Mutating callback"), "changed after review");
        assertEquals(10, rom.getBinary()[0]); assertFalse(RomEditHistory.getInstance().canUndo(rom));
    }
    @Test public void originalUnsigned32ValuesMustBeUndoableByTheExistingWriter() throws Exception {
        byte[] values = new byte[12]; values[0] = (byte) 0x80; values[7] = 20; values[11] = 30;
        Table2D table = target(4, values);
        rejected(() -> ReviewedMafTransfer.preview(table, v -> -50, "Fit"), "undo writer");
        assertEquals(0x80000000L, (long) table.getDataCell(0).getBinValue());
    }
    @Test public void littleEndianIntegerStorageMatchesReviewAndUndo() throws Exception {
        Table2D table = target(2, new byte[] {10, 0, 20, 0, 30, 0}); table.setEndian(Settings.Endian.LITTLE);
        for (DataCell cell : table.getData()) cell.updateBinValueFromMemory();
        byte[] original = rom.getBinary().clone();
        ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> 15, "Fit"); review.apply(() -> true);
        assertEquals(12, rom.getBinary()[0]); assertEquals(0, rom.getBinary()[1]); assertEquals(35, table.getDataCell(2).getBinValue(), 0);
        RomEditHistory.getInstance().undo(rom); assertArrayEquals(original, rom.getBinary());
    }
    @Test public void finiteStaticVoltageAxisWorksAndInvalidTextIsRejected() throws Exception {
        Table2D table = target(); Table1D axis = table.getAxis(); axis.setStaticDataTable(true);
        axis.setData(new DataCell[] {new DataCell(axis, "0.5", rom), new DataCell(axis, "1.5", rom), new DataCell(axis, "2.5", rom)});
        ReviewedMafTransfer review = ReviewedMafTransfer.preview(table, v -> v * 10, "Fit");
        assertEquals(.5, review.getRows().get(0).volts(), 0); review.apply(() -> true);
        assertEquals(11, table.getDataCell(0).getBinValue(), 0);
        axis.getData()[1] = new DataCell(axis, "Unavailable", rom);
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "Static voltage");
    }
    @Test public void unpopulatedAndOutOfBoundsTargetsFailWithoutMutation() throws Exception {
        Table2D table = target(); byte[] original = rom.getBinary().clone(); table.setStorageAddress(1000);
        rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "outside the ROM");
        table.setData(null); rejected(() -> ReviewedMafTransfer.preview(table, v -> 10, "Fit"), "populated");
        assertArrayEquals(original, rom.getBinary());
    }
    @Test public void committedEditSurvivesBrokenHistoryObserversAndStillNotifiesOthers() throws Exception {
        Table2D table = target(); int[] notifications = {0};
        com.romraider.maps.history.EditHistoryListener broken = r -> { throw new IllegalStateException("Synthetic history observer failure"); };
        com.romraider.maps.history.EditHistoryListener healthy = r -> notifications[0]++;
        RomEditHistory history = RomEditHistory.getInstance(); history.addListener(broken); history.addListener(healthy);
        try {
            ReviewedMafTransfer.preview(table, v -> 10, "Fit").apply(() -> true);
            assertEquals(11, table.getDataCell(0).getBinValue(), 0); assertEquals(1, history.undoDepth(rom));
            history.undo(rom); assertEquals(10, table.getDataCell(0).getBinValue(), 0);
            history.redo(rom); assertEquals(11, table.getDataCell(0).getBinValue(), 0); assertEquals(3, notifications[0]);
        } finally { history.removeListener(broken); history.removeListener(healthy); }
    }
}
