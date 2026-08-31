/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

/** Immutable selection context shared by editor controls and future clients. */
public final class TableSelectionSummary {
    private final int selectedCells;
    private final int changedCells;
    private final double minimum;
    private final double maximum;

    private TableSelectionSummary(int selectedCells, int changedCells,
            double minimum, double maximum) {
        this.selectedCells = selectedCells;
        this.changedCells = changedCells;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static TableSelectionSummary of(Table table) {
        Accumulator values = new Accumulator();
        if (table instanceof Table3D) {
            Table3D surface = (Table3D) table;
            add(values, surface.get3dData());
            add(values, surface.getXAxis() == null
                    ? null : surface.getXAxis().getData());
            add(values, surface.getYAxis() == null
                    ? null : surface.getYAxis().getData());
        } else {
            add(values, table == null ? null : table.getData());
            if (table instanceof Table2D) {
                Table1D axis = ((Table2D) table).getAxis();
                add(values, axis == null ? null : axis.getData());
            }
        }
        return new TableSelectionSummary(values.count, values.changed,
                values.minimum, values.maximum);
    }

    public int getSelectedCells() {
        return selectedCells;
    }

    public boolean hasSelection() {
        return selectedCells > 0;
    }

    public int getChangedCells() {
        return changedCells;
    }

    public boolean hasChangedSelection() {
        return changedCells > 0;
    }

    public double getMinimum() {
        return minimum;
    }

    public double getMaximum() {
        return maximum;
    }

    private static void add(Accumulator values, DataCell[][] cells) {
        if (cells == null) return;
        for (DataCell[] row : cells) add(values, row);
    }

    private static void add(Accumulator values, DataCell[] cells) {
        if (cells == null) return;
        for (DataCell cell : cells) {
            if (cell == null || !cell.isSelected()) continue;
            double value = cell.getRealValue();
            if (values.count == 0) {
                values.minimum = value;
                values.maximum = value;
            } else {
                values.minimum = Math.min(values.minimum, value);
                values.maximum = Math.max(values.maximum, value);
            }
            if (Double.compare(cell.getBinValue(),
                    cell.getOriginalValue()) != 0) values.changed++;
            values.count++;
        }
    }

    private static final class Accumulator {
        private int count;
        private int changed;
        private double minimum;
        private double maximum;
    }
}
