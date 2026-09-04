/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;

import com.romraider.Settings;
import com.romraider.logger.runtime.LoggerDesktopRuntime;
import com.romraider.util.SettingsManager;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

final class FxLoggerSetup {
    private FxLoggerSetup() { }

    static void show(Window owner, LoggerDesktopRuntime runtime,
            Runnable applied) {
        Settings settings = runtime.getSettings();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Logger Setup");

        TextField definition = field(settings.getLoggerDefinitionFilePath());
        Button browseDefinition = new Button("Browse…");
        browseDefinition.setOnAction(event -> {
            File current = path(definition.getText());
            File selected = FxDialogs.chooseLoggerDefinition(stage,
                    current == null ? settings.getLastDefinitionDir()
                            : current.getParentFile());
            if (selected != null) definition.setText(selected.getAbsolutePath());
        });
        TextField output = field(settings.getLoggerOutputDirPath());
        Button browseOutput = new Button("Browse…");
        browseOutput.setOnAction(event -> {
            File selected = FxDialogs.chooseDirectory(stage,
                    "Select log output directory", path(output.getText()));
            if (selected != null) output.setText(selected.getAbsolutePath());
        });
        TextField port = field(settings.getLoggerPort());
        TextField protocol = field(settings.getLoggerProtocol());
        TextField transport = field(settings.getTransportProtocol());
        TextField target = field(settings.getTargetModule());
        CheckBox autoConnect = new CheckBox("Connect automatically at startup");
        autoConnect.setSelected(settings.getAutoConnectOnStartup());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(18));
        form.addRow(0, new Label("Logger definition"), definition,
                browseDefinition);
        form.addRow(1, new Label("Log output directory"), output,
                browseOutput);
        form.addRow(2, new Label("Port"), port);
        form.addRow(3, new Label("Protocol"), protocol);
        form.addRow(4, new Label("Transport"), transport);
        form.addRow(5, new Label("Target module"), target);
        form.add(autoConnect, 1, 6, 2, 1);
        GridPane.setHgrow(definition, Priority.ALWAYS);
        GridPane.setHgrow(output, Priority.ALWAYS);

        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> stage.close());
        Button save = new Button("Save setup");
        save.setDefaultButton(true);
        save.setOnAction(event -> {
            try {
                runtime.requireConfigurationEditable();
                settings.setLoggerDefinitionFilePath(definition.getText().trim());
                settings.setLoggerOutputDirPath(output.getText().trim());
                settings.setLoggerPort(port.getText().trim());
                settings.setLoggerProtocol(protocol.getText().trim());
                settings.setTransportProtocol(transport.getText().trim());
                settings.setTargetModule(target.getText().trim());
                settings.setAutoConnectOnStartup(autoConnect.isSelected());
                File selectedDefinition = path(definition.getText());
                if (selectedDefinition != null) {
                    settings.setLastDefinitionDir(
                            selectedDefinition.getParentFile());
                }
                runtime.reloadConfiguration();
                SettingsManager.save(settings);
                applied.run();
                stage.close();
            } catch (RuntimeException failure) {
                FxDialogs.error(stage, "Logger setup could not be applied",
                        FxDialogs.rootMessage(failure));
            }
        });
        HBox actions = new HBox(8, fill, cancel, save);
        actions.setPadding(new Insets(10));
        BorderPane root = new BorderPane(form, null, null, actions, null);
        Scene scene = new Scene(root, 780, 500);
        FxTheme.apply(stage, scene);
        stage.setScene(scene);
        stage.show();
    }

    private static TextField field(String value) {
        return new TextField(value == null ? "" : value);
    }

    private static File path(String value) {
        return value == null || value.isBlank() ? null : new File(value.trim());
    }
}
