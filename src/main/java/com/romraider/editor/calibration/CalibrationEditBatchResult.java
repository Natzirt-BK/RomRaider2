/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** Result of one grouped calibration operation and undo step. */
public final class CalibrationEditBatchResult {
    private final int requestedCellCount;
    private final int changedCellCount;
    private final CalibrationGridSnapshot snapshot;

    public CalibrationEditBatchResult(int requestedCellCount,
            int changedCellCount, CalibrationGridSnapshot snapshot) {
        this.requestedCellCount = requestedCellCount;
        this.changedCellCount = changedCellCount;
        this.snapshot = snapshot;
    }

    public int getRequestedCellCount() { return requestedCellCount; }
    public int getChangedCellCount() { return changedCellCount; }
    public boolean isChanged() { return changedCellCount > 0; }
    public CalibrationGridSnapshot getSnapshot() { return snapshot; }
}
