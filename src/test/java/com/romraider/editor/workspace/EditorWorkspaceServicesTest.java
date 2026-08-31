/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.romraider.maps.DataCell;
import com.romraider.maps.Table1D;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;

public class EditorWorkspaceServicesTest {
    @Test
    public void navigationSupportsBackForwardAndTruncatesForwardBranch() {
        EditorNavigationHistory history = new EditorNavigationHistory();
        TableLocation fuel = location("Fuel");
        TableLocation boost = location("Boost");
        TableLocation timing = location("Timing");

        history.visit(fuel);
        history.visit(boost);
        history.visit(timing);
        assertEquals(boost, history.back());
        assertEquals(fuel, history.back());
        assertNull(history.back());
        assertEquals(boost, history.forward());

        TableLocation idle = location("Idle");
        history.visit(idle);
        assertFalse(history.canGoForward());
        assertEquals(idle, history.current());
    }

    @Test
    public void favoritesToggleAndRecentTablesRemainUniqueAndBounded() {
        EditorWorkspacePreferences preferences = new EditorWorkspacePreferences();
        TableLocation favorite = location("Primary Open Loop Fueling");
        assertTrue(preferences.toggleFavorite(favorite));
        assertTrue(preferences.isFavorite(favorite));
        assertTrue(preferences.removeFavorite(favorite));
        assertFalse(preferences.isFavorite(favorite));
        assertFalse(preferences.removeFavorite(favorite));

        for (int i = 0; i < 35; i++) {
            preferences.recordRecent(location("Table " + i));
        }
        preferences.recordRecent(location("Table 20"));
        assertEquals(30, preferences.getRecent().size());
        assertEquals("Table 20", preferences.getRecent().get(0).getTableName());
    }

    @Test
    public void openTablesAreIsolatedByRom() {
        EditorWorkspacePreferences preferences = new EditorWorkspacePreferences();
        TableLocation evoFuel = new TableLocation("EVO-8-9653", "Fuel Map");
        TableLocation evoTiming = new TableLocation("EVO-8-9653", "Timing Map");
        TableLocation subaruFuel = new TableLocation("A2WC510N", "Fuel Map");
        preferences.markOpen(evoFuel);
        preferences.markOpen(evoTiming);
        preferences.markOpen(subaruFuel);

        assertEquals(2, preferences.getOpenTables("EVO-8-9653").size());
        assertEquals(1, preferences.getOpenTables("A2WC510N").size());
        preferences.markClosed(evoFuel);
        assertEquals(1, preferences.getOpenTables("EVO-8-9653").size());
    }

    @Test
    public void activeTableAndUserOrderArePersistedTogether() {
        EditorWorkspacePreferences preferences = new EditorWorkspacePreferences();
        preferences.markOpen(location("Fuel"));
        preferences.markOpen(location("Timing"));
        preferences.markOpen(location("Boost"));
        preferences.markActive(location("Timing"));

        preferences.replaceOpenTables("EVO-8-9653",
                Arrays.asList("Boost", "Fuel", "Timing"));

        assertEquals(Arrays.asList("Boost", "Fuel", "Timing"),
                preferences.getOpenTables("EVO-8-9653"));
        assertEquals("Timing", preferences.getActiveTable("EVO-8-9653"));
        preferences.markClosed(location("Timing"));
        assertNull(preferences.getActiveTable("EVO-8-9653"));
    }

    @Test
    public void notesAndChangedCellSummaryRepresentRealWorkspaceState() {
        EditorWorkspacePreferences preferences = new EditorWorkspacePreferences();
        TableLocation fuel = location("Fuel");
        preferences.setTableNote(fuel, "Adjusted for track fuel.");
        assertEquals("Adjusted for track fuel.", preferences.getTableNote(fuel));

        Table1D table = new Table1D();
        DataCell cell = new DataCell(table, (com.romraider.maps.Rom) null);
        cell.setOriginalValue(1.0);
        table.setData(new DataCell[] {cell});
        assertEquals(1, RomChangeSummary.countChangedCells(table));
        cell.setOriginalValue(0.0);
        assertEquals(0, RomChangeSummary.countChangedCells(table));
    }

    @Test
    public void changedTablesAreSortedByImpactThenName() {
        Table1D fuel = changedTable("Fuel", 2);
        Table1D boost = changedTable("Boost", 1);
        Table1D timing = changedTable("Timing", 2);

        List<TableChangeSummary> summaries = RomChangeSummary.summarizeTables(
                Arrays.asList(boost, timing, fuel));

        assertEquals(3, summaries.size());
        assertEquals("Fuel", summaries.get(0).getTableName());
        assertEquals(2, summaries.get(0).getChangedCells());
        assertEquals("Timing", summaries.get(1).getTableName());
        assertEquals("Boost", summaries.get(2).getTableName());
    }

    @Test
    public void changedRomsAreDetectedInWorkspaceOrder() {
        Rom clean = romWithTable("Clean", 0);
        Rom fuel = romWithTable("Fuel", 2);
        Rom timing = romWithTable("Timing", 1);

        List<Rom> changed = RomChangeSummary.changedRoms(
                Arrays.asList(clean, fuel, timing));

        assertEquals(Arrays.asList(fuel, timing), changed);
        try {
            changed.clear();
            throw new AssertionError("changed ROM result must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: callers cannot alter the guarded shutdown snapshot.
        }
    }

    @Test
    public void changedMapNavigationPreservesRomAndImpactOrder() {
        Rom clean = romWithTable("Clean", 0);
        clean.setFileName("clean.bin");
        Rom fuel = romWithTable("Fuel", 3);
        fuel.setFileName("fuel.bin");
        Rom timing = romWithTable("Timing", 1);
        timing.setFileName("timing.bin");

        Map<TableLocation, Integer> changed =
                EditorWorkspaceService.getInstance().changedTables(
                        Arrays.asList(clean, fuel, timing));

        assertEquals(Arrays.asList(
                new TableLocation("fuel.bin", "Fuel"),
                new TableLocation("timing.bin", "Timing")),
                new ArrayList<TableLocation>(changed.keySet()));
        assertEquals(Integer.valueOf(3), changed.get(
                new TableLocation("fuel.bin", "Fuel")));
        try {
            changed.clear();
            throw new AssertionError("changed-map navigation must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Callers cannot alter the live workspace result.
        }
    }

    @Test
    public void savedBinarySnapshotDetectsChangesOutsideDefinedTables() {
        Rom rom = new Rom(new RomID());
        rom.populateTables(new byte[] {1, 2, 3}, new JProgressPane());
        RomChangeService.rememberSavedBinary(rom);
        assertFalse(RomChangeService.hasBinaryChanges(rom));

        rom.getBinary()[1] = 9;
        assertTrue(RomChangeService.hasBinaryChanges(rom));
        assertEquals(Arrays.asList(rom),
                RomChangeSummary.changedRoms(Arrays.asList(rom)));

        RomChangeService.markSaved(rom);
        assertFalse(RomChangeService.hasBinaryChanges(rom));
        RomChangeService.forget(rom);
    }

    @Test
    public void recoveredRomRemainsUnsavedUntilExplicitSave() {
        Rom rom = new Rom(new RomID());
        rom.populateTables(new byte[] {1, 2, 3}, new JProgressPane());
        RomChangeService.rememberSavedBinary(rom);
        RomChangeService.markUnsaved(rom);

        assertTrue(RomChangeService.hasBinaryChanges(rom));

        RomChangeService.markSaved(rom);
        assertFalse(RomChangeService.hasBinaryChanges(rom));
        RomChangeService.forget(rom);
    }

    @Test
    public void savedStateCanBeMarkedAndResetAsOneUndoableOperation()
            throws Exception {
        Rom rom = new Rom(new RomID());
        Table1D table = new Table1D();
        table.setName("Fuel Target");
        MemoryCell cell = new MemoryCell(table, rom, 10.0);
        table.setData(new DataCell[] {cell});
        rom.addTableByName(table);

        RomChangeService.markSaved(rom);
        change(cell, 12.0);
        assertEquals(1, RomChangeSummary.countChangedCells(rom));

        RomChangeService.resetToSaved(rom);
        assertEquals(10.0, cell.getBinValue(), 0.0);
        assertEquals(0, RomChangeSummary.countChangedCells(rom));
        assertEquals("Reset ROM changes",
                RomEditHistory.getInstance().nextUndoDescription(rom));

        RomEditHistory.getInstance().undo(rom);
        assertEquals(12.0, cell.getBinValue(), 0.0);
        RomEditHistory.getInstance().clear(rom);
    }

    private static Table1D changedTable(String name, int changedCells) {
        Table1D table = new Table1D();
        table.setName(name);
        DataCell[] cells = new DataCell[changedCells];
        for (int index = 0; index < changedCells; index++) {
            cells[index] = new DataCell(table, (com.romraider.maps.Rom) null);
            cells[index].setOriginalValue(index + 1.0);
        }
        table.setData(cells);
        return table;
    }

    private static Rom romWithTable(String name, int changedCells) {
        Rom rom = new Rom(new RomID());
        Table1D table = changedTable(name, changedCells);
        rom.addTableByName(table);
        return rom;
    }

    private static TableLocation location(String table) {
        return new TableLocation("EVO-8-9653", table);
    }

    private static void change(MemoryCell cell, double value) {
        try {
            cell.setBinValue(value);
        } catch (UserLevelException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static final class MemoryCell extends DataCell {
        private double value;

        private MemoryCell(Table1D table, Rom rom, double value) {
            super(table, rom);
            this.value = value;
        }

        public double getBinValue() { return value; }
        public void setBinValue(double value) throws UserLevelException {
            double old = this.value;
            this.value = value;
            RomEditHistory.getInstance().recordChange(
                    getTable().getRom(), this, old, value);
        }

        public void setRevertPoint() { setOriginalValue(value); }
    }
}
