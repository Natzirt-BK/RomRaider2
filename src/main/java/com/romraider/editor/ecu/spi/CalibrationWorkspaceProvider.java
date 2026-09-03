/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import javax.swing.JComponent;

/** Optional replacement calibration surface loaded by the desktop host. */
public interface CalibrationWorkspaceProvider {
    String getName();
    JComponent createWorkspace(CalibrationWorkspaceContext context);
}
