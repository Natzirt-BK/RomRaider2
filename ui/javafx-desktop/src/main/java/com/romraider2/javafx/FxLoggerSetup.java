/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;

import com.romraider.Settings;
import com.romraider.logger.runtime.LoggerDesktopRuntime;
import com.romraider.logger.runtime.LoggerCaptureOptions;
import com.romraider.util.SettingsManager;

import javafx.geometry.Insets;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
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
        browseDefinition.setMinWidth(Region.USE_PREF_SIZE);
        browseDefinition.setOnAction(event -> {
            File current = path(definition.getText());
            File selected = FxDialogs.chooseLoggerDefinition(stage,
                    current == null ? settings.getLastDefinitionDir()
                            : current.getParentFile());
            if (selected != null) definition.setText(selected.getAbsolutePath());
        });
        TextField output = field(settings.getLoggerOutputDirPath());
        Button browseOutput = new Button("Browse…");
        browseOutput.setMinWidth(Region.USE_PREF_SIZE);
        browseOutput.setOnAction(event -> {
            File selected = FxDialogs.chooseDirectory(stage,
                    "Select log output directory", path(output.getText()));
            if (selected != null) output.setText(selected.getAbsolutePath());
        });
        TextField port = field(settings.getLoggerPort());
        ComboBox<String> protocol = connectionSelector("Select protocol",
                "Protocols declared by the logger definition with an implementation in RR2. Select the protocol for your vehicle.");
        ComboBox<String> transport = connectionSelector("Select transport",
                "Transports supported by the selected protocol and definition. ISO9141 is K-Line; ISO15765 is CAN; ISO14230 is KWP. Vehicle and adapter compatibility still apply.");
        ComboBox<String> target = connectionSelector("Select target module",
                "Modules declared by the selected protocol and transport. Choose the module you intend to log.");
        CheckBox autoConnect = new CheckBox("Connect automatically at startup");
        autoConnect.setSelected(settings.getAutoConnectOnStartup());
        Button stopAttempts = disconnectButton(
                () -> runtime.getWorkspaceContext().getSession().disconnect());
        Label startupHelp = new Label("Stops connections and retries. Startup preference stays unchanged.");
        startupHelp.setWrapText(true);
        startupHelp.setMinWidth(0);
        startupHelp.setMinHeight(Region.USE_PREF_SIZE);

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
        form.add(stopAttempts, 0, 7);
        form.add(startupHelp, 1, 7, 2, 1);
        for (var node : form.getChildren()) {
            if (node instanceof Label label && label != startupHelp) label.setMinWidth(Region.USE_PREF_SIZE);
        }
        GridPane.setHgrow(definition, Priority.ALWAYS);
        GridPane.setHgrow(output, Priority.ALWAYS);

        Label heading = new Label("Logger connection and capture");
        heading.getStyleClass().add("title");
        Label detail = new Label("Choose a logger definition and output folder, "
                + "then confirm the transport settings for this ECU session.");
        detail.getStyleClass().add("muted");
        javafx.scene.layout.VBox introduction = new javafx.scene.layout.VBox(
                4, heading, detail);
        introduction.getStyleClass().add("dialog-header");

        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> stage.close());
        Button save = new Button("Save setup");
        SetupChoices choices = new SetupChoices(definition, protocol, transport, target, save,
                settings.getLoggerProtocol(), settings.getTransportProtocol(), settings.getTargetModule());
        CheckBox fast = option("Fast Polling", "logger-fast-polling", settings.isFastPoll(),
                "Use the original fast-polling mode where the selected definition/module supports it. Leave off if your connection is unstable.");
        Runnable fastSupport = () -> {
            boolean supported = choices.catalog.supportsFastPolling(protocol.getValue(), transport.getValue(), target.getValue());
            fast.setDisable(!supported);
        };
        target.valueProperty().addListener((o, before, after) -> fastSupport.run());
        transport.valueProperty().addListener((o, before, after) -> fastSupport.run());
        fastSupport.run();
        CheckBox controlSwitch = option("Control recording with the vehicle switch", "logger-switch-recording",
                settings.isFileLoggingControllerSwitchActive(),
                "Uses the recording switch defined by the logger definition, normally the rear-window defogger. Save validates availability; this does not activate the vehicle switch.");
        CheckBox absolute = option("Use clock time in CSV instead of elapsed milliseconds", "logger-absolute-time",
                settings.isFileLoggingAbsoluteTimestamp(), "Off keeps the standard Time (msec) column. Applies to the next recording.");
        CheckBox numbers = option("Use US numeric formatting (restart required)", "logger-us-numbers",
                settings.isUsNumberFormat(), "Shared application setting: decimal point and comma-separated fields. Restart RR2 after changing it.");
        TextField logName = field(settings.getLogfileNameText());
        logName.setId("logger-log-name"); logName.setPromptText("Optional name, e.g. idle-test");
        Label nameHelp = new Label("A timestamp and ECU ID are added automatically. Existing logs are never overwritten.");
        nameHelp.setWrapText(true);
        Label captureHelp = new Label("Disconnect before changing capture options. Disabled Fast Polling means the selected module does not declare support. Switch availability is checked when saving.");
        captureHelp.setWrapText(true);
        javafx.scene.layout.VBox capture = new javafx.scene.layout.VBox(12,
                new Label("Log filename prefix"), logName, nameHelp, fast, controlSwitch, absolute, numbers, captureHelp);
        capture.setPadding(new Insets(18));
        javafx.scene.control.Tab connectionTab = new javafx.scene.control.Tab("Connection", scroll(form));
        javafx.scene.control.Tab captureTab = new javafx.scene.control.Tab("Recording", scroll(capture));
        connectionTab.setClosable(false); captureTab.setClosable(false);
        javafx.scene.control.TabPane setupTabs = new javafx.scene.control.TabPane(connectionTab, captureTab);
        definition.textProperty().addListener((o, before, after) -> choices.refresh());
        stage.setOnHidden(event -> choices.close());
        save.setDefaultButton(true);
        save.setOnAction(event -> {
            try {
                runtime.applySetup(definition.getText(), output.getText(),
                        port.getText(), protocol.getValue(), transport.getValue(), target.getValue(),
                        autoConnect.isSelected(), new LoggerCaptureOptions(!fast.isDisabled() && fast.isSelected(),
                                controlSwitch.isSelected(), absolute.isSelected(), numbers.isSelected(), logName.getText()),
                        () -> SettingsManager.save(settings));
                applied.run();
                stage.close();
            } catch (RuntimeException failure) {
                FxDialogs.error(stage, "Logger setup could not be applied",
                        FxDialogs.rootMessage(failure));
            }
        });
        HBox actions = new HBox(8, fill, cancel, save);
        actions.setPadding(new Insets(10));
        BorderPane root = new BorderPane(setupTabs, introduction, null, actions, null);
        Scene scene = new Scene(root, 780, 560);
        FxTheme.apply(stage, scene);
        FxTheme.closeOnEscape(stage, scene);
        stage.setScene(scene);
        FxWindowPlacement.show(stage);
        choices.refresh();
    }

    private static CheckBox option(String text, String id, boolean selected, String help) {
        CheckBox box = new CheckBox(text); box.setId(id); box.setSelected(selected);
        box.setWrapText(true); box.setMinHeight(Region.USE_PREF_SIZE);
        Tooltip tooltip = new Tooltip(help); tooltip.setWrapText(true); tooltip.setMaxWidth(400);
        box.setTooltip(tooltip); box.setAccessibleHelp(help); return box;
    }

    private static javafx.scene.control.ScrollPane scroll(javafx.scene.Node content) {
        var scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true); scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    /** Debounced background metadata read; changes stay in this dialog until Save. */
    static final class SetupChoices {
        private final TextField definition;
        private final ComboBox<String> protocol, transport, target;
        private final Button save;
        private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
        private FxLoggerConnectionChoices catalog = FxLoggerConnectionChoices.empty();
        private Task<FxLoggerConnectionChoices> pending;
        private long revision;
        private boolean updating, closed;
        private String wantedProtocol, wantedTransport, wantedTarget;

        SetupChoices(TextField definition, ComboBox<String> protocol, ComboBox<String> transport,
                ComboBox<String> target, Button save, String initialProtocol, String initialTransport, String initialTarget) {
            this.definition = definition; this.protocol = protocol; this.transport = transport;
            this.target = target; this.save = save;
            wantedProtocol = initialProtocol; wantedTransport = initialTransport; wantedTarget = initialTarget;
            protocol.valueProperty().addListener((o, before, after) -> {
                if (!updating) { wantedProtocol = after; updateDependents(); }
            });
            transport.valueProperty().addListener((o, before, after) -> {
                if (!updating) { wantedTransport = after; updateDependents(); }
            });
            target.valueProperty().addListener((o, before, after) -> {
                if (!updating) { wantedTarget = after; updateSave(); }
            });
            debounce.setOnFinished(event -> load());
        }

        void refresh() {
            revision++;
            if (pending != null) pending.cancel();
            save.setDisable(true);
            updating = true;
            for (var selector : java.util.List.of(protocol, transport, target)) {
                selector.getItems().clear(); selector.setValue(null); selector.setDisable(true);
            }
            updating = false;
            protocol.setPromptText("Reading definition…");
            debounce.playFromStart();
        }

        private void load() {
            if (closed) return;
            long request = revision;
            String source = definition.getText();
            Task<FxLoggerConnectionChoices> task = new Task<>() {
                @Override protected FxLoggerConnectionChoices call() throws Exception {
                    return FxLoggerConnectionChoices.read(source);
                }
            };
            pending = task;
            task.setOnSucceeded(event -> {
                if (closed || request != revision) return;
                catalog = task.getValue();
                definition.setTooltip(null);
                updating = true;
                select(protocol, catalog.protocols(), wantedProtocol);
                updating = false;
                protocol.setPromptText("Select protocol");
                updateDependents();
            });
            task.setOnFailed(event -> {
                if (closed || request != revision) return;
                catalog = FxLoggerConnectionChoices.empty();
                protocol.setPromptText("Choose a valid logger XML");
                definition.setTooltip(new Tooltip(task.getException().getMessage()));
            });
            Thread worker = new Thread(task, "logger-setup-choices");
            worker.setDaemon(true);
            worker.start();
        }

        private void updateDependents() {
            updating = true;
            select(transport, catalog.transports(protocol.getValue()), wantedTransport);
            select(target, catalog.modules(protocol.getValue(), transport.getValue()), wantedTarget);
            updating = false;
            updateSave();
        }

        private void updateSave() {
            save.setDisable(protocol.getValue() == null || transport.getValue() == null || target.getValue() == null);
        }

        void close() {
            closed = true; revision++; debounce.stop();
            if (pending != null) pending.cancel();
        }
    }

    static ComboBox<String> connectionSelector(String prompt, String explanation) {
        ComboBox<String> selector = new ComboBox<>();
        selector.setEditable(false);
        selector.setMinWidth(0);
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setPromptText(prompt);
        Tooltip tooltip = new Tooltip(explanation);
        tooltip.setWrapText(true); tooltip.setMaxWidth(360);
        selector.setTooltip(tooltip);
        return selector;
    }

    static void select(ComboBox<String> selector, java.util.List<String> choices, String current) {
        selector.getItems().setAll(choices);
        selector.setValue(choices.stream().filter(value -> value.equalsIgnoreCase(current)).findFirst().orElse(null));
        selector.setDisable(choices.isEmpty());
    }

    static Button disconnectButton(Runnable disconnect) {
        Button button = new Button("Disconnect");
        String explanation = "Disconnects the logger and stops any connection or reconnection attempts. "
                + "Use this before changing connection settings. "
                + "Does not change Connect automatically at startup.";
        Tooltip tooltip = new Tooltip(explanation);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(360);
        button.setTooltip(tooltip);
        button.setAccessibleHelp(explanation);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> disconnect.run());
        return button;
    }

    private static TextField field(String value) {
        TextField field = new TextField(value == null ? "" : value);
        field.setMinWidth(0);
        return field;
    }

    static ComboBox<String> targetSelector(java.util.List<String> choices, String current) {
        ComboBox<String> target = new ComboBox<>();
        target.getItems().setAll(choices);
        target.setEditable(false);
        target.setMaxWidth(Double.MAX_VALUE);
        target.setPromptText("Select target module");
        target.setValue(choices.stream().filter(value -> value.equalsIgnoreCase(current))
                .findFirst().orElse(current));
        target.setTooltip(new javafx.scene.control.Tooltip(
                "Modules from the chosen definition and transport. Invalid selections are rejected when saving."));
        return target;
    }

    private static File path(String value) {
        return value == null || value.isBlank() ? null : new File(value.trim());
    }
}
