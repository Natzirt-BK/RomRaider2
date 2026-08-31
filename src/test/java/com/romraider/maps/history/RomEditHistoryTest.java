/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;
import com.romraider.maps.UserLevelException;

public class RomEditHistoryTest {
    private final RomEditHistory history = RomEditHistory.getInstance();
    private Rom rom;
    private Table1D table;

    @Before
    public void setUp() {
        rom = new Rom(new RomID());
        table = new Table1D();
        table.setName("Boost Target");
        table.setRom(rom);
        history.clear(rom);
    }

    @After
    public void cleanUp() {
        history.clear(rom);
    }

    @Test
    public void multiCellTransactionIsOneUndoStepAndCanBeRedone()
            throws Exception {
        MemoryCell first = new MemoryCell(table, 1.0);
        MemoryCell second = new MemoryCell(table, 2.0);
        try (EditTransaction edit = history.begin(table,
                "Increase selected values")) {
            change(first, 3.0);
            change(second, 4.0);
        }

        assertEquals(1, history.undoDepth(rom));
        assertEquals("Increase selected values",
                history.nextUndoDescription(rom));
        history.undo(rom);
        assertEquals(1.0, first.getBinValue(), 0.0);
        assertEquals(2.0, second.getBinValue(), 0.0);
        assertTrue(history.canRedo(rom));

        history.redo(rom);
        assertEquals(3.0, first.getBinValue(), 0.0);
        assertEquals(4.0, second.getBinValue(), 0.0);
        assertTrue(history.canUndo(rom));
    }

    @Test
    public void newEditAfterUndoDiscardsRedoBranch() throws Exception {
        MemoryCell cell = new MemoryCell(table, 1.0);
        change(cell, 2.0);
        history.undo(rom);
        assertTrue(history.canRedo(rom));

        change(cell, 5.0);
        assertFalse(history.canRedo(rom));
        assertEquals(1, history.undoDepth(rom));
    }

    @Test
    public void repeatedCellChangesInsideTransactionKeepOriginalBoundary()
            throws Exception {
        MemoryCell cell = new MemoryCell(table, 10.0);
        try (EditTransaction edit = history.begin(table, "Drag adjustment")) {
            change(cell, 11.0);
            change(cell, 12.0);
            change(cell, 13.0);
        }
        history.undo(rom);
        assertEquals(10.0, cell.getBinValue(), 0.0);
        history.redo(rom);
        assertEquals(13.0, cell.getBinValue(), 0.0);
    }

    @Test
    public void historySnapshotsAreNewestFirstDetailedAndImmutable()
            throws Exception {
        MemoryCell first = new MemoryCell(table, 1.0);
        MemoryCell second = new MemoryCell(table, 2.0);
        try (EditTransaction edit = history.begin(table, "First adjustment")) {
            change(first, 3.0);
            change(second, 4.0);
        }
        change(first, 5.0);

        assertEquals(2, history.undoHistory(rom).size());
        EditHistoryEntry latest = history.undoHistory(rom).get(0);
        assertEquals("Edit Boost Target", latest.getDescription());
        assertEquals(1, latest.getChangedCells());
        assertEquals(Arrays.asList("Boost Target"), latest.getTableNames());
        assertTrue(latest.getCreatedAt() > 0L);
        try {
            history.undoHistory(rom).clear();
            throw new AssertionError("History views must be immutable");
        } catch (UnsupportedOperationException expected) {
            // UI clients cannot mutate the service's history stacks.
        }

        history.undo(rom);
        assertEquals("Edit Boost Target",
                history.redoHistory(rom).get(0).getDescription());
    }

    private void change(MemoryCell cell, double value) {
        double old = cell.getBinValue();
        cell.value = value;
        history.recordChange(rom, cell, old, value);
    }

    private static final class MemoryCell extends DataCell {
        private double value;

        private MemoryCell(Table1D table, double value) {
            super(table, (Rom) null);
            this.value = value;
        }

        public double getBinValue() { return value; }

        public void setBinValue(double value) throws UserLevelException {
            this.value = value;
        }
    }
}
