/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import com.romraider.Settings;
import com.romraider.Version;
import com.romraider.logger.analysis.LogChannel;
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.api.LiveDataSample;
import com.romraider.logger.api.LoggerChannel;
import com.romraider.logger.api.LoggerDashboardTile;
import com.romraider.logger.api.LoggerDashboardTileRole;
import com.romraider.logger.api.LoggerDashboardTileSize;
import com.romraider.logger.api.LoggerGaugeConfiguration;
import com.romraider.logger.api.LoggerGaugeAlertTracker;
import com.romraider.logger.api.LoggerGaugeTheme;
import com.romraider.portable.gauge.GaugeFaceRenderer;
import com.romraider.portable.gauge.GaugeReferenceScale;
import com.romraider.logger.api.LoggerLiveDataListener;
import com.romraider.logger.api.LoggerMessageSnapshot;
import com.romraider.logger.api.LoggerSessionState;
import com.romraider.logger.api.LoggerWorkspaceView;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import com.romraider.logger.runtime.LoggerDesktopRuntime;
import com.romraider.ui.ApplicationThemeService;
import com.romraider.ui.ThemeMode;
import com.romraider.util.SettingsManager;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** JavaFX Logger backed by the neutral LoggerDesktopRuntime. */
final class FxLoggerWindow {
    private final Stage stage = new Stage();
    private final Runnable closed;
    private final LoggerDesktopRuntime runtime;
    private final FxLoggerSetupTransfer setupTransfer;
    private final LoggerWorkspaceContext context;
    private final BorderPane root = new BorderPane();
    private final SplitPane workspace = new SplitPane();
    private final FxLoggerChannelPane channelRail;
    private final TabPane views = new TabPane();
    private final FlowPane overview = new FlowPane(10, 10);
    private final TableView<LiveDataSample> data = new TableView<>();
    private final FxLiveGraph graph = new FxLiveGraph();
    private final Map<String, VBox> overviewCards = new LinkedHashMap<>();
    private final Map<String, VBox> dashboardCards = new LinkedHashMap<>();
    private final Map<String, String> dashboardCardKeys = new LinkedHashMap<>();
    private final FlowPane dashboard = new FlowPane(12, 12);
    private final FxMountedGaugePane mountedGauges = new FxMountedGaugePane();
    private final Label mountedStatus = new Label();
    private FxGaugeStylePicker gaugeStylePicker;
    private final Map<String, Long> receivedAt = new LinkedHashMap<>();
    private final Map<String, com.romraider.portable.gauge.GaugeMotion> gaugeMotions = new java.util.concurrent.ConcurrentHashMap<>();
    private final javafx.animation.Timeline gaugeClock = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), event -> {
                refreshRecordingElapsed();
                refreshViews();
            }));
    private Node normalTop, normalBottom;
    private boolean gaugesOnly;
    private boolean mountedFullScreen, previousFullScreen;
    private VBox mountedSetup;
    private StackPane mountedViewport;
    private HBox mountedMenu;
    private final com.romraider.ui.DesktopDisplayAwake displayAwake;
    private final Label mountedAwakeStatus = new Label();
    private final Label mountedAwakeWarning = new Label("Screen awake unavailable");
    private Dialog<?> mountedChannelPicker;
    private final javafx.animation.PauseTransition mountedMenuTimeout =
            new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
    private final BorderPane analysis = new BorderPane();
    private FxDynoPane dyno;
    private FxFuelAnalysisPane mafAnalysis;
    private FxFuelAnalysisPane injectorAnalysis;
    private final Label sessionState = new Label();
    private final Label status = new Label("Ready");
    private final Label statistics = new Label();
    private final Label channelsMetric = styled("0 SELECTED", "metric");
    private final Button connect = new Button("Connect");
    private final Button disconnect = new Button("Disconnect");
    private final Button record = new Button("Start recording");
    private final Label recordingElapsed = new Label("00:00:00");
    private final Button mountedStartRecording = new Button("Start recording");
    private final Button mountedStopRecording = new Button("Stop recording");
    private final Label mountedRecordingElapsed = new Label("00:00:00");
    private final ToggleButton channels = new ToggleButton("Channels");
    private final Map<String, LiveDataSample> samples = new LinkedHashMap<>();
    private final LoggerGaugeAlertTracker gaugeAlerts = new LoggerGaugeAlertTracker();
    private final Map<String, Color> gaugeColors = new LinkedHashMap<>();
    private final Map<String, double[]> customGaugeSizes = new LinkedHashMap<>();
    private final Map<String, Stage> detachedGauges = new LinkedHashMap<>();
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private List<LoggerChannel> channelSnapshot = List.of();
    private Map<String, List<LiveDataSample>> viewHistory = Map.of();
    private String selectedDashboardParameter;
    private final ToggleGroup dashboardRoles = new ToggleGroup();
    private final ToggleGroup dashboardSizes = new ToggleGroup();
    private final Label dashboardSelection = new Label("Select a gauge");
    private final Consumer<List<LoggerChannel>> channelListener;
    private final Consumer<LoggerSessionState> stateListener;
    private final Consumer<LoggerMessageSnapshot> messageListener;
    private final LoggerLiveDataListener liveListener;
    private final ApplicationThemeService.Listener themeListener;
    private boolean disposed;
    private final FxLogLoadCoordinator logLoads;
    private FxLogAnalysisPane analysisPane;
    private FxAnalysisRangeLink analysisRanges;
    private final FxLoggerStartup startup = new FxLoggerStartup();
    private FxElmAdapterTest adapterTest;

    FxLoggerWindow(Runnable closed) {
        this(closed, null);
    }

    void setMafTransferTarget(java.util.function.Supplier<FxMafTransferTarget> target) { mafAnalysis.setTransferTarget(target); }
    private java.util.function.Supplier<FxMapTraceTarget> mapTraceTarget = () -> null;
    void setMapTraceTarget(java.util.function.Supplier<FxMapTraceTarget> target) {
        mapTraceTarget = java.util.Objects.requireNonNull(target);
        if (analysisPane != null) analysisPane.setMapTraceTarget(target);
    }

    FxLoggerWindow(Runnable closed,
            Function<File, CompletableFuture<LogDataset>> logParser) {
        this(closed, logParser, new com.romraider.ui.DesktopDisplayAwake());
    }

    FxLoggerWindow(Runnable closed, Function<File, CompletableFuture<LogDataset>> logParser,
            com.romraider.ui.DesktopDisplayAwake displayAwake) {
        this.displayAwake = java.util.Objects.requireNonNull(displayAwake);
        this.closed = closed;
        logLoads = new FxLogLoadCoordinator(logParser, Platform::runLater,
                this::showDataset, (file, failure) -> {
                    status.setText("Unable to open " + file.getName());
                    FxDialogs.error(stage, "Unable to open log",
                            FxDialogs.rootMessage(failure));
                });
        runtime = new LoggerDesktopRuntime();
        setupTransfer = new FxLoggerSetupTransfer(stage, runtime, status::setText);
        context = runtime.getWorkspaceContext();
        channelRail = new FxLoggerChannelPane(context.getChannels(),
                (title, message) -> FxDialogs.confirm(stage, title, message, "Clear selection"),
                () -> context.getSession().getState() == LoggerSessionState.RECORDING);

        channelListener = next -> {
            synchronized (samples) {
                gaugeAlerts.retainChannels(next);
                samples.entrySet().removeIf(entry -> next.stream().noneMatch(channel ->
                        channel.isSelected() && channel.getParameterId().equals(entry.getKey())
                        && channel.getConversionIdentity().equals(entry.getValue().getConversionIdentity())));
            }
            Platform.runLater(() -> {
                if (disposed) return;
                channelSnapshot = next;
                channelRail.update(next);
                if (gaugesOnly && !mountedFullScreen) refreshMountedSetup();
                refreshViews();
            });
        };
        stateListener = next -> Platform.runLater(() -> updateState(next));
        messageListener = next -> Platform.runLater(() -> {
            status.setText(next.getMessage());
            statistics.setText(next.getStatistics());
            status.getStyleClass().remove("danger");
            if (next.isError()) status.getStyleClass().add("danger");
        });
        liveListener = new LoggerLiveDataListener() {
            @Override public void sessionStateChanged(LoggerSessionState next) {
                gaugeAlerts.sessionChanged(next);
                if (next == LoggerSessionState.CONNECTING || next == LoggerSessionState.RECONNECTING) {
                    synchronized (samples) { samples.clear(); }
                    scheduleRefresh();
                }
                Platform.runLater(() -> updateState(next));
            }

            @Override public void sampleUpdated(LiveDataSample sample) {
                synchronized (samples) {
                    gaugeAlerts.update(sample, configurationFor(sample));
                    samples.put(sample.getParameterId(), sample);
                    receivedAt.put(sample.getParameterId(), System.nanoTime());
                }
                scheduleRefresh();
            }

            @Override public void parameterRemoved(String parameterId) {
                synchronized (samples) {
                    samples.remove(parameterId);
                    receivedAt.remove(parameterId);
                    gaugeMotions.keySet().removeIf(key -> key.startsWith(parameterId + "\n"));
                    gaugeAlerts.remove(parameterId);
                }
                scheduleRefresh();
            }
        };
        themeListener = mode -> Platform.runLater(this::refreshTheme);

        context.getChannels().addListener(channelListener);
        context.getSession().addStateListener(stateListener);
        context.getMessages().addListener(messageListener);
        context.getLiveData().addListener(liveListener);
        ApplicationThemeService.getInstance().addListener(themeListener);

        normalTop = new VBox(menuBar(), header(), viewBar());
        normalBottom = statusBar();
        root.setTop(normalTop);
        root.setCenter(workspace());
        root.setBottom(normalBottom);
        gaugeClock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        gaugeClock.play();
        Scene scene = new Scene(root, 1380, 860);
        FxTheme.apply(stage, scene);
        stage.setScene(scene);
        stage.fullScreenProperty().addListener((value, oldState, full) -> {
            if (mountedFullScreen && !full) { previousFullScreen = false; setMountedFullScreen(false); }
            updateDisplayAwake();
        });
        stage.focusedProperty().addListener((value, oldFocus, focused) -> {
            if (!focused && mountedMenu != null) { mountedMenuTimeout.stop(); mountedMenu.setVisible(false); }
            updateDisplayAwake();
        });
        stage.showingProperty().addListener((value, oldState, showing) -> updateDisplayAwake());
        stage.iconifiedProperty().addListener((value, oldState, minimized) -> updateDisplayAwake());
        stage.setTitle(Version.PRODUCT_NAME + " " + Version.VERSION
                + " | Logger");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setOnCloseRequest(event -> {
            if (context.getSession().getState() != LoggerSessionState.STOPPED
                    && !FxDialogs.confirm(stage, "Logger is active",
                    "Disconnect the active Logger session and close?",
                    "Disconnect and close")) event.consume();
        });
        stage.setOnHidden(event -> dispose());
        updateState(context.getSession().getState());
    }

    private MenuBar menuBar() {
        MenuItem saveProfile = item("Save Profile", event -> setupTransfer.showProfileSave());
        saveProfile.setAccelerator(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.S,
                javafx.scene.input.KeyCombination.SHORTCUT_DOWN));
        MenuItem saveProfileAs = item("Save Profile As…", event -> setupTransfer.showProfileSaveAs());
        saveProfileAs.setAccelerator(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.S,
                javafx.scene.input.KeyCombination.SHORTCUT_DOWN, javafx.scene.input.KeyCombination.SHIFT_DOWN));
        MenuItem reloadProfile = item("Reload Profile", event -> setupTransfer.reloadProfile());
        reloadProfile.setDisable(true);
        Menu file = new Menu("File", null,
                item("Open CSV log…", event -> openLog()),
                new SeparatorMenuItem(),
                item("Logger Setup…", event -> showSetup()),
                item("Load Profile…", event -> setupTransfer.showProfileLoad()),
                saveProfile, saveProfileAs, reloadProfile,
                new SeparatorMenuItem(),
                item("Import channel setup…", event -> setupTransfer.showImport()),
                item("Export channel setup…", event -> setupTransfer.showExport()),
                item("Close", event -> close()));
        file.setOnShowing(event -> reloadProfile.setDisable(setupTransfer.profilePath() == null));
        Menu logger = new Menu("Logger", null,
                item("Connect", event -> context.getSession().connect()),
                item("Disconnect", event -> context.getSession().disconnect()),
                new SeparatorMenuItem(),
                item("Start recording",
                        event -> context.getSession().startRecording()),
                item("Stop recording",
                        event -> context.getSession().stopRecording()),
                new SeparatorMenuItem(),
                item("Read-only adapter test…", event -> showAdapterTest()));
        ToggleGroup theme = new ToggleGroup();
        RadioMenuItem light = themeItem("Light", ThemeMode.LIGHT, theme);
        RadioMenuItem dark = themeItem("Dark", ThemeMode.DARK, theme);
        ThemeMode active = SettingsManager.getSettings().getThemeMode();
        (active == ThemeMode.LIGHT ? light : dark).setSelected(true);
        Menu view = new Menu("View", null,
                item("Gauges only", event -> setGaugesOnly(true)),
                item("Toggle channels", event ->
                        setChannelsVisible(!channels.isSelected())),
                new SeparatorMenuItem(), light, dark);
        return new MenuBar(file, logger, view);
    }

    private RadioMenuItem themeItem(String name, ThemeMode mode,
            ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(name);
        item.setToggleGroup(group);
        item.setOnAction(event -> applyTheme(mode));
        return item;
    }

    private void applyTheme(ThemeMode mode) {
        Settings settings = SettingsManager.getSettings();
        settings.setThemeMode(mode);
        SettingsManager.save(settings);
        ApplicationThemeService.getInstance().apply(mode);
        status.setText(mode + " theme applied");
    }

    private void refreshTheme() {
        if (disposed) return;
        FxTheme.refresh(stage.getScene());
        detachedGauges.values().stream().map(Stage::getScene)
                .forEach(FxTheme::refresh);
        refreshViews();
    }

    private Node header() {
        Label studio = new Label("REAL-TIME ECU LOGGER");
        studio.getStyleClass().add("studio-kicker");
        VBox brand = new VBox(4, FxTheme.brandLogo(150), studio);
        connect.setDefaultButton(true);
        connect.setOnAction(event -> context.getSession().connect());
        disconnect.setOnAction(event -> context.getSession().disconnect());
        record.setOnAction(event -> {
            if (context.getSession().getState() == LoggerSessionState.RECORDING) {
                context.getSession().stopRecording();
            } else context.getSession().startRecording();
        });
        Button setup = new Button("Logger Setup");
        setup.setOnAction(event -> showSetup());
        Button loadDefinition = new Button("Load Definition");
        loadDefinition.setOnAction(event -> loadLoggerDefinition());
        Button loadProfile = new Button("Load Profile");
        loadProfile.setId("logger-load-profile");
        loadProfile.setTooltip(new Tooltip("Load channel selections and units from a logger XML profile. Disconnect first; selections remain editable afterward."));
        loadProfile.setOnAction(event -> setupTransfer.showProfileLoad());
        channels.setSelected(context.getPreferences().isChannelRailVisible());
        channels.setOnAction(event -> setChannelsVisible(channels.isSelected()));
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        VBox connection = new VBox(1,
                styled("CONNECTION STATE", "studio-kicker"), sessionState);
        sessionState.getStyleClass().add("header-context-title");
        connection.setAlignment(Pos.CENTER_RIGHT);
        Node brandSeparator = separator();
        brand.visibleProperty().bind(
                root.widthProperty().greaterThanOrEqualTo(1050));
        brand.managedProperty().bind(brand.visibleProperty());
        brandSeparator.visibleProperty().bind(brand.visibleProperty());
        brandSeparator.managedProperty().bind(brand.visibleProperty());
        recordingElapsed.setId("logger-recording-elapsed");
        recordingElapsed.setAccessibleText("Recording elapsed time");
        recordingElapsed.setTooltip(new Tooltip("Elapsed time for this recording. Stops when recording ends and resets for the next recording; not the CSV sample span."));
        recordingElapsed.getStyleClass().add("header-context-title");
        recordingElapsed.setMinWidth(Region.USE_PREF_SIZE);
        record.setMinWidth(Region.USE_PREF_SIZE);
        HBox recordingControls = new HBox(7, record, recordingElapsed);
        recordingControls.setAlignment(Pos.CENTER_LEFT);
        FlowPane actions = new FlowPane(9, 6, connect, disconnect, recordingControls, loadDefinition, loadProfile, setup);
        actions.setId("logger-header-actions");
        actions.setMinWidth(0);
        HBox.setHgrow(actions, Priority.ALWAYS);
        for (var node : actions.getChildren()) {
            if (node instanceof Region control) control.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox header = new HBox(9, brand, brandSeparator, actions, fill, connection);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("brand-header");
        return header;
    }

    private Node viewBar() {
        Label title = styled("LOGGER WORKSPACE", "section-kicker");
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        Label hint = styled("Live views · Dyno · Log Analysis · MAF · Injector",
                "muted");
        hint.visibleProperty().bind(
                root.widthProperty().greaterThanOrEqualTo(1050));
        hint.managedProperty().bind(hint.visibleProperty());
        channels.setMinWidth(Region.USE_PREF_SIZE);
        channels.setTooltip(new Tooltip("Show or hide the channel list. Your selected channels keep logging when the list is hidden."));
        HBox bar = new HBox(8, channels, title, channelsMetric, fill, hint);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("command-deck");
        return bar;
    }

    private Node workspace() {
        overview.setPadding(new Insets(14));
        overview.setAlignment(Pos.TOP_CENTER);
        ScrollPane overviewScroll = new ScrollPane(overview);
        overviewScroll.setFitToWidth(true);
        dashboard.setPadding(new Insets(14));
        dashboard.setAlignment(Pos.TOP_CENTER);
        ScrollPane dashboardScroll = new ScrollPane(dashboard);
        dashboardScroll.setFitToWidth(true);
        configureDataTable();
        views.getTabs().addAll(
                fixedTab("Overview", overviewScroll),
                fixedTab("Data", dataWorkspace()),
                fixedTab("Graph", graph),
                fixedTab("Dashboard", dashboardWorkspace(dashboardScroll)),
                fixedTab("Dyno", dynoWorkspace()),
                fixedTab("Log Analysis", analysisWorkspace()),
                fixedTab("MAF", mafAnalysis = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, this::openLog)),
                fixedTab("Injector", injectorAnalysis = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.INJECTOR, this::openLog)));
        new FxFuelAnalysisLink(mafAnalysis, injectorAnalysis);
        views.getSelectionModel().select(tabFor(
                context.getPreferences().getView()));
        views.getSelectionModel().selectedIndexProperty().addListener(
                (value, oldIndex, newIndex) -> {
                    LoggerWorkspaceView selected = viewFor(newIndex.intValue());
                    if (selected != null) context.getPreferences().setView(selected);
                    refreshViews();
                });

        workspace.setOrientation(Orientation.HORIZONTAL);
        workspace.getItems().addAll(channelRail, views);
        workspace.setDividerPositions(.24);
        setChannelsVisible(channels.isSelected());
        return workspace;
    }

    private Node dashboardWorkspace(Node cards) {
        Button mounted = new Button("Open gauge display");
        mounted.setId("dashboard-open-gauge-display");
        mounted.setMinHeight(38);
        mounted.getStyleClass().add("dashboard-primary");
        mounted.setOnAction(event -> setGaugesOnly(true));
        dashboardSelection.getStyleClass().add("muted");
        Label title = styled("Dashboard", "title");
        Label subtitle = styled("Live gauges, values, trends and alerts. Customize each tile here; use Gauge Display to set up a full-screen layout.", "muted");
        subtitle.setWrapText(true);
        VBox intro = new VBox(4, title, subtitle);
        HBox.setHgrow(intro, Priority.ALWAYS);
        FlowPane actions = new FlowPane(8, 6, mounted);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(12, intro, actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dashboard-header");
        VBox controls = new VBox(8, header);
        controls.setPadding(new Insets(12, 14, 6, 14));
        BorderPane pane = new BorderPane(cards);
        pane.setId("desktop-dashboard");
        pane.setTop(controls);
        return pane;
    }

    private Node dynoWorkspace() {
        dyno = new FxDynoPane(context);
        return dyno;
    }

    private Node analysisWorkspace() {
        Label title = new Label("Log Analysis");
        title.getStyleClass().add("title");
        Label detail = new Label("Open a captured CSV log for table review, "
                + "playback, markers, time-series, and X/Y analysis.");
        detail.getStyleClass().add("muted");
        Button open = new Button("Open log file…");
        open.setDefaultButton(true);
        open.setOnAction(event -> openLog());
        VBox empty = new VBox(10, title, detail, open);
        empty.setAlignment(Pos.CENTER);
        analysis.setCenter(empty);
        return analysis;
    }

    private Node statusBar() {
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        HBox bar = new HBox(10, status, fill, statistics);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void configureDataTable() {
        TableColumn<LiveDataSample, String> name = new TableColumn<>("Channel");
        name.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                value.getValue().getName()));
        name.setPrefWidth(360);
        TableColumn<LiveDataSample, String> value = new TableColumn<>("Value");
        value.setCellValueFactory(sample -> new ReadOnlyStringWrapper(
                sample.getValue().getDisplayValue()));
        value.setPrefWidth(180);
        TableColumn<LiveDataSample, String> units = new TableColumn<>("Units");
        units.setCellValueFactory(sample -> new ReadOnlyStringWrapper(
                sample.getValue().getUnits()));
        units.setPrefWidth(150);
        data.getColumns().addAll(name, value, units);
        addStatisticColumn("Minimum", FxLoggerStatistics::minimum);
        addStatisticColumn("Maximum", FxLoggerStatistics::maximum);
        addStatisticColumn("Average", FxLoggerStatistics::average);
        name.setMinWidth(120); name.setPrefWidth(200);
        value.setMinWidth(65); value.setPrefWidth(85);
        units.setMinWidth(55); units.setPrefWidth(65);
        data.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        data.setPlaceholder(styled(
                "Select channels from the rail to populate live data.",
                "muted"));
    }

    private Node dataWorkspace() {
        Button reset = new Button("Reset statistics");
        reset.setId("logger-reset-statistics");
        reset.setTooltip(new Tooltip("Reset rolling statistics and graph history; keep current readings and recording"));
        reset.setOnAction(event -> {
            context.getLiveData().resetHistory();
            if (dyno != null) dyno.invalidate();
            refreshViews();
            status.setText("View statistics reset. Recording and saved logs are unchanged.");
        });
        Label explanation = new Label("Rolling statistics: up to 2,000 readings per channel");
        explanation.setWrapText(true);
        HBox controls = new HBox(10, reset, explanation);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(10));
        BorderPane pane = new BorderPane(data);
        pane.setTop(controls);
        return pane;
    }

    private void addStatisticColumn(String title,
            java.util.function.ToDoubleFunction<FxLoggerStatistics> value) {
        TableColumn<LiveDataSample, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> {
            LiveDataSample sample = cell.getValue();
            FxLoggerStatistics stats = FxLoggerStatistics.from(viewHistory.getOrDefault(
                    sample.getParameterId(), List.of()), sample.getUnits());
            return new ReadOnlyStringWrapper(stats.display(value.applyAsDouble(stats)));
        });
        column.setMinWidth(65); column.setPrefWidth(80);
        data.getColumns().add(column);
    }

    private void scheduleRefresh() {
        if (!refreshPending.compareAndSet(false, true)) return;
        Platform.runLater(() -> {
            refreshPending.set(false);
            refreshViews();
        });
    }

    private void refreshViews() {
        refreshDisplayAwakeStatus();
        if (disposed) return;
        viewHistory = context.getLiveData().getRecentSamples();
        if (gaugesOnly) { refreshMountedGauges(); return; }
        List<LiveDataSample> selected = selectedSamples();
        channelsMetric.setText(selected.size() + " SELECTED");
        if (selectedDashboardParameter != null && selected.stream().noneMatch(
                sample -> sample.getParameterId().equals(
                        selectedDashboardParameter))) {
            selectedDashboardParameter = null;
        }
        LiveDataSample previousSelection = data.getSelectionModel().getSelectedItem();
        data.getItems().setAll(selected);
        if (previousSelection != null) selected.stream().filter(s -> s.getParameterId().equals(previousSelection.getParameterId()))
                .findFirst().ifPresent(s -> data.getSelectionModel().select(s));
        List<Node> overviewNodes = new ArrayList<>(), dashboardNodes = new ArrayList<>();
        int index = 0;
        for (LiveDataSample sample : selected) {
            VBox card = overviewCards.computeIfAbsent(sample.getParameterId(), id -> valueCard(sample, false));
            ((Label) card.getChildren().get(0)).setText(sample.getName());
            ((Label) card.getChildren().get(1)).setText(sample.getDisplayValue());
            ((Label) card.getChildren().get(2)).setText(sample.getUnits());
            card.setPrefWidth(190);
            overviewNodes.add(card);
            dashboardNodes.add(dashboardCard(sample, index++, false));
        }
        if (selected.isEmpty()) {
            overviewNodes.add(emptyLoggerState(
                    "No live channels selected",
                    "Choose channels from the left rail to build the overview."));
            dashboardNodes.add(emptyLoggerState(
                    "No dashboard gauges yet",
                    "Select channels first, then choose each gauge style and size."));
        }
        if (!overview.getChildren().equals(overviewNodes)) overview.getChildren().setAll(overviewNodes);
        if (!dashboard.getChildren().equals(dashboardNodes)) dashboard.getChildren().setAll(dashboardNodes);
        var activeIds = selected.stream().map(LiveDataSample::getParameterId).collect(java.util.stream.Collectors.toSet());
        overviewCards.keySet().retainAll(activeIds);
        dashboardCards.keySet().removeIf(key -> !activeIds.contains(key.substring(2)));
        dashboardCardKeys.keySet().retainAll(dashboardCards.keySet());
        updateDashboardControls();
        refreshDetachedGauges(selected);
        if (views.getSelectionModel().getSelectedIndex() == 2) graph.setData(viewHistory, selected);
        if (dyno != null) dyno.refresh(channelSnapshot);
    }

    private Node emptyLoggerState(String title, String detail) {
        Label heading = styled(title, "subtitle");
        Label message = styled(detail, "muted");
        message.setWrapText(true);
        VBox empty = new VBox(7, heading, message);
        empty.getStyleClass().add("logger-empty");
        return empty;
    }

    private Node dashboardCard(LiveDataSample sample, int order,
            boolean detached) {
        LoggerDashboardTile tile = tileFor(sample.getParameterId(), order);
        Color accent = gaugeColors.computeIfAbsent(sample.getParameterId(),
                ignored -> savedGaugeColor(tile));
        String cacheId = (detached ? "D:" : "A:") + sample.getParameterId();
        String signature = tile.getRole() + "/" + tile.getSize() + "/" + tile.getCustomWidth() + "/" + tile.getCustomHeight()
                + "/" + tile.resolveGaugeTheme(context.getPreferences().getGaugeTheme()) + "/" + accent + "/" + sample.getConversionIdentity()
                + "/" + sample.getName() + "/" + sample.getUnits();
        VBox cached = dashboardCards.get(cacheId);
        if (cached != null && signature.equals(dashboardCardKeys.get(cacheId))) {
            Node body = cached.getChildren().get(1);
            if (body instanceof FxInstrumentView instrument) instrument.setReading(gaugeReading(sample));
            else if (body instanceof FxLegacyGaugeView legacy) legacy.setReading(gaugeReading(sample));
            else cached.getChildren().set(1, switch (tile.getRole()) {
                case GAUGE -> analogGauge(sample, accent);
                case VALUE -> digitalGauge(sample, accent);
                case TREND -> trendGauge(sample, accent);
                case ALARM -> alarmGauge(sample, accent);
            });
            cached.getStyleClass().remove("logger-card-selected");
            if (sample.getParameterId().equals(selectedDashboardParameter)) cached.getStyleClass().add("logger-card-selected");
            return cached;
        }
        Label name = styled(sample.getName(), "section-kicker");
        Label units = styled(sample.getUnits(), "muted");
        Node body = switch (tile.getRole()) {
            case GAUGE -> analogGauge(sample, accent);
            case VALUE -> digitalGauge(sample, accent);
            case TREND -> trendGauge(sample, accent);
            case ALARM -> alarmGauge(sample, accent);
        };
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        Button color = new Button("Color");
        Button settings = new Button("Limits");
        Button styleChoice = new Button("Style");
        styleChoice.setAccessibleText("Choose gauge style for " + sample.getName());
        styleChoice.setOnAction(event -> chooseGaugeStyle(sample.getParameterId()));
        settings.setOnAction(event -> configureGauge(sample.getParameterId()));
        color.setOnAction(event -> chooseGaugeColor(sample.getParameterId()));
        Button detach = new Button(detached ? "Attached view" : "Detach");
        detach.setDisable(detached);
        detach.setOnAction(event -> detachGauge(sample));
        Label resize = styled("↘ Drag to resize", "muted");
        resize.setVisible(tile.getSize() == LoggerDashboardTileSize.WIDE
                && !detached);
        resize.setManaged(resize.isVisible());
        javafx.scene.control.MenuButton customize = new javafx.scene.control.MenuButton("Customize");
        customize.setAccessibleText("Customize " + sample.getName());
        MenuItem detachItem = item(detached ? "Already detached" : "Detach window", event -> detach.fire());
        detachItem.setDisable(detached);
        customize.getItems().addAll(item("Gauge style…", event -> styleChoice.fire()),
                item("Limits and alerts…", event -> settings.fire()),
                item("Accent color…", event -> color.fire()), detachItem);
        Menu displayType = new Menu("Display type");
        for (LoggerDashboardTileRole role : LoggerDashboardTileRole.values()) {
            RadioMenuItem choice = new RadioMenuItem(role.getDisplayName());
            choice.setSelected(tile.getRole() == role);
            choice.setOnAction(event -> { selectedDashboardParameter = sample.getParameterId(); updateSelectedTile(role, null); });
            displayType.getItems().add(choice);
        }
        Menu tileSize = new Menu("Tile size");
        for (LoggerDashboardTileSize size : LoggerDashboardTileSize.values()) {
            RadioMenuItem choice = new RadioMenuItem(switch (size) { case STANDARD -> "Standard"; case LARGE -> "Large"; case WIDE -> "Custom"; });
            choice.setSelected(tile.getSize() == size);
            choice.setOnAction(event -> { selectedDashboardParameter = sample.getParameterId(); updateSelectedTile(null, size); });
            tileSize.getItems().add(choice);
        }
        customize.getItems().addAll(new SeparatorMenuItem(), displayType, tileSize);
        FlowPane footer = new FlowPane(8, 4, units, resize, customize);
        footer.setVisible(!gaugesOnly); footer.setManaged(!gaugesOnly);
        footer.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(7, name, body, footer);
        VBox.setVgrow(body, Priority.ALWAYS);
        card.setPadding(new Insets(13));
        card.getStyleClass().add(gaugesOnly ? "logger-gauge-seamless" : "logger-card");
        if (!gaugesOnly && sample.getParameterId().equals(selectedDashboardParameter)) {
            card.getStyleClass().add("logger-card-selected");
        }
        double width = switch (tile.getSize()) {
            case STANDARD -> 238;
            case LARGE -> 330;
            case WIDE -> 390;
        };
        double height = tile.getSize() == LoggerDashboardTileSize.LARGE
                ? 270 : 225;
        if (instrumentStyle(sample.getParameterId()) != null) height = Math.max(height, 275);
        double[] custom = customGaugeSizes.get(sample.getParameterId());
        if (custom == null && tile.hasCustomSize()) {
            custom = new double[] {tile.getCustomWidth(), tile.getCustomHeight()};
            customGaugeSizes.put(sample.getParameterId(), custom);
        }
        if (tile.getSize() == LoggerDashboardTileSize.WIDE && custom != null) {
            width = custom[0];
            height = custom[1];
        }
        card.setPrefSize(width, height);
        card.setMinSize(210, 190);
        double[] drag = new double[4];
        resize.setOnMousePressed(event -> {
            drag[0] = event.getScreenX();
            drag[1] = event.getScreenY();
            drag[2] = card.getWidth();
            drag[3] = card.getHeight();
            event.consume();
        });
        resize.setOnMouseDragged(event -> {
            double nextWidth = Math.max(210, Math.min(700,
                    drag[2] + event.getScreenX() - drag[0]));
            double nextHeight = Math.max(190, Math.min(520,
                    drag[3] + event.getScreenY() - drag[1]));
            card.setPrefSize(nextWidth, nextHeight);
            customGaugeSizes.put(sample.getParameterId(),
                    new double[] {nextWidth, nextHeight});
            event.consume();
        });
        resize.setOnMouseReleased(event -> {
            double[] saved = customGaugeSizes.get(sample.getParameterId());
            if (saved != null) {
                context.getPreferences().setDashboardTile(
                        sample.getParameterId(), tileFor(
                                sample.getParameterId(), order)
                                .withCustomSize(saved[0], saved[1]));
            }
            event.consume();
        });
        card.setOnMouseClicked(event -> {
            selectedDashboardParameter = sample.getParameterId();
            refreshViews();
        });
        dashboardCards.put(cacheId, card); dashboardCardKeys.put(cacheId, signature);
        return card;
    }

    private Node analogGauge(LiveDataSample sample, Color accent) {
        GaugeFaceRenderer.Style style = instrumentStyle(sample.getParameterId());
        if (style != null) return instrument(sample, style);
        return legacyGauge(sample, accent);
    }

    private Node legacyGauge(LiveDataSample sample, Color accent) {
        LoggerDashboardTile tile = tileFor(sample.getParameterId(), 0);
        return new FxLegacyGaugeView(tile.resolveGaugeTheme(context.getPreferences().getGaugeTheme()),
                gaugeReading(sample), tile.getAccentColor().isEmpty() ? null : accent);
    }

    private GaugeFaceRenderer.Style instrumentStyle(String parameterId) {
        LoggerGaugeTheme theme = tileFor(parameterId, 0).resolveGaugeTheme(context.getPreferences().getGaugeTheme());
        try { return GaugeFaceRenderer.Style.valueOf(theme.name()); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private void chooseGaugeStyle(String parameterId) {
        if (disposed || mountedFullScreen) return;
        if (gaugeStylePicker != null) gaugeStylePicker.close();
        LoggerGaugeTheme selected = parameterId == null ? context.getPreferences().getGaugeTheme()
                : tileFor(parameterId, 0).getGaugeTheme();
        gaugeStylePicker = new FxGaugeStylePicker(stage,
                parameterId == null ? "Default gauge style" : "Gauge style · " + parameterId,
                selected, parameterId != null, this::gaugeStylePreview, theme -> {
                    if (disposed) return;
                    if (parameterId == null) context.getPreferences().setGaugeTheme(theme);
                    else context.getPreferences().setDashboardTile(parameterId,
                            tileFor(parameterId, 0).withGaugeTheme(theme));
                    refreshViews();
                });
        gaugeStylePicker.setOnHidden(event -> gaugeStylePicker = null);
        gaugeStylePicker.show();
    }

    private Node gaugeStylePreview(LoggerGaugeTheme theme) {
        GaugeFaceRenderer.Style style;
        try { style = GaugeFaceRenderer.Style.valueOf(theme.name()); }
        catch (IllegalArgumentException legacy) { style = null; }
        if (style != null) {
            FxInstrumentView preview = new FxInstrumentView(style, new GaugeFaceRenderer.Reading(
                    "Engine Speed", "4200", "rpm", 4200, 0, 9000, 6650, "SAMPLE", "REFERENCE SCALE", false));
            preview.setMinSize(192, 150); preview.setPrefSize(192, 150); preview.setMaxSize(192, 150);
            return preview;
        }
        FxLegacyGaugeView preview = new FxLegacyGaugeView(theme, new GaugeFaceRenderer.Reading(
                "Engine Speed", "4200", "rpm", 4200, 0, 9000, 6650, "SAMPLE", "REFERENCE SCALE", false), null);
        preview.setMinSize(192, 150); preview.setPrefSize(192, 150); preview.setMaxSize(192, 150);
        return preview;
    }

    private Node instrument(LiveDataSample sample, GaugeFaceRenderer.Style style) {
        FxInstrumentView view = new FxInstrumentView(style, gaugeReading(sample),
                gaugeMotions.computeIfAbsent(sample.getParameterId() + "\n" + sample.getConversionIdentity(),
                        key -> new com.romraider.portable.gauge.GaugeMotion()));
        view.setPrefSize(220, 172);
        if (gaugesOnly) view.setPresentation(GaugeFaceRenderer.Presentation.SEAMLESS);
        return view;
    }

    private GaugeFaceRenderer.Reading gaugeReading(LiveDataSample sample) {
        List<LiveDataSample> history = viewHistory.getOrDefault(sample.getParameterId(), List.of()).stream()
                .filter(item -> item.getConversionIdentity().equals(sample.getConversionIdentity())).toList();
        double low = history.stream().mapToDouble(LiveDataSample::getRawValue).filter(Double::isFinite).min().orElse(Double.NaN);
        double high = history.stream().mapToDouble(LiveDataSample::getRawValue).filter(Double::isFinite).max().orElse(Double.NaN);
        GaugeReferenceScale reference = GaugeReferenceScale.forChannel(sample.getParameterId(), sample.getName(), sample.getUnits(), low, high);
        LoggerGaugeConfiguration config = configurationFor(sample);
        boolean custom = config != null && config.hasCustomScale();
        boolean live = context.getSession().getState().isLive();
        boolean fresh;
        synchronized (samples) { fresh = receivedAt.containsKey(sample.getParameterId())
                && System.nanoTime() - receivedAt.get(sample.getParameterId()) < 3_000_000_000L; }
        String state = !live ? "STOPPED" : !fresh ? "NO RECENT DATA"
                : !Double.isFinite(sample.getRawValue()) ? "NO VALID DATA" : "LIVE";
        LoggerGaugeConfiguration.AlertState alert = gaugeAlerts.state(sample.getParameterId(), config);
        return new GaugeFaceRenderer.Reading(
                sample.getName(), sample.getDisplayValue(), sample.getUnits(), live && fresh ? sample.getRawValue() : Double.NaN,
                custom ? config.getScaleMinimum() : reference.minimum,
                custom ? config.getScaleMaximum() : reference.maximum, high, state,
                custom ? "CUSTOM SCALE" : reference.reference ? "REFERENCE SCALE" : "RECENT SCALE",
                live && fresh && (alert == LoggerGaugeConfiguration.AlertState.HIGH || alert == LoggerGaugeConfiguration.AlertState.LOW));
    }

    void setGaugesOnly(boolean enabled) {
        if (disposed || gaugesOnly == enabled) return;
        if (!enabled) setMountedFullScreen(false);
        gaugesOnly = enabled;
        if (!enabled) {
            if (gaugeStylePicker != null) gaugeStylePicker.close();
            if (mountedChannelPicker != null) mountedChannelPicker.close();
            root.setTop(normalTop); root.setCenter(workspace); root.setBottom(normalBottom);
            views.getSelectionModel().select(3); refreshViews(); return;
        }
        String mountedStyle = "-rr-text: #edf1f4; -rr-muted: #a6b1bf; -fx-base: #17212b; "
                + "-fx-background: #0f151b; -fx-background-color: #0f151b; -fx-text-background-color: #edf1f4;";
        mountedGauges.setStyle(mountedStyle);
        mountedViewport = new StackPane(mountedGauges);
        mountedViewport.setStyle(mountedStyle);
        Button exitFull = new Button("Exit full screen"); exitFull.setMinHeight(48);
        exitFull.setOnAction(event -> setMountedFullScreen(false));
        mountedStartRecording.setId("gauges-start-recording");
        mountedStopRecording.setId("gauges-stop-recording");
        mountedStartRecording.setMinHeight(48); mountedStopRecording.setMinHeight(48);
        mountedStartRecording.setMinWidth(Region.USE_PREF_SIZE); mountedStopRecording.setMinWidth(Region.USE_PREF_SIZE);
        mountedStartRecording.setOnAction(event -> {
            context.getSession().startRecording(); updateMountedRecordingControls(); showMountedMenu();
        });
        mountedStopRecording.setOnAction(event -> {
            context.getSession().stopRecording(); updateMountedRecordingControls(); showMountedMenu();
        });
        mountedRecordingElapsed.setAccessibleText("Recording elapsed time");
        mountedRecordingElapsed.setMinWidth(Region.USE_PREF_SIZE);
        mountedMenu = new HBox(12, exitFull, mountedStartRecording, mountedStopRecording,
                mountedRecordingElapsed, mountedAwakeStatus); mountedMenu.setStyle(mountedStyle);
        updateMountedRecordingControls();
        mountedMenu.setAlignment(Pos.CENTER_LEFT);
        mountedMenu.setPadding(new Insets(8)); mountedMenu.setMaxHeight(64);
        StackPane.setAlignment(mountedMenu, Pos.TOP_CENTER);
        mountedViewport.getChildren().add(mountedMenu); mountedMenu.setVisible(false);
        mountedAwakeWarning.setStyle("-fx-background-color: #17212b; -fx-text-fill: #ffbc72; -fx-padding: 8;");
        mountedAwakeWarning.setMouseTransparent(true);
        StackPane.setAlignment(mountedAwakeWarning, Pos.BOTTOM_CENTER);
        mountedViewport.getChildren().add(mountedAwakeWarning);
        refreshDisplayAwakeStatus();
        mountedMenuTimeout.setOnFinished(event -> mountedMenu.setVisible(false));
        mountedViewport.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, event -> {
            if (mountedFullScreen) showMountedMenu();
        });
        root.setCenter(mountedViewport); root.setBottom(null);
        refreshMountedSetup();
        gaugeClock.play(); refreshViews();
    }

    private void refreshMountedGauges() {
        List<LiveDataSample> displayed = new java.util.ArrayList<>();
        for (String id : context.getPreferences().getGaugeDisplay().getVisibleChannels()) {
            LoggerChannel channel = channelSnapshot.stream().filter(item -> item.getParameterId().equals(id)).findFirst().orElse(null);
            LiveDataSample sample;
            synchronized (samples) { sample = samples.get(id); }
            if (channel == null || sample == null || !channel.isSelected()
                    || !sample.getConversionIdentity().equals(channel.getConversionIdentity())) {
                sample = new LiveDataSample(id, channel == null ? id : channel.getName(), Double.NaN, "—",
                        channel == null ? "" : channel.getUnits(), 0, channel == null ? "" : channel.getConversionIdentity());
            }
            displayed.add(sample);
        }
        mountedStatus.setText(context.getSession().getState().getDisplayName() + "  ·  "
                + displayed.size() + " display channels");
        mountedGauges.getChildren().clear();
        for (LiveDataSample sample : displayed) {
            GaugeFaceRenderer.Style style = instrumentStyle(sample.getParameterId());
            Node gauge = style == null ? legacyGauge(sample, savedGaugeColor(tileFor(sample.getParameterId(), 0))) : instrument(sample, style);
            mountedGauges.getChildren().add(gauge);
        }
        if (displayed.isEmpty()) mountedGauges.getChildren().add(emptyLoggerState("No display channels assigned",
                "Choose display channels in Gauges setup, or explicitly copy Logger channels. This view never starts a connection."));
    }

    private void refreshMountedSetup() {
        if (!gaugesOnly || mountedFullScreen) return;
        var display = context.getPreferences().getGaugeDisplay();
        Button back = new Button("Back to Dashboard"); back.setOnAction(event -> setGaugesOnly(false));
        Button full = new Button("Full screen"); full.setOnAction(event -> setMountedFullScreen(true));
        Button copy = new Button("Use Logger Channels");
        copy.setOnAction(event -> setGaugeDisplay(context.getPreferences().getGaugeDisplay().useLoggerChannels(channelSnapshot)));
        Button style = new Button("Default style…"); style.setOnAction(event -> chooseGaugeStyle(null));
        ComboBox<Integer> count = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6));
        count.setValue(display.getCount()); count.setAccessibleText("Number of display gauges");
        count.setOnAction(event -> setGaugeDisplay(context.getPreferences().getGaugeDisplay().withCount(count.getValue())));
        FlowPane commands = new FlowPane(8, 6, back, full, mountedStatus, new Label("Layout"), count, copy, style);
        FlowPane slots = new FlowPane(8, 6);
        for (int i = 0; i < display.getCount(); i++) {
            int slot = i; String id = display.getSlots().get(i);
            String name = channelSnapshot.stream().filter(channel -> channel.getParameterId().equals(id))
                    .map(LoggerChannel::getName).findFirst().orElse(id.isEmpty() ? "Choose channel" : id);
            Button channel = new Button((i + 1) + ": " + name);
            channel.setWrapText(true); channel.setPrefWidth(250); channel.setMaxWidth(250);
            channel.setOnAction(event -> chooseMountedChannel(slot));
            Button face = new Button("Style"); face.setDisable(id.isEmpty());
            face.setOnAction(event -> chooseGaugeStyle(id));
            Button limits = new Button("Limits & alerts");
            limits.setId("gauge-slot-" + slot + "-limits");
            limits.setDisable(channelSnapshot.stream().noneMatch(value -> value.getParameterId().equals(id)
                    && !value.getConversionIdentity().isEmpty()));
            limits.setOnAction(event -> configureGauge(id));
            slots.getChildren().add(new VBox(4, channel, new HBox(4, face, limits)));
        }
        Label help = new Label("Set up channels, styles, scales and alerts here. Full screen shows only the gauges; tap it for recording controls and Exit. Display assignments do not select channels for logging. Configure while parked.");
        help.setWrapText(true);
        mountedSetup = new VBox(8, commands, slots, help); mountedSetup.setPadding(new Insets(8));
        root.setTop(mountedSetup);
    }

    private void setGaugeDisplay(com.romraider.logger.api.LoggerGaugeDisplay display) {
        context.getPreferences().setGaugeDisplay(display); refreshMountedSetup(); refreshViews();
    }

    private void chooseMountedChannel(int slot) {
        if (mountedFullScreen || !gaugesOnly) return;
        Dialog<Void> picker = new Dialog<>(); picker.initOwner(stage); picker.setTitle("Gauge " + (slot + 1) + " channel");
        ButtonType assign = new ButtonType("Assign channel", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        picker.getDialogPane().getButtonTypes().addAll(assign, ButtonType.CANCEL);
        TextField search = new TextField(); search.setPromptText("Search channels");
        ListView<LoggerChannel> choices = new ListView<>(); choices.setPrefSize(440, 300);
        choices.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(LoggerChannel channel, boolean empty) {
                super.updateItem(channel, empty);
                setText(empty || channel == null ? "" : channel.getName() + " [" + channel.getParameterId() + "]"
                        + (channel.isSelected() ? "" : " · not selected in Logger"));
            }
        });
        Runnable filter = () -> choices.setItems(FXCollections.observableArrayList(channelSnapshot.stream()
                .filter(channel -> (channel.getName() + " " + channel.getParameterId()).toLowerCase(java.util.Locale.ROOT)
                        .contains(search.getText().trim().toLowerCase(java.util.Locale.ROOT))).toList()));
        search.textProperty().addListener((value, oldText, newText) -> filter.run()); filter.run();
        Button use = (Button) picker.getDialogPane().lookupButton(assign);
        use.setId("gauges-assign-channel");
        use.disableProperty().bind(choices.getSelectionModel().selectedItemProperty().isNull());
        use.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            LoggerChannel selected = choices.getSelectionModel().getSelectedItem();
            if (selected == null || disposed || !gaugesOnly || mountedFullScreen) { event.consume(); return; }
            String id = selected.getParameterId();
            setGaugeDisplay(context.getPreferences().getGaugeDisplay().withChannel(slot, id));
        });
        Button clear = new Button("Clear slot"); clear.setOnAction(event -> {
            picker.close(); setGaugeDisplay(context.getPreferences().getGaugeDisplay().withChannel(slot, ""));
        });
        picker.getDialogPane().setContent(new VBox(8, search, choices, clear));
        mountedChannelPicker = picker; picker.setOnHidden(event -> mountedChannelPicker = null); picker.show();
    }

    void setMountedFullScreen(boolean enabled) {
        if (enabled && (!gaugesOnly || disposed)) return;
        if (mountedFullScreen == enabled) return;
        mountedFullScreen = enabled; mountedMenuTimeout.stop(); mountedMenu.setVisible(false);
        if (enabled) {
            previousFullScreen = stage.isFullScreen(); root.setTop(null); stage.setFullScreen(true);
        } else {
            stage.setFullScreen(previousFullScreen); refreshMountedSetup();
        }
        updateDisplayAwake();
    }
    private void updateDisplayAwake() {
        displayAwake.setActive(!disposed && gaugesOnly && mountedFullScreen && stage.isFullScreen()
                && stage.isShowing() && stage.isFocused() && !stage.isIconified());
        refreshDisplayAwakeStatus();
    }
    private void refreshDisplayAwakeStatus() {
        var state = displayAwake.getStatus();
        mountedAwakeStatus.setText(state.getLabel());
        mountedAwakeWarning.setVisible(mountedFullScreen && state == com.romraider.ui.DesktopDisplayAwake.Status.UNAVAILABLE);
    }
    private void showMountedMenu() {
        if (!mountedFullScreen) return;
        updateMountedRecordingControls();
        mountedMenu.setVisible(true); mountedMenu.toFront(); mountedMenuTimeout.playFromStart();
    }

    private Node digitalGauge(LiveDataSample sample, Color accent) {
        Label value = new Label(sample.getDisplayValue());
        value.getStyleClass().add("gauge-digital");
        value.setStyle("-fx-text-fill: " + colorCss(accent) + ";");
        return new StackPane(value);
    }

    private Node trendGauge(LiveDataSample sample, Color accent) {
        Canvas canvas = new Canvas(210, 140);
        List<LiveDataSample> history = context.getLiveData().getRecentSamples()
                .getOrDefault(sample.getParameterId(), List.of());
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setStroke(accent);
        graphics.setLineWidth(2.5);
        if (history.size() > 1) {
            double min = history.stream().mapToDouble(
                    LiveDataSample::getRawValue).filter(Double::isFinite).min().orElse(0);
            double max = history.stream().mapToDouble(
                    LiveDataSample::getRawValue).filter(Double::isFinite).max().orElse(min + 1);
            if (max == min) max = min + 1;
            for (int index = 1; index < history.size(); index++) {
                if (!Double.isFinite(history.get(index - 1).getRawValue())
                        || !Double.isFinite(history.get(index).getRawValue())) continue;
                double x1 = (index - 1.0) / (history.size() - 1) * 210;
                double x2 = index / (double) (history.size() - 1) * 210;
                double y1 = 135 - (history.get(index - 1).getRawValue() - min)
                        / (max - min) * 125;
                double y2 = 135 - (history.get(index).getRawValue() - min)
                        / (max - min) * 125;
                graphics.strokeLine(x1, y1, x2, y2);
            }
        }
        Label value = new Label(sample.getDisplayValue());
        value.getStyleClass().add("subtitle");
        StackPane pane = new StackPane(canvas, value);
        StackPane.setAlignment(value, Pos.TOP_RIGHT);
        return pane;
    }

    private Node alarmGauge(LiveDataSample sample, Color accent) {
        LoggerGaugeConfiguration configuration = configurationFor(sample);
        LoggerGaugeConfiguration.AlertState state = gaugeAlerts.state(sample.getParameterId(), configuration);
        boolean warning = state == LoggerGaugeConfiguration.AlertState.LOW
                || state == LoggerGaugeConfiguration.AlertState.HIGH;
        String label = configuration == null || !configuration.hasWarnings() ? "LIMITS NOT SET"
                : state == LoggerGaugeConfiguration.AlertState.UNAVAILABLE ? "NO VALID DATA"
                : warning ? state.name() + " WARNING" : "NORMAL";
        Label stateLabel = new Label(label);
        stateLabel.getStyleClass().add("alarm-state");
        Color stateColor = warning ? Color.web("#d92632") : accent;
        stateLabel.setStyle("-fx-text-fill: " + colorCss(stateColor) + ";");
        Label value = new Label(sample.getDisplayValue());
        value.getStyleClass().add("gauge-value");
        VBox box = new VBox(7, stateLabel, value);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private double gaugeFraction(LiveDataSample sample) {
        if (!Double.isFinite(sample.getRawValue())) return 0;
        LoggerGaugeConfiguration configuration = configurationFor(sample);
        if (configuration != null && configuration.hasCustomScale()) {
            return clamp((sample.getRawValue() - configuration.getScaleMinimum())
                    / (configuration.getScaleMaximum()
                    - configuration.getScaleMinimum()));
        }
        List<LiveDataSample> history = context.getLiveData().getRecentSamples()
                .getOrDefault(sample.getParameterId(), List.of());
        double min = history.stream().mapToDouble(
                LiveDataSample::getRawValue).filter(Double::isFinite).min().orElse(0);
        double max = history.stream().mapToDouble(
                LiveDataSample::getRawValue).filter(Double::isFinite).max().orElse(100);
        if (max == min) return .5;
        return clamp((sample.getRawValue() - min) / (max - min));
    }

    private LoggerGaugeConfiguration configurationFor(LiveDataSample sample) {
        return context.getPreferences().getGaugeConfiguration(
                sample.getParameterId(), sample.getConversionIdentity());
    }

    private void configureGauge(String id) {
        LoggerChannel channel = channelSnapshot.stream().filter(value -> value.getParameterId().equals(id))
                .findFirst().orElse(null);
        if (channel == null || channel.getConversionIdentity().isEmpty()) {
            status.setText("Gauge limits require a known channel conversion.");
            return;
        }
        LoggerGaugeConfiguration raw = context.getPreferences().getGaugeConfiguration(id);
        LoggerGaugeConfiguration active = context.getPreferences().getGaugeConfiguration(id, channel.getConversionIdentity());
        FxGaugeConfigurationDialog.create(stage, channel, active, raw != null && active == null)
                .showAndWait().ifPresent(result -> {
                    LoggerChannel current = channelSnapshot.stream().filter(value -> value.getParameterId().equals(id))
                            .findFirst().orElse(null);
                    if (current == null || !current.getConversionIdentity().equals(channel.getConversionIdentity())) {
                        status.setText("Channel conversion changed; reopen gauge limits.");
                        return;
                    }
                    context.getPreferences().setGaugeConfiguration(id, result.configuration());
                    synchronized (samples) {
                        gaugeAlerts.remove(id);
                        LiveDataSample sample = samples.get(id);
                        if (sample != null) gaugeAlerts.update(sample, configurationFor(sample));
                    }
                    refreshViews();
                });
    }

    private LoggerDashboardTile tileFor(String parameterId, int order) {
        LoggerDashboardTile tile = context.getPreferences()
                .getDashboardTile(parameterId);
        return tile == null ? new LoggerDashboardTile(
                LoggerDashboardTileRole.GAUGE,
                LoggerDashboardTileSize.STANDARD, order) : tile;
    }

    private ToggleButton roleButton(LoggerDashboardTileRole role) {
        ToggleButton button = toggle(role.getDisplayName(), dashboardRoles, false);
        button.setUserData(role);
        button.setOnAction(event -> updateSelectedTile(role, null));
        return button;
    }

    private ToggleButton sizeButton(String label, LoggerDashboardTileSize size) {
        ToggleButton button = toggle(label, dashboardSizes, false);
        button.setUserData(size);
        button.setOnAction(event -> updateSelectedTile(null, size));
        return button;
    }

    private void updateSelectedTile(LoggerDashboardTileRole role,
            LoggerDashboardTileSize size) {
        if (selectedDashboardParameter == null) return;
        int order = selectedOrder();
        LoggerDashboardTile current = tileFor(selectedDashboardParameter, order);
        LoggerDashboardTile updated = role == null
                ? current : current.withRole(role);
        if (size != null) updated = updated.withSize(size);
        context.getPreferences().setDashboardTile(selectedDashboardParameter,
                updated);
        refreshViews();
    }

    private int selectedOrder() {
        return orderFor(selectedDashboardParameter);
    }

    private int orderFor(String parameterId) {
        for (int index = 0; index < channelSnapshot.size(); index++) {
            if (channelSnapshot.get(index).getParameterId()
                    .equals(parameterId)) return index;
        }
        return 0;
    }

    private void updateDashboardControls() {
        boolean enabled = selectedDashboardParameter != null;
        dashboardRoles.getToggles().forEach(toggle ->
                ((ToggleButton) toggle).setDisable(!enabled));
        dashboardSizes.getToggles().forEach(toggle ->
                ((ToggleButton) toggle).setDisable(!enabled));
        if (!enabled) {
            dashboardSelection.setText("Select a gauge");
            return;
        }
        LoggerDashboardTile tile = tileFor(selectedDashboardParameter,
                selectedOrder());
        dashboardSelection.setText(channelSnapshot.stream()
                .filter(channel -> channel.getParameterId()
                        .equals(selectedDashboardParameter))
                .map(LoggerChannel::getName).findFirst()
                .orElse(selectedDashboardParameter));
        dashboardRoles.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == tile.getRole())
                .findFirst().ifPresent(dashboardRoles::selectToggle);
        dashboardSizes.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == tile.getSize())
                .findFirst().ifPresent(dashboardSizes::selectToggle);
    }

    private void chooseGaugeColor(String parameterId) {
        ColorPicker picker = new ColorPicker(gaugeColors.getOrDefault(parameterId,
                Color.web("#0d948c")));
        Dialog<javafx.scene.control.ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Gauge color");
        dialog.setHeaderText("Choose a color for the selected gauge");
        dialog.getDialogPane().setContent(picker);
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);
        FxTheme.applyDialog(dialog.getDialogPane());
        if (dialog.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                == javafx.scene.control.ButtonType.OK) {
            gaugeColors.put(parameterId, picker.getValue());
            context.getPreferences().setDashboardTile(parameterId,
                    tileFor(parameterId, orderFor(parameterId)).withAccentColor(
                            picker.getValue().toString()));
            refreshViews();
        }
    }

    private static Color savedGaugeColor(LoggerDashboardTile tile) {
        if (!tile.getAccentColor().isEmpty()) {
            try {
                return Color.web(tile.getAccentColor());
            } catch (IllegalArgumentException ignored) {
                // Keep corrupt legacy customization from blocking the dashboard.
            }
        }
        return Color.web("#0d948c");
    }

    private void detachGauge(LiveDataSample sample) {
        Stage existing = detachedGauges.get(sample.getParameterId());
        if (existing != null) {
            existing.toFront();
            return;
        }
        Stage detached = new Stage();
        detached.setTitle(sample.getName() + " · RomRaider2 Gauge");
        detached.setMinWidth(260);
        detached.setMinHeight(240);
        Scene scene = new Scene(new StackPane(dashboardCard(sample,
                selectedOrder(), true)), 380, 330);
        FxTheme.apply(detached, scene);
        detached.setScene(scene);
        detached.setOnHidden(event -> {
            detachedGauges.remove(sample.getParameterId());
            dashboardCards.remove("D:" + sample.getParameterId());
            dashboardCardKeys.remove("D:" + sample.getParameterId());
        });
        detachedGauges.put(sample.getParameterId(), detached);
        FxWindowPlacement.show(detached);
    }

    private void refreshDetachedGauges(List<LiveDataSample> selected) {
        Map<String, LiveDataSample> byId = new LinkedHashMap<>();
        selected.forEach(sample -> byId.put(sample.getParameterId(), sample));
        detachedGauges.forEach((parameterId, detached) -> {
            LiveDataSample sample = byId.get(parameterId);
            StackPane holder = (StackPane) detached.getScene().getRoot();
            Node content = sample != null ? dashboardCard(sample, selectedOrder(), true)
                    : styled("Channel removed — no live data", "muted");
            if (holder.getChildren().size() != 1 || holder.getChildren().getFirst() != content) holder.getChildren().setAll(content);
        });
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static String colorCss(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    private List<LiveDataSample> selectedSamples() {
        Map<String, LiveDataSample> current;
        synchronized (samples) { current = new LinkedHashMap<>(samples); }
        List<LiveDataSample> result = new ArrayList<>();
        for (LoggerChannel channel : channelSnapshot) {
            if (!channel.isSelected()) continue;
            LiveDataSample sample = current.get(channel.getParameterId());
            if (sample != null && !sample.getConversionIdentity().equals(channel.getConversionIdentity())) sample = null;
            if (sample == null) sample = new LiveDataSample(
                    channel.getParameterId(), channel.getName(), Double.NaN, "—",
                    channel.getUnits(), System.currentTimeMillis(), channel.getConversionIdentity());
            result.add(sample);
        }
        return result;
    }

    private VBox valueCard(LiveDataSample sample, boolean gauge) {
        Label name = new Label(sample.getName());
        name.getStyleClass().add("section-kicker");
        Label value = new Label(sample.getDisplayValue());
        value.getStyleClass().add(gauge ? "gauge-value" : "title");
        Label units = new Label(sample.getUnits());
        units.getStyleClass().add("muted");
        VBox card = new VBox(5, name, value, units);
        card.setAlignment(gauge ? Pos.CENTER : Pos.CENTER_LEFT);
        card.getStyleClass().add("logger-card");
        return card;
    }

    private void updateState(LoggerSessionState next) {
        if (disposed) return;
        channelRail.setRecording(next == LoggerSessionState.RECORDING);
        sessionState.setText(next.getDisplayName());
        connect.setDisable(next != LoggerSessionState.STOPPED);
        disconnect.setDisable(false); // Also cancels a queued start before CONNECTING is published.
        boolean canRecord = next == LoggerSessionState.LIVE_ECU
                || next == LoggerSessionState.LIVE_EXTERNAL
                || next == LoggerSessionState.RECORDING;
        record.setDisable(!canRecord);
        record.setText(next == LoggerSessionState.RECORDING
                ? "Stop recording" : "Start recording");
        refreshRecordingElapsed();
    }

    private void updateMountedRecordingControls() {
        LoggerSessionState state = context.getSession().getState();
        boolean pending = context.getSession().isCommandPending();
        mountedStartRecording.setDisable(pending || (state != LoggerSessionState.LIVE_ECU && state != LoggerSessionState.LIVE_EXTERNAL));
        mountedStopRecording.setDisable(pending || state != LoggerSessionState.RECORDING);
    }

    private void refreshRecordingElapsed() {
        if (disposed) return;
        recordingElapsed.setText(formatRecordingElapsed(runtime.getRecordingElapsedMillis()));
        mountedRecordingElapsed.setText(recordingElapsed.getText());
        updateMountedRecordingControls();
    }

    static String formatRecordingElapsed(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private void setChannelsVisible(boolean visible) {
        channels.setSelected(visible);
        channels.setText(visible ? "Hide Channels" : "Show Channels");
        context.getPreferences().setChannelRailVisible(visible);
        if (visible && !workspace.getItems().contains(channelRail)) {
            workspace.getItems().add(0, channelRail);
            workspace.setDividerPositions(.24);
        } else if (!visible) workspace.getItems().remove(channelRail);
    }

    private void showSetup() {
        FxLoggerSetup.show(stage, runtime, () -> {
            status.setText("Logger configuration loaded");
            channelRail.update(channelSnapshot);
            considerAutoConnect();
        });
    }

    private void showAdapterTest() {
        if (context.getSession().getState() != LoggerSessionState.STOPPED) {
            FxDialogs.error(stage, "Logger is active", "Disconnect and stop connection attempts before opening the adapter test.");
            return;
        }
        if (adapterTest != null && adapterTest.stage.isShowing()) { adapterTest.stage.toFront(); return; }
        String output = runtime.getSettings().getLoggerOutputDirPath();
        adapterTest = new FxElmAdapterTest(stage, output == null ? null : new File(output),
                () -> !disposed && context.getSession().getState() == LoggerSessionState.STOPPED);
        adapterTest.show();
    }

    private void loadLoggerDefinition() {
        String configured = runtime.getSettings().getLoggerDefinitionFilePath();
        File selected = FxDialogs.chooseLoggerDefinition(stage,
                configured == null || configured.isBlank()
                        ? runtime.getSettings().getLastDefinitionDir()
                        : new File(configured).getParentFile());
        if (selected == null) return;
        try {
            Settings settings = runtime.getSettings();
            runtime.applySetup(selected.getAbsolutePath(), settings.getLoggerOutputDirPath(), settings.getLoggerPort(),
                    settings.getLoggerProtocol(), settings.getTransportProtocol(), settings.getTargetModule(),
                    settings.getAutoConnectOnStartup(), () -> SettingsManager.save(settings));
            status.setText("Loaded Logger definition: " + selected.getName());
            channelRail.update(channelSnapshot);
            considerAutoConnect();
        } catch (RuntimeException failure) {
            FxDialogs.error(stage, "Logger definition could not be loaded",
                    FxDialogs.rootMessage(failure));
        }
    }

    private void openLog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open CSV log");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "CSV logs", "*.csv"));
        String outputPath = runtime.getSettings().getLoggerOutputDirPath();
        if (outputPath != null) {
            File outputDirectory = new File(outputPath);
            if (outputDirectory.isDirectory()) {
                chooser.setInitialDirectory(outputDirectory);
            }
        }
        File file = chooser.showOpenDialog(stage);
        openLog(file);
    }

    void openLog(File file) {
        if (disposed || file == null) return;
        status.setText("Opening " + file.getName() + "…");
        logLoads.open(file);
    }

    private void showDataset(File source, LogDataset dataset) {
        FxLogAnalysisPane replacement = new FxLogAnalysisPane(source, dataset);
        replacement.setMapTraceTarget(mapTraceTarget);
        if (analysisRanges != null) analysisRanges.close();
        if (analysisPane != null) analysisPane.close();
        analysisPane = replacement;
        analysis.setCenter(analysisPane);
        mafAnalysis.setDataset(dataset);
        injectorAnalysis.setDataset(dataset);
        analysisRanges = new FxAnalysisRangeLink(analysisPane, mafAnalysis, injectorAnalysis);
        if (views.getSelectionModel().getSelectedIndex() < 6) views.getSelectionModel().select(5);
        status.setText("Loaded " + dataset.getSourceName());
    }

    void show() {
        FxWindowPlacement.show(stage);
        stage.toFront();
        String definition = runtime.getSettings().getLoggerDefinitionFilePath();
        if (definition == null || definition.isBlank()) {
            showSetup();
        } else considerAutoConnect();
    }

    void setTouchMode() {
        root.getProperties().put("rr-touch-override", true);
        FxTheme.refresh(stage.getScene());
    }

    void enterFullScreen() { stage.setFullScreen(true); }

    private void considerAutoConnect() {
        if (disposed) return;
        String definition = runtime.getSettings().getLoggerDefinitionFilePath();
        startup.consider(runtime.getSettings().getAutoConnectOnStartup(),
                definition != null && !definition.isBlank() && new File(definition).isFile(),
                context.getSession().getState(), () -> context.getSession().connect());
    }

    void close() {
        FxCloseRequest.request(stage);
    }

    private void dispose() {
        if (disposed) return;
        disposed = true;
        displayAwake.close();
        mountedMenuTimeout.stop();
        if (mountedChannelPicker != null) mountedChannelPicker.close();
        if (gaugeStylePicker != null) gaugeStylePicker.close();
        gaugeClock.stop();
        gaugeMotions.clear();
        logLoads.close();
        setupTransfer.close();
        if (adapterTest != null) adapterTest.close();
        context.getChannels().removeListener(channelListener);
        context.getSession().removeStateListener(stateListener);
        context.getMessages().removeListener(messageListener);
        context.getLiveData().removeListener(liveListener);
        ApplicationThemeService.getInstance().removeListener(themeListener);
        runtime.close();
        if (analysisPane != null) analysisPane.close();
        mafAnalysis.close();
        injectorAnalysis.close();
        new ArrayList<>(detachedGauges.values()).forEach(Stage::close);
        detachedGauges.clear();
        closed.run();
    }

    private static MenuItem item(String text,
            javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(action);
        return item;
    }

    private static Tab fixedTab(String name, Node content) {
        Tab tab = new Tab(name, content);
        tab.setClosable(false);
        return tab;
    }

    private static int tabFor(LoggerWorkspaceView view) {
        return switch (view) {
            case OVERVIEW -> 0;
            case DATA -> 1;
            case GRAPH -> 2;
            case DASHBOARD -> 3;
            case ANALYSIS -> 5;
        };
    }

    private static LoggerWorkspaceView viewFor(int tab) {
        return switch (tab) {
            case 0 -> LoggerWorkspaceView.OVERVIEW;
            case 1 -> LoggerWorkspaceView.DATA;
            case 2 -> LoggerWorkspaceView.GRAPH;
            case 3 -> LoggerWorkspaceView.DASHBOARD;
            case 5 -> LoggerWorkspaceView.ANALYSIS;
            default -> null;
        };
    }

    private static ToggleButton toggle(String text, ToggleGroup group,
            boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(selected);
        return button;
    }

    private static Label styled(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private static Region separator() {
        Region region = new Region();
        region.setPrefWidth(1);
        region.setPrefHeight(42);
        region.setStyle("-fx-background-color: rgba(255,255,255,.16);");
        return region;
    }

}
