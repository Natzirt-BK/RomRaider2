/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import java.util.ArrayList;
import java.util.List;

import com.romraider.maps.DataCell;
import com.romraider.maps.Table;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table3D;
import com.romraider.util.NumberUtil;

/** Projects the existing ROM model without constructing or retaining a view. */
public final class CalibrationGridProjectionService {
    private CalibrationGridProjectionService() { }

    public static CalibrationGridSnapshot project(Table table) {
        if (table == null) {
            throw new IllegalArgumentException("A calibration table is required");
        }
        if (table instanceof Table3D) return project3d((Table3D) table);
        return projectLine(table);
    }

    private static CalibrationGridSnapshot project3d(Table3D table) {
        int rows = table.getSizeY();
        int columns = table.getSizeX();
        DataCell[][] values = table.get3dData();
        List<CalibrationCellSnapshot> cells =
                new ArrayList<CalibrationCellSnapshot>(rows * columns);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                cells.add(cell(row, column, values[column][row]));
            }
        }
        return snapshot(table, rows, columns,
                labels(table.getYAxis().getData(), rows),
                labels(table.getXAxis().getData(), columns), cells);
    }

    private static CalibrationGridSnapshot projectLine(Table table) {
        DataCell[] values = table.getData();
        int columns = values == null ? 0 : values.length;
        List<CalibrationCellSnapshot> cells =
                new ArrayList<CalibrationCellSnapshot>(columns);
        for (int column = 0; column < columns; column++) {
            cells.add(cell(0, column, values[column]));
        }
        List<String> columnLabels;
        if (table instanceof Table2D) {
            columnLabels = labels(((Table2D) table).getAxis().getData(), columns);
        } else {
            columnLabels = ordinalLabels(columns);
        }
        List<String> rowLabels = new ArrayList<String>();
        rowLabels.add("");
        return snapshot(table, 1, columns, rowLabels, columnLabels, cells);
    }

    private static CalibrationGridSnapshot snapshot(Table table, int rows,
            int columns, List<String> rowLabels, List<String> columnLabels,
            List<CalibrationCellSnapshot> cells) {
        String unit = table.getCurrentScale() == null
                ? "" : table.getCurrentScale().getUnit();
        return new CalibrationGridSnapshot(table.getName(),
                table.getType().name(), unit == null ? "" : unit,
                rows, columns, rowLabels, columnLabels, cells);
    }

    private static CalibrationCellSnapshot cell(int row, int column,
            DataCell cell) {
        if (cell == null) {
            return new CalibrationCellSnapshot(row, column, "", 0.0, 0.0);
        }
        String display = cell.getStaticText() == null
                ? NumberUtil.stringValue(cell.getRealValue())
                : cell.getStaticText();
        return new CalibrationCellSnapshot(row, column, display,
                cell.getBinValue(), cell.getOriginalValue());
    }

    private static List<String> labels(DataCell[] cells, int expected) {
        if (cells == null || cells.length != expected) return ordinalLabels(expected);
        List<String> labels = new ArrayList<String>(expected);
        for (DataCell cell : cells) {
            labels.add(cell == null ? "" : cell.getStaticText() == null
                    ? NumberUtil.stringValue(cell.getRealValue())
                    : cell.getStaticText());
        }
        return labels;
    }

    private static List<String> ordinalLabels(int count) {
        List<String> labels = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            labels.add(Integer.toString(index));
        }
        return labels;
    }
}
