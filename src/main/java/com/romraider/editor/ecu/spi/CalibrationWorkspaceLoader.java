/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import static org.apache.log4j.Logger.getLogger;

import java.util.ServiceLoader;

import javax.swing.JComponent;

import org.apache.log4j.Logger;

import com.romraider.editor.calibration.CalibrationGridProjectionService;
import com.romraider.editor.calibration.CalibrationEditController;
import com.romraider.editor.calibration.TableCalibrationEditController;
import com.romraider.maps.Table;

/** Discovers an optional calibration workspace without linking Compose to Ant. */
public final class CalibrationWorkspaceLoader {
    private static final Logger LOGGER = getLogger(
            CalibrationWorkspaceLoader.class);

    private CalibrationWorkspaceLoader() {
    }

    public static JComponent create(Table table) {
        if (table == null) return null;
        CalibrationEditController editController = null;
        try {
            editController = new TableCalibrationEditController(table);
            CalibrationWorkspaceContext context =
                    new CalibrationWorkspaceContext(
                            CalibrationGridProjectionService.project(table),
                            editController, table.getRom(), table);
            for (CalibrationWorkspaceProvider provider : ServiceLoader.load(
                    CalibrationWorkspaceProvider.class)) {
                JComponent workspace = provider.createWorkspace(context);
                if (workspace != null) {
                    workspace.setName("CALIBRATION REPLACEMENT WORKSPACE");
                    workspace.putClientProperty(
                            CalibrationEditController.class.getName(),
                            editController);
                    LOGGER.info("Loaded calibration workspace: "
                            + provider.getName());
                    return workspace;
                }
            }
            editController.close();
            editController = null;
        } catch (RuntimeException failure) {
            LOGGER.warn("Replacement calibration workspace is unavailable",
                    failure);
        } catch (LinkageError failure) {
            LOGGER.warn("Replacement calibration workspace could not be linked",
                    failure);
        }
        if (editController != null) editController.close();
        return null;
    }

    public static void dispose(JComponent workspace) {
        if (workspace == null) return;
        Object controller = workspace.getClientProperty(
                CalibrationEditController.class.getName());
        if (controller instanceof CalibrationEditController) {
            ((CalibrationEditController) controller).close();
            workspace.putClientProperty(
                    CalibrationEditController.class.getName(), null);
        }
    }
}
