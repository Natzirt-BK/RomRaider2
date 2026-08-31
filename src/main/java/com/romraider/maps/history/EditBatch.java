/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps.history;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.UserLevelException;

/** One user operation, potentially spanning many calibration cells. */
final class EditBatch {
    private final Rom rom;
    private final String description;
    private final List<CellEdit> edits = new ArrayList<CellEdit>();
    private final Map<DataCell, CellEdit> editsByCell =
            new IdentityHashMap<DataCell, CellEdit>();
    private final long createdAt = System.currentTimeMillis();

    EditBatch(Rom rom, String description) {
        this.rom = rom;
        this.description = description == null || description.trim().isEmpty()
                ? "Edit calibration" : description.trim();
    }

    Rom getRom() { return rom; }
    String getDescription() { return description; }
    int size() { return edits.size(); }
    boolean isEmpty() { return edits.isEmpty(); }

    EditHistoryEntry summary() {
        LinkedHashSet<String> tables = new LinkedHashSet<String>();
        for (CellEdit edit : edits) {
            if (edit.cell.getTable() != null
                    && edit.cell.getTable().getName() != null) {
                tables.add(edit.cell.getTable().getName());
            }
        }
        return new EditHistoryEntry(description, edits.size(),
                new ArrayList<String>(tables), createdAt);
    }

    void add(DataCell cell, double oldValue, double newValue) {
        CellEdit existing = editsByCell.get(cell);
        if (existing == null) {
            CellEdit edit = new CellEdit(cell, oldValue, newValue);
            edits.add(edit);
            editsByCell.put(cell, edit);
        } else {
            existing.newValue = newValue;
            if (Double.compare(existing.oldValue, newValue) == 0) {
                edits.remove(existing);
                editsByCell.remove(cell);
            }
        }
    }

    void undo() throws UserLevelException {
        for (int index = edits.size() - 1; index >= 0; index--) {
            edits.get(index).undo();
        }
    }

    void redo() throws UserLevelException {
        for (CellEdit edit : edits) edit.redo();
    }

    private static final class CellEdit {
        private final DataCell cell;
        private final double oldValue;
        private double newValue;

        private CellEdit(DataCell cell, double oldValue, double newValue) {
            this.cell = cell;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        private void undo() throws UserLevelException { cell.setBinValue(oldValue); }
        private void redo() throws UserLevelException { cell.setBinValue(newValue); }
    }
}
