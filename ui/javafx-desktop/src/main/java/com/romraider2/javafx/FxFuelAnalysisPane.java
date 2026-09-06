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
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;

import com.romraider.logger.analysis.FuelLogAnalysis;
import com.romraider.logger.analysis.FuelAnalysisSetup;
import com.romraider.logger.analysis.FuelAnalysisSetupStore;
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
import javafx.stage.FileChooser;

/** Offline-only analysis; setup exports cannot write CSV or ROM destinations. */
final class FxFuelAnalysisPane extends BorderPane implements AutoCloseable {
    enum Mode { MAF, INJECTOR }
    private final Mode mode;
    private final ComboBox<LogChannel> x = choice(), y = choice(), correction = choice();
    private final TextField first = new TextField("1"), last = new TextField();
    private final TextField binWidth;
    private final TextField stoich = new TextField("14.7"), density = new TextField("732");
    private final CheckBox confirmed = new CheckBox();
    private final CheckBox linkConditions = new CheckBox("Link MAF / Injector range and filters…");
    private final Label linkStatus = new Label("Independent conditions");
    private FxFuelAnalysisLink conditionsLink;
    private FxAnalysisRangeLink rangeLink;
    private boolean copyingSharedRange;
    private final Button applySharedRange = new Button("Apply shared range");
    private final Label sharedRangeStatus = new Label();
    private final List<FilterRow> filters = new ArrayList<>();
    private final Label source = new Label("No saved log loaded");
    private final Label status = new Label("Open a CSV log, map its channels, and confirm the units.");
    private final TableView<FuelLogAnalysis.Bin> results = new TableView<>();
    private final ScatterChart<Number, Number> chart;
    private final FxFuelCurvePane curve;
    private final FxFuelRateFilterPane rateFilter;
    private final FxFuelOperatingConditionsPane operating;
    private final Button calculate = new Button("Analyze saved log");
    private final Button copy = new Button("Copy results");
    private final Button saveSetup = new Button("Save analysis setup…");
    private final Button loadSetup = new Button("Load analysis setup…");
    private final FuelAnalysisSetupStore setupStore = new FuelAnalysisSetupStore();
    private final ExecutorService setupWorker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "rr2-analysis-setup-io"); thread.setDaemon(true); return thread;
    });
    private long setupGeneration;
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
        curve = new FxFuelCurvePane(!maf);
        rateFilter = new FxFuelRateFilterPane(this::conditionsChanged);
        operating = new FxFuelOperatingConditionsPane(this::conditionsChanged);
        binWidth = new TextField(maf ? "0.05" : "0.1");
        NumberAxis horizontal = new NumberAxis(), vertical = new NumberAxis();
        horizontal.setLabel(maf ? "Observed mean MAF voltage (V)" : "Observed mean pulse width (ms)");
        vertical.setLabel(yLabel());
        chart = new ScatterChart<>(horizontal, vertical);
        chart.setAnimated(false); chart.setLegendVisible(false); chart.setMinSize(0, 0);
        chart.setTitle("Bin averages — read-only, not an applied correction");
        Label title = new Label(maf ? "MAF · saved-log analysis" : "Injector · saved-log analysis");
        title.getStyleClass().add("title");
        source.setWrapText(true);
        Button open = new Button("Open CSV log…"); open.setOnAction(event -> openLog.run());
        saveSetup.setDisable(true); loadSetup.setDisable(true);
        saveSetup.setOnAction(event -> chooseSetupFile(true));
        loadSetup.setOnAction(event -> chooseSetupFile(false));
        FlowPane actions = new FlowPane(8, 5, open, saveSetup, loadSetup);
        VBox heading = new VBox(5, title, source, actions); heading.setPadding(new Insets(10));
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
        applySharedRange.setVisible(false); applySharedRange.setManaged(false);
        sharedRangeStatus.setVisible(false); sharedRangeStatus.setManaged(false); sharedRangeStatus.setWrapText(true);
        applySharedRange.setOnAction(event -> {
            try { if (rangeLink != null) rangeLink.commit(); }
            catch (IllegalArgumentException failure) { sharedRangeStatus.setText(failure.getMessage()); }
        });
        setup.getChildren().addAll(applySharedRange, sharedRangeStatus);
        linkConditions.setDisable(true);
        linkConditions.setWrapText(true); linkStatus.setWrapText(true);
        setup.getChildren().add(new VBox(3, linkConditions, linkStatus));
        linkConditions.setOnAction(event -> {
            if (conditionsLink == null) return;
            try {
                if (!linkConditions.isSelected()) conditionsLink.disconnect();
                else conditionsLink.enable(this, () -> FxDialogs.confirmScrollable(
                        getScene() == null ? null : getScene().getWindow(), "Link analysis conditions?",
                        "Use the current " + mode + " conditions in BOTH tabs? This replaces the other tab's range and filters.\n\n"
                                + conditionsDraft().summary()
                                + "\n\nFurther range/filter edits in either tab update both. Existing results and unit confirmations are cleared."
                                + " Channel mappings, bin widths and fuel assumptions stay independent. Loading a setup or log breaks the link.",
                        "Link conditions"));
            } catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); }
            finally { conditionsLink.refresh(); }
        });
        for (int index = 1; index <= 3; index++) {
            FilterRow filter = new FilterRow(); filters.add(filter);
            setup.getChildren().add(new VBox(3, new Label("Filter " + index + " (optional)"),
                    filter.channel, new HBox(5, filter.minimum, filter.maximum)));
        }
        confirmed.setText(maf
                ? "I confirmed volts/percent units and any enabled filter units/time scale. Signal units are not converted."
                : "I confirmed ms and g/rev units, fuel properties, the legacy four-cylinder assumption (load ÷ 2), and any enabled filter units/time scale. Defaults are examples, not detected fuel properties.");
        confirmed.setWrapText(true);
        calculate.setMaxWidth(Double.MAX_VALUE); calculate.setDisable(true);
        calculate.setOnAction(event -> analyze());
        status.setWrapText(true);
        setup.getChildren().addAll(operating, rateFilter, confirmed, calculate, status);
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
        TabPane output = new TabPane(tab("Binned results", table), tab("Chart", chart), tab("Curve review", curve));
        output.setMinSize(0, 0); setCenter(output);

        for (ComboBox<LogChannel> mappingChoice : List.of(x, y, correction)) {
            mappingChoice.valueProperty().addListener((value, oldValue, newValue) -> {
                confirmed.setSelected(false); invalidate();
            });
        }
        binWidth.textProperty().addListener((value, oldValue, newValue) -> invalidate());
        for (TextField text : List.of(stoich, density)) {
            text.textProperty().addListener((value, oldValue, newValue) -> {
                confirmed.setSelected(false); invalidate();
            });
        }
        for (TextField text : List.of(first, last)) {
            text.textProperty().addListener((value, oldValue, newValue) -> {
                if (copyingSharedRange) return;
                if (rangeLink != null && rangeLink.isLinked()) rangeLink.draftChanged(this);
                else conditionsChanged();
            });
        }
        confirmed.selectedProperty().addListener((value, oldValue, newValue) -> invalidate());
        for (FilterRow filter : filters) {
            filter.channel.valueProperty().addListener((value, oldValue, newValue) -> conditionsChanged());
            filter.minimum.textProperty().addListener((value, oldValue, newValue) -> conditionsChanged());
            filter.maximum.textProperty().addListener((value, oldValue, newValue) -> conditionsChanged());
        }
    }

    void setTransferTarget(java.util.function.Supplier<FxMafTransferTarget> target) { curve.setTransferTarget(target); }

    void setDataset(LogDataset next) {
        if (closed) return;
        if (rangeLink != null) rangeLink.close();
        if (conditionsLink != null) conditionsLink.disconnect();
        invalidate(); dataset = next;
        rateFilter.setDataset(next);
        operating.setDataset(next);
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
        calculate.setDisable(false); saveSetup.setDisable(false); loadSetup.setDisable(false); invalidate();
        if (conditionsLink != null) conditionsLink.refresh();
    }

    void attachConditionsLink(FxFuelAnalysisLink link) { conditionsLink = link; showConditionsLink(false); }
    boolean conditionsAvailable() { return !closed && dataset != null; }
    long inputRevision() { return generation; }
    FxFuelAnalysisLink.Draft conditionsDraft() {
        return new FxFuelAnalysisLink.Draft(dataset, first.getText(), last.getText(), filters.stream()
                .map(row -> new FxFuelAnalysisLink.FilterDraft(row.channel.getValue(), row.minimum.getText(), row.maximum.getText())).toList(), rateFilter.draft(), operating.draft());
    }
    void applyConditionsDraft(FxFuelAnalysisLink.Draft draft) {
        if (!conditionsAvailable() || draft.dataset() != dataset || draft.filters().size() != filters.size()) {
            throw new IllegalArgumentException("Conditions do not match this dataset or filter layout.");
        }
        invalidateConditions();
        copyingSharedRange = true;
        try { first.setText(draft.first()); last.setText(draft.last()); }
        finally { copyingSharedRange = false; }
        rateFilter.apply(draft.rate());
        operating.apply(draft.operating());
        for (int i = 0; i < filters.size(); i++) {
            FilterRow row = filters.get(i);
            FxFuelAnalysisLink.FilterDraft next = draft.filters().get(i);
            row.channel.setValue(next.channel()); row.channel.setTooltip(null);
            row.minimum.setText(next.minimum()); row.maximum.setText(next.maximum());
        }
        if (rangeLink != null && rangeLink.isLinked()) rangeLink.draftChanged(this);
    }
    FxAnalysisRangeLink.Draft rangeDraft() { return new FxAnalysisRangeLink.Draft(first.getText(), last.getText()); }
    void attachRangeLink(FxAnalysisRangeLink link) { rangeLink = link; showRangeLink(false, false); }
    void copySharedRange(FxAnalysisRangeLink.Draft draft) {
        copyingSharedRange = true;
        try { first.setText(draft.first()); last.setText(draft.last()); }
        finally { copyingSharedRange = false; }
        invalidateConditions();
    }
    void showRangeLink(boolean linked, boolean applied) {
        applySharedRange.setVisible(linked); applySharedRange.setManaged(linked);
        sharedRangeStatus.setVisible(linked); sharedRangeStatus.setManaged(linked);
        sharedRangeStatus.setText(applied ? "Shared range applied. Confirm units before analysis. Fuel filters do not change Log Analysis statistics."
                : "Shared range draft. Apply it to refresh all three workspaces; fuel filters remain separate.");
    }
    void invalidateConditions() { confirmed.setSelected(false); invalidate(); }
    private void conditionsChanged() {
        invalidateConditions();
        if (conditionsLink != null) conditionsLink.changed(this);
    }
    void showConditionsLink(boolean linked) {
        linkConditions.setSelected(linked);
        linkConditions.setDisable(conditionsLink == null || !conditionsLink.available());
        linkStatus.setText(linked ? "Linked · edits update both tabs; each tab still needs unit confirmation."
                : "Independent conditions · linking requires review; setup/log changes disconnect.");
    }

    private void chooseSetupFile(boolean save) {
        if (closed || dataset == null) return;
        // Validate before opening a chooser, without running any analysis.
        try { if (save) snapshotSetup(); }
        catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); return; }
        long revision = generation;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(save ? "Save analysis setup" : "Load analysis setup for review");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("RomRaider2 analysis setup", "*.rr2analysis"));
        if (save) chooser.setInitialFileName(mode.name().toLowerCase(Locale.ROOT) + FuelAnalysisSetupStore.EXTENSION);
        var owner = getScene() == null ? null : getScene().getWindow();
        File file = save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
        if (file == null || closed) return;
        if (save && !file.getName().contains(".")) file = new File(file.getParentFile(), file.getName() + FuelAnalysisSetupStore.EXTENSION);
        if (save && Files.exists(file.toPath()) && !FxDialogs.confirm(owner, "Replace analysis setup?",
                "Replace only this setup file? Captured logs and ROMs are not changed.", "Replace setup")) return;
        if (closed || revision != generation) { status.setText("Inputs changed while choosing a file. Try again with the current setup."); return; }
        if (save) saveSetup(file.toPath()); else loadSetup(file.toPath());
    }

    FuelAnalysisSetup snapshotSetup() {
        if (closed || dataset == null) throw new IllegalArgumentException("Open a CSV log first");
        // A stale/out-of-range visible range is an input error, even though ranges
        // are intentionally not portable and are not written to the setup file.
        LogRange.of(integer(first, "First sample") - 1, integer(last, "Last sample"), dataset.getRowCount());
        var savedFilters = new ArrayList<FuelAnalysisSetup.Filter>();
        for (FilterRow filter : filters) {
            if (filter.channel.getValue() == null) {
                if (!filter.minimum.getText().isBlank() || !filter.maximum.getText().isBlank()) {
                    throw new IllegalArgumentException("Select a channel for each entered filter, or clear its limits.");
                }
            } else savedFilters.add(new FuelAnalysisSetup.Filter(FuelAnalysisSetup.Channel.of(filter.channel.getValue()),
                    decimal(filter.minimum, "Filter minimum"), decimal(filter.maximum, "Filter maximum")));
        }
        return new FuelAnalysisSetup(FuelAnalysisSetup.Kind.valueOf(mode.name()),
                FuelAnalysisSetup.Channel.of(x.getValue()), FuelAnalysisSetup.Channel.of(y.getValue()),
                mode == Mode.MAF ? FuelAnalysisSetup.Channel.of(correction.getValue()) : null,
                decimal(binWidth, "Bin width"), decimal(stoich, "Stoichiometric AFR"),
                decimal(density, "Fuel density"), savedFilters, rateFilter.setup(dataset), operating.draft().setup(dataset));
    }

    Future<?> saveSetup(Path target) {
        final FuelAnalysisSetup setup;
        try { setup = snapshotSetup(); }
        catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); return null; }
        long revision = generation, operation = ++setupGeneration;
        status.setText("Saving analysis setup…");
        return setupWorker.submit(() -> {
            String message;
            try { setupStore.write(target, setup); message = "Analysis setup saved. CSV data, sample ranges and unit confirmation are not included."; }
            catch (Exception failure) { message = "Setup was not saved: " + FxDialogs.rootMessage(failure); }
            String outcome = message;
            Platform.runLater(() -> { if (!closed && revision == generation && operation == setupGeneration) status.setText(outcome); });
        });
    }

    Future<?> loadSetup(Path input) {
        if (closed || dataset == null) return null;
        long revision = generation, operation = ++setupGeneration;
        status.setText("Loading analysis setup for review…");
        return setupWorker.submit(() -> {
            try {
                FuelAnalysisSetup setup = setupStore.read(input);
                Platform.runLater(() -> {
                    if (closed || operation != setupGeneration) return;
                    if (revision != generation) { status.setText("Setup load skipped because the log or inputs changed. Load again to review it."); return; }
                    try { applySetup(setup); }
                    catch (IllegalArgumentException failure) { status.setText(failure.getMessage()); }
                });
            } catch (Exception failure) {
                Platform.runLater(() -> { if (!closed && revision == generation && operation == setupGeneration)
                    status.setText("Setup was not loaded; existing inputs are unchanged. " + FxDialogs.rootMessage(failure)); });
            }
        });
    }

    void applySetup(FuelAnalysisSetup setup) {
        if (closed || dataset == null) throw new IllegalArgumentException("Open a CSV log first");
        if (!setup.kind().name().equals(mode.name())) throw new IllegalArgumentException("This setup belongs in the " + setup.kind() + " tab; existing inputs are unchanged.");
        if (rangeLink != null) rangeLink.disconnect();
        if (conditionsLink != null) conditionsLink.disconnect();
        invalidate(); confirmed.setSelected(false);
        List<String> unresolved = new ArrayList<>();
        restoreChannel(x, setup.x(), unresolved); restoreChannel(y, setup.y(), unresolved);
        if (setup.correction() != null) restoreChannel(correction, setup.correction(), unresolved);
        binWidth.setText(Double.toString(setup.binWidth()));
        stoich.setText(Double.toString(setup.stoichAfr())); density.setText(Double.toString(setup.fuelDensity()));
        for (int i = 0; i < filters.size(); i++) {
            FilterRow row = filters.get(i);
            if (i < setup.filters().size()) {
                var filter = setup.filters().get(i);
                restoreChannel(row.channel, filter.channel(), unresolved);
                row.minimum.setText(Double.toString(filter.minimum())); row.maximum.setText(Double.toString(filter.maximum()));
            } else { row.channel.setValue(null); row.minimum.clear(); row.maximum.clear(); row.channel.setTooltip(null); }
        }
        rateFilter.restore(setup.rate(), dataset, unresolved);
        operating.restore(setup.conditions(), dataset, unresolved);
        first.setText("1"); last.setText(Integer.toString(dataset.getRowCount()));
        status.setText("Setup loaded for review. Sample range reset to all " + dataset.getRowCount()
                + " rows; review the range, units, filters and assumptions before analyzing."
                + (unresolved.isEmpty() ? "" : " Remap missing or ambiguous channels: " + String.join(", ", unresolved)));
    }

    private void restoreChannel(ComboBox<LogChannel> choice, FuelAnalysisSetup.Channel saved, List<String> unresolved) {
        LogChannel resolved = saved.resolve(dataset);
        choice.setValue(resolved); choice.setTooltip(new Tooltip("Saved channel: " + saved.label() + " · units: " + saved.units()));
        if (resolved == null) unresolved.add(saved.label());
    }

    private void invalidate() {
        generation++;
        if (pending != null) pending.cancel(true);
        results.getItems().clear(); chart.getData().clear(); copy.setDisable(true);
        curve.setAnalysis(null);
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
                        series.getData().add(new XYChart.Data<>(bin.getMeanX(), bin.getMean()));
                    }
                    chart.getData().setAll(List.of(series));
                    curve.setAnalysis(result);
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
        if (rangeLink != null) rangeLink.requireApplied();
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
        selectedFilters.addAll(operating.draft().filters(input));
        if (mode == Mode.MAF) {
            int correctionIndex = selected(correction);
            var rate = rateFilter.draft().filter(input);
            return () -> FuelLogAnalysis.maf(input, range, xIndex, yIndex, correctionIndex, width, selectedFilters, rate);
        }
        double afr = decimal(stoich, "Stoichiometric AFR"), fuelDensity = decimal(density, "Fuel density");
        var rate = rateFilter.draft().filter(input);
        return () -> FuelLogAnalysis.injector(input, range, xIndex, yIndex, afr, fuelDensity, width, selectedFilters, rate);
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
    private int selected(ComboBox<LogChannel> choice) {
        if (choice.getValue() == null) throw new IllegalArgumentException("Map every required channel before analyzing.");
        int index = choice.getValue().getIndex();
        if (dataset == null || index < 0 || index >= dataset.getChannels().size()
                || dataset.getChannels().get(index) != choice.getValue() || choice.getValue().isTimeChannel()) {
            throw new IllegalArgumentException("Map channels from the current dataset before analyzing.");
        }
        return index;
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
                setText(channel == null ? "None / choose channel" : "[" + (channel.getIndex() + 1) + "] " + channel.getLabel());
                setTooltip(channel == null ? null : new Tooltip("CSV column " + (channel.getIndex() + 1) + ": " + channel.getLabel()));
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
        if (rangeLink != null) rangeLink.close();
        if (conditionsLink != null) conditionsLink.disconnect();
        closed = true; invalidate(); dataset = null; worker.shutdownNow();
        curve.close();
        if (conditionsLink != null) conditionsLink.refresh(); else showConditionsLink(false);
        // Complete already-requested atomic exports; closed/load-generation guards
        // prevent any queued import from mutating a disposed pane.
        setupWorker.shutdown();
    }
}
