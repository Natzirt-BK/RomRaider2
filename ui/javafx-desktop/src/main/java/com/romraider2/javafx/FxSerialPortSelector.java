package com.romraider2.javafx;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Lists OS serial devices without opening ports or probing adapters. */
final class FxSerialPortSelector extends VBox implements AutoCloseable {
    record Port(String address, String description) { }

    final ComboBox<String> ports = new ComboBox<>();
    final Button refresh = new Button("Refresh");
    final Label status = new Label("Select a detected port or enter one manually.");
    private final Supplier<List<Port>> discover;
    private Map<String, String> descriptions = Map.of();
    private Task<List<Port>> scan;
    private boolean closed;

    FxSerialPortSelector() { this(FxSerialPortSelector::systemPorts); }

    FxSerialPortSelector(Supplier<List<Port>> discover) {
        super(5);
        this.discover = discover;
        ports.setEditable(true);
        ports.setPromptText("Select or enter a serial port");
        ports.setMaxWidth(Double.MAX_VALUE);
        ports.setMinWidth(0);
        ports.setTooltip(new Tooltip("Choose a detected serial port or enter its address, such as COM3 or /dev/ttyUSB0. Detection does not verify adapter compatibility."));
        ports.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(String address, boolean empty) {
                super.updateItem(address, empty);
                String description = address == null ? "" : descriptions.getOrDefault(address, "");
                String text = empty || address == null ? null : address + (description.isBlank() ? "" : " — " + description);
                setText(text);
                setTooltip(text == null ? null : new Tooltip(text));
            }
        });
        refresh.setMinWidth(USE_PREF_SIZE);
        refresh.setTooltip(new Tooltip("Refresh the list after plugging in or pairing an adapter. No connection commands are sent."));
        refresh.setOnAction(event -> scan());
        HBox row = new HBox(8, ports, refresh);
        HBox.setHgrow(ports, Priority.ALWAYS);
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.getStyleClass().add("muted");
        getChildren().addAll(row, status);
    }

    String address() { return ports.getEditor().getText().trim(); }

    void scan() {
        if (closed || isDisabled() || scan != null) return;
        status.setText("Looking for serial ports…");
        refresh.setDisable(true);
        Task<List<Port>> task = new Task<>() {
            @Override protected List<Port> call() { return List.copyOf(discover.get()); }
        };
        scan = task;
        task.setOnSucceeded(event -> {
            if (closed || scan != task) return;
            // Preserve edits made while enumeration was running, including custom paths.
            String entered = ports.getEditor().getText();
            String selected = ports.getValue();
            Map<String, String> found = new TreeMap<>();
            for (Port port : task.getValue()) {
                if (port.address() != null && !port.address().isBlank())
                    found.putIfAbsent(port.address(), port.description() == null ? "" : port.description());
            }
            descriptions = found;
            ports.getItems().setAll(found.keySet());
            ports.setValue(selected);
            ports.getEditor().setText(entered);
            scan = null;
            refresh.setDisable(false);
            status.setText(found.isEmpty()
                    ? "No serial ports found. Connect the adapter, check its driver, then Refresh—or enter a port manually."
                    : found.size() + " serial port(s) found. Choose your adapter; compatibility is checked only when you start the test.");
        });
        task.setOnFailed(event -> {
            if (closed || scan != task) return;
            scan = null;
            refresh.setDisable(false);
            status.setText("Could not list serial ports. Check the adapter driver, then Refresh—or enter a port manually.");
        });
        Thread worker = new Thread(task, "rr2-serial-port-discovery");
        worker.setDaemon(true);
        worker.start();
    }

    void cancelScan() {
        Task<List<Port>> previous = scan;
        scan = null;
        if (previous != null) {
            previous.cancel(true);
            refresh.setDisable(false);
            status.setText("Select a detected port or enter one manually.");
        }
    }

    @Override public void close() { closed = true; cancelScan(); }

    private static List<Port> systemPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(port -> new Port(port.getSystemPortPath(), port.getDescriptivePortName()))
                .toList();
    }
}
