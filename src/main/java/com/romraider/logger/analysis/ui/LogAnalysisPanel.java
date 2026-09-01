/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.romraider.logger.analysis.ChannelStatistics;
import com.romraider.logger.analysis.LogChannel;
import com.romraider.logger.analysis.LogCursorModel;
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.LogPlaybackService;
import com.romraider.logger.analysis.LogPlaybackSnapshot;
import com.romraider.logger.analysis.LogRange;
import com.romraider.logger.analysis.LogMarker;
import com.romraider.logger.analysis.LogMarkerStore;
import com.romraider.logger.analysis.LogMarkerType;
import com.romraider.logger.analysis.LogStatisticsService;
import com.romraider.logger.analysis.PlaybackState;
import com.romraider.logger.analysis.RomRaiderCsvLogParser;
import com.romraider.logger.analysis.RecentLogCaptureService;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.swing.IntegratedFileChooser;

/** Read-only CSV statistics workspace kept independent from live ECU state. */
public final class LogAnalysisPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DecimalFormat DURATION_FORMAT = new DecimalFormat("0.###");

    private final RomRaiderCsvLogParser parser = new RomRaiderCsvLogParser();
    private final LogCursorModel cursor = new LogCursorModel();
    private final LogPlaybackService playback = new LogPlaybackService(cursor);
    private final LogAnalysisTableModel tableModel = new LogAnalysisTableModel();
    private final JTable statisticsTable = new JTable(tableModel);
    private final LogTimeGraphPanel graph = new LogTimeGraphPanel(cursor);
    private final LogXyGraphPanel xyGraph = new LogXyGraphPanel(cursor);
    private final JComboBox<LogChannel> xAxisSelector =
            new JComboBox<LogChannel>();
    private final JComboBox<LogChannel> yAxisSelector =
            new JComboBox<LogChannel>();
    private final JLabel sourceLabel = new JLabel("No log loaded");
    private final JLabel statusLabel = new JLabel(
            "Load a RomRaider2 or RomRaider CSV log to inspect it offline.");
    private final JButton loadButton = new JButton("Load Log");
    private final JButton replayLastButton = new JButton("Replay Last");
    private final JButton applyRangeButton = new JButton("Apply Range");
    private final JButton playPauseButton = new JButton("Play");
    private final JSlider cursorSlider = new JSlider(1, 1, 1);
    private final JLabel cursorLabel = new JLabel("No playback cursor");
    private final JComboBox<String> speedSelector = new JComboBox<String>(
            new String[] {"0.25x", "0.5x", "1x", "2x", "4x", "8x"});
    private final JSpinner firstSample = new JSpinner(
            new SpinnerNumberModel(1, 1, 1, 1));
    private final JSpinner lastSample = new JSpinner(
            new SpinnerNumberModel(1, 1, 1, 1));
    private final JComboBox<LogMarkerType> markerType =
            new JComboBox<LogMarkerType>(LogMarkerType.values());
    private final JTextField markerLabel = new JTextField(10);
    private final JButton addMarkerButton = new JButton("Add Marker");
    private final JButton previousMarkerButton = new JButton("Previous");
    private final JButton nextMarkerButton = new JButton("Next");
    private final JButton deleteMarkerButton = new JButton("Delete Here");
    private final JLabel markerSummary = new JLabel("No markers");
    private final LogMarkerStore markerStore = new LogMarkerStore();
    private final List<LogMarker> markers = new ArrayList<LogMarker>();
    private final Consumer<File> recentLogListener = this::offerRecentLog;
    private LogDataset dataset;
    private File datasetFile;
    private File recentLog;
    private File chooserDirectory;
    private boolean recentLogAttached;
    private boolean updatingSlider;
    private long lastTickNanos;
    private final Timer playbackTimer = new Timer(20, event -> playbackTick());

    public LogAnalysisPanel() {
        super(new BorderLayout(8, 8));
        setName("LOGGER ANALYSIS WORKSPACE");
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("ANALYSIS");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 2.0f));
        JLabel boundary = new JLabel(
                "Offline playback, markers, graphs, and channel statistics");
        boundary.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        sourceLabel.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));

        JPanel identity = new JPanel(new BorderLayout(8, 2));
        identity.add(title, BorderLayout.NORTH);
        identity.add(sourceLabel, BorderLayout.CENTER);
        identity.add(boundary, BorderLayout.SOUTH);

        JPanel range = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        range.add(new JLabel("Samples"));
        range.add(firstSample);
        range.add(new JLabel("to"));
        range.add(lastSample);
        range.add(applyRangeButton);
        replayLastButton.setName("REPLAY LAST LOG CAPTURE");
        replayLastButton.setEnabled(false);
        replayLastButton.addActionListener(event -> {
            if (recentLog != null) load(recentLog);
        });
        range.add(replayLastButton);
        range.add(loadButton);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.add(identity, BorderLayout.CENTER);
        header.add(range, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        statisticsTable.setAutoCreateRowSorter(true);
        statisticsTable.setFillsViewportHeight(true);
        statisticsTable.setName("LOG ANALYSIS STATISTICS");
        statisticsTable.setShowVerticalLines(false);
        statisticsTable.setRowHeight(Math.max(statisticsTable.getRowHeight(),
                26));
        statisticsTable.getTableHeader().setReorderingAllowed(false);
        statisticsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        statisticsTable.getColumnModel().getColumn(0).setMinWidth(180);
        statisticsTable.getColumnModel().getColumn(0).setPreferredWidth(310);
        statisticsTable.getColumnModel().getColumn(1).setMinWidth(70);
        statisticsTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        for (int column = 2; column < tableModel.getColumnCount(); column++) {
            statisticsTable.getColumnModel().getColumn(column).setMinWidth(65);
            statisticsTable.getColumnModel().getColumn(column)
                    .setPreferredWidth(90);
        }
        JSplitPane analysisSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildGraphWorkspace(), new JScrollPane(statisticsTable));
        analysisSplit.setResizeWeight(0.58);
        analysisSplit.setDividerLocation(310);
        analysisSplit.setDividerSize(6);

        JPanel analysisWorkspace = new JPanel(new BorderLayout(0, 6));
        analysisWorkspace.add(buildPlaybackControls(), BorderLayout.NORTH);
        analysisWorkspace.add(analysisSplit, BorderLayout.CENTER);
        add(analysisWorkspace, BorderLayout.CENTER);
        statusLabel.setName("LOG ANALYSIS STATUS");
        statusLabel.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        UiThemeService.getInstance().color(
                                ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(5, 2, 0, 2)));
        add(statusLabel, BorderLayout.SOUTH);

        applyRangeButton.setEnabled(false);
        loadButton.setName("LOAD LOG FOR ANALYSIS");
        loadButton.addActionListener(event -> chooseLog());
        applyRangeButton.addActionListener(event -> applySelectedRange());
        statisticsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateGraphChannels();
        });
        cursor.addListener((value, rangeValue, sample) ->
                updateCursorControls(value, rangeValue, sample));
        playback.addListener(this::updatePlaybackControls);
        addMarkerButton.addActionListener(event -> addMarker());
        previousMarkerButton.addActionListener(event -> seekMarker(-1));
        nextMarkerButton.addActionListener(event -> seekMarker(1));
        deleteMarkerButton.addActionListener(event -> deleteMarkerAtCursor());
        updateMarkerControls();
        offerRecentLog(RecentLogCaptureService.getInstance().getLastCompleted());
    }

    private JTabbedPane buildGraphWorkspace() {
        JTabbedPane graphs = new JTabbedPane();
        graphs.setName("LOG ANALYSIS GRAPHS");
        graphs.addTab("Time", new JScrollPane(graph));

        xAxisSelector.setName("LOG XY X AXIS");
        yAxisSelector.setName("LOG XY Y AXIS");
        Dimension axisSize = new Dimension(210,
                xAxisSelector.getPreferredSize().height);
        xAxisSelector.setPreferredSize(axisSize);
        yAxisSelector.setPreferredSize(axisSize);
        xAxisSelector.addActionListener(event -> updateXyAxes());
        yAxisSelector.addActionListener(event -> updateXyAxes());
        JPanel axes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        axes.add(new JLabel("X axis"));
        axes.add(xAxisSelector);
        axes.add(new JLabel("Y axis"));
        axes.add(yAxisSelector);
        JPanel xy = new JPanel(new BorderLayout(0, 4));
        xy.add(axes, BorderLayout.NORTH);
        xy.add(xyGraph, BorderLayout.CENTER);
        graphs.addTab("X/Y", xy);
        return graphs;
    }

    private JPanel buildPlaybackControls() {
        JButton startButton = new JButton("First Sample");
        JButton previousButton = new JButton("Previous Sample");
        JButton nextButton = new JButton("Next Sample");
        startButton.setName("LOG PLAYBACK START");
        previousButton.setName("LOG PLAYBACK PREVIOUS");
        playPauseButton.setName("LOG PLAYBACK PLAY PAUSE");
        nextButton.setName("LOG PLAYBACK NEXT");
        startButton.addActionListener(event -> playback.stop());
        previousButton.addActionListener(event -> playback.step(-1));
        nextButton.addActionListener(event -> playback.step(1));
        playPauseButton.addActionListener(event -> {
            if (playback.snapshot().getState() == PlaybackState.PLAYING) {
                playback.pause();
            } else {
                playback.play();
            }
        });
        speedSelector.setSelectedItem("1x");
        speedSelector.addActionListener(event -> playback.setSpeed(
                parseSpeed((String) speedSelector.getSelectedItem())));
        cursorSlider.setEnabled(false);
        cursorSlider.addChangeListener(event -> {
            if (!updatingSlider && dataset != null) {
                playback.seek(cursorSlider.getValue() - 1);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        buttons.add(startButton);
        buttons.add(previousButton);
        buttons.add(playPauseButton);
        buttons.add(nextButton);
        buttons.add(new JLabel("Speed"));
        buttons.add(speedSelector);

        JPanel controls = new JPanel(new BorderLayout(8, 5));
        JPanel timeline = new JPanel(new BorderLayout(8, 0));
        timeline.add(buttons, BorderLayout.WEST);
        timeline.add(cursorSlider, BorderLayout.CENTER);
        timeline.add(cursorLabel, BorderLayout.EAST);
        controls.add(timeline, BorderLayout.NORTH);

        JPanel markerControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        markerType.setName("LOG MARKER TYPE");
        markerLabel.setName("LOG MARKER LABEL");
        addMarkerButton.setName("ADD LOG MARKER");
        previousMarkerButton.setName("PREVIOUS LOG MARKER");
        nextMarkerButton.setName("NEXT LOG MARKER");
        deleteMarkerButton.setName("DELETE LOG MARKER");
        markerControls.add(new JLabel("Markers"));
        markerControls.add(markerType);
        markerControls.add(markerLabel);
        markerControls.add(addMarkerButton);
        markerControls.add(previousMarkerButton);
        markerControls.add(nextMarkerButton);
        markerControls.add(deleteMarkerButton);
        markerControls.add(markerSummary);
        controls.add(markerControls, BorderLayout.SOUTH);
        return controls;
    }

    private void chooseLog() {
        JFileChooser chooser = chooserDirectory == null
                ? new IntegratedFileChooser()
                : new IntegratedFileChooser(chooserDirectory);
        chooser.setDialogTitle("Open logger capture");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV logger files", "csv"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            chooserDirectory = file.getParentFile();
            load(file);
        }
    }

    public void load(File file) {
        if (file == null) throw new IllegalArgumentException("file");
        loadButton.setEnabled(false);
        applyRangeButton.setEnabled(false);
        statusLabel.setText("Loading " + file.getName() + "...");
        new SwingWorker<LogDataset, Void>() {
            protected LogDataset doInBackground() throws Exception {
                return parser.parse(file);
            }

            protected void done() {
                loadButton.setEnabled(true);
                try {
                    setDataset(get(), file);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showLoadError("Loading was interrupted.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    showLoadError(cause == null ? e.getMessage()
                            : cause.getMessage());
                }
            }
        }.execute();
    }

    private void setDataset(LogDataset dataset, File sourceFile) {
        this.dataset = dataset;
        datasetFile = sourceFile == null ? null : sourceFile.getAbsoluteFile();
        markers.clear();
        if (datasetFile != null) {
            try {
                markers.addAll(markerStore.load(datasetFile,
                        dataset.getRowCount()));
            } catch (IOException failure) {
                statusLabel.setText("Log loaded; marker sidecar could not be read: "
                        + failure.getMessage());
            }
        }
        Collections.sort(markers);
        graph.setMarkers(markers);
        xyGraph.setMarkers(markers);
        int rowCount = dataset.getRowCount();
        configureSpinner(firstSample, 1, rowCount, 1);
        configureSpinner(lastSample, 1, rowCount, rowCount);
        sourceLabel.setText(dataset.getSourceName());
        applyRangeButton.setEnabled(true);
        LogRange all = LogRange.all(dataset);
        playback.load(dataset, all);
        updateStatistics(all);
        selectDefaultGraphChannels();
        configureXyAxes();
        updateMarkerControls();
    }

    private static void configureSpinner(JSpinner spinner, int minimum,
            int maximum, int value) {
        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
        model.setMinimum(Integer.valueOf(minimum));
        model.setMaximum(Integer.valueOf(maximum));
        model.setValue(Integer.valueOf(value));
    }

    private void applySelectedRange() {
        if (dataset == null) return;
        int first = ((Number) firstSample.getValue()).intValue();
        int last = ((Number) lastSample.getValue()).intValue();
        if (first > last) {
            JOptionPane.showMessageDialog(this,
                    "The first sample must not follow the last sample.",
                    "Invalid sample range", JOptionPane.WARNING_MESSAGE);
            return;
        }
        applyRange(LogRange.of(first - 1, last, dataset.getRowCount()));
    }

    private void applyRange(LogRange range) {
        playback.setRange(range);
        updateStatistics(range);
    }

    private void updateStatistics(LogRange range) {
        List<LogChannel> graphed = graph.getChannels();
        tableModel.setStatistics(LogStatisticsService.analyze(dataset, range));
        if (!graphed.isEmpty()) reselectGraphChannels(graphed);
        String duration = describeDuration(dataset, range);
        statusLabel.setText(range.size() + " of " + dataset.getRowCount()
                + " samples • " + dataset.getChannelCount() + " channels"
                + (duration.isEmpty() ? "" : " • " + duration));
    }

    private void selectDefaultGraphChannels() {
        statisticsTable.clearSelection();
        int selected = 0;
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
            if (tableModel.getStatisticsAt(modelRow).getChannel().isTimeChannel()) {
                continue;
            }
            int viewRow = statisticsTable.convertRowIndexToView(modelRow);
            statisticsTable.addRowSelectionInterval(viewRow, viewRow);
            if (++selected == 3) break;
        }
        updateGraphChannels();
    }

    private void reselectGraphChannels(List<LogChannel> channels) {
        statisticsTable.clearSelection();
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
            LogChannel candidate = tableModel.getStatisticsAt(modelRow).getChannel();
            for (LogChannel selected : channels) {
                if (candidate.getIndex() == selected.getIndex()) {
                    int viewRow = statisticsTable.convertRowIndexToView(modelRow);
                    statisticsTable.addRowSelectionInterval(viewRow, viewRow);
                    break;
                }
            }
        }
        updateGraphChannels();
    }

    private void updateGraphChannels() {
        List<LogChannel> selected = new ArrayList<LogChannel>();
        for (int viewRow : statisticsTable.getSelectedRows()) {
            int modelRow = statisticsTable.convertRowIndexToModel(viewRow);
            ChannelStatistics statistics = tableModel.getStatisticsAt(modelRow);
            if (!statistics.getChannel().isTimeChannel()) {
                selected.add(statistics.getChannel());
            }
            if (selected.size() == 5) break;
        }
        graph.setChannels(selected);
    }

    private void playbackTick() {
        long now = System.nanoTime();
        if (lastTickNanos != 0L) {
            playback.advance((now - lastTickNanos) / 1_000_000.0);
        }
        lastTickNanos = now;
    }

    private void updatePlaybackControls(LogPlaybackSnapshot snapshot) {
        Runnable update = () -> {
            boolean playing = snapshot.getState() == PlaybackState.PLAYING;
            playPauseButton.setText(playing ? "Pause" : "Play");
            if (playing && !playbackTimer.isRunning()) {
                lastTickNanos = System.nanoTime();
                playbackTimer.start();
            } else if (!playing) {
                playbackTimer.stop();
                lastTickNanos = 0L;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    private void updateCursorControls(LogDataset value, LogRange range,
            int sample) {
        Runnable update = () -> {
            updatingSlider = true;
            try {
                cursorSlider.setMinimum(range.getStartInclusive() + 1);
                cursorSlider.setMaximum(range.getEndExclusive());
                cursorSlider.setValue(sample + 1);
                cursorSlider.setEnabled(true);
            } finally {
                updatingSlider = false;
            }
            cursorLabel.setText(describeCursor(value, sample));
            updateMarkerControls();
        };
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    private static String describeCursor(LogDataset dataset, int sample) {
        String text = "Sample " + (sample + 1) + " / " + dataset.getRowCount();
        LogChannel time = dataset.getTimeChannel();
        if (time == null) return text;
        double value = dataset.getValue(sample, time.getIndex());
        return text + (Double.isFinite(value) ? " • "
                + DURATION_FORMAT.format(value) + (time.getUnits().isEmpty()
                        ? "" : " " + time.getUnits()) : "");
    }

    private static double parseSpeed(String selected) {
        return Double.parseDouble(selected.substring(0, selected.length() - 1));
    }

    private static String describeDuration(LogDataset dataset, LogRange range) {
        LogChannel time = dataset.getTimeChannel();
        if (time == null) return "";
        double start = dataset.getValue(range.getStartInclusive(), time.getIndex());
        double end = dataset.getValue(range.getEndExclusive() - 1, time.getIndex());
        if (!Double.isFinite(start) || !Double.isFinite(end)) return "";
        double duration = Math.max(0.0, end - start);
        String units = time.getUnits();
        if ("msec".equalsIgnoreCase(units) || "ms".equalsIgnoreCase(units)) {
            return DURATION_FORMAT.format(duration / 1000.0) + " seconds";
        }
        return DURATION_FORMAT.format(duration) + (units.isEmpty()
                ? " time units" : " " + units);
    }

    private void showLoadError(String detail) {
        playbackTimer.stop();
        statusLabel.setText("Unable to load log: " + detail);
        applyRangeButton.setEnabled(dataset != null);
        JOptionPane.showMessageDialog(this, statusLabel.getText(),
                "Log analysis", JOptionPane.ERROR_MESSAGE);
    }

    private void addMarker() {
        if (dataset == null || cursor.getSampleIndex() < 0) return;
        LogMarkerType type = (LogMarkerType) markerType.getSelectedItem();
        markers.add(new LogMarker(cursor.getSampleIndex(), type,
                markerLabel.getText()));
        Collections.sort(markers);
        markerLabel.setText("");
        persistMarkers();
    }

    private void deleteMarkerAtCursor() {
        int sample = cursor.getSampleIndex();
        for (int index = 0; index < markers.size(); index++) {
            if (markers.get(index).getSampleIndex() == sample) {
                markers.remove(index);
                persistMarkers();
                return;
            }
        }
    }

    private void seekMarker(int direction) {
        if (markers.isEmpty() || cursor.getRange() == null) return;
        int sample = cursor.getSampleIndex();
        LogRange range = cursor.getRange();
        if (direction > 0) {
            for (LogMarker marker : markers) {
                if (marker.getSampleIndex() > sample
                        && contains(range, marker)) {
                    playback.seek(marker.getSampleIndex());
                    return;
                }
            }
            for (LogMarker marker : markers) {
                if (contains(range, marker)) {
                    playback.seek(marker.getSampleIndex());
                    return;
                }
            }
        } else {
            for (int index = markers.size() - 1; index >= 0; index--) {
                LogMarker marker = markers.get(index);
                if (marker.getSampleIndex() < sample
                        && contains(range, marker)) {
                    playback.seek(marker.getSampleIndex());
                    return;
                }
            }
            for (int index = markers.size() - 1; index >= 0; index--) {
                LogMarker marker = markers.get(index);
                if (contains(range, marker)) {
                    playback.seek(marker.getSampleIndex());
                    return;
                }
            }
        }
    }

    private static boolean contains(LogRange range, LogMarker marker) {
        return marker.getSampleIndex() >= range.getStartInclusive()
                && marker.getSampleIndex() < range.getEndExclusive();
    }

    private void persistMarkers() {
        graph.setMarkers(markers);
        xyGraph.setMarkers(markers);
        if (datasetFile != null) {
            try {
                markerStore.save(datasetFile, markers);
            } catch (IOException failure) {
                statusLabel.setText("Markers changed but could not be saved: "
                        + failure.getMessage());
            }
        }
        updateMarkerControls();
    }

    private void configureXyAxes() {
        xAxisSelector.removeAllItems();
        yAxisSelector.removeAllItems();
        if (dataset == null) return;
        for (LogChannel channel : dataset.getChannels()) {
            if (channel.isTimeChannel()) continue;
            xAxisSelector.addItem(channel);
            yAxisSelector.addItem(channel);
        }
        if (xAxisSelector.getItemCount() > 0) xAxisSelector.setSelectedIndex(0);
        if (yAxisSelector.getItemCount() > 1) yAxisSelector.setSelectedIndex(1);
        else if (yAxisSelector.getItemCount() > 0) yAxisSelector.setSelectedIndex(0);
        updateXyAxes();
    }

    private void updateXyAxes() {
        xyGraph.setAxes((LogChannel) xAxisSelector.getSelectedItem(),
                (LogChannel) yAxisSelector.getSelectedItem());
    }

    private void updateMarkerControls() {
        boolean loaded = dataset != null && cursor.getSampleIndex() >= 0;
        boolean markerInRange = false;
        boolean markerHere = false;
        LogRange range = cursor.getRange();
        for (LogMarker marker : markers) {
            markerHere |= marker.getSampleIndex() == cursor.getSampleIndex();
            markerInRange |= range != null && contains(range, marker);
        }
        addMarkerButton.setEnabled(loaded);
        previousMarkerButton.setEnabled(loaded && markerInRange);
        nextMarkerButton.setEnabled(loaded && markerInRange);
        deleteMarkerButton.setEnabled(loaded && markerHere);
        markerSummary.setText(markers.isEmpty() ? "No markers"
                : markers.size() + (markers.size() == 1 ? " marker" : " markers"));
    }

    private void offerRecentLog(File file) {
        Runnable update = () -> {
            recentLog = file != null && file.isFile() ? file : null;
            replayLastButton.setEnabled(recentLog != null);
            replayLastButton.setToolTipText(recentLog == null
                    ? "No completed capture is available in this session"
                    : "Load " + recentLog.getName()
                            + " without changing the Logger layout");
        };
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!recentLogAttached) {
            recentLogAttached = true;
            RecentLogCaptureService.getInstance().addListener(
                    recentLogListener);
        }
        offerRecentLog(RecentLogCaptureService.getInstance().getLastCompleted());
    }

    public void removeNotify() {
        if (recentLogAttached) {
            RecentLogCaptureService.getInstance().removeListener(
                    recentLogListener);
            recentLogAttached = false;
        }
        playback.pause();
        playbackTimer.stop();
        super.removeNotify();
    }
}
