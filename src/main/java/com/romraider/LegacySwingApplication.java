/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider;

import static com.romraider.Version.PRODUCT_NAME;
import static com.romraider.editor.ecu.ECUEditorManager.getECUEditor;
import static com.romraider.editor.ecu.ECUEditorManager.getECUEditorWithoutCreation;
import static com.romraider.logger.ecu.EcuLogger.startLogger;
import static com.romraider.swing.LookAndFeelManager.initLookAndFeel;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.romraider.editor.ecu.ECUEditor;
import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.EditorLoggerCommunication.ExecutableInstance;
import com.romraider.logger.ecu.EcuLogger;
import com.romraider.runtime.RuntimeArchitecture;
import com.romraider.util.ResourceUtil;

/** Explicitly selected compatibility launcher for the former Swing shell. */
final class LegacySwingApplication {
    private static final Logger LOGGER = Logger.getLogger(
            LegacySwingApplication.class);
    private static final ResourceBundle RB = new ResourceUtil().getBundle(
            ECUExec.class.getName());

    private LegacySwingApplication() { }

    static void launch(String[] arguments) {
        initLookAndFeel();
        showArchitectureWarningIfNeeded(arguments);
        if (ECUExec.containsLoggerArg(arguments)) {
            openLogger(DISPOSE_ON_CLOSE, arguments);
        } else {
            openEditor(arguments);
        }
    }

    static void handleForwarded(ExecutableInstance instance) {
        if (instance.execType == EditorLoggerCommunication.Exec_type.LOGGER) {
            if (EditorLoggerCommunication.getExecutableType()
                    == EditorLoggerCommunication.Exec_type.LOGGER
                    || EcuLogger.getEcuLoggerWithoutCreation() != null) {
                showAlreadyRunningMessage();
                return;
            }
            openLogger(DISPOSE_ON_CLOSE, instance.currentArgs);
            LOGGER.info("Opening Logger with args: "
                    + Arrays.toString(instance.currentArgs));
        } else if (instance.execType
                == EditorLoggerCommunication.Exec_type.EDITOR) {
            openEditor(instance.currentArgs);
            if (EditorLoggerCommunication.getExecutableType()
                    == EditorLoggerCommunication.Exec_type.LOGGER) {
                EcuLogger.getEcuLoggerWithoutCreation().setEcuEditor(
                        ECUEditorManager.getECUEditorWithoutCreation());
            }
            LOGGER.info("Opening Editor with args: "
                    + Arrays.toString(instance.currentArgs));
        } else {
            LOGGER.error("Unknown type in Editor/Logger communication with args: "
                    + Arrays.toString(instance.currentArgs));
        }
    }

    private static void showArchitectureWarningIfNeeded(String[] args) {
        if (RuntimeArchitecture.isCompatible(Version.BUILD_ARCH)
                || ECUExec.containsLoggerArg(args)) return;
        showMessageDialog(null,
                MessageFormat.format(RB.getString("COMPATJRE"),
                        PRODUCT_NAME, Version.BUILD_ARCH),
                RB.getString("JREWARN"), WARNING_MESSAGE);
    }

    private static void showAlreadyRunningMessage() {
        showMessageDialog(null,
                MessageFormat.format(RB.getString("ISRUNNING"), PRODUCT_NAME),
                PRODUCT_NAME, INFORMATION_MESSAGE);
    }

    private static void openLogger(int closeOperation, String[] args) {
        startLogger(closeOperation, getECUEditorWithoutCreation(), args);
    }

    private static void openEditor(String[] args) {
        ECUEditor editor = getECUEditor();
        editor.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        editor.initializeEditorUI();
        editor.checkDefinitions();
        editor.reviewRecoverySnapshots();
        if (args.length > 0) editor.openImage(args[0]);
    }
}
