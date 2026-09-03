/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.RomEditHistory;

public final class EditorDocumentSessionTest {
    private final RomEditHistory history = RomEditHistory.getInstance();
    private Rom left;
    private Rom right;

    @After
    public void cleanUp() {
        history.clear(left);
        history.clear(right);
        RomChangeService.forget(left);
        RomChangeService.forget(right);
    }

    @Test
    public void ownsOpenRomAndTableStateWithoutSwingObjects() {
        left = rom("stock.bin", "Fuel Target");
        right = rom("tuned.bin", "Boost Target");
        EditorDocumentSession session = new EditorDocumentSession();
        session.openRom(left);
        session.openRom(right);
        Table1D fuel = (Table1D) left.getTableByName("Fuel Target");
        session.openTable(left, fuel);

        EditorDocumentSnapshot snapshot = session.snapshot();
        assertEquals(2, snapshot.getDocuments().size());
        assertSame(left, snapshot.getActiveRom());
        assertSame(fuel, snapshot.getActiveTable());
        assertEquals(1, snapshot.getActiveDocument().getOpenTables().size());

        session.activateRom(right);
        assertSame(right, session.snapshot().getActiveRom());
        session.closeRom(right);
        assertSame(left, session.snapshot().getActiveRom());
        session.close();
    }

    @Test
    public void editHistoryUpdatesDirtyUndoAndRedoState() throws Exception {
        left = rom("stock.bin", "Fuel Target");
        Table1D fuel = (Table1D) left.getTableByName("Fuel Target");
        MemoryCell cell = new MemoryCell(fuel, 10.0);
        fuel.setData(new DataCell[] {cell});
        EditorDocumentSession session = new EditorDocumentSession();
        final long[] observedRevision = {-1};
        session.addListener(snapshot ->
                observedRevision[0] = snapshot.getRevision());
        session.openRom(left);
        RomChangeService.rememberSavedBinary(left);

        cell.changeTo(12.0);
        history.recordChange(left, cell, 10.0, 12.0);

        EditorDocument document = session.snapshot().getActiveDocument();
        assertTrue(document.isDirty());
        assertTrue(document.canSave());
        assertTrue(document.canUndo());
        assertFalse(document.canRedo());
        assertTrue(observedRevision[0] >= 2);

        session.undo();
        assertEquals(10.0, cell.getBinValue(), 0.0);
        assertTrue(session.snapshot().getActiveDocument().canRedo());
        session.redo();
        assertEquals(12.0, cell.getBinValue(), 0.0);
        session.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTableFromAnotherRom() {
        left = rom("stock.bin", "Fuel Target");
        right = rom("tuned.bin", "Boost Target");
        EditorDocumentSession session = new EditorDocumentSession();
        try {
            session.openRom(left);
            session.openTable(left, right.getTableByName("Boost Target"));
        } finally {
            session.close();
        }
    }

    private static Rom rom(String name, String tableName) {
        Rom rom = new Rom(new RomID());
        rom.setFileName(name);
        Table1D table = new Table1D();
        table.setName(tableName);
        rom.addTableByName(table);
        return rom;
    }

    private static final class MemoryCell extends DataCell {
        private double value;

        private MemoryCell(Table1D table, double value) {
            super(table, (Rom) null);
            this.value = value;
            setOriginalValue(value);
        }

        void changeTo(double next) { value = next; }
        public double getBinValue() { return value; }
        public void setBinValue(double next) throws UserLevelException {
            value = next;
        }
    }
}
