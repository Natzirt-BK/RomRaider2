/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.romraider.logger.analysis.ChannelStatistics;
import com.romraider.logger.analysis.LogChannel;
import com.romraider.logger.analysis.LogCursorModel;
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.LogMarker;
import com.romraider.logger.analysis.LogMarkerStore;
import com.romraider.logger.analysis.LogMarkerType;
import com.romraider.logger.analysis.LogPlaybackService;
import com.romraider.logger.analysis.LogRange;
import com.romraider.logger.analysis.LogStatisticsService;
import com.romraider.logger.analysis.PlaybackState;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Linked table, charts, statistics, markers, and playback for one CSV log. */
final class FxLogAnalysisPane extends BorderPane implements AutoCloseable {
    private static final int MAX_CHART_POINTS = 2500;
    private final File source;
    private final LogDataset dataset;
    private final LogCursorModel cursor = new LogCursorModel();
    private final LogPlaybackService playback = new LogPlaybackService(cursor);
    private final TableView<Integer> values = new TableView<>();
    private final Slider position;
    private final Label positionLabel = new Label();
    private final Label status = new Label();
    private final Button play = new Button("Play");
    private final ComboBox<LogChannel> timelineChannel;
    private final ComboBox<LogChannel> xChannel;
    private final ComboBox<LogChannel> yChannel;
    private final LineChart<Number, Number> timelineChart = lineChart();
    private final ScatterChart<Number, Number> scatterChart = scatterChart();
    private final ListView<LogMarker> markers = new ListView<>();
    private final Timeline clock = new Timeline(new KeyFrame(
            Duration.millis(40), event -> playback.advance(40)));
    private boolean movingSlider;

    FxLogAnalysisPane(File source, LogDataset dataset) {
        this.source = source;
        this.dataset = dataset;
        position = new Slider(0, dataset.getRowCount() - 1, 0);
        timelineChannel = channelBox(false);
        xChannel = channelBox(true);
        yChannel = channelBox(false);
        clock.setCycleCount(Timeline.INDEFINITE);
        setTop(header());
        setCenter(new TabPane(tab("Table", values),
                tab("Time Series", timelineWorkspace()),
                tab("X/Y Plot", scatterWorkspace()),
                tab("Statistics", statisticsTable()),
                tab("Markers", markerWorkspace())));
        setBottom(playbackBar());
        setPadding(new Insets(12));
        configureTable();
        configurePlayback();
        configureCharts();
        loadMarkers();
        playback.load(dataset, LogRange.all(dataset));
    }

    private Node header() {
        Label title = new Label("Log Analysis · " + dataset.getSourceName());
        title.getStyleClass().add("title");
        Label detail = new Label(dataset.getRowCount() + " samples  ·  "
                + dataset.getChannelCount() + " numeric channels  ·  "
                + "linked playback cursor");
        detail.getStyleClass().add("muted");
        VBox box = new VBox(3, title, detail);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

    private Node timelineWorkspace() {
        HBox controls = new HBox(8, label("CHANNEL"), timelineChannel);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(8));
        return new BorderPane(timelineChart, controls, null, null, null);
    }

    private Node scatterWorkspace() {
        HBox controls = new HBox(8, label("X AXIS"), xChannel,
                label("Y AXIS"), yChannel);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(8));
        return new BorderPane(scatterChart, controls, null, null, null);
    }

    private Node markerWorkspace() {
        ComboBox<LogMarkerType> type = new ComboBox<>(
                FXCollections.observableArrayList(LogMarkerType.values()));
        type.setValue(LogMarkerType.CUSTOM);
        TextField name = new TextField();
        name.setPromptText("Marker label");
        HBox.setHgrow(name, Priority.ALWAYS);
        Button add = new Button("Add at cursor");
        add.setOnAction(event -> {
            markers.getItems().add(new LogMarker(cursor.getSampleIndex(),
                    type.getValue(), name.getText()));
            FXCollections.sort(markers.getItems());
            saveMarkers();
            name.clear();
        });
        Button remove = new Button("Remove selected");
        remove.disableProperty().bind(
                markers.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> {
            markers.getItems().remove(
                    markers.getSelectionModel().getSelectedItem());
            saveMarkers();
        });
        HBox controls = new HBox(8, type, name, add, remove);
        controls.setPadding(new Insets(8));
        markers.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(LogMarker marker, boolean empty) {
                super.updateItem(marker, empty);
                setText(empty || marker == null ? null
                        : "Sample " + (marker.getSampleIndex() + 1) + "  ·  "
                        + marker.getDisplayName());
            }
        });
        markers.setOnMouseClicked(event -> {
            LogMarker marker = markers.getSelectionModel().getSelectedItem();
            if (marker != null && event.getClickCount() == 2) {
                playback.seek(marker.getSampleIndex());
            }
        });
        return new BorderPane(markers, controls, null, status, null);
    }

    private Node playbackBar() {
        Button previous = new Button("−1");
        previous.setOnAction(event -> playback.step(-1));
        Button next = new Button("+1");
        next.setOnAction(event -> playback.step(1));
        Button stop = new Button("Stop");
        stop.setOnAction(event -> playback.stop());
        ComboBox<Double> speed = new ComboBox<>(FXCollections
                .observableArrayList(.25, .5, 1.0, 2.0, 4.0, 8.0));
        speed.setValue(1.0);
        speed.setOnAction(event -> playback.setSpeed(speed.getValue()));
        play.setOnAction(event -> {
            if (playback.snapshot().getState() == PlaybackState.PLAYING) {
                playback.pause();
            } else playback.play();
        });
        position.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(position, Priority.ALWAYS);
        Region gap = new Region();
        gap.setPrefWidth(4);
        HBox bar = new HBox(7, previous, play, stop, next, gap,
                position, positionLabel, speed, new Label("×"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(9, 0, 0, 0));
        return bar;
    }

    private void configureTable() {
        List<Integer> rows = new ArrayList<>();
        for (int row = 0; row < dataset.getRowCount(); row++) rows.add(row);
        values.setItems(FXCollections.observableArrayList(rows));
        addValueColumn("Sample", row -> Integer.toString(row + 1), 85);
        for (LogChannel channel : dataset.getChannels()) {
            addValueColumn(channel.getLabel(), row -> format(dataset.getValue(
                    row, channel.getIndex())), 145);
        }
        values.getSelectionModel().selectedIndexProperty().addListener(
                (value, oldRow, newRow) -> {
                    if (newRow.intValue() >= 0 && !movingSlider) {
                        playback.seek(newRow.intValue());
                    }
                });
    }

    private void addValueColumn(String title,
            java.util.function.Function<Integer, String> function,
            double width) {
        TableColumn<Integer, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new ReadOnlyStringWrapper(
                function.apply(row.getValue())));
        column.setPrefWidth(width);
        values.getColumns().add(column);
    }

    private void configurePlayback() {
        position.valueProperty().addListener((value, oldRow, newRow) -> {
            if (!movingSlider) playback.seek(newRow.intValue());
        });
        cursor.addListener((loaded, range, row) -> javafx.application.Platform
                .runLater(() -> {
                    movingSlider = true;
                    try {
                        position.setValue(row);
                        values.getSelectionModel().select(row);
                        values.scrollTo(row);
                        positionLabel.setText((row + 1) + " / "
                                + dataset.getRowCount());
                    } finally {
                        movingSlider = false;
                    }
                }));
        playback.addListener(snapshot -> javafx.application.Platform.runLater(() -> {
            boolean running = snapshot.getState() == PlaybackState.PLAYING;
            play.setText(running ? "Pause" : "Play");
            if (running && clock.getStatus() != Timeline.Status.RUNNING) {
                clock.play();
            } else if (!running) clock.pause();
        }));
    }

    private void configureCharts() {
        timelineChannel.setOnAction(event -> rebuildTimeline());
        xChannel.setOnAction(event -> rebuildScatter());
        yChannel.setOnAction(event -> rebuildScatter());
        timelineChannel.getSelectionModel().select(
                Math.min(1, dataset.getChannelCount() - 1));
        LogChannel time = dataset.getTimeChannel();
        xChannel.setValue(time == null ? dataset.getChannels().get(0) : time);
        yChannel.getSelectionModel().select(
                Math.min(1, dataset.getChannelCount() - 1));
        rebuildTimeline();
        rebuildScatter();
    }

    private void rebuildTimeline() {
        LogChannel channel = timelineChannel.getValue();
        if (channel == null) return;
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(channel.getName());
        LogChannel time = dataset.getTimeChannel();
        for (int row = 0; row < dataset.getRowCount(); row += chartStep()) {
            double x = time == null ? row : dataset.getValue(row, time.getIndex());
            double y = dataset.getValue(row, channel.getIndex());
            if (Double.isFinite(x) && Double.isFinite(y)) {
                series.getData().add(new XYChart.Data<>(x, y));
            }
        }
        timelineChart.getData().setAll(series);
        timelineChart.setTitle(channel.getLabel() + " over "
                + (time == null ? "sample" : time.getLabel()));
    }

    private void rebuildScatter() {
        LogChannel x = xChannel.getValue();
        LogChannel y = yChannel.getValue();
        if (x == null || y == null) return;
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(y.getName() + " by " + x.getName());
        for (int row = 0; row < dataset.getRowCount(); row += chartStep()) {
            double xv = dataset.getValue(row, x.getIndex());
            double yv = dataset.getValue(row, y.getIndex());
            if (Double.isFinite(xv) && Double.isFinite(yv)) {
                series.getData().add(new XYChart.Data<>(xv, yv));
            }
        }
        scatterChart.getData().setAll(series);
        scatterChart.setTitle(y.getLabel() + " versus " + x.getLabel());
    }

    private Node statisticsTable() {
        TableView<ChannelStatistics> table = new TableView<>(
                FXCollections.observableArrayList(LogStatisticsService.analyze(
                        dataset, LogRange.all(dataset))));
        statisticColumn(table, "Channel", value -> value.getChannel().getLabel(), 260);
        statisticColumn(table, "Min", value -> format(value.getMinimum()), 110);
        statisticColumn(table, "Max", value -> format(value.getMaximum()), 110);
        statisticColumn(table, "Mean", value -> format(value.getMean()), 110);
        statisticColumn(table, "Median", value -> format(value.getMedian()), 110);
        statisticColumn(table, "Std dev", value -> format(
                value.getStandardDeviation()), 110);
        statisticColumn(table, "P05", value -> format(value.getPercentile05()), 100);
        statisticColumn(table, "P95", value -> format(value.getPercentile95()), 100);
        return table;
    }

    private void loadMarkers() {
        if (source == null) return;
        try {
            markers.setItems(FXCollections.observableArrayList(
                    new LogMarkerStore().load(source, dataset.getRowCount())));
        } catch (Exception failure) {
            status.setText("Markers could not be loaded: "
                    + FxDialogs.rootMessage(failure));
        }
    }

    private void saveMarkers() {
        if (source == null) return;
        try {
            new LogMarkerStore().save(source,
                    new ArrayList<>(markers.getItems()));
            status.setText("Marker sidecar saved");
        } catch (Exception failure) {
            status.setText("Markers could not be saved: "
                    + FxDialogs.rootMessage(failure));
        }
    }

    private ComboBox<LogChannel> channelBox(boolean preferTime) {
        ComboBox<LogChannel> box = new ComboBox<>(FXCollections
                .observableArrayList(dataset.getChannels()));
        if (preferTime && dataset.getTimeChannel() != null) {
            box.setValue(dataset.getTimeChannel());
        }
        return box;
    }

    private int chartStep() {
        return Math.max(1, (int) Math.ceil(dataset.getRowCount()
                / (double) MAX_CHART_POINTS));
    }

    private static LineChart<Number, Number> lineChart() {
        LineChart<Number, Number> chart = new LineChart<>(
                new NumberAxis(), new NumberAxis());
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        return chart;
    }

    private static ScatterChart<Number, Number> scatterChart() {
        ScatterChart<Number, Number> chart = new ScatterChart<>(
                new NumberAxis(), new NumberAxis());
        chart.setAnimated(false);
        return chart;
    }

    private static void statisticColumn(TableView<ChannelStatistics> table,
            String title,
            java.util.function.Function<ChannelStatistics, String> value,
            double width) {
        TableColumn<ChannelStatistics, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new ReadOnlyStringWrapper(
                value.apply(row.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-kicker");
        return label;
    }

    private static Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) return "—";
        return String.format(Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override public void close() {
        clock.stop();
        playback.pause();
    }
}
