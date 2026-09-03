/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** Receives a fresh immutable grid after this table's edit history changes. */
public interface CalibrationEditListener {
    void calibrationChanged(CalibrationGridSnapshot snapshot);
}
