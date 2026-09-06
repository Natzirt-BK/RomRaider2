/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import com.romraider.logger.analysis.FuelLogAnalysis;
import com.romraider.logger.analysis.LogChannel;
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.LogRange;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

/** Offline-only data inspection. No runtime, transport, ROM editor or file writer. */
final class FxFuelAnalysisPane extends BorderPane implements AutoCloseable {
    enum Mode { MAF, INJECTOR }
    private final Mode mode;
    private final ComboBox<LogChannel> x = choice(), y = choice(), correction = choice();
    private final TextField first = new TextField("1"), last = new TextField();
    private final TextField binWidth;
    private final TextField stoich = new TextField("14.7"), density = new TextField("732");
    private final CheckBox confirmed = new CheckBox();
    private final List<FilterRow> filters = new ArrayList<>();
    private final Label source = new Label("No saved log loaded");
    private final Label status = new Label("Open a CSV log, map its channels, and confirm the units.");
    private final TableView<FuelLogAnalysis.Bin> results = new TableView<>();
    private final ScatterChart<Number, Number> chart;
    private final Button calculate = new Button("Analyze saved log");
    private final Button copy = new Button("Copy results");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "rr2-offline-fuel-analysis");
        thread.setDaemon(true); return thread;
    });
    private Future<?> pending;
    private long generation;
    private boolean closed;
    private LogDataset dataset;

    FxFuelAnalysisPane(Mode mode, Runnable openLog) {
        this.mode = mode;
        boolean maf = mode == Mode.MAF;
        binWidth = new TextField(maf ? "0.05" : "0.1");
        NumberAxis horizontal = new NumberAxis(), vertical = new NumberAxis();
        horizontal.setLabel(maf ? "MAF voltage bin midpoint (V)" : "Pulse-width bin midpoint (ms)");
        vertical.setLabel(yLabel());
        chart = new ScatterChart<>(horizontal, vertical);
        chart.setAnimated(false); chart.setLegendVisible(false); chart.setMinSize(0, 0);
        chart.setTitle("Bin averages — read-only, not an applied correction");
        Label title = new Label(maf ? "MAF · saved-log analysis" : "Injector · saved-log analysis");
        title.getStyleClass().add("title");
        source.setWrapText(true);
        Button open = new Button("Open CSV log…"); open.setOnAction(event -> openLog.run());
        VBox heading = new VBox(5, title, source, open); heading.setPadding(new Insets(10));
        setTop(heading);

        GridPane mapping = new GridPane(); mapping.setHgap(8); mapping.setVgap(7);
        mapping.addRow(0, new Label(maf ? "MAF voltage (V)" : "Pulse width (ms)"), x);
        mapping.addRow(1, new Label(maf ? "A/F learning (%)" : "Engine load (g/rev)"), y);
        if (maf) mapping.addRow(2, new Label("A/F correction (%)"), correction);
        else {
            mapping.addRow(2, new Label("Stoichiometric AFR"), stoich);
            mapping.addRow(3, new Label("Fuel density (g/L)"), density);
        }
        mapping.addRow(4, new Label("First sample (1-based)"), first);
        mapping.addRow(5, new Label("Last sample (inclusive)"), last);
        mapping.addRow(6, new Label(maf ? "Bin width (V)" : "Bin width (ms)"), binWidth);
        Label limits = new Label("No automatic operating-condition filters. Choose a suitable sample range and add filters for closed-loop state, temperatures or other conditions as needed. Missing filter values are rejected.");
        limits.setWrapText(true);
        VBox setup = new VBox(10, mapping, limits);
        for (int index = 1; index <= 3; index++) {
            FilterRow filter = new FilterRow(); filters.add(filter);
            setup.getChildren().add(new VBox(3, new Label("Filter " + index + " (optional)"),
                    filter.channel, new HBox(5, filter.minimum, filter.maximum)));
        }
        confirmed.setText(maf
                ? "I confirmed volts and percent units for these channels (no unit conversion)."
                : "I confirmed ms and g/rev units, fuel properties, and the legacy four-cylinder assumption (load ÷ 2). Defaults are examples, not detected fuel properties.");
        confirmed.setWrapText(true);
        calculate.setMaxWidth(Double.MAX_VALUE); calculate.setDisable(true);
        calculate.setOnAction(event -> analyze());
        status.setWrapText(true);
        setup.getChildren().addAll(confirmed, calculate, status);
        setup.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(setup); scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(340); scroll.setMinWidth(270); scroll.setMinHeight(0);
        setLeft(scroll);

        addColumn("Bin from (" + xUnits() + ")", bin -> number(bin.getLower()));
        addColumn("Bin to (exclusive)", bin -> number(bin.getUpper()));
        addColumn("Samples", bin -> Integer.toString(bin.getCount()));
        addColumn("Mean (" + yUnits() + ")", bin -> number(bin.getMean()));
        addColumn("Minimum", bin -> number(bin.getMinimum()));
        addColumn("Maximum", bin -> number(bin.getMaximum()));
        copy.setDisable(true); copy.setOnAction(event -> copyResults());
        BorderPane table = new BorderPane(results); table.setBottom(copy);
        TabPane output = new TabPane(tab("Binned results", table), tab("Chart", chart));
        output.setMinSize(0, 0); setCenter(output);

        for (ComboBox<LogChannel> mappingChoice : List.of(x, y, correction)) {
            mappingChoice.valueProperty().addListener((value, oldValue, newValue) -> {
                confirmed.setSelected(false); invalidate();
            });
        }
        for (TextField text : List.of(first, last, binWidth, stoich, density)) {
            text.textProperty().addListener((value, oldValue, newValue) -> invalidate());
        }
        confirmed.selectedProperty().addListener((value, oldValue, newValue) -> invalidate());
        for (FilterRow filter : filters) {
            filter.channel.valueProperty().addListener((value, oldValue, newValue) -> invalidate());
            filter.minimum.textProperty().addListener((value, oldValue, newValue) -> invalidate());
            filter.maximum.textProperty().addListener((value, oldValue, newValue) -> invalidate());
        }
    }

    void setDataset(LogDataset next) {
        if (closed) return;
        invalidate(); dataset = next;
        List<LogChannel> channels = next.getChannels().stream().filter(channel -> !channel.isTimeChannel()).toList();
        for (ComboBox<LogChannel> mapping : List.of(x, y, correction)) {
            mapping.setItems(FXCollections.observableArrayList(channels)); mapping.setValue(null);
        }
        for (FilterRow filter : filters) {
            filter.channel.getItems().clear(); filter.channel.getItems().add(null);
            filter.channel.getItems().addAll(channels); filter.channel.setValue(null);
            filter.minimum.clear(); filter.maximum.clear();
        }
        first.setText("1"); last.setText(Integer.toString(next.getRowCount()));
        confirmed.setSelected(false);
        source.setText(next.getSourceName() + " · " + next.getRowCount() + " samples · saved data only");
        calculate.setDisable(false); invalidate();
    }

    private void invalidate() {
        generation++;
        if (pending != null) pending.cancel(true);
        results.getItems().clear(); chart.getData().clear(); copy.setDisable(true);
        status.setText(dataset == null ? "Open a CSV log to begin."
                : "Inputs changed. Confirm mappings and analyze to refresh results.");
    }

    private void analyze() {
        if (closed || dataset == null) return;
        invalidate();
        final Supplier<FuelLogAnalysis.Result> request;
        try { request = request(); }
        catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); return; }
        long revision = generation;
        status.setText("Analyzing saved samples…");
        pending = worker.submit(() -> {
            try {
                FuelLogAnalysis.Result result = request.get();
                Platform.runLater(() -> {
                    if (closed || revision != generation) return;
                    results.setItems(FXCollections.observableArrayList(result.getBins()));
                    XYChart.Series<Number, Number> series = new XYChart.Series<>();
                    for (FuelLogAnalysis.Bin bin : result.getBins()) {
                        series.getData().add(new XYChart.Data<>(bin.getLower() / 2 + bin.getUpper() / 2, bin.getMean()));
                    }
                    chart.getData().setAll(List.of(series));
                    copy.setDisable(result.getBins().isEmpty());
                    status.setText(result.getAccepted() + " accepted · " + result.getFiltered()
                            + " filtered · " + result.getInvalid() + " invalid · " + result.getBins().size()
                            + " bins. " + (result.getAccepted() == 0 ? "No usable samples." : "Review counts and conditions; these are not ready-to-apply tuning values."));
                });
            } catch (RuntimeException failure) {
                Platform.runLater(() -> { if (!closed && revision == generation) status.setText(FxDialogs.rootMessage(failure)); });
            }
        });
    }

    private Supplier<FuelLogAnalysis.Result> request() {
        if (!confirmed.isSelected()) throw new IllegalArgumentException("Confirm the selected units and assumptions first.");
        LogDataset input = dataset;
        int from = integer(first, "First sample"), to = integer(last, "Last sample");
        LogRange range = LogRange.of(from - 1, to, input.getRowCount());
        int xIndex = selected(x), yIndex = selected(y);
        double width = decimal(binWidth, "Bin width");
        List<FuelLogAnalysis.Filter> selectedFilters = new ArrayList<>();
        for (FilterRow filter : filters) {
            if (filter.channel.getValue() == null) {
                if (!filter.minimum.getText().isBlank() || !filter.maximum.getText().isBlank()) {
                    throw new IllegalArgumentException("Select a channel for each entered filter, or clear its limits.");
                }
                continue;
            }
            selectedFilters.add(new FuelLogAnalysis.Filter(selected(filter.channel),
                    decimal(filter.minimum, "Filter minimum"), decimal(filter.maximum, "Filter maximum")));
        }
        if (mode == Mode.MAF) {
            int correctionIndex = selected(correction);
            return () -> FuelLogAnalysis.maf(input, range, xIndex, yIndex, correctionIndex, width, selectedFilters);
        }
        double afr = decimal(stoich, "Stoichiometric AFR"), fuelDensity = decimal(density, "Fuel density");
        return () -> FuelLogAnalysis.injector(input, range, xIndex, yIndex, afr, fuelDensity, width, selectedFilters);
    }

    private void copyResults() {
        StringBuilder text = new StringBuilder("Bin from (" + xUnits() + ")\tBin to (exclusive)\tSamples\tMean (" + yUnits() + ")\tMinimum\tMaximum\n");
        for (FuelLogAnalysis.Bin bin : results.getItems()) {
            text.append(bin.getLower()).append('\t').append(bin.getUpper()).append('\t')
                    .append(bin.getCount()).append('\t').append(bin.getMean()).append('\t')
                    .append(bin.getMinimum()).append('\t').append(bin.getMaximum()).append('\n');
        }
        ClipboardContent content = new ClipboardContent(); content.putString(text.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String xUnits() { return mode == Mode.MAF ? "V" : "ms"; }
    private String yUnits() { return mode == Mode.MAF ? "%" : "cc/event"; }
    private String yLabel() { return mode == Mode.MAF ? "A/F learning + correction (%)" : "Estimated fuel per combustion event (cc)"; }
    private static String number(double value) { return String.format(Locale.ROOT, "%.6g", value); }
    private void addColumn(String label, Function<FuelLogAnalysis.Bin, String> format) {
        TableColumn<FuelLogAnalysis.Bin, String> column = new TableColumn<>(label);
        column.setCellValueFactory(row -> new ReadOnlyStringWrapper(format.apply(row.getValue())));
        column.setSortable(false); column.setPrefWidth(125); results.getColumns().add(column);
    }
    private static Tab tab(String name, javafx.scene.Node content) { Tab tab = new Tab(name, content); tab.setClosable(false); return tab; }
    private static int selected(ComboBox<LogChannel> choice) {
        if (choice.getValue() == null) throw new IllegalArgumentException("Map every required channel before analyzing.");
        return choice.getValue().getIndex();
    }
    private static int integer(TextField field, String name) {
        try { return Integer.parseInt(field.getText().trim()); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException(name + " must be a whole sample number"); }
    }
    private static double decimal(TextField field, String name) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (Double.isFinite(value)) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(name + " must be a finite number");
    }
    private static ComboBox<LogChannel> choice() {
        ComboBox<LogChannel> choice = new ComboBox<>();
        choice.setPromptText("Choose channel"); choice.setPrefWidth(200); choice.setMaxWidth(Double.MAX_VALUE);
        choice.setCellFactory(ignored -> channelCell()); choice.setButtonCell(channelCell());
        return choice;
    }
    private static ListCell<LogChannel> channelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(LogChannel channel, boolean empty) {
                super.updateItem(channel, empty);
                setText(channel == null ? "None / choose channel" : channel.getLabel());
                setTooltip(channel == null ? null : new Tooltip(channel.getLabel()));
            }
        };
    }
    private static final class FilterRow {
        final ComboBox<LogChannel> channel = choice();
        final TextField minimum = new TextField(), maximum = new TextField();
        FilterRow() {
            minimum.setPromptText("Minimum (inclusive)"); maximum.setPromptText("Maximum (inclusive)");
            minimum.setPrefColumnCount(8); maximum.setPrefColumnCount(8);
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; invalidate(); dataset = null; worker.shutdownNow();
    }
}
