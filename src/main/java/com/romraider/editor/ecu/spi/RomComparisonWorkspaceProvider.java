/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import javax.swing.JComponent;

/** Optional replacement ROM comparison surface loaded by the desktop host. */
public interface RomComparisonWorkspaceProvider {
    String getName();
    JComponent createWorkspace(RomComparisonWorkspaceContext context);
}
