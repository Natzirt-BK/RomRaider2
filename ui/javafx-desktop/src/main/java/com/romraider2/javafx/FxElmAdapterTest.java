/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.portable.logger.Elm327Session;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;

/** Deliberately separate test mode. It never changes normal Logger configuration. */
final class FxElmAdapterTest {
    final Stage stage = new Stage();
    final TextField port = new TextField();
    final TextField output = new TextField();
    final ComboBox<Integer> baud = new ComboBox<>();
    final ComboBox<Integer> seconds = new ComboBox<>();
    final ComboBox<Elm327Session.Protocol> protocol = new ComboBox<>();
    final CheckBox confirmed = new CheckBox("This is an ELM327/OBDLink serial adapter, not an OpenPort or KKL cable");
    final Button start = new Button("Start read-only test");
    final Button stop = new Button("Stop");
    final Label status = label("Ready. Nothing connects until you start the test.");
    final Label values = label("Only ECU-advertised RPM, coolant temperature and vehicle speed will be recorded.");
    final Label result = label("");
    private final BooleanSupplier loggerStopped;
    private final Function<ElmAdapterTestRun.Configuration, ElmAdapterTestRun> factory;
    private final Timeline updates = new Timeline(new KeyFrame(Duration.millis(100), event -> refresh()));
    private ElmAdapterTestRun run;
    private boolean closeWhenStopped;

    FxElmAdapterTest(Window owner, File directory, BooleanSupplier loggerStopped) {
        this(owner, directory, loggerStopped, ElmAdapterTestRun::new);
    }
    FxElmAdapterTest(Window owner, File directory, BooleanSupplier loggerStopped,
            Function<ElmAdapterTestRun.Configuration, ElmAdapterTestRun> factory) {
        this.loggerStopped = loggerStopped; this.factory = factory;
        stage.initOwner(owner); stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Read-only adapter test — standard OBD-II");
        port.setPromptText("For example /dev/ttyUSB0, /dev/cu.usbserial… or COM3");
        baud.getItems().setAll(9600, 19200, 38400, 57600, 115200, 230400, 500000, 1000000); baud.setValue(38400);
        seconds.getItems().setAll(30, 60, 120); seconds.setValue(60);
        protocol.getItems().setAll(Elm327Session.Protocol.values()); protocol.setValue(Elm327Session.Protocol.AUTOMATIC);
        protocol.setConverter(new StringConverter<>() {
            public String toString(Elm327Session.Protocol value) {
                if (value == null) return "";
                return switch (value) {
                    case AUTOMATIC -> "Automatic (standard OBD-II only)";
                    case ISO9141_2 -> "ISO 9141-2";
                    case KWP_5_BAUD -> "ISO 14230 / KWP — 5-baud init";
                    case KWP_FAST -> "ISO 14230 / KWP — fast init";
                    default -> value.name().replace('_', ' ');
                };
            }
            public Elm327Session.Protocol fromString(String value) { throw new UnsupportedOperationException(); }
        });
        if (directory != null && directory.isDirectory()) output.setText(new File(directory,
                "romraider2-obd-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv").getAbsolutePath());
        Button browse = new Button("Choose CSV…");
        browse.setOnAction(event -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("New adapter test CSV (existing files are never replaced)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV log", "*.csv"));
            chooser.setInitialFileName("romraider2-obd-" + System.currentTimeMillis() + ".csv");
            if (directory != null && directory.isDirectory()) chooser.setInitialDirectory(directory);
            File file = chooser.showSaveDialog(stage); if (file != null) output.setText(file.getAbsolutePath());
        });
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10);
        form.addRow(0, new Label("Serial port"), port);
        form.addRow(1, new Label("Adapter baud"), baud);
        form.addRow(2, new Label("OBD-II protocol"), protocol);
        form.addRow(3, new Label("Record seconds"), seconds);
        form.addRow(4, new Label("New CSV file"), output, browse);
        GridPane.setHgrow(port, Priority.ALWAYS); GridPane.setHgrow(output, Priority.ALWAYS);
        protocol.setMaxWidth(Double.MAX_VALUE);
        confirmed.setWrapText(true);
        Label intro = label("Compatibility test • ELM327 / OBDLink serial\nPark safely and use ignition ON, not ACC. Match the baud rate to your adapter.\nNo flashing, tuning, code clearing or manufacturer-specific SSM/MUT-II commands.");
        intro.getStyleClass().add("muted");
        Button close = new Button("Close"); close.setOnAction(event -> requestClose());
        start.setId("elm-test-start"); stop.setId("elm-test-stop"); port.setId("elm-test-port");
        stop.setDisable(true); start.setDisable(true);
        confirmed.selectedProperty().addListener((o, before, after) -> refresh());
        start.setOnAction(event -> start()); stop.setOnAction(event -> cancel());
        VBox content = new VBox(14, intro, form, confirmed, new HBox(10, start, stop, close), status, values, result);
        content.setPadding(new Insets(18));
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        Scene scene = new Scene(scroll, 760, 560); FxTheme.apply(stage, scene); stage.setScene(scene);
        stage.setMinWidth(440); stage.setMinHeight(350);
        // Freeze all connection/file options for the lifetime of the worker.
        form.disableProperty().bind(stop.disabledProperty().not());
        confirmed.disableProperty().bind(stop.disabledProperty().not());
        stage.setOnCloseRequest(event -> { if (active()) { event.consume(); requestClose(); } });
        stage.setOnHidden(event -> { updates.stop(); if (run != null) run.cancel(); });
        updates.setCycleCount(Timeline.INDEFINITE);
    }
    void show() { updates.play(); FxWindowPlacement.show(stage); }
    private static Label label(String value) { Label label = new Label(value); label.setWrapText(true); label.setMaxWidth(Double.MAX_VALUE); return label; }
    private boolean active() { return run != null && !run.isFinished(); }
    private void start() {
        if (active() || !confirmed.isSelected()) return;
        if (!loggerStopped.getAsBoolean()) { status.setText("Disconnect the normal Logger and stop connection attempts before testing another adapter."); return; }
        try {
            if (output.getText().isBlank()) throw new IllegalArgumentException("Choose a new CSV file first");
            var config = new ElmAdapterTestRun.Configuration(port.getText().trim(), baud.getValue(), protocol.getValue(), seconds.getValue(), Path.of(output.getText()).toAbsolutePath());
            run = factory.apply(config); closeWhenStopped = false;
            result.setText("CSV: " + config.output()); values.setText("Waiting for verified ECU data…");
            run.start(); refresh();
        } catch (RuntimeException failure) { status.setText("Cannot start: " + FxDialogs.rootMessage(failure)); }
    }
    void refresh() {
        boolean active = active(); start.setDisable(active || !confirmed.isSelected()); stop.setDisable(!active);
        if (run == null) return;
        if (active && !loggerStopped.getAsBoolean()) run.cancel();
        status.setText(run.status());
        var progress = run.progress();
        if (progress != null) {
            StringBuilder text = new StringBuilder(progress.rows + " rows • " + progress.validValues + " valid values • " + progress.missingValues + " missing\n");
            for (int i = 0; i < progress.channels.size(); i++) {
                var channel = progress.channels.get(i); double value = progress.values.get(i);
                text.append(channel.label).append(": ").append(Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f %s", value, channel.units) : "no data").append("   ");
            }
            values.setText(text.toString());
        }
        if (!active && run.isCreated() && progress == null) values.setText("No complete samples. The new CSV may be empty.");
        if (!active && closeWhenStopped) stage.close();
    }
    private void cancel() { if (run != null) { run.cancel(); status.setText("Stopping and closing the adapter…"); } }
    private void requestClose() { if (active()) { closeWhenStopped = true; cancel(); } else stage.close(); }
    void close() { if (run != null) run.cancel(); updates.stop(); stage.close(); }
}
