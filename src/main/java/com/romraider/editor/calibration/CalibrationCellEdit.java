/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** One requested value in a grouped calibration edit. */
public final class CalibrationCellEdit {
    private final int row;
    private final int column;
    private final String value;

    public CalibrationCellEdit(int row, int column, String value) {
        this.row = row;
        this.column = column;
        this.value = value;
    }

    public int getRow() { return row; }
    public int getColumn() { return column; }
    public String getValue() { return value; }
}
