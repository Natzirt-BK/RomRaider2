/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

/** Immutable changed-cell count for one calibration table. */
public final class TableChangeSummary {
    private final String tableName;
    private final int changedCells;

    public TableChangeSummary(String tableName, int changedCells) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("table name is required");
        }
        if (changedCells <= 0) {
            throw new IllegalArgumentException("changed cells must be positive");
        }
        this.tableName = tableName.trim();
        this.changedCells = changedCells;
    }

    public String getTableName() { return tableName; }
    public int getChangedCells() { return changedCells; }
}
