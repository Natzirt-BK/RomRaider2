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
    private final int valueFractionDigits;
    private final int rowFractionDigits;
    private final int columnFractionDigits;
    private final String rowAxisName;
    private final String rowAxisUnit;
    private final String columnAxisName;
    private final String columnAxisUnit;
    private final List<String> rowLabels;
    private final List<String> columnLabels;
    private final List<CalibrationCellSnapshot> cells;
    private final int changedAxisValueCount;

    CalibrationGridSnapshot(String tableName, String tableType, String unit,
            int rows, int columns, int valueFractionDigits,
            int rowFractionDigits, int columnFractionDigits,
            String rowAxisName, String rowAxisUnit,
            String columnAxisName, String columnAxisUnit,
            List<String> rowLabels,
            List<String> columnLabels, List<CalibrationCellSnapshot> cells,
            int changedAxisValueCount) {
        this.tableName = tableName;
        this.tableType = tableType;
        this.unit = unit;
        this.rows = rows;
        this.columns = columns;
        this.valueFractionDigits = valueFractionDigits;
        this.rowFractionDigits = rowFractionDigits;
        this.columnFractionDigits = columnFractionDigits;
        this.rowAxisName = normalize(rowAxisName);
        this.rowAxisUnit = normalize(rowAxisUnit);
        this.columnAxisName = normalize(columnAxisName);
        this.columnAxisUnit = normalize(columnAxisUnit);
        this.rowLabels = immutable(rowLabels);
        this.columnLabels = immutable(columnLabels);
        this.cells = Collections.unmodifiableList(
                new ArrayList<CalibrationCellSnapshot>(cells));
        this.changedAxisValueCount = changedAxisValueCount;
    }

    public String getTableName() { return tableName; }
    public String getTableType() { return tableType; }
    public String getUnit() { return unit; }
    public int getRows() { return rows; }
    public int getColumns() { return columns; }
    public int getValueFractionDigits() { return valueFractionDigits; }
    public int getRowFractionDigits() { return rowFractionDigits; }
    public int getColumnFractionDigits() { return columnFractionDigits; }
    public String getRowAxisName() { return rowAxisName; }
    public String getRowAxisUnit() { return rowAxisUnit; }
    public String getColumnAxisName() { return columnAxisName; }
    public String getColumnAxisUnit() { return columnAxisUnit; }
    public List<String> getRowLabels() { return rowLabels; }
    public List<String> getColumnLabels() { return columnLabels; }
    public List<CalibrationCellSnapshot> getCells() { return cells; }
    public int getChangedAxisValueCount() { return changedAxisValueCount; }

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

    public int getChangedValueCount() {
        return getChangedCellCount() + changedAxisValueCount;
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
