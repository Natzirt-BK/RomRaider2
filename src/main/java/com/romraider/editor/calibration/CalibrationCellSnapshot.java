/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** One immutable value in a replacement calibration-grid projection. */
public final class CalibrationCellSnapshot {
    private final int row;
    private final int column;
    private final String displayValue;
    private final double rawValue;
    private final double originalRawValue;

    CalibrationCellSnapshot(int row, int column, String displayValue,
            double rawValue, double originalRawValue) {
        this.row = row;
        this.column = column;
        this.displayValue = displayValue;
        this.rawValue = rawValue;
        this.originalRawValue = originalRawValue;
    }

    public int getRow() { return row; }
    public int getColumn() { return column; }
    public String getDisplayValue() { return displayValue; }
    public double getRawValue() { return rawValue; }
    public double getOriginalRawValue() { return originalRawValue; }
    public boolean isChanged() {
        return Double.compare(rawValue, originalRawValue) != 0;
    }
}
