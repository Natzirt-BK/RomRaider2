/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Test;

/** Verifies the maintained backend and temporary Log4j 1 API bridge. */
public final class LoggingRuntimeTest {
    @Test
    public void maintainedLog4jBackendIsLoaded() {
        assertEquals("2.26.1", org.apache.logging.log4j.LogManager.class
                .getPackage().getImplementationVersion());
        assertEquals("2.26.1", org.apache.logging.log4j.core.Logger.class
                .getPackage().getImplementationVersion());

        Object context = org.apache.logging.log4j.LogManager.getContext(false);
        assertTrue(context instanceof LoggerContext);
    }

    @Test
    public void defaultConfigurationProvidesConsoleAndRollingFile() {
        LoggerContext context = (LoggerContext)
                org.apache.logging.log4j.LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();

        assertTrue(configuration.getAppenders().containsKey("console"));
        assertTrue(configuration.getAppenders().containsKey("file"));
    }

    @Test
    public void legacyApiCallsReachTheLog4j2Backend() {
        String loggerName = getClass().getName() + ".legacy";
        org.apache.log4j.Logger legacy = org.apache.log4j.Logger.getLogger(loggerName);

        Configurator.setLevel(loggerName, Level.DEBUG);
        assertTrue(legacy.isDebugEnabled());
        Configurator.setLevel(loggerName, Level.INFO);
        assertFalse(legacy.isDebugEnabled());
    }
}
