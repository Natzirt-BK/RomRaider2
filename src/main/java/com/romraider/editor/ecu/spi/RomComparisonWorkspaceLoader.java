/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import static org.apache.log4j.Logger.getLogger;

import java.util.List;
import java.util.ServiceLoader;

import javax.swing.JComponent;

import org.apache.log4j.Logger;

import com.romraider.maps.Rom;

/** Discovers a replacement comparison workspace without linking Compose to Ant. */
public final class RomComparisonWorkspaceLoader {
    private static final Logger LOGGER = getLogger(
            RomComparisonWorkspaceLoader.class);

    private RomComparisonWorkspaceLoader() { }

    public static JComponent create(List<Rom> roms,
            RomComparisonWorkspaceContext.Listener listener) {
        RomComparisonWorkspaceContext context =
                new RomComparisonWorkspaceContext(roms, listener);
        try {
            for (RomComparisonWorkspaceProvider provider : ServiceLoader.load(
                    RomComparisonWorkspaceProvider.class)) {
                JComponent workspace = provider.createWorkspace(context);
                if (workspace != null) {
                    workspace.setName("ROM COMPARISON REPLACEMENT WORKSPACE");
                    LOGGER.info("Loaded ROM comparison workspace: "
                            + provider.getName());
                    return workspace;
                }
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Replacement ROM comparison workspace is unavailable",
                    failure);
        } catch (LinkageError failure) {
            LOGGER.warn("Replacement ROM comparison workspace could not be linked",
                    failure);
        }
        return null;
    }
}
