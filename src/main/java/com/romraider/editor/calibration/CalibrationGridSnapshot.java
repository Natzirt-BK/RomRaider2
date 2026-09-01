/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Toolkit-neutral, immutable view of one calibration table revision. */
public final class CalibrationGridSnapshot {
    private final String tableName;
    private final String tableType;
    private final String unit;
    private final int rows;
    private final int columns;
    private final List<String> rowLabels;
    private final List<String> columnLabels;
    private final List<CalibrationCellSnapshot> cells;

    CalibrationGridSnapshot(String tableName, String tableType, String unit,
            int rows, int columns, List<String> rowLabels,
            List<String> columnLabels, List<CalibrationCellSnapshot> cells) {
        this.tableName = tableName;
        this.tableType = tableType;
        this.unit = unit;
        this.rows = rows;
        this.columns = columns;
        this.rowLabels = immutable(rowLabels);
        this.columnLabels = immutable(columnLabels);
        this.cells = Collections.unmodifiableList(
                new ArrayList<CalibrationCellSnapshot>(cells));
    }

    public String getTableName() { return tableName; }
    public String getTableType() { return tableType; }
    public String getUnit() { return unit; }
    public int getRows() { return rows; }
    public int getColumns() { return columns; }
    public List<String> getRowLabels() { return rowLabels; }
    public List<String> getColumnLabels() { return columnLabels; }
    public List<CalibrationCellSnapshot> getCells() { return cells; }

    public CalibrationCellSnapshot cellAt(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IndexOutOfBoundsException(
                    "Calibration cell " + row + "," + column);
        }
        return cells.get(row * columns + column);
    }

    public int getChangedCellCount() {
        int count = 0;
        for (CalibrationCellSnapshot cell : cells) {
            if (cell.isChanged()) count++;
        }
        return count;
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
