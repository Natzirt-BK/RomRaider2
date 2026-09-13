/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.Settings;
import com.romraider.logger.runtime.LoggerDebugSettings;
import com.romraider.logger.runtime.LoggerDebugSettings.Verbosity;
import com.romraider.util.LogManager;
import com.romraider.util.SettingsManager;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Application logs only. No adapter discovery or vehicle commands. */
final class FxLoggerTroubleshooting {
    interface FolderOpener { void open(Path path) throws Exception; }
    final Stage stage = new Stage();
    final ComboBox<Verbosity> verbosity = new ComboBox<>();
    final Button save = new Button("Save");
    final Button openFolder = new Button("Open log folder");
    final Label status = new Label();
    private Task<Void> opening;
    private boolean closed;

    static void show(Window owner, Settings settings) {
        new FxLoggerTroubleshooting(owner, settings, () -> SettingsManager.save(settings),
                path -> java.awt.Desktop.getDesktop().open(path.toFile())).show();
    }

    FxLoggerTroubleshooting(Window owner, Settings settings, Runnable persist, FolderOpener opener) {
        stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Logger troubleshooting");
        verbosity.getItems().setAll(Verbosity.values());
        verbosity.setValue(LoggerDebugSettings.current(settings));
        verbosity.setMaxWidth(Double.MAX_VALUE);
        verbosity.setId("logger-diagnostic-verbosity");
        Label help = wrapped("These are application diagnostic logs, separate from vehicle CSV recordings. "
                + "Normal is recommended. Detailed and Trace capture more troubleshooting information; "
                + "Trace can generate large logs and slow logging. Return to Normal when finished.");
        Path directory = LogManager.getLogDirectory();
        TextField location = new TextField(directory.toString()); location.setEditable(false);
        location.setId("logger-diagnostic-location");
        Label privacy = wrapped("Review rr_system.log before sharing it: it can contain local file paths, ECU identifiers and communication details. Nothing is uploaded automatically.");
        status.setWrapText(true); status.setMaxWidth(Double.MAX_VALUE);
        openFolder.setMinWidth(Region.USE_PREF_SIZE);
        openFolder.setOnAction(event -> {
            if (closed || opening != null) return;
            openFolder.setDisable(true); status.setText("Opening log folder…");
            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    if (!Files.isDirectory(directory)) throw new IllegalStateException("No diagnostic log folder exists yet at this location.");
                    if (!isCancelled()) opener.open(directory);
                    return null;
                }
            };
            opening = task;
            task.setOnSucceeded(done -> {
                if (closed || opening != task) return;
                opening = null; openFolder.setDisable(false); status.setText("Log folder opened.");
            });
            task.setOnFailed(done -> {
                if (closed || opening != task) return;
                opening = null; openFolder.setDisable(false);
                status.setText("Could not open the folder: " + FxDialogs.rootMessage(task.getException()) + " You can copy the path above.");
            });
            Thread worker = new Thread(task, "rr2-open-diagnostic-folder"); worker.setDaemon(true); worker.start();
        });
        save.setOnAction(event -> {
            if (closed) return;
            try { LoggerDebugSettings.apply(settings, verbosity.getValue(), persist); stage.close(); }
            catch (RuntimeException failure) { status.setText("Could not save: " + FxDialogs.rootMessage(failure)); }
        });
        Button cancel = new Button("Cancel"); cancel.setOnAction(event -> stage.close());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox content = new VBox(12, help, new Label("Diagnostic detail"), verbosity,
                new Label("System log folder"), location, openFolder, privacy, status,
                new HBox(8, spacer, cancel, save));
        content.setPadding(new Insets(18));
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        Scene scene = new Scene(scroll, 600, 450); FxTheme.apply(stage, scene); FxTheme.closeOnEscape(stage, scene);
        stage.setScene(scene); stage.setOnHidden(event -> {
            closed = true; if (opening != null) opening.cancel(true); opening = null;
        });
    }

    void show() { FxWindowPlacement.show(stage); }
    private static Label wrapped(String text) {
        Label label = new Label(text); label.setWrapText(true); label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
}
