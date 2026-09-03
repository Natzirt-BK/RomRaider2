/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

/** A rejected calibration command with a user-readable explanation. */
public final class CalibrationEditException extends Exception {
    private static final long serialVersionUID = 1L;

    public CalibrationEditException(String message) {
        super(message);
    }

    CalibrationEditException(String message, Throwable cause) {
        super(message, cause);
    }
}
