/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.spi;

import static org.apache.log4j.Logger.getLogger;

import java.util.ServiceLoader;

import javax.swing.JComponent;

import org.apache.log4j.Logger;

/** Discovers the packaged replacement Logger without linking it into Ant. */
public final class LoggerWorkspaceLoader {
    private static final Logger LOGGER = getLogger(LoggerWorkspaceLoader.class);

    private LoggerWorkspaceLoader() { }

    public static JComponent create(LoggerWorkspaceContext context) {
        try {
            for (LoggerWorkspaceProvider provider
                    : ServiceLoader.load(LoggerWorkspaceProvider.class)) {
                JComponent workspace = provider.createWorkspace(context);
                if (workspace != null) {
                    workspace.setName("LOGGER REPLACEMENT WORKSPACE");
                    LOGGER.info("Loaded Logger workspace: "
                            + provider.getName());
                    return workspace;
                }
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Replacement Logger workspace is unavailable", failure);
        } catch (LinkageError failure) {
            LOGGER.warn("Replacement Logger workspace could not be linked",
                    failure);
        }
        return null;
    }
}
