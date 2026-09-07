/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu;

import com.romraider.Settings;
import org.junit.Test;
import static org.junit.Assert.*;

public class SwingLoggerDefinitionAvailabilityTest {
    @Test public void failureBlocksEcuPollingAndBackupWithoutForgettingOriginalMode() {
        for (boolean externalOnly : new boolean[] {false, true}) {
            Settings settings = new Settings();
            settings.setLogExternalsOnly(externalOnly);
            settings.setFileLoggingControllerSwitchActive(true);
            SwingLoggerDefinitionAvailability state = new SwingLoggerDefinitionAvailability();
            assertTrue(state.canBackupProfile());
            state.unavailable(settings);
            state.unavailable(settings);
            assertTrue(settings.isLogExternalsOnly());
            assertFalse(settings.isFileLoggingControllerSwitchActive());
            assertFalse(state.canBackupProfile());
            state.available(settings);
            assertEquals(externalOnly, settings.isLogExternalsOnly());
            assertTrue(state.canBackupProfile());
            assertFalse(settings.isFileLoggingControllerSwitchActive());
        }
    }

    @Test public void healthyLoadsDoNotOverrideModeAndEachFailureCapturesFreshIntent() {
        Settings settings = new Settings();
        SwingLoggerDefinitionAvailability state = new SwingLoggerDefinitionAvailability();
        settings.setLogExternalsOnly(false);
        state.available(settings);
        assertFalse(settings.isLogExternalsOnly());
        state.unavailable(settings);
        state.available(settings);
        settings.setLogExternalsOnly(true);
        state.unavailable(settings);
        state.available(settings);
        assertTrue(settings.isLogExternalsOnly());
    }

    @Test public void stoppedOwnerRestoresPreferenceWithoutOverwritingRecoveryProfile() {
        Settings settings = new Settings();
        settings.setLogExternalsOnly(false);
        SwingLoggerDefinitionAvailability state = new SwingLoggerDefinitionAvailability();
        state.unavailable(settings);
        state.restorePreferenceAfterStop(settings);
        assertFalse(settings.isLogExternalsOnly());
        assertFalse(state.canBackupProfile());
    }
}
