/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.desktop;

import static org.apache.log4j.Logger.getLogger;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.apache.log4j.Logger;

/** Discovers the replacement desktop shell without linking a UI toolkit to Ant. */
public final class DesktopApplicationLoader {
    private static final Logger LOGGER = getLogger(DesktopApplicationLoader.class);

    private DesktopApplicationLoader() { }

    public static boolean launch(String[] arguments) {
        String[] safeArguments = arguments == null ? new String[0]
                : arguments.clone();
        try {
            for (DesktopApplicationProvider provider :
                    ServiceLoader.load(DesktopApplicationProvider.class)) {
                if (!provider.supports(safeArguments)) continue;
                LOGGER.info("Launching desktop shell: " + provider.getName());
                provider.launch(safeArguments);
                return true;
            }
        } catch (ServiceConfigurationError | RuntimeException
                | LinkageError failure) {
            LOGGER.error("Replacement desktop shell is unavailable", failure);
        }
        return false;
    }
}
