/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.spi;

import javax.swing.JComponent;

/** Optional desktop-toolkit provider loaded by the Swing compatibility host. */
public interface LoggerWorkspaceProvider {
    String getName();
    JComponent createWorkspace(LoggerWorkspaceContext context);
}
