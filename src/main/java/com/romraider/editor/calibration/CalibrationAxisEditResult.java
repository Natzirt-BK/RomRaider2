/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** Result of one validated, undoable calibration-axis edit. */
public final class CalibrationAxisEditResult {
    private final CalibrationAxis axis;
    private final int index;
    private final String previousValue;
    private final String currentValue;
    private final boolean changed;
    private final CalibrationGridSnapshot snapshot;

    public CalibrationAxisEditResult(CalibrationAxis axis, int index,
            String previousValue, String currentValue, boolean changed,
            CalibrationGridSnapshot snapshot) {
        this.axis = axis;
        this.index = index;
        this.previousValue = previousValue;
        this.currentValue = currentValue;
        this.changed = changed;
        this.snapshot = snapshot;
    }

    public CalibrationAxis getAxis() { return axis; }
    public int getIndex() { return index; }
    public String getPreviousValue() { return previousValue; }
    public String getCurrentValue() { return currentValue; }
    public boolean isChanged() { return changed; }
    public CalibrationGridSnapshot getSnapshot() { return snapshot; }
}
