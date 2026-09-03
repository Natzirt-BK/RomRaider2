/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** Result of a value command after scaling, rounding, and range checks. */
public final class CalibrationEditResult {
    private final int row;
    private final int column;
    private final String previousValue;
    private final String currentValue;
    private final boolean changed;
    private final CalibrationGridSnapshot snapshot;

    CalibrationEditResult(int row, int column, String previousValue,
            String currentValue, boolean changed,
            CalibrationGridSnapshot snapshot) {
        this.row = row;
        this.column = column;
        this.previousValue = previousValue;
        this.currentValue = currentValue;
        this.changed = changed;
        this.snapshot = snapshot;
    }

    public int getRow() { return row; }
    public int getColumn() { return column; }
    public String getPreviousValue() { return previousValue; }
    public String getCurrentValue() { return currentValue; }
    public boolean isChanged() { return changed; }
    public CalibrationGridSnapshot getSnapshot() { return snapshot; }
}
