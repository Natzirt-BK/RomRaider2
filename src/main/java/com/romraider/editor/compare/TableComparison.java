/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.compare;

public final class TableComparison implements Comparable<TableComparison> {
    private final String tableName;
    private final TableComparisonStatus status;

    TableComparison(String tableName, TableComparisonStatus status) {
        this.tableName = tableName == null ? "" : tableName;
        this.status = status;
    }

    public String getTableName() { return tableName; }
    public TableComparisonStatus getStatus() { return status; }
    public boolean isAvailableInBoth() {
        return status == TableComparisonStatus.EQUAL
                || status == TableComparisonStatus.DIFFERENT;
    }

    public int compareTo(TableComparison other) {
        int statusOrder = status.compareTo(other.status);
        return statusOrder != 0 ? statusOrder
                : tableName.compareToIgnoreCase(other.tableName);
    }
}
