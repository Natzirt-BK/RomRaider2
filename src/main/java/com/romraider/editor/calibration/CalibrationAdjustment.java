/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** Definition-backed single-cell increment commands. */
public enum CalibrationAdjustment {
    FINE_DECREASE(false, -1),
    FINE_INCREASE(false, 1),
    COARSE_DECREASE(true, -1),
    COARSE_INCREASE(true, 1);

    private final boolean coarse;
    private final int direction;

    CalibrationAdjustment(boolean coarse, int direction) {
        this.coarse = coarse;
        this.direction = direction;
    }

    public boolean isCoarse() { return coarse; }
    public int getDirection() { return direction; }
}
