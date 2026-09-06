/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Exact accepted samples from a completed analysis, separate from range-only Log Analysis. */
final class FxFuelSamplePane extends BorderPane implements AutoCloseable {
    static final int PAGE_SIZE = 200;
    private final ComboBox<LogChannel> channel = new ComboBox<>();
    private final Button inspect = new Button("Inspect accepted samples");
    private final Button calculate = new Button("Calculate channel statistics");
    private final Button previous = new Button("Previous page"), next = new Button("Next page");
    private final Label status = new Label(), pageLabel = new Label();
    private final TableView<Integer> samples = new TableView<>();
    private final TextArea statistics = new TextArea();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "rr2-accepted-sample-review"); thread.setDaemon(true); return thread;
    });
    private Future<?> pending;
    private long generation;
    private boolean closed, busy;
    private int page;
    private FuelLogAnalysis.Result analysis;
    private FuelSampleReview review;

    FxFuelSamplePane() {
        Label explanation = new Label("Exact accepted rows from this fuel analysis, including its range, projections and enabled filters. Statistics cover ALL accepted rows for the selected CSV channel, not just this page. Missing readings remain unavailable.");
        explanation.setWrapText(true); status.setWrapText(true); pageLabel.setWrapText(true);
        channel.setPromptText("Choose a CSV channel"); channel.setPrefWidth(230);
        channel.setCellFactory(ignored -> channelCell()); channel.setButtonCell(channelCell());
        inspect.setOnAction(event -> capture()); calculate.setOnAction(event -> calculate());
        channel.valueProperty().addListener((value, before, after) -> {
            cancelWork(); statistics.clear(); showPage(); refresh();
            if (review != null) status.setText("Channel changed. Calculate fresh statistics for all accepted samples.");
        });
        VBox heading = new VBox(7, explanation, new FlowPane(7, 5, inspect, channel, calculate), status);
        heading.setPadding(new Insets(10)); setTop(heading);
        column("Original sample (1-based)", row -> Integer.toString(row + 1), 175);
        column("Recorded time (CSV units)", row -> {
            LogChannel time = review.getDataset().getTimeChannel();
            return time == null ? "—" : number(review.getDataset().getValue(row, time.getIndex()));
        }, 175);
        column("Selected channel value", row -> channel.getValue() == null ? "—"
                : number(review.getDataset().getValue(row, channel.getValue().getIndex())), 175);
        samples.setPlaceholder(new Label("Complete an analysis, then inspect its accepted samples."));
        samples.setMinSize(0, 0); setCenter(samples);
        previous.setOnAction(event -> { page--; showPage(); });
        next.setOnAction(event -> { page++; showPage(); });
        statistics.setEditable(false); statistics.setWrapText(true); statistics.setPrefRowCount(4);
        statistics.setPromptText("Channel statistics are calculated on request, across all accepted rows.");
        VBox footer = new VBox(7, new FlowPane(7, 5, previous, next, pageLabel), statistics);
        footer.setPadding(new Insets(10)); setBottom(footer); setAnalysis(null);
    }

    void setAnalysis(FuelLogAnalysis.Result value) {
        if (closed) return;
        cancelWork(); analysis = value; review = null; page = 0;
        samples.getItems().clear(); statistics.clear(); channel.getItems().clear();
        if (value != null) {
            channel.getItems().setAll(value.getDataset().getChannels());
            channel.setValue(value.getDataset().getChannels().stream().filter(c -> !c.isTimeChannel()).findFirst().orElse(value.getDataset().getChannels().get(0)));
        }
        status.setText(value == null ? "Analyze the saved log to inspect its accepted rows."
                : value.getAccepted() + " accepted · " + value.getFiltered() + " filtered · " + value.getInvalid() + " invalid. Inspection does not change the Log Analysis range or cursor.");
        showPage(); refresh();
    }

    private void capture() {
        if (closed || analysis == null) return;
        cancelWork(); review = null; page = 0; statistics.clear(); showPage();
        FuelLogAnalysis.Result source = analysis; long revision = generation;
        busy = true; refresh(); status.setText("Indexing exact accepted samples…");
        pending = worker.submit(() -> {
            try {
                FuelSampleReview result = FuelSampleReview.capture(source);
                Platform.runLater(() -> {
                    if (closed || generation != revision || analysis != source) return;
                    review = result; busy = false; showPage(); refresh();
                    status.setText(result.size() + " exact accepted samples. Choose any original CSV channel; statistics include every accepted row.");
                });
            } catch (RuntimeException failure) { failed(revision, failure); }
        });
    }

    private void calculate() {
        if (closed || review == null || channel.getValue() == null) return;
        cancelWork(); statistics.clear(); FuelSampleReview source = review; LogChannel selected = channel.getValue();
        if (!source.getDataset().getChannels().contains(selected)) return;
        long revision = generation; busy = true; refresh(); status.setText("Calculating statistics over all accepted samples…");
        pending = worker.submit(() -> {
            try {
                ChannelStatistics result = source.statistics(selected.getIndex());
                Platform.runLater(() -> {
                    if (closed || generation != revision || review != source || channel.getValue() != selected) return;
                    busy = false; refresh();
                    statistics.setText("[" + (selected.getIndex() + 1) + "] " + selected.getLabel() + " · all " + source.size() + " accepted rows\n"
                            + "Finite: " + result.getSampleCount() + " · Missing: " + result.getMissingCount()
                            + " · Minimum: " + number(result.getMinimum()) + " · Maximum: " + number(result.getMaximum()) + "\n"
                            + "Mean: " + number(result.getMean()) + " · Median: " + number(result.getMedian())
                            + " · Population SD: " + number(result.getStandardDeviation()) + "\n"
                            + "5th percentile: " + number(result.getPercentile05()) + " · 95th percentile: " + number(result.getPercentile95()));
                    status.setText("Statistics use exact accepted rows; this is not a calibration recommendation.");
                });
            } catch (RuntimeException failure) { failed(revision, failure); }
        });
    }

    private void failed(long revision, RuntimeException failure) {
        Platform.runLater(() -> {
            if (closed || generation != revision) return;
            busy = false; refresh(); status.setText(FxDialogs.rootMessage(failure));
        });
    }
    private void cancelWork() { generation++; if (pending != null) pending.cancel(true); busy = false; }
    private void showPage() {
        ArrayList<Integer> rows = new ArrayList<>();
        int size = review == null ? 0 : review.size();
        page = Math.max(0, Math.min(page, Math.max(0, (size - 1) / PAGE_SIZE)));
        int start = page * PAGE_SIZE, end = Math.min(size, start + PAGE_SIZE);
        for (int index = start; index < end; index++) rows.add(review.originalRow(index));
        samples.getItems().setAll(rows);
        pageLabel.setText(size == 0 ? "No accepted samples to display" : "Accepted " + (start + 1) + "–" + end + " of " + size);
        previous.setDisable(page == 0); next.setDisable(end >= size);
    }
    private void refresh() {
        inspect.setDisable(closed || analysis == null || busy);
        calculate.setDisable(closed || review == null || channel.getValue() == null || busy);
        channel.setDisable(closed || analysis == null || busy);
    }
    private void column(String title, java.util.function.Function<Integer, String> text, int width) {
        TableColumn<Integer, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        column.setPrefWidth(width); column.setSortable(false); column.setStyle("-fx-alignment: CENTER;");
        samples.getColumns().add(column);
    }
    private static ListCell<LogChannel> channelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(LogChannel value, boolean empty) {
                super.updateItem(value, empty);
                setText(value == null ? "Choose a CSV channel" : "[" + (value.getIndex() + 1) + "] " + value.getLabel());
            }
        };
    }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.9g", value) : "—"; }
    @Override public void close() { if (!closed) { setAnalysis(null); closed = true; cancelWork(); worker.shutdownNow(); refresh(); } }
}
