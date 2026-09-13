/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import com.romraider.Settings;
import java.util.Locale;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

/** Application diagnostic verbosity; unrelated to vehicle CSV recording. */
public final class LoggerDebugSettings {
    public enum Verbosity {
        INFO("Normal"), DEBUG("Detailed"), TRACE("Trace");
        private final String label;
        Verbosity(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private LoggerDebugSettings() { }

    public static Verbosity current(Settings settings) {
        String value = settings.getLoggerDebuggingLevel();
        try { return Verbosity.valueOf(value == null ? "INFO" : value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return Verbosity.INFO; }
    }

    public static void initialize(Settings settings) {
        Configurator.setRootLevel(Level.valueOf(current(settings).name()));
    }

    /** Restore the effective level and preference if saving fails. */
    public static void apply(Settings settings, Verbosity verbosity, Runnable persist) {
        Objects.requireNonNull(verbosity);
        Objects.requireNonNull(persist);
        String before = settings.getLoggerDebuggingLevel();
        Level previousLevel = LogManager.getRootLogger().getLevel();
        try {
            Configurator.setRootLevel(Level.valueOf(verbosity.name()));
            settings.setLoggerDebuggingLevel(verbosity.name().toLowerCase(Locale.ROOT));
            persist.run();
        } catch (RuntimeException failure) {
            settings.setLoggerDebuggingLevel(before);
            try { Configurator.setRootLevel(previousLevel); }
            catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
}
