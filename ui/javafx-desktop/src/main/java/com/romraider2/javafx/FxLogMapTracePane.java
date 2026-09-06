/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.util.*;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Frozen table geometry follows only the saved-log cursor, never a live ECU session. */
final class FxLogMapTracePane extends BorderPane {
    private final LogDataset dataset;
    private Supplier<FxMapTraceTarget> target = () -> null;
    private final Button capture = new Button("Capture selected editor table…");
    private final Label identity = new Label("No table captured"), axes = new Label(), status = new Label();
    private final ComboBox<LogChannel> horizontal = new ComboBox<>(), vertical = new ComboBox<>();
    private final CheckBox confirmed = new CheckBox("I confirmed the CSV channels match the captured axis units. No conversion is performed.");
    private final TableView<Integer> grid = new TableView<>();
    private LogMapTrace snapshot;
    private LogMapTrace.Point point;
    private int row;
    private boolean pendingRange, closed;

    FxLogMapTracePane(LogDataset dataset) {
        this.dataset = dataset;
        Label explanation = new Label("Read-only table snapshot · highlighted neighbors and linear/bilinear weights describe axis geometry, not verified ECU lookup behavior. Capture again to see later ROM edits.");
        explanation.setWrapText(true); identity.setWrapText(true); axes.setWrapText(true); status.setWrapText(true); confirmed.setWrapText(true);
        for (ComboBox<LogChannel> choice : List.of(horizontal, vertical)) {
            choice.getItems().setAll(dataset.getChannels().stream().filter(c -> !c.isTimeChannel()).toList());
            choice.setPrefWidth(230); choice.setCellFactory(ignored -> channelCell()); choice.setButtonCell(channelCell());
            choice.valueProperty().addListener((value, before, after) -> { confirmed.setSelected(false); update(); });
        }
        horizontal.setPromptText("Map horizontal axis"); vertical.setPromptText("Map vertical axis");
        confirmed.selectedProperty().addListener((value, before, after) -> update());
        capture.setOnAction(event -> capture());
        VBox controls = new VBox(7, explanation, capture, identity, axes, new FlowPane(8, 5, horizontal, vertical), confirmed, status);
        controls.setPadding(new Insets(10)); setTop(controls); grid.setMinSize(0, 0); setCenter(grid);
        grid.setPlaceholder(new Label("Select a 2D or 3D table in the editor, then capture it here."));
        update();
    }
    void setTarget(Supplier<FxMapTraceTarget> value) { target = Objects.requireNonNull(value); }
    private void capture() {
        if (closed) return;
        try {
            FxMapTraceTarget selected = target.get();
            if (selected == null || !selected.stillCurrent().getAsBoolean()) throw new IllegalArgumentException("Select an open 2D or 3D table in the editor first; wait for any document operation to finish.");
            LogMapTrace proposed = LogMapTrace.capture(selected.table());
            if (!FxDialogs.confirmScrollable(getScene() == null ? null : getScene().getWindow(), "Capture map for saved-log tracing?",
                    selected.documentName() + " · " + proposed.name() + "\n"
                            + proposed.columns() + " columns × " + proposed.rows() + " rows\n"
                            + "Horizontal: " + proposed.xName() + " (" + proposed.xUnits() + ")\n"
                            + (proposed.isSurface() ? "Vertical: " + proposed.yName() + " (" + proposed.yUnits() + ")\n" : "")
                            + "\nCapture the displayed table values and exact numeric axes for offline review? This replaces only the previous trace snapshot and clears its channel mappings."
                            + "\n\nThe snapshot stays frozen after later ROM edits or closing the editor. No table values, editor selections, ROM files or ECU state are changed. Geometric neighbors are not a verified ECU interpolation model.", "Capture snapshot")) return;
            if (closed || !selected.stillCurrent().getAsBoolean()) throw new IllegalArgumentException("The editor selection changed during review. Capture and review again.");
            install(proposed, selected.documentName());
        } catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); }
    }
    void install(LogMapTrace value, String document) {
        if (closed) return;
        snapshot = value; point = null; confirmed.setSelected(false); horizontal.setValue(null); vertical.setValue(null);
        identity.setText("Captured: " + document + " · " + value.name() + " · " + value.units() + " (frozen snapshot)");
        axes.setText("Horizontal: " + value.xName() + " (" + value.xUnits() + ")" + (value.isSurface() ? " · Vertical: " + value.yName() + " (" + value.yUnits() + ")" : ""));
        vertical.setVisible(value.isSurface()); vertical.setManaged(value.isSurface());
        grid.getColumns().clear();
        TableColumn<Integer, String> axis = new TableColumn<>(value.isSurface() ? value.yName() + " (" + value.yUnits() + ")" : "Axis");
        axis.setCellValueFactory(cell -> new ReadOnlyStringWrapper("R" + (cell.getValue() + 1) + " · " + (value.isSurface() ? number(value.yAt(cell.getValue())) : value.xName())));
        axis.setPrefWidth(130); axis.setSortable(false); grid.getColumns().add(axis);
        for (int column = 0; column < value.columns(); column++) {
            int index = column;
            TableColumn<Integer, String> data = new TableColumn<>();
            Label header = new Label("C" + (column + 1) + " · " + number(value.xAt(column)));
            header.setTooltip(new Tooltip("Captured horizontal breakpoint: " + Double.toString(value.xAt(column)) + " " + value.xUnits())); data.setGraphic(header);
            data.setCellValueFactory(cell -> new ReadOnlyStringWrapper(number(value.valueAt(cell.getValue(), index))));
            data.setCellFactory(ignored -> new TableCell<>() {
                @Override protected void updateItem(String text, boolean empty) {
                    super.updateItem(text, empty); setText(empty ? null : text);
                    LogMapTrace.Neighbor neighbor = point == null ? null : point.neighbors().stream().filter(n -> n.row() == getIndex() && n.column() == index).findFirst().orElse(null);
                    setStyle("-fx-alignment: CENTER;" + (!empty && neighbor != null ? "-fx-background-color: #bcebe4; -fx-text-fill: #092e2b; -fx-font-weight: bold;" : ""));
                    setTooltip(empty || getIndex() < 0 || getIndex() >= value.rows() ? null
                            : new Tooltip("Captured value: " + Double.toString(value.valueAt(getIndex(), index)) + " " + value.units()
                                    + (value.isSurface() ? "\nVertical breakpoint: " + Double.toString(value.yAt(getIndex())) + " " + value.yUnits() : "")
                                    + (neighbor == null ? "" : "\nGeometric weight: " + number(neighbor.weight() * 100) + "% · not verified ECU interpolation")));
                }
            });
            data.setPrefWidth(95); data.setSortable(false); grid.getColumns().add(data);
        }
        List<Integer> rows = new ArrayList<>(); for (int i = 0; i < value.rows(); i++) rows.add(i);
        grid.setItems(FXCollections.observableArrayList(rows)); update();
    }
    void showSample(int sample, boolean rangePending) { row = sample; pendingRange = rangePending; update(); }
    private void update() {
        if (closed) return;
        LogMapTrace.Point before = point; point = null;
        if (snapshot == null) status.setText("Capture a table, then explicitly map its axis channels.");
        else if (pendingRange) status.setText("Apply the shared sample range to resume tracing.");
        else if (!confirmed.isSelected() || !current(horizontal.getValue()) || (snapshot.isSurface() && !current(vertical.getValue()))) status.setText("Map and confirm the axis channels and units before tracing.");
        else if (row < 0 || row >= dataset.getRowCount()) status.setText("No current saved sample.");
        else {
            double x = dataset.getValue(row, horizontal.getValue().getIndex());
            double y = snapshot.isSurface() ? dataset.getValue(row, vertical.getValue().getIndex()) : Double.NaN;
            point = snapshot.locate(x, y);
            String sample = "Sample " + (row + 1) + " · X " + number(x) + (snapshot.isSurface() ? " · Y " + number(y) : "");
            if (point.state() == LogMapTrace.State.COVERED) {
                StringJoiner neighbors = new StringJoiner(" · ");
                for (var n : point.neighbors()) neighbors.add("R" + (n.row() + 1) + "/C" + (n.column() + 1) + " " + number(n.weight() * 100) + "%");
                status.setText(sample + " · Geometric neighbors: " + neighbors);
            } else status.setText(sample + (point.state() == LogMapTrace.State.MISSING ? " · Missing mapped reading; no trace." : " · Outside captured axes; no clamping or extrapolation."));
        }
        if (!Objects.equals(before, point)) grid.refresh();
    }
    private boolean current(LogChannel channel) { return channel != null && dataset.getChannels().contains(channel); }
    private static ListCell<LogChannel> channelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(LogChannel channel, boolean empty) {
                super.updateItem(channel, empty); setText(channel == null ? "Choose CSV channel" : "[" + (channel.getIndex() + 1) + "] " + channel.getLabel());
            }
        };
    }
    private static String number(double value) {
        if (!Double.isFinite(value)) return "—";
        var rounded = java.math.BigDecimal.valueOf(value).round(new java.math.MathContext(9)).stripTrailingZeros();
        return Math.abs(value) >= 1e9 || (value != 0 && Math.abs(value) < 1e-4) ? rounded.toString() : rounded.toPlainString();
    }
    void close() { closed = true; snapshot = null; point = null; target = () -> null; grid.getItems().clear(); grid.getColumns().clear(); }
}
