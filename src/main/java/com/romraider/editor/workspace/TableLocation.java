/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import java.io.Serializable;

public final class TableLocation implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String romId;
    private final String tableName;

    public TableLocation(String romId, String tableName) {
        if (romId == null || romId.trim().isEmpty()
                || tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("ROM identity and table name are required");
        }
        this.romId = romId.trim();
        this.tableName = tableName.trim();
    }

    public String getRomId() {
        return romId;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof TableLocation)) return false;
        TableLocation other = (TableLocation) value;
        return romId.equals(other.romId) && tableName.equals(other.tableName);
    }

    @Override
    public int hashCode() {
        return (31 * romId.hashCode()) + tableName.hashCode();
    }

    @Override
    public String toString() {
        return romId + " / " + tableName;
    }
}
