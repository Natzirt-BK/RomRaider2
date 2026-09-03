/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import static org.apache.log4j.Logger.getLogger;

import java.util.ServiceLoader;

import org.apache.log4j.Logger;

/** Discovers replacement navigation without making the Ant core link Compose. */
public final class EditorNavigationWorkspaceLoader {
    private static final Logger LOGGER = getLogger(
            EditorNavigationWorkspaceLoader.class);

    private EditorNavigationWorkspaceLoader() { }

    public static EditorNavigationWorkspace create(
            EditorNavigationWorkspaceContext context) {
        try {
            for (EditorNavigationWorkspaceProvider provider :
                    ServiceLoader.load(EditorNavigationWorkspaceProvider.class)) {
                EditorNavigationWorkspace workspace =
                        provider.createWorkspace(context);
                if (workspace != null && workspace.getComponent() != null) {
                    workspace.getComponent().setName(
                            "EDITOR NAVIGATION REPLACEMENT WORKSPACE");
                    LOGGER.info("Loaded editor navigation workspace: "
                            + provider.getName());
                    return workspace;
                }
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Replacement editor navigation is unavailable", failure);
        } catch (LinkageError failure) {
            LOGGER.warn("Replacement editor navigation could not be linked",
                    failure);
        }
        return null;
    }
}
