/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

final class FxDialogs {
    enum RecoveryDecision { RESTORE, DISCARD, KEEP }

    private FxDialogs() { }

    static void error(Window owner, String title, String message) {
        runAndWait(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(owner);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message == null ? "Unknown error" : message);
            FxTheme.applyDialog(alert.getDialogPane());
            alert.showAndWait();
            return null;
        });
    }

    static void info(Window owner, String title, String message) {
        runAndWait(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(owner);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            FxTheme.applyDialog(alert.getDialogPane());
            alert.showAndWait();
            return null;
        });
    }

    static boolean confirm(Window owner, String title, String message,
            String approve) {
        return runAndWait(() -> {
            ButtonType yes = new ButtonType(approve,
                    ButtonBar.ButtonData.OK_DONE);
            ButtonType no = new ButtonType("Cancel",
                    ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    message, yes, no);
            alert.initOwner(owner);
            alert.setTitle(title);
            alert.setHeaderText(title);
            FxTheme.applyDialog(alert.getDialogPane());
            Optional<ButtonType> result = alert.showAndWait();
            return result.orElse(no) == yes;
        });
    }

    static RecoveryDecision chooseRecovery(Window owner, String message) {
        return runAndWait(() -> {
            ButtonType restore = new ButtonType("Restore as unsaved",
                    ButtonBar.ButtonData.OK_DONE);
            ButtonType discard = new ButtonType("Discard recovery",
                    ButtonBar.ButtonData.OTHER);
            ButtonType keep = new ButtonType("Keep for later",
                    ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                    restore, discard, keep);
            alert.initOwner(owner);
            alert.setTitle("Recover unsaved ROM");
            alert.setHeaderText("Recover unsaved ROM");
            FxTheme.applyDialog(alert.getDialogPane());
            ButtonType selected = alert.showAndWait().orElse(keep);
            if (selected == restore) return RecoveryDecision.RESTORE;
            if (selected == discard) return RecoveryDecision.DISCARD;
            return RecoveryDecision.KEEP;
        });
    }

    static File chooseRom(Window owner, File directory) {
        FileChooser chooser = chooser("Open ROM image", directory);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "ROM images", "*.bin", "*.hex", "*.srf"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "All files", "*.*"));
        return chooser.showOpenDialog(owner);
    }

    static File saveRom(Window owner, File directory, String name) {
        FileChooser chooser = chooser("Save ROM image", directory);
        chooser.setInitialFileName(name);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "ROM images", "*.bin", "*.hex", "*.srf"));
        return normalizeRomSaveTarget(chooser.showSaveDialog(owner));
    }

    static File normalizeRomSaveTarget(File selected) {
        if (selected == null) return null;
        String name = selected.getName();
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        for (String extension : new String[] {".bin", ".hex", ".srf"}) {
            if (lowerName.endsWith(extension + extension)) {
                String normalizedName = name.substring(
                        0, name.length() - extension.length());
                File parent = selected.getParentFile();
                return parent == null ? new File(normalizedName)
                        : new File(parent, normalizedName);
            }
        }
        return selected;
    }

    static File chooseDefinition(Window owner, File directory) {
        FileChooser chooser = definitionChooser(directory);
        return chooser.showOpenDialog(owner);
    }

    static List<File> chooseDefinitions(Window owner, File directory) {
        return definitionChooser(directory).showOpenMultipleDialog(owner);
    }

    static File chooseLoggerDefinition(Window owner, File directory) {
        FileChooser chooser = chooser("Load Logger definition", directory);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Logger definitions", "*.xml"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser.showOpenDialog(owner);
    }

    static File chooseDirectory(Window owner, String title, File directory) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (directory != null && directory.isDirectory()) {
            chooser.setInitialDirectory(directory);
        }
        return chooser.showDialog(owner);
    }

    private static FileChooser definitionChooser(File directory) {
        FileChooser chooser = chooser("Add ECU definitions", directory);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("ECU definitions",
                        "*.xml", "*.xdf", "*.vdf", "*.jdf", "*.C??"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser;
    }

    private static FileChooser chooser(String title, File directory) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        if (directory != null && directory.isDirectory()) {
            chooser.setInitialDirectory(directory);
        }
        return chooser;
    }

    static <T> T runAndWait(Callable<T> work) {
        if (Platform.isFxApplicationThread()) {
            try {
                return work.call();
            } catch (Exception failure) {
                throw wrap(failure);
            }
        }
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (Exception failure) {
            Throwable cause = failure.getCause();
            throw wrap(cause == null ? failure : cause);
        }
    }

    static String rootMessage(Throwable failure) {
        Throwable cursor = failure;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank()
                ? cursor.getClass().getSimpleName() : message;
    }

    private static RuntimeException wrap(Throwable failure) {
        return failure instanceof RuntimeException runtime
                ? runtime : new IllegalStateException(failure);
    }
}
