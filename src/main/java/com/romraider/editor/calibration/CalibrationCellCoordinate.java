/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** Immutable row/column address used by multi-cell editor commands. */
public final class CalibrationCellCoordinate {
    private final int row;
    private final int column;

    public CalibrationCellCoordinate(int row, int column) {
        this.row = row;
        this.column = column;
    }

    public int getRow() { return row; }
    public int getColumn() { return column; }
}
