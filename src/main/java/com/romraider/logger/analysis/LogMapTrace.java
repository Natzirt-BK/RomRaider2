/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import com.romraider.maps.*;
import java.util.*;

/** Frozen, read-only displayed-axis geometry. Not an ECU lookup algorithm. */
public final class LogMapTrace {
    public static final int MAX_AXIS = 256, MAX_CELLS = 8192;
    public enum State { COVERED, MISSING, OUT_OF_RANGE }
    public record Neighbor(int row, int column, double weight) { }
    public record Point(State state, List<Neighbor> neighbors) {
        public Point { neighbors = List.copyOf(neighbors); }
    }
    private record Bracket(int first, int second, double weight) { }
    private final String name, units, xName, xUnits, yName, yUnits;
    private final boolean surface;
    private final double[] x, y;
    private final double[][] values;

    private LogMapTrace(Table table, Table1D xAxis, Table1D yAxis, int columns, int rows) {
        if (xAxis == null || (table instanceof Table3D && yAxis == null)) throw new IllegalArgumentException("Explicit numeric axes are required.");
        if (columns < 1 || rows < 1 || columns > MAX_AXIS || rows > MAX_AXIS || (long) columns * rows > MAX_CELLS)
            throw new IllegalArgumentException("Map tracing supports up to 256 breakpoints per axis and 8,192 displayed cells.");
        surface = yAxis != null; name = table.getName(); units = units(table);
        xName = xAxis.getName(); xUnits = units(xAxis);
        yName = surface ? yAxis.getName() : ""; yUnits = surface ? units(yAxis) : "";
        x = axis(xAxis, columns); y = surface ? axis(yAxis, rows) : new double[] {0};
        if (table instanceof Table3D table3d) {
            if (table3d.get3dData() == null || table3d.get3dData().length != columns) throw new IllegalArgumentException("Displayed table dimensions are inconsistent.");
            for (DataCell[] column : table3d.get3dData())
                if (column == null || column.length != rows) throw new IllegalArgumentException("Displayed table dimensions are inconsistent.");
        } else if (table.getData() == null || table.getData().length != columns) throw new IllegalArgumentException("Displayed table dimensions are inconsistent.");
        values = new double[rows][columns];
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            DataCell cell = table instanceof Table3D ? ((Table3D) table).get3dData()[column][row] : table.getDataCell(column);
            if (cell == null) throw new IllegalArgumentException("The displayed table is not fully loaded.");
            // Do not invent numeric values for switches, strings or missing cells.
            values[row][column] = cell.getTable().isStaticDataTable() ? numericStatic(cell) : cell.getRealValue();
        }
    }
    /** Call on the editor's serialized command/UI thread; no live table references are retained. */
    public static LogMapTrace capture(Table table) {
        if (table instanceof Table3D surface)
            return new LogMapTrace(table, surface.getXAxis(), surface.getYAxis(), surface.getSizeX(), surface.getSizeY());
        if (table instanceof Table2D curve)
            return new LogMapTrace(table, curve.getAxis(), null, curve.getDataSize(), 1);
        throw new IllegalArgumentException("Select an open numeric 2D curve or 3D table with explicit axes.");
    }
    private static double[] axis(Table1D axis, int size) {
        if (axis == null || axis.getData() == null || axis.getData().length != size)
            throw new IllegalArgumentException("Axis size does not match the displayed table.");
        double[] values = new double[size];
        for (int index = 0; index < size; index++) {
            DataCell cell = axis.getDataCell(index);
            if (cell == null) throw new IllegalArgumentException("The axis is not fully loaded.");
            values[index] = axis.isStaticDataTable() ? numericStatic(cell) : cell.getRealValue();
            if (!Double.isFinite(values[index])) throw new IllegalArgumentException("Trace axes must contain finite numeric breakpoints.");
        }
        if (size > 1) {
            boolean ascending = values[1] > values[0];
            for (int index = 1; index < size; index++)
                if (ascending ? values[index] <= values[index - 1] : values[index] >= values[index - 1])
                    throw new IllegalArgumentException("Trace axes must be strictly increasing or decreasing; duplicates and folded axes are ambiguous.");
        }
        return values;
    }
    private static double numericStatic(DataCell cell) {
        try { return Double.parseDouble(cell.getStaticText().trim()); }
        catch (RuntimeException failure) { return Double.NaN; }
    }
    private static String units(Table table) {
        return table.getCurrentScale() == null || table.getCurrentScale().getUnit() == null ? "" : table.getCurrentScale().getUnit();
    }
    public String name() { return name; }
    public String units() { return units; }
    public String xName() { return xName; }
    public String xUnits() { return xUnits; }
    public String yName() { return yName; }
    public String yUnits() { return yUnits; }
    public boolean isSurface() { return surface; }
    public int columns() { return x.length; }
    public int rows() { return y.length; }
    public double xAt(int column) { return x[column]; }
    public double yAt(int row) { return y[row]; }
    public double valueAt(int row, int column) { return values[row][column]; }

    /** No clamping, extrapolation, implicit unit conversion or guessed ECU addressing. */
    public Point locate(double horizontal, double vertical) {
        if (!Double.isFinite(horizontal) || (surface && !Double.isFinite(vertical))) return new Point(State.MISSING, List.of());
        Bracket bx = bracket(x, horizontal), by = surface ? bracket(y, vertical) : new Bracket(0, 0, 0);
        if (bx == null || by == null) return new Point(State.OUT_OF_RANGE, List.of());
        List<Neighbor> result = new ArrayList<>();
        add(result, by.first, bx.first, (1 - by.weight) * (1 - bx.weight));
        if (bx.second != bx.first) add(result, by.first, bx.second, (1 - by.weight) * bx.weight);
        if (by.second != by.first) {
            add(result, by.second, bx.first, by.weight * (1 - bx.weight));
            if (bx.second != bx.first) add(result, by.second, bx.second, by.weight * bx.weight);
        }
        return new Point(State.COVERED, result);
    }
    private static void add(List<Neighbor> result, int row, int column, double weight) {
        if (weight > 0) result.add(new Neighbor(row, column, weight));
    }
    private static Bracket bracket(double[] axis, double value) {
        if (value < Math.min(axis[0], axis[axis.length - 1]) || value > Math.max(axis[0], axis[axis.length - 1])) return null;
        for (int index = 0; index < axis.length; index++) {
            if (value == axis[index]) return new Bracket(index, index, 0);
            if (index + 1 < axis.length && value > Math.min(axis[index], axis[index + 1]) && value < Math.max(axis[index], axis[index + 1])) {
                double a = axis[index], b = axis[index + 1], difference = b - a;
                double weight = Double.isFinite(difference) ? (value - a) / difference
                        : (value / 2 - a / 2) / (b / 2 - a / 2);
                return new Bracket(index, index + 1, Math.max(0, Math.min(1, weight)));
            }
        }
        return null;
    }
}
