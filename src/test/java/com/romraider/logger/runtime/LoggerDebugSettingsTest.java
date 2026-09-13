package com.romraider.logger.runtime;

import static org.junit.Assert.*;
import com.romraider.Settings;
import com.romraider.logger.runtime.LoggerDebugSettings.Verbosity;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Test;

public class LoggerDebugSettingsTest {
    @Test public void normalizesPersistedLevelsAndUsesNormalForUnknownValues() {
        Settings settings = new Settings();
        for (String value : new String[] {null, "", "invalid", "OFF"}) {
            settings.setLoggerDebuggingLevel(value);
            assertEquals(Verbosity.INFO, LoggerDebugSettings.current(settings));
        }
        settings.setLoggerDebuggingLevel(" trace ");
        assertEquals(Verbosity.TRACE, LoggerDebugSettings.current(settings));
    }

    @Test public void appliesAndInitializesEachSupportedLevel() {
        Settings settings = new Settings();
        Level original = LogManager.getRootLogger().getLevel();
        try {
            for (Verbosity value : Verbosity.values()) {
                LoggerDebugSettings.apply(settings, value, () -> {});
                assertEquals(value.name().toLowerCase(java.util.Locale.ROOT), settings.getLoggerDebuggingLevel());
                assertEquals(value.name(), LogManager.getRootLogger().getLevel().name());
                Configurator.setRootLevel(Level.ERROR);
                LoggerDebugSettings.initialize(settings);
                assertEquals(value.name(), LogManager.getRootLogger().getLevel().name());
            }
        } finally { Configurator.setRootLevel(original); }
    }

    @Test public void failedSaveRestoresPreferenceAndActualLevel() {
        Settings settings = new Settings(); settings.setLoggerDebuggingLevel("info");
        Level original = LogManager.getRootLogger().getLevel();
        try {
            Configurator.setRootLevel(Level.WARN);
            try {
                LoggerDebugSettings.apply(settings, Verbosity.TRACE, () -> { throw new IllegalStateException("Synthetic failed save"); });
                fail("Save must fail");
            } catch (IllegalStateException expected) { assertEquals("Synthetic failed save", expected.getMessage()); }
            assertEquals("info", settings.getLoggerDebuggingLevel());
            assertEquals(Level.WARN, LogManager.getRootLogger().getLevel());
        } finally { Configurator.setRootLevel(original); }
    }
}
