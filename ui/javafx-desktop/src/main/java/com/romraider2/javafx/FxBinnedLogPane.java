/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

final class FxBinnedLogPane extends BorderPane implements AutoCloseable {
    private final LogDataset dataset;
    private LogRange range;
    private final CheckBox surface = new CheckBox("Two-axis grid (3D)");
    private final ComboBox<LogChannel> x = new ComboBox<>(), y = new ComboBox<>(), value = new ComboBox<>();
    private final TextField xOrigin = new TextField("0"), yOrigin = new TextField("0"), xWidth = new TextField(), yWidth = new TextField();
    private final Spinner<Integer> minimumCount = new Spinner<>(1, BinnedLogAnalysis.MAX_ROWS, 1);
    private final Button calculate = new Button("Calculate bins"), copy = new Button("Copy bin table");
    private final ToggleButton hideAxes = new ToggleButton("Hide axes");
    private final Label status = new Label(), rangeLabel = new Label();
    private final Label heatLegend = new Label();
    private final TableView<BinnedLogAnalysis.Cell> table = new TableView<>();
    private final TableView<Integer> heatmap = new TableView<>();
    private final FxBinnedLogPlot plot = new FxBinnedLogPlot();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "rr2-binned-log-analysis"); thread.setDaemon(true); return thread;
    });
    private Future<?> pending;
    private long generation;
    private boolean closed, rangePending;
    private BinnedLogAnalysis.Result result;

    FxBinnedLogPane(LogDataset dataset) {
        this.dataset = dataset; range = LogRange.all(dataset);
        Label explanation = new Label("Applied range only · original CSV units · no MAF/Injector filters · bins = origin + integer × width · empty/low-count bins remain gaps.");
        explanation.setWrapText(true); status.setWrapText(true); rangeLabel.setWrapText(true);
        for (ComboBox<LogChannel> choice : List.of(x, y, value)) {
            choice.getItems().setAll(dataset.getChannels()); choice.setPrefWidth(205); choice.setPromptText("Choose CSV channel");
            choice.setCellFactory(ignored -> channelCell()); choice.setButtonCell(channelCell());
            choice.valueProperty().addListener((v, before, after) -> invalidate());
        }
        for (TextField text : List.of(xOrigin, yOrigin, xWidth, yWidth)) {
            text.setPrefColumnCount(7); text.textProperty().addListener((v, before, after) -> invalidate());
        }
        xWidth.setPromptText("Required"); yWidth.setPromptText("Required"); minimumCount.setPrefWidth(110);
        minimumCount.valueProperty().addListener((v, before, after) -> invalidate());
        FlowPane vertical = new FlowPane(7, 5, new Label("Y axis"), y, new Label("Origin"), yOrigin, new Label("Width"), yWidth);
        vertical.visibleProperty().bind(surface.selectedProperty().and(hideAxes.selectedProperty().not())); vertical.managedProperty().bind(vertical.visibleProperty());
        FlowPane horizontal = new FlowPane(7, 5, new Label("X axis"), x, new Label("Origin"), xOrigin, new Label("Width"), xWidth);
        horizontal.visibleProperty().bind(hideAxes.selectedProperty().not()); horizontal.managedProperty().bind(horizontal.visibleProperty());
        hideAxes.setPrefWidth(100); hideAxes.selectedProperty().addListener((v, before, after) -> hideAxes.setText(after ? "Show axes" : "Hide axes"));
        surface.selectedProperty().addListener((v, before, after) -> invalidate());
        calculate.setOnAction(event -> calculate()); copy.setOnAction(event -> copy());
        VBox controls = new VBox(6, explanation, rangeLabel,
                horizontal, vertical,
                new FlowPane(7, 5, new Label("Value"), value, surface, new Label("Minimum count"), minimumCount, calculate, copy, hideAxes), status);
        controls.setPadding(new Insets(8)); setTop(controls);
        column("X from", cell -> number(result.x().edge(result.firstX() + cell.column())));
        column("X to (excl.)", cell -> number(result.x().edge(result.firstX() + cell.column() + 1)));
        column("Y from", cell -> result.isSurface() ? number(result.y().edge(result.firstY() + cell.row())) : "—");
        column("Y to (excl.)", cell -> result.isSurface() ? number(result.y().edge(result.firstY() + cell.row() + 1)) : "—");
        column("Count", cell -> Integer.toString(cell.count()));
        column("Mean", cell -> qualified(cell) ? number(cell.mean()) : "—");
        column("Minimum", cell -> qualified(cell) ? number(cell.minimum()) : "—");
        column("Maximum", cell -> qualified(cell) ? number(cell.maximum()) : "—");
        column("Coverage", cell -> cell.count() == 0 ? "Empty" : qualified(cell) ? "Meets count" : "Low count");
        table.setMinSize(0, 0); heatmap.setMinSize(0, 0);
        heatLegend.setWrapText(true); heatLegend.setPadding(new Insets(5));
        BorderPane heatView = new BorderPane(heatmap); heatView.setBottom(heatLegend);
        TabPane views = new TabPane(tab("Bin table", table), tab("Mean heatmap", heatView), tab("2D / 3D plot", plot)); setCenter(views);
        setRange(range, false);
    }
    void setRange(LogRange range, boolean pending) {
        if (closed) return;
        this.range = range; rangePending = pending; invalidate();
        rangeLabel.setText(pending ? "Shared range draft · Apply range before binning" : "Samples " + (range.getStartInclusive() + 1) + "–" + range.getEndExclusive() + " · " + range.size() + " selected");
        calculate.setDisable(pending);
    }
    private void invalidate() {
        generation++; if (pending != null) pending.cancel(true);
        table.getItems().clear(); heatmap.getItems().clear(); heatmap.getColumns().clear(); result = null;
        plot.show(null, 1, ""); heatLegend.setText(""); copy.setDisable(true); status.setText("Choose channels, origins and widths, then calculate. Input changes clear previous results.");
    }
    private void calculate() {
        if (closed || rangePending) return;
        invalidate();
        final BinnedLogAnalysis.Axis horizontal, vertical; final int channel;
        try {
            horizontal = new BinnedLogAnalysis.Axis(selected(x), decimal(xOrigin), decimal(xWidth));
            vertical = surface.isSelected() ? new BinnedLogAnalysis.Axis(selected(y), decimal(yOrigin), decimal(yWidth)) : null;
            channel = selected(value);
        } catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); return; }
        long revision = generation; LogRange selection = range;
        status.setText("Aggregating all selected samples…");
        pending = worker.submit(() -> {
            try {
                BinnedLogAnalysis.Result completed = BinnedLogAnalysis.analyze(dataset, selection, horizontal, vertical, channel);
                Platform.runLater(() -> {
                    if (closed || generation != revision) return;
                    result = completed; table.getItems().setAll(completed.cells()); buildHeatmap();
                    plot.show(result, minimumCount.getValue(), label()); copy.setDisable(completed.cells().isEmpty());
                    status.setText(completed.accepted() + " accepted · " + completed.invalid() + " missing/nonfinite · " + completed.columns() + " × " + completed.rows() + " bins including gaps. " + label());
                });
            } catch (RuntimeException failure) {
                Platform.runLater(() -> { if (!closed && generation == revision) status.setText(FxDialogs.rootMessage(failure)); });
            }
        });
    }
    private String label() {
        return "X: " + channelLabel(result.x().channel())
                + (result.isSurface() ? " · Y: " + channelLabel(result.y().channel()) : "")
                + " · Value: " + channelLabel(result.valueChannel()) + " · count ≥ " + minimumCount.getValue();
    }
    private String channelLabel(int index) { return dataset.getChannels().get(index).getLabel().replace('\n', ' ').replace('\r', ' ').replace('\t', ' '); }
    private void buildHeatmap() {
        BinnedLogAnalysis.Result displayed = result;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (var cell : result.cells()) if (qualified(cell)) { low = Math.min(low, cell.mean()); high = Math.max(high, cell.mean()); }
        double minimum = low, maximum = high;
        heatLegend.setText("Cells: mean / count · " + (Double.isFinite(low) ? "blue " + number(low) + " → amber " + number(high) : "no bins meet the count threshold")
                + " · uncolored cells are empty or below the minimum count; colors are relative to this result.");
        TableColumn<Integer, String> rowAxis = new TableColumn<>(result.isSurface() ? "Y bin from" : "Bins");
        rowAxis.setCellValueFactory(cell -> new ReadOnlyStringWrapper(displayed.isSurface() ? number(displayed.y().edge(displayed.firstY() + cell.getValue())) : "Mean / count"));
        rowAxis.setPrefWidth(115); rowAxis.setSortable(false); heatmap.getColumns().add(rowAxis);
        for (int column = 0; column < result.columns(); column++) {
            int index = column; TableColumn<Integer, String> bin = new TableColumn<>(number(result.x().edge(result.firstX() + column)));
            bin.setPrefWidth(90); bin.setSortable(false);
            bin.setCellValueFactory(cell -> {
                var value = displayed.cellAt(cell.getValue(), index);
                return new ReadOnlyStringWrapper((qualified(value) ? number(value.mean()) : "—") + " / " + value.count());
            });
            bin.setCellFactory(ignored -> new TableCell<>() {
                @Override protected void updateItem(String text, boolean empty) {
                    super.updateItem(text, empty); setText(empty ? null : text); setStyle("-fx-alignment: CENTER;");
                    if (!empty && getIndex() >= 0 && getIndex() < displayed.rows()) {
                        var cell = displayed.cellAt(getIndex(), index);
                        if (qualified(cell)) {
                            double shade = FxBinnedLogPlot.normalized(cell.mean(), minimum, maximum);
                            var color = javafx.scene.paint.Color.web("#d8eff4").interpolate(javafx.scene.paint.Color.web("#f3c16d"), shade);
                            setStyle(String.format(Locale.ROOT, "-fx-alignment: CENTER; -fx-background-color: rgb(%d,%d,%d); -fx-text-fill: #172c3d;", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255)));
                        }
                    }
                }
            });
            heatmap.getColumns().add(bin);
        }
        List<Integer> rows = new ArrayList<>(); for (int row = 0; row < result.rows(); row++) rows.add(row); heatmap.getItems().setAll(rows);
    }
    private boolean qualified(BinnedLogAnalysis.Cell cell) { return cell.count() >= minimumCount.getValue(); }
    private void column(String title, java.util.function.Function<BinnedLogAnalysis.Cell, String> value) {
        TableColumn<BinnedLogAnalysis.Cell, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue()))); column.setPrefWidth(100); column.setStyle("-fx-alignment: CENTER;"); table.getColumns().add(column);
    }
    private int selected(ComboBox<LogChannel> choice) {
        if (choice.getValue() == null || !dataset.getChannels().contains(choice.getValue())) throw new IllegalArgumentException("Map every required channel from this CSV.");
        return choice.getValue().getIndex();
    }
    private static double decimal(TextField text) {
        try { double value = Double.parseDouble(text.getText().trim()); if (Double.isFinite(value)) return value; } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Enter finite bin origins and positive widths in the mapped CSV units.");
    }
    private void copy() {
        if (result == null) return;
        StringBuilder text = new StringBuilder(label()).append("\nX from\tX to (exclusive)\tY from\tY to (exclusive)\tCount\tMean\tMinimum\tMaximum\tCoverage\n");
        for (var cell : result.cells()) {
            text.append(result.x().edge(result.firstX() + cell.column())).append('\t')
                    .append(result.x().edge(result.firstX() + cell.column() + 1)).append('\t')
                    .append(result.isSurface() ? Double.toString(result.y().edge(result.firstY() + cell.row())) : "—").append('\t')
                    .append(result.isSurface() ? Double.toString(result.y().edge(result.firstY() + cell.row() + 1)) : "—").append('\t')
                    .append(cell.count()).append('\t').append(qualified(cell) ? Double.toString(cell.mean()) : "—").append('\t')
                    .append(qualified(cell) ? Double.toString(cell.minimum()) : "—").append('\t')
                    .append(qualified(cell) ? Double.toString(cell.maximum()) : "—").append('\t')
                    .append(cell.count() == 0 ? "Empty" : qualified(cell) ? "Meets count" : "Low count").append('\n');
        }
        ClipboardContent content = new ClipboardContent(); content.putString(text.toString()); Clipboard.getSystemClipboard().setContent(content);
    }
    static String number(double value) {
        if (!Double.isFinite(value)) return "—";
        var decimal = java.math.BigDecimal.valueOf(value).round(new java.math.MathContext(9)).stripTrailingZeros();
        return Math.abs(value) >= 1e9 || (value != 0 && Math.abs(value) < 1e-4) ? decimal.toString() : decimal.toPlainString();
    }
    private static Tab tab(String name, javafx.scene.Node content) { Tab tab = new Tab(name, content); tab.setClosable(false); return tab; }
    private static ListCell<LogChannel> channelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(LogChannel channel, boolean empty) {
                super.updateItem(channel, empty); setText(channel == null ? "Choose CSV channel" : "[" + (channel.getIndex() + 1) + "] " + channel.getLabel());
            }
        };
    }
    @Override public void close() { if (!closed) { closed = true; invalidate(); worker.shutdownNow(); } }
}
