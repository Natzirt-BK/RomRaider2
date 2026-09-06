/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.FuelCurveAnalysis;
import com.romraider.logger.analysis.FuelLogAnalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.DoubleUnaryOperator;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

/** Review-only curve workspace; the owning analysis pane controls result lifetime. */
final class FxFuelCurvePane extends BorderPane implements AutoCloseable {
    record Row(double x, double y) { }
    private final boolean injector;
    private final Spinner<Integer> degree = new Spinner<>(1, FuelCurveAnalysis.MAX_DEGREE, 3);
    private final TextField targets = new TextField();
    private final Button interpolate = new Button("Interpolate observed bins"), fit = new Button("Fit accepted raw samples"), copy = new Button("Copy curve review");
    private final Label status = new Label();
    private final LineChart<Number, Number> chart;
    private final TableView<Row> table = new TableView<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "rr2-fuel-curve-review"); thread.setDaemon(true); return thread;
    });
    private FuelLogAnalysis.Result analysis;
    private Future<?> pending;
    private long generation;
    private boolean closed;
    private String summary = "";

    FxFuelCurvePane(boolean injector) {
        this.injector = injector;
        NumberAxis x = new NumberAxis(), y = new NumberAxis(); x.setLabel(xUnits()); y.setLabel(yUnits());
        x.setForceZeroInRange(false); y.setForceZeroInRange(false);
        chart = new LineChart<>(x, y); chart.setAnimated(false); chart.setCreateSymbols(false); chart.setLegendVisible(false);
        chart.setTitle("Calculated curve · review only"); chart.setMinSize(0, 0);
        degree.setPrefWidth(85); degree.setVisible(!injector); degree.setManaged(!injector);
        Label order = new Label(injector ? "Injector: linear fit only" : "Polynomial degree");
        FlowPane actions = new FlowPane(8, 5, interpolate, order, degree, fit, copy);
        targets.setPromptText("Optional evaluation inputs (" + xUnits() + "), separated by spaces or commas; blank = chart grid");
        status.setWrapText(true);
        Label warning = new Label("Review only. Fits can cross unsampled intervals or overfit. Interpolation preserves empty-bin gaps. No extrapolation or ROM changes."
                + (injector ? " Injector estimates are model-derived, not measured flow or battery-dependent latency." : ""));
        warning.setWrapText(true);
        VBox controls = new VBox(6, actions, targets, warning, status); controls.setPadding(new Insets(8)); setTop(controls);
        TableColumn<Row, String> input = new TableColumn<>("Input (" + xUnits() + ")");
        input.setCellValueFactory(row -> new ReadOnlyStringWrapper(number(row.getValue().x()))); input.setMinWidth(90); input.setPrefWidth(110);
        TableColumn<Row, String> output = new TableColumn<>("Calculated (" + yUnits() + ")");
        output.setCellValueFactory(row -> new ReadOnlyStringWrapper(number(row.getValue().y()))); output.setMinWidth(110); output.setPrefWidth(130);
        input.setSortable(false); output.setSortable(false); table.getColumns().addAll(input, output);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        SplitPane results = new SplitPane(chart, table); results.setDividerPositions(.62); setCenter(results);
        interpolate.setOnAction(event -> run(false)); fit.setOnAction(event -> run(true)); copy.setOnAction(event -> copy());
        targets.textProperty().addListener((value, oldValue, newValue) -> clearCurve());
        degree.valueProperty().addListener((value, oldValue, newValue) -> clearCurve());
        setAnalysis(null);
    }

    void setAnalysis(FuelLogAnalysis.Result result) {
        if (closed) return;
        analysis = result; clearCurve();
        boolean unavailable = result == null || result.getBins().isEmpty();
        interpolate.setDisable(unavailable); fit.setDisable(unavailable);
    }
    private void clearCurve() {
        generation++; if (pending != null) pending.cancel(true);
        table.getItems().clear(); chart.getData().clear(); summary = ""; copy.setDisable(true);
        status.setText(analysis == null ? "Analyze saved samples first. Changes to analysis inputs clear curve results."
                : "Choose interpolation or a fit, then review coverage and residuals. No curve is applied to a ROM.");
    }
    private void run(boolean polynomial) {
        if (closed || analysis == null) return;
        clearCurve();
        final List<Double> requested;
        try { requested = parseTargets(targets.getText()); }
        catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); return; }
        FuelLogAnalysis.Result data = analysis;
        int order = injector ? 1 : degree.getValue();
        long revision = generation;
        status.setText("Calculating review curve…");
        pending = worker.submit(() -> {
            try {
                FuelCurveAnalysis.Polynomial fitted = polynomial ? FuelCurveAnalysis.fit(data, order) : null;
                DoubleUnaryOperator evaluate = polynomial ? fitted::predict : value -> FuelCurveAnalysis.interpolate(data, value);
                double minimum = polynomial ? fitted.getMinimumX() : data.getBins().get(0).getMeanX();
                double maximum = polynomial ? fitted.getMaximumX() : data.getBins().get(data.getBins().size() - 1).getMeanX();
                List<Row> grid = new ArrayList<>();
                for (int i = 0; i <= 200; i++) {
                    if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                    double x = i == 0 ? minimum : i == 200 ? maximum : minimum * (1 - i / 200.0) + maximum * (i / 200.0);
                    grid.add(new Row(x, evaluate.applyAsDouble(x)));
                    if (minimum == maximum) break;
                }
                List<Row> rows = requested.isEmpty() ? grid : requested.stream().map(x -> new Row(x, evaluate.applyAsDouble(x))).toList();
                List<Row> drawing = polynomial ? grid : interpolationGrid(data);
                String details = polynomial ? fitSummary(fitted) : "Linear interpolation of observed bin means. Empty-bin gaps and outside-coverage inputs are unavailable.";
                details += " Input coverage: " + number(minimum) + " to " + number(maximum) + " " + xUnits() + ".";
                String message = details;
                Platform.runLater(() -> {
                    if (closed || revision != generation || data != analysis) return;
                    table.setItems(FXCollections.observableArrayList(rows));
                    chart.setCreateSymbols(!polynomial); showGrid(drawing);
                    summary = message; status.setText(message); copy.setDisable(false);
                });
            } catch (RuntimeException failure) {
                Platform.runLater(() -> { if (!closed && revision == generation) status.setText(FxDialogs.rootMessage(failure)); });
            }
        });
    }
    private String fitSummary(FuelCurveAnalysis.Polynomial fitted) {
        String result = "Degree " + fitted.getDegree() + " · " + fitted.getSamples() + " raw samples · RMSE " + number(fitted.getRmse())
                + " " + yUnits() + " · residual SD " + number(fitted.getResidualDeviation()) + " · R² " + number(fitted.getRSquared()) + ".";
        if (injector) {
            double slope = fitted.linearSlope(), intercept = fitted.linearIntercept();
            double flow = slope * 60000, offset = -intercept / slope;
            result += slope > 0 && Double.isFinite(flow) && Double.isFinite(offset)
                    ? " Apparent flow " + number(flow) + " cc/min; fitted zero-fuel intercept " + number(offset)
                            + " ms (may be outside measured coverage; not verified injector latency)."
                    : " No finite positive-flow injector estimate is supported by this line.";
        }
        return result + " Good fit statistics do not establish a safe calibration.";
    }
    private void showGrid(List<Row> grid) {
        XYChart.Series<Number, Number> segment = null;
        for (Row row : grid) {
            if (!Double.isFinite(row.y())) { segment = null; continue; }
            if (segment == null) { segment = new XYChart.Series<>(); chart.getData().add(segment); }
            segment.getData().add(new XYChart.Data<>(row.x(), row.y()));
        }
    }
    private static List<Row> interpolationGrid(FuelLogAnalysis.Result data) {
        List<Row> points = new ArrayList<>();
        FuelLogAnalysis.Bin previous = null;
        for (FuelLogAnalysis.Bin bin : data.getBins()) {
            if (previous != null && !Double.isFinite(FuelCurveAnalysis.interpolate(data,
                    previous.getMeanX() / 2 + bin.getMeanX() / 2))) points.add(new Row(Double.NaN, Double.NaN));
            points.add(new Row(bin.getMeanX(), bin.getMean())); previous = bin;
        }
        return points;
    }
    static List<Double> parseTargets(String text) {
        if (text == null || text.length() > 8192) throw new IllegalArgumentException("Evaluation inputs are limited to 8,192 characters.");
        if (text.isBlank()) return List.of();
        String[] tokens = text.trim().split("[\\s,]+");
        if (tokens.length > 512) throw new IllegalArgumentException("Use at most 512 evaluation inputs.");
        List<Double> result = new ArrayList<>();
        for (String token : tokens) {
            try {
                double value = Double.parseDouble(token);
                if (!Double.isFinite(value)) throw new NumberFormatException();
                result.add(value);
            } catch (NumberFormatException failure) { throw new IllegalArgumentException("Evaluation inputs must be finite numbers separated by spaces or commas."); }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Enter at least one evaluation input or leave the field blank.");
        return List.copyOf(result);
    }
    private void copy() {
        StringBuilder text = new StringBuilder("Read-only curve review\n").append(summary).append("\nInput (").append(xUnits())
                .append(")\tCalculated (").append(yUnits()).append(")\n");
        for (Row row : table.getItems()) text.append(number(row.x())).append('\t').append(number(row.y())).append('\n');
        ClipboardContent content = new ClipboardContent(); content.putString(text.toString()); Clipboard.getSystemClipboard().setContent(content);
    }
    private String xUnits() { return injector ? "ms" : "V"; }
    private String yUnits() { return injector ? "cc/event" : "%"; }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.8g", value) : "Unavailable"; }
    @Override public void close() { if (!closed) { closed = true; analysis = null; clearCurve(); worker.shutdownNow(); } }
}
