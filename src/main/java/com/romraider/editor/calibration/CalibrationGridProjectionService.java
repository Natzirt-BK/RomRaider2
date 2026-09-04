/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import java.util.ArrayList;
import java.util.List;
import java.text.DecimalFormat;

import com.romraider.maps.DataCell;
import com.romraider.maps.Table;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table3D;
import com.romraider.maps.Scale;
import com.romraider.util.JEPUtil;
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
                cells.add(cell(row, column, values[column][row], table));
            }
        }
        return snapshot(table, rows, columns,
                fractionDigits(table), fractionDigits(table.getYAxis()),
                fractionDigits(table.getXAxis()),
                table.getYAxis().getName(), unit(table.getYAxis()),
                table.getXAxis().getName(), unit(table.getXAxis()),
                labels(table.getYAxis(), table.getYAxis().getData(), rows),
                labels(table.getXAxis(), table.getXAxis().getData(), columns),
                cells,
                changed(table.getYAxis().getData())
                        + changed(table.getXAxis().getData()));
    }

    private static CalibrationGridSnapshot projectLine(Table table) {
        DataCell[] values = table.getData();
        int columns = values == null ? 0 : values.length;
        List<CalibrationCellSnapshot> cells =
                new ArrayList<CalibrationCellSnapshot>(columns);
        for (int column = 0; column < columns; column++) {
            cells.add(cell(0, column, values[column], table));
        }
        List<String> columnLabels;
        if (table instanceof Table2D) {
            Table axis = ((Table2D) table).getAxis();
            columnLabels = labels(axis, axis.getData(), columns);
        } else {
            columnLabels = ordinalLabels(columns);
        }
        List<String> rowLabels = new ArrayList<String>();
        rowLabels.add("");
        int columnFractionDigits = table instanceof Table2D
                ? fractionDigits(((Table2D) table).getAxis()) : 0;
        Table columnAxis = table instanceof Table2D
                ? ((Table2D) table).getAxis() : null;
        return snapshot(table, 1, columns, fractionDigits(table), 0,
                columnFractionDigits, "", "",
                columnAxis == null ? "" : columnAxis.getName(), unit(columnAxis),
                rowLabels, columnLabels, cells,
                columnAxis == null ? 0 : changed(columnAxis.getData()));
    }

    private static CalibrationGridSnapshot snapshot(Table table, int rows,
            int columns, int valueFractionDigits, int rowFractionDigits,
            int columnFractionDigits,
            String rowAxisName, String rowAxisUnit,
            String columnAxisName, String columnAxisUnit,
            List<String> rowLabels,
            List<String> columnLabels,
            List<CalibrationCellSnapshot> cells,
            int changedAxisValueCount) {
        String unit = table.getCurrentScale() == null
                ? "" : table.getCurrentScale().getUnit();
        return new CalibrationGridSnapshot(table.getName(),
                table.getType().name(), unit == null ? "" : unit,
                rows, columns, valueFractionDigits, rowFractionDigits,
                columnFractionDigits, rowAxisName, rowAxisUnit,
                columnAxisName, columnAxisUnit, rowLabels, columnLabels, cells,
                changedAxisValueCount);
    }

    private static String unit(Table table) {
        if (table == null || table.getCurrentScale() == null
                || table.getCurrentScale().getUnit() == null) return "";
        return table.getCurrentScale().getUnit();
    }

    private static int fractionDigits(Table table) {
        if (table == null) return 0;
        Scale scale = table.getCurrentScale();
        if (scale == null || scale.getFormat() == null) return 0;
        try {
            if (new DecimalFormat(scale.getFormat()).getMaximumFractionDigits()
                    == 0) return 0;
        } catch (IllegalArgumentException ignored) {
            return 2;
        }
        String expression = scale.getExpression();
        if (expression == null || expression.trim().isEmpty()) return 0;
        for (int bin = 0; bin <= 4; bin++) {
            double value = JEPUtil.evaluate(expression, bin);
            if (Double.isFinite(value)
                    && Math.abs(value - Math.rint(value)) > 0.0000001) {
                return 2;
            }
        }
        return 0;
    }

    private static CalibrationCellSnapshot cell(int row, int column,
            DataCell cell, Table table) {
        if (cell == null) {
            return new CalibrationCellSnapshot(row, column, "", 0.0,
                    0.0, 0.0);
        }
        String display = cell.getStaticText() == null
                ? displayValue(table, cell.getRealValue())
                : cell.getStaticText();
        return new CalibrationCellSnapshot(row, column, display,
                cell.getRealValue(), cell.getBinValue(),
                cell.getOriginalValue());
    }

    private static List<String> labels(Table table, DataCell[] cells,
            int expected) {
        if (cells == null || cells.length != expected) return ordinalLabels(expected);
        List<String> labels = new ArrayList<String>(expected);
        for (DataCell cell : cells) {
            labels.add(cell == null ? "" : cell.getStaticText() == null
                    ? displayValue(table, cell.getRealValue())
                    : cell.getStaticText());
        }
        return labels;
    }

    private static String displayValue(Table table, double value) {
        Scale scale = table == null ? null : table.getCurrentScale();
        if (scale == null || scale.getFormat() == null
                || "Raw Value".equalsIgnoreCase(scale.getCategory())) {
            return NumberUtil.stringValue(value);
        }
        try {
            return new DecimalFormat(scale.getFormat()).format(value);
        } catch (IllegalArgumentException invalidFormat) {
            return NumberUtil.stringValue(value);
        }
    }

    private static List<String> ordinalLabels(int count) {
        List<String> labels = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            labels.add(Integer.toString(index));
        }
        return labels;
    }

    private static int changed(DataCell[] cells) {
        if (cells == null) return 0;
        int changed = 0;
        for (DataCell cell : cells) {
            if (cell != null && Double.compare(cell.getBinValue(),
                    cell.getOriginalValue()) != 0) changed++;
        }
        return changed;
    }
}
