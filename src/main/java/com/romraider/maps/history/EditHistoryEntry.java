/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable summary of one undoable calibration operation. */
public final class EditHistoryEntry {
    private final String description;
    private final int changedCells;
    private final List<String> tableNames;
    private final long createdAt;

    EditHistoryEntry(String description, int changedCells,
            List<String> tableNames, long createdAt) {
        this.description = description;
        this.changedCells = changedCells;
        this.tableNames = Collections.unmodifiableList(
                new ArrayList<String>(tableNames));
        this.createdAt = createdAt;
    }

    public String getDescription() { return description; }
    public int getChangedCells() { return changedCells; }
    public List<String> getTableNames() { return tableNames; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return description + "  ·  " + changedCells
                + (changedCells == 1 ? " cell" : " cells");
    }
}
