/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.external.core;

import javax.swing.Action;

import com.romraider.logger.ecu.EcuLogger;

/** Optional menu integration used only by the legacy Swing Logger shell. */
public interface SwingExternalDataSource extends ExternalDataSource {
    Action getMenuAction(EcuLogger logger);
}
