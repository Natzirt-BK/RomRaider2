/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu;

import com.romraider.Settings;

/** Owner-local fallback; invalid catalogs must not overwrite the recovery profile. */
final class SwingLoggerDefinitionAvailability {
    private Boolean previousExternalOnly;

    void unavailable(Settings settings) {
        if (previousExternalOnly == null) previousExternalOnly = settings.isLogExternalsOnly();
        settings.setLogExternalsOnly(true);
        settings.setFileLoggingControllerSwitchActive(false);
    }

    void available(Settings settings) {
        if (previousExternalOnly != null) {
            settings.setLogExternalsOnly(previousExternalOnly);
            previousExternalOnly = null;
        }
    }

    boolean canBackupProfile() { return previousExternalOnly == null; }

    /** Only after the controller has stopped; retain the failed-profile guard. */
    void restorePreferenceAfterStop(Settings settings) {
        if (previousExternalOnly != null) settings.setLogExternalsOnly(previousExternalOnly);
    }
}
