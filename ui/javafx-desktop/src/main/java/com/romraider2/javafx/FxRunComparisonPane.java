/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/** Two explicitly selected saved runs. B imports never replace the main log workspace. */
final class FxRunComparisonPane extends BorderPane implements AutoCloseable {
    @FunctionalInterface interface CsvLoader { LogDataset load(File file) throws IOException; }
    private final LogDataset a;
    private LogDataset b;
    private LogRange rangeA;
    private final CsvLoader loader;
    private final ComboBox<LogChannel> ax = new ComboBox<>(), av = new ComboBox<>(), bx = new ComboBox<>(), bv = new ComboBox<>();
    private final TextField firstB = new TextField("1"), lastB = new TextField(), origin = new TextField("0"), width = new TextField();
    private final Spinner<Integer> minimumCount = new Spinner<>(1, BinnedLogAnalysis.MAX_ROWS, 1);
    private final CheckBox confirmed = new CheckBox("I confirmed corresponding X/value channels, matching units and comparable run conditions. No unit conversion or time alignment is inferred.");
    private final CheckBox difference = new CheckBox("Plot B − A");
    private final ToggleButton hideSetup = new ToggleButton("Hide setup");
    private final Button compare = new Button("Compare runs"), copy = new Button("Copy comparison");
    private final Label identity = new Label(), status = new Label();
    private final TableView<LogRunComparison.Row> table = new TableView<>();
    private final FxRunComparisonPlot plot = new FxRunComparisonPlot();
    private final ExecutorService worker = executor("rr2-run-comparison"), imports = executor("rr2-comparison-csv-import");
    private Future<?> pending, loading;
    private long generation, loadGeneration;
    private boolean closed, rangePending;
    private LogRunComparison.Result result;
    private String completedSummary = "";

    FxRunComparisonPane(LogDataset data) { this(data, file -> new RomRaiderCsvLogParser().parse(file, RomRaiderCsvLogParser.REVIEW_LIMITS)); }
    FxRunComparisonPane(LogDataset data, CsvLoader loader) {
        a = data; rangeA = LogRange.all(data); this.loader = loader;
        Button open = new Button("Open run B CSV…"), same = new Button("Use this CSV for B");
        open.setOnAction(event -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("Open comparison run B"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV logs", "*.csv"));
            loadB(chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow()));
        });
        same.setOnAction(event -> { cancelImport(); installB(a); });
        identity.setWrapText(true); status.setWrapText(true); confirmed.setWrapText(true);
        for (ComboBox<LogChannel> choice : List.of(ax, av, bx, bv)) {
            choice.setPrefWidth(220); choice.setPromptText("Choose CSV channel"); choice.setCellFactory(ignored -> channelCell()); choice.setButtonCell(channelCell());
            choice.valueProperty().addListener((v, before, after) -> { confirmed.setSelected(false); invalidate(); });
        }
        ax.getItems().setAll(a.getChannels()); av.getItems().setAll(a.getChannels());
        for (TextField field : List.of(firstB, lastB, origin, width)) {
            field.setPrefColumnCount(7); field.textProperty().addListener((v, before, after) -> { confirmed.setSelected(false); invalidate(); });
        }
        width.setPromptText("Required"); minimumCount.setPrefWidth(105); minimumCount.valueProperty().addListener((v, before, after) -> invalidate());
        confirmed.selectedProperty().addListener((v, before, after) -> invalidate());
        difference.selectedProperty().addListener((v, before, after) -> plot.show(result, after));
        hideSetup.selectedProperty().addListener((v, before, after) -> hideSetup.setText(after ? "Show setup" : "Hide setup"));
        compare.setOnAction(event -> compare()); copy.setOnAction(event -> copy());
        VBox setup = new VBox(6,
                new FlowPane(7, 5, new Label("A: X"), ax, new Label("Value"), av),
                new FlowPane(7, 5, new Label("B: X"), bx, new Label("Value"), bv, new Label("Samples"), firstB, new Label("through"), lastB),
                new FlowPane(7, 5, new Label("Common X origin"), origin, new Label("Width"), width, new Label("Minimum count per run"), minimumCount), confirmed);
        setup.visibleProperty().bind(hideSetup.selectedProperty().not()); setup.managedProperty().bind(setup.visibleProperty());
        VBox controls = new VBox(6, new FlowPane(7, 5, open, same), identity, setup,
                new FlowPane(7, 5, compare, copy, difference, hideSetup), status);
        controls.setPadding(new Insets(8));
        ScrollPane settings = new ScrollPane(controls); settings.setFitToWidth(true); settings.setMinHeight(0);
        settings.prefViewportHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.min(controls.prefHeight(Math.max(300, getWidth() - 20)), Math.max(120, getHeight() * .6)),
                widthProperty(), heightProperty(), hideSetup.selectedProperty(), identity.textProperty(), status.textProperty()));
        setTop(settings);
        column("X from", row -> number(row.lower())); column("X to (excl.)", row -> number(row.upper()));
        column("A count", row -> Integer.toString(row.countA())); column("B count", row -> Integer.toString(row.countB()));
        column("A mean", row -> number(row.meanA())); column("B mean", row -> number(row.meanB())); column("B − A", row -> number(row.difference()));
        column("Coverage", row -> coverage(row.coverage()));
        table.setMinSize(0, 0); TabPane views = new TabPane(tab("Comparison table", table), tab("Run plots", plot)); setCenter(views);
        setRange(rangeA, false);
    }
    void setRange(LogRange range, boolean pending) {
        if (closed) return; rangeA = range; rangePending = pending; confirmed.setSelected(false); invalidate(); identify();
        compare.setDisable(pending);
    }
    private void identify() {
        identity.setText("A: " + oneLine(a.getSourceName()) + " · applied samples " + (rangeA.getStartInclusive() + 1) + "–" + rangeA.getEndExclusive()
                + (rangePending ? " (draft; Apply required)" : "") + "\nB: " + (b == null ? "choose a CSV or reuse A with another range" : oneLine(b.getSourceName()) + " · " + b.getRowCount() + " available samples")
                + " · comparison uses original values, not MAF/Injector filters");
    }
    void loadB(File file) {
        if (closed || file == null) return;
        cancelImport(); long ticket = loadGeneration, revision = generation; status.setText("Loading run B with review limits… Current run B is retained until success.");
        loading = imports.submit(() -> {
            try {
                LogDataset loaded = loader.load(file);
                if (loaded == null) throw new IOException("No CSV dataset was returned.");
                Platform.runLater(() -> {
                    if (closed || loadGeneration != ticket) return;
                    if (generation != revision) { status.setText("Inputs changed while run B was loading. Previous run B retained; choose the file again."); return; }
                    installB(loaded);
                });
            } catch (IOException | RuntimeException failure) {
                Platform.runLater(() -> { if (!closed && loadGeneration == ticket) status.setText("Run B import failed; previous run B retained. " + FxDialogs.rootMessage(failure)); });
            }
        });
    }
    private void cancelImport() { loadGeneration++; if (loading != null) loading.cancel(true); }
    void installB(LogDataset data) {
        if (closed) return;
        b = Objects.requireNonNull(data); confirmed.setSelected(false); bx.getItems().setAll(b.getChannels()); bv.getItems().setAll(b.getChannels());
        bx.setValue(null); bv.setValue(null); firstB.setText("1"); lastB.setText(Integer.toString(b.getRowCount())); invalidate(); identify();
    }
    private void invalidate() {
        generation++; if (pending != null) pending.cancel(true); table.getItems().clear(); result = null; completedSummary = ""; plot.show(null, difference.isSelected()); copy.setDisable(true);
        status.setText("Map both runs, set the ranges/common bins and confirm before comparison.");
    }
    private void compare() {
        if (closed || rangePending) return; invalidate();
        final LogRunComparison.Run runA, runB; final double start, step;
        try {
            if (b == null || !confirmed.isSelected()) throw new IllegalArgumentException("Load run B and confirm channel/unit correspondence and run conditions first.");
            runA = new LogRunComparison.Run(a, rangeA, selected(ax, a), selected(av, a));
            runB = new LogRunComparison.Run(b, LogRange.of(Integer.parseInt(firstB.getText().trim()) - 1, Integer.parseInt(lastB.getText().trim()), b.getRowCount()), selected(bx, b), selected(bv, b));
            start = Double.parseDouble(origin.getText().trim()); step = Double.parseDouble(width.getText().trim());
        } catch (IllegalArgumentException failure) { status.setText("Review mappings, inclusive B range and bin settings: " + FxDialogs.rootMessage(failure)); return; }
        int threshold = minimumCount.getValue(); long revision = generation; status.setText("Comparing independent runs in common X bins…");
        pending = worker.submit(() -> {
            try {
                LogRunComparison.Result completed = LogRunComparison.compare(runA, runB, start, step, threshold);
                Platform.runLater(() -> {
                    if (closed || generation != revision) return;
                    result = completed; table.getItems().setAll(completed.rows()); copy.setDisable(completed.rows().isEmpty()); plot.show(completed, difference.isSelected());
                    completedSummary = runSummary("A", runA) + "\n" + runSummary("B", runB)
                            + "\nCommon X origin: " + start + " · width: " + step + " · minimum count per run: " + threshold + " · difference: B minus A";
                    status.setText("A: " + completed.acceptedA() + " accepted / " + completed.invalidA() + " invalid · B: " + completed.acceptedB() + " / " + completed.invalidB()
                            + " · B samples " + (runB.range().getStartInclusive() + 1) + "–" + runB.range().getEndExclusive()
                            + " · X (" + oneLine(completed.xUnits()) + "), values (" + oneLine(completed.valueUnits()) + ") · B − A only where both counts ≥ " + threshold + ". Not a causal or tuning recommendation.");
                });
            } catch (RuntimeException failure) { Platform.runLater(() -> { if (!closed && generation == revision) status.setText(FxDialogs.rootMessage(failure)); }); }
        });
    }
    private int selected(ComboBox<LogChannel> choice, LogDataset data) {
        if (choice.getValue() == null || !data.getChannels().contains(choice.getValue())) throw new IllegalArgumentException("Map current CSV channels for both runs.");
        return choice.getValue().getIndex();
    }
    private void copy() {
        if (result == null) return;
        StringBuilder text = new StringBuilder(completedSummary)
                .append("\nX from\tX to (exclusive)\tA count\tB count\tA mean\tB mean\tB minus A\tCoverage\n");
        for (var row : result.rows()) text.append(row.lower()).append('\t').append(row.upper()).append('\t').append(row.countA()).append('\t').append(row.countB()).append('\t')
                .append(exact(row.meanA())).append('\t').append(exact(row.meanB())).append('\t').append(exact(row.difference())).append('\t').append(coverage(row.coverage())).append('\n');
        ClipboardContent content = new ClipboardContent(); content.putString(text.toString()); Clipboard.getSystemClipboard().setContent(content);
    }
    private void column(String title, java.util.function.Function<LogRunComparison.Row, String> value) {
        TableColumn<LogRunComparison.Row, String> column = new TableColumn<>(title); column.setPrefWidth(title.equals("Coverage") ? 170 : 100); column.setStyle("-fx-alignment: CENTER;");
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue()))); table.getColumns().add(column);
    }
    static String coverage(LogRunComparison.Coverage value) { return switch (value) {
        case BOTH -> "Both qualified"; case A_ONLY -> "A only"; case B_ONLY -> "B only";
        case LOW_COUNT -> "Low count"; case EMPTY -> "Empty"; case DIFFERENCE_OVERFLOW -> "Difference overflow";
    }; }
    private static String number(double value) { return FxBinnedLogPane.number(value); }
    private static String exact(double value) { return Double.isFinite(value) ? Double.toString(value) : "—"; }
    private static String oneLine(String text) { return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' '); }
    private static String runSummary(String name, LogRunComparison.Run run) {
        return name + ": " + oneLine(run.dataset().getSourceName()) + " · samples " + (run.range().getStartInclusive() + 1) + "–" + run.range().getEndExclusive()
                + " · X [" + (run.xChannel() + 1) + "]: " + oneLine(run.dataset().getChannels().get(run.xChannel()).getLabel())
                + " · Value [" + (run.valueChannel() + 1) + "]: " + oneLine(run.dataset().getChannels().get(run.valueChannel()).getLabel());
    }
    private static Tab tab(String name, javafx.scene.Node content) { Tab tab = new Tab(name, content); tab.setClosable(false); return tab; }
    private static ListCell<LogChannel> channelCell() { return new ListCell<>() {
        @Override protected void updateItem(LogChannel channel, boolean empty) { super.updateItem(channel, empty); setText(channel == null ? "Choose CSV channel" : "[" + (channel.getIndex() + 1) + "] " + channel.getLabel()); }
    }; }
    private static ExecutorService executor(String name) { return Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; }); }
    @Override public void close() { if (!closed) { closed = true; cancelImport(); invalidate(); b = null; worker.shutdownNow(); imports.shutdownNow(); } }
}
