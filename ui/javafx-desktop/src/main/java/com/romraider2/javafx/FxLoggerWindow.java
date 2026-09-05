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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
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
import javafx.scene.control.TextField;
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
    private final LoggerWorkspaceContext context;
    private final BorderPane root = new BorderPane();
    private final SplitPane workspace = new SplitPane();
    private final VBox channelRail = new VBox();
    private final TextField channelSearch = new TextField();
    private final VBox channelList = new VBox(3);
    private final TabPane views = new TabPane();
    private final FlowPane overview = new FlowPane(10, 10);
    private final TableView<LiveDataSample> data = new TableView<>();
    private final LiveGraph graph = new LiveGraph();
    private final FlowPane dashboard = new FlowPane(12, 12);
    private final BorderPane analysis = new BorderPane();
    private FxDynoPane dyno;
    private final Label sessionState = new Label();
    private final Label status = new Label("Ready");
    private final Label statistics = new Label();
    private final Label channelsMetric = styled("0 SELECTED", "metric");
    private final Button connect = new Button("Connect");
    private final Button disconnect = new Button("Disconnect");
    private final Button record = new Button("Start recording");
    private final ToggleButton channels = new ToggleButton("Channels");
    private final Map<String, LiveDataSample> samples = new LinkedHashMap<>();
    private final Map<String, Color> gaugeColors = new LinkedHashMap<>();
    private final Map<String, double[]> customGaugeSizes = new LinkedHashMap<>();
    private final Map<String, Stage> detachedGauges = new LinkedHashMap<>();
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private List<LoggerChannel> channelSnapshot = List.of();
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
    private final FxLoggerStartup startup = new FxLoggerStartup();

    FxLoggerWindow(Runnable closed) {
        this(closed, FxLogLoadCoordinator::parseAsync);
    }

    FxLoggerWindow(Runnable closed,
            Function<File, CompletableFuture<LogDataset>> logParser) {
        this.closed = closed;
        logLoads = new FxLogLoadCoordinator(logParser, Platform::runLater,
                this::showDataset, (file, failure) -> {
                    status.setText("Unable to open " + file.getName());
                    FxDialogs.error(stage, "Unable to open log",
                            FxDialogs.rootMessage(failure));
                });
        runtime = new LoggerDesktopRuntime();
        context = runtime.getWorkspaceContext();

        channelListener = next -> Platform.runLater(() -> {
            channelSnapshot = next;
            rebuildChannels();
            refreshViews();
        });
        stateListener = next -> Platform.runLater(() -> updateState(next));
        messageListener = next -> Platform.runLater(() -> {
            status.setText(next.getMessage());
            statistics.setText(next.getStatistics());
            status.getStyleClass().remove("danger");
            if (next.isError()) status.getStyleClass().add("danger");
        });
        liveListener = new LoggerLiveDataListener() {
            @Override public void sessionStateChanged(LoggerSessionState next) {
                Platform.runLater(() -> updateState(next));
            }

            @Override public void sampleUpdated(LiveDataSample sample) {
                synchronized (samples) {
                    samples.put(sample.getParameterId(), sample);
                }
                scheduleRefresh();
            }

            @Override public void parameterRemoved(String parameterId) {
                synchronized (samples) { samples.remove(parameterId); }
                scheduleRefresh();
            }
        };
        themeListener = mode -> Platform.runLater(this::refreshTheme);

        context.getChannels().addListener(channelListener);
        context.getSession().addStateListener(stateListener);
        context.getMessages().addListener(messageListener);
        context.getLiveData().addListener(liveListener);
        ApplicationThemeService.getInstance().addListener(themeListener);

        root.setTop(new VBox(menuBar(), header(), viewBar()));
        root.setCenter(workspace());
        root.setBottom(statusBar());
        Scene scene = new Scene(root, 1380, 860);
        FxTheme.apply(stage, scene);
        stage.setScene(scene);
        stage.setTitle(Version.PRODUCT_NAME + " " + Version.VERSION
                + " | JavaFX Logger");
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
        Menu file = new Menu("File", null,
                item("Open CSV log…", event -> openLog()),
                new SeparatorMenuItem(),
                item("Logger Setup…", event -> showSetup()),
                item("Close", event -> close()));
        Menu logger = new Menu("Logger", null,
                item("Connect", event -> context.getSession().connect()),
                item("Disconnect", event -> context.getSession().disconnect()),
                new SeparatorMenuItem(),
                item("Start recording",
                        event -> context.getSession().startRecording()),
                item("Stop recording",
                        event -> context.getSession().stopRecording()));
        ToggleGroup theme = new ToggleGroup();
        RadioMenuItem light = themeItem("Light", ThemeMode.LIGHT, theme);
        RadioMenuItem dark = themeItem("Dark", ThemeMode.DARK, theme);
        ThemeMode active = SettingsManager.getSettings().getThemeMode();
        (active == ThemeMode.LIGHT ? light : dark).setSelected(true);
        Menu view = new Menu("View", null,
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
        Label studio = new Label("REAL-TIME ECU LOGGER · JAVAFX DESKTOP");
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
        HBox header = new HBox(9, brand, brandSeparator, connect, disconnect,
                record, loadDefinition, setup, channels, fill, connection);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("brand-header");
        return header;
    }

    private Node viewBar() {
        Label title = styled("LOGGER WORKSPACE", "section-kicker");
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        Label hint = styled("Overview · Data · Graph · Dashboard · Dyno · Log Analysis",
                "muted");
        hint.visibleProperty().bind(
                root.widthProperty().greaterThanOrEqualTo(1050));
        hint.managedProperty().bind(hint.visibleProperty());
        HBox bar = new HBox(8, title, channelsMetric, fill, hint);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("command-deck");
        return bar;
    }

    private Node workspace() {
        channelSearch.setPromptText("Search channels");
        channelSearch.textProperty().addListener((value, oldText, newText) ->
                rebuildChannels());
        ScrollPane channelScroll = new ScrollPane(channelList);
        channelScroll.setFitToWidth(true);
        VBox.setVgrow(channelScroll, Priority.ALWAYS);
        Label channelHeading = styled("CHANNELS", "section-kicker");
        channelRail.getChildren().addAll(channelHeading, channelSearch,
                channelScroll);
        channelRail.setSpacing(9);
        channelRail.setPadding(new Insets(12));
        channelRail.setMinWidth(230);
        channelRail.setPrefWidth(320);
        channelRail.getStyleClass().add("nav-pane");

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
                fixedTab("Data", data),
                fixedTab("Graph", graph),
                fixedTab("Dashboard", dashboardWorkspace(dashboardScroll)),
                fixedTab("Dyno", dynoWorkspace()),
                fixedTab("Log Analysis", analysisWorkspace()));
        views.getSelectionModel().select(tabFor(
                context.getPreferences().getView()));
        views.getSelectionModel().selectedIndexProperty().addListener(
                (value, oldIndex, newIndex) -> {
                    LoggerWorkspaceView selected = viewFor(newIndex.intValue());
                    if (selected != null) context.getPreferences().setView(selected);
                });

        workspace.setOrientation(Orientation.HORIZONTAL);
        workspace.getItems().addAll(channelRail, views);
        workspace.setDividerPositions(.24);
        setChannelsVisible(channels.isSelected());
        return workspace;
    }

    private Node dashboardWorkspace(Node cards) {
        dashboardSelection.getStyleClass().add("muted");
        HBox selection = new HBox(6,
                styled("SELECTED GAUGE", "section-kicker"),
                dashboardSelection);
        HBox roles = new HBox(6,
                roleButton(LoggerDashboardTileRole.GAUGE),
                roleButton(LoggerDashboardTileRole.VALUE),
                roleButton(LoggerDashboardTileRole.TREND),
                roleButton(LoggerDashboardTileRole.ALARM));
        HBox sizes = new HBox(6,
                sizeButton("Standard", LoggerDashboardTileSize.STANDARD),
                sizeButton("Large", LoggerDashboardTileSize.LARGE),
                sizeButton("Custom", LoggerDashboardTileSize.WIDE));
        selection.setAlignment(Pos.CENTER_LEFT);
        roles.setAlignment(Pos.CENTER_LEFT);
        sizes.setAlignment(Pos.CENTER_LEFT);
        FlowPane styles = new FlowPane(12, 6, selection, roles, sizes);
        styles.setAlignment(Pos.CENTER_LEFT);
        styles.setPadding(new Insets(8, 12, 8, 12));
        BorderPane pane = new BorderPane(cards);
        pane.setTop(styles);
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
        data.setPlaceholder(styled(
                "Select channels from the rail to populate live data.",
                "muted"));
    }

    private void rebuildChannels() {
        String query = channelSearch.getText() == null ? ""
                : channelSearch.getText().trim().toLowerCase();
        channelList.getChildren().clear();
        for (LoggerChannel channel : channelSnapshot) {
            if (!query.isEmpty() && !(channel.getName() + " "
                    + channel.getParameterId()).toLowerCase().contains(query)) continue;
            CheckBox selected = new CheckBox(channel.getName()
                    + (channel.getUnits().isBlank() ? ""
                    : "  [" + channel.getUnits() + "]"));
            selected.setSelected(channel.isSelected());
            selected.setMaxWidth(Double.MAX_VALUE);
            selected.setTooltip(new Tooltip(channel.getName()
                    + (channel.getUnits().isBlank() ? ""
                    : " [" + channel.getUnits() + "]")));
            selected.setOnAction(event -> context.getChannels().setSelected(
                    channel.getParameterId(), selected.isSelected()));
            channelList.getChildren().add(selected);
        }
    }

    private void scheduleRefresh() {
        if (!refreshPending.compareAndSet(false, true)) return;
        Platform.runLater(() -> {
            refreshPending.set(false);
            refreshViews();
        });
    }

    private void refreshViews() {
        List<LiveDataSample> selected = selectedSamples();
        channelsMetric.setText(selected.size() + " SELECTED");
        if (selectedDashboardParameter != null && selected.stream().noneMatch(
                sample -> sample.getParameterId().equals(
                        selectedDashboardParameter))) {
            selectedDashboardParameter = null;
        }
        data.setItems(FXCollections.observableArrayList(selected));
        overview.getChildren().clear();
        dashboard.getChildren().clear();
        int index = 0;
        for (LiveDataSample sample : selected) {
            VBox card = valueCard(sample, false);
            card.setPrefWidth(190);
            overview.getChildren().add(card);
            dashboard.getChildren().add(dashboardCard(sample, index++, false));
        }
        if (selected.isEmpty()) {
            overview.getChildren().add(emptyLoggerState(
                    "No live channels selected",
                    "Choose channels from the left rail to build the overview."));
            dashboard.getChildren().add(emptyLoggerState(
                    "No dashboard gauges yet",
                    "Select channels first, then choose each gauge style and size."));
        }
        updateDashboardControls();
        refreshDetachedGauges(selected);
        graph.setData(context.getLiveData().getRecentSamples(), selected);
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
        color.setOnAction(event -> chooseGaugeColor(sample.getParameterId()));
        Button detach = new Button(detached ? "Attached view" : "Detach");
        detach.setDisable(detached);
        detach.setOnAction(event -> detachGauge(sample));
        Label resize = styled("↘ Drag to resize", "muted");
        resize.setVisible(tile.getSize() == LoggerDashboardTileSize.WIDE
                && !detached);
        resize.setManaged(resize.isVisible());
        HBox footer = new HBox(6, units, resize, fill, color, detach);
        footer.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(7, name, body, footer);
        card.setPadding(new Insets(13));
        card.getStyleClass().add("logger-card");
        if (sample.getParameterId().equals(selectedDashboardParameter)) {
            card.getStyleClass().add("logger-card-selected");
        }
        double width = switch (tile.getSize()) {
            case STANDARD -> 238;
            case LARGE -> 330;
            case WIDE -> 390;
        };
        double height = tile.getSize() == LoggerDashboardTileSize.LARGE
                ? 270 : 225;
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
        return card;
    }

    private Node analogGauge(LiveDataSample sample, Color accent) {
        Canvas canvas = new Canvas(190, 125);
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setLineWidth(12);
        graphics.setStroke(FxTheme.isDark() ? Color.web("#34404c")
                : Color.web("#d2dbe1"));
        graphics.strokeArc(20, 10, 150, 150, 25, 130,
                javafx.scene.shape.ArcType.OPEN);
        double fraction = gaugeFraction(sample);
        graphics.setStroke(accent);
        graphics.strokeArc(20, 10, 150, 150, 155, -130 * fraction,
                javafx.scene.shape.ArcType.OPEN);
        Label value = new Label(sample.getDisplayValue());
        value.getStyleClass().add("gauge-value");
        value.setStyle("-fx-text-fill: " + colorCss(accent) + ";");
        StackPane gauge = new StackPane(canvas, value);
        gauge.setMinHeight(135);
        return gauge;
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
                    LiveDataSample::getRawValue).min().orElse(0);
            double max = history.stream().mapToDouble(
                    LiveDataSample::getRawValue).max().orElse(min + 1);
            if (max == min) max = min + 1;
            for (int index = 1; index < history.size(); index++) {
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
        LoggerGaugeConfiguration configuration = context.getPreferences()
                .getGaugeConfiguration(sample.getParameterId());
        LoggerGaugeConfiguration.AlertState state = configuration == null
                ? LoggerGaugeConfiguration.AlertState.NORMAL
                : configuration.alertState(sample.getRawValue(), null);
        Label stateLabel = new Label(state == LoggerGaugeConfiguration.AlertState.NORMAL
                ? "NORMAL" : state.name() + " WARNING");
        stateLabel.getStyleClass().add("alarm-state");
        Color stateColor = state == LoggerGaugeConfiguration.AlertState.NORMAL
                ? accent : Color.web("#d92632");
        stateLabel.setStyle("-fx-text-fill: " + colorCss(stateColor) + ";");
        Label value = new Label(sample.getDisplayValue());
        value.getStyleClass().add("gauge-value");
        VBox box = new VBox(7, stateLabel, value);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private double gaugeFraction(LiveDataSample sample) {
        LoggerGaugeConfiguration configuration = context.getPreferences()
                .getGaugeConfiguration(sample.getParameterId());
        if (configuration != null && configuration.hasCustomScale()) {
            return clamp((sample.getRawValue() - configuration.getScaleMinimum())
                    / (configuration.getScaleMaximum()
                    - configuration.getScaleMinimum()));
        }
        List<LiveDataSample> history = context.getLiveData().getRecentSamples()
                .getOrDefault(sample.getParameterId(), List.of());
        double min = history.stream().mapToDouble(
                LiveDataSample::getRawValue).min().orElse(0);
        double max = history.stream().mapToDouble(
                LiveDataSample::getRawValue).max().orElse(100);
        if (max == min) return .5;
        return clamp((sample.getRawValue() - min) / (max - min));
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
        detached.setOnHidden(event -> detachedGauges.remove(
                sample.getParameterId()));
        detachedGauges.put(sample.getParameterId(), detached);
        FxWindowPlacement.show(detached);
    }

    private void refreshDetachedGauges(List<LiveDataSample> selected) {
        Map<String, LiveDataSample> byId = new LinkedHashMap<>();
        selected.forEach(sample -> byId.put(sample.getParameterId(), sample));
        detachedGauges.forEach((parameterId, detached) -> {
            LiveDataSample sample = byId.get(parameterId);
            if (sample != null) detached.getScene().setRoot(new StackPane(
                    dashboardCard(sample, selectedOrder(), true)));
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
            if (sample == null) sample = new LiveDataSample(
                    channel.getParameterId(), channel.getName(), 0, "—",
                    channel.getUnits(), System.currentTimeMillis());
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
        sessionState.setText(next.getDisplayName());
        connect.setDisable(next != LoggerSessionState.STOPPED);
        disconnect.setDisable(next == LoggerSessionState.STOPPED);
        boolean canRecord = next == LoggerSessionState.LIVE_ECU
                || next == LoggerSessionState.LIVE_EXTERNAL
                || next == LoggerSessionState.RECORDING;
        record.setDisable(!canRecord);
        record.setText(next == LoggerSessionState.RECORDING
                ? "Stop recording" : "Start recording");
    }

    private void setChannelsVisible(boolean visible) {
        channels.setSelected(visible);
        context.getPreferences().setChannelRailVisible(visible);
        if (visible && !workspace.getItems().contains(channelRail)) {
            workspace.getItems().add(0, channelRail);
            workspace.setDividerPositions(.24);
        } else if (!visible) workspace.getItems().remove(channelRail);
    }

    private void showSetup() {
        FxLoggerSetup.show(stage, runtime, () -> {
            status.setText("Logger configuration loaded");
            rebuildChannels();
            considerAutoConnect();
        });
    }

    private void loadLoggerDefinition() {
        String configured = runtime.getSettings().getLoggerDefinitionFilePath();
        File selected = FxDialogs.chooseLoggerDefinition(stage,
                configured == null || configured.isBlank()
                        ? runtime.getSettings().getLastDefinitionDir()
                        : new File(configured).getParentFile());
        if (selected == null) return;
        try {
            runtime.requireConfigurationEditable();
            runtime.getSettings().setLoggerDefinitionFilePath(
                    selected.getAbsolutePath());
            runtime.getSettings().setLastDefinitionDir(selected.getParentFile());
            runtime.reloadConfiguration();
            com.romraider.util.SettingsManager.save(runtime.getSettings());
            status.setText("Loaded Logger definition: " + selected.getName());
            rebuildChannels();
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
        if (analysisPane != null) analysisPane.close();
        analysisPane = replacement;
        analysis.setCenter(analysisPane);
        views.getSelectionModel().select(5);
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
        logLoads.close();
        context.getChannels().removeListener(channelListener);
        context.getSession().removeStateListener(stateListener);
        context.getMessages().removeListener(messageListener);
        context.getLiveData().removeListener(liveListener);
        ApplicationThemeService.getInstance().removeListener(themeListener);
        runtime.close();
        if (analysisPane != null) analysisPane.close();
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

    private static final class LiveGraph extends StackPane {
        private final Canvas canvas = new Canvas();
        private final Label empty = styled(
                "Select live channels to draw the graph.", "muted");
        private Map<String, List<LiveDataSample>> history = Map.of();
        private List<LiveDataSample> selected = List.of();

        LiveGraph() {
            getChildren().addAll(canvas, empty);
            canvas.widthProperty().bind(widthProperty());
            canvas.heightProperty().bind(heightProperty());
            widthProperty().addListener((value, oldWidth, newWidth) -> draw());
            heightProperty().addListener((value, oldHeight, newHeight) -> draw());
        }

        void setData(Map<String, List<LiveDataSample>> history,
                List<LiveDataSample> selected) {
            this.history = history;
            this.selected = selected;
            empty.setVisible(selected.isEmpty());
            draw();
        }

        private void draw() {
            double width = canvas.getWidth();
            double height = canvas.getHeight();
            if (width <= 0 || height <= 0) return;
            GraphicsContext graphics = canvas.getGraphicsContext2D();
            graphics.setFill(FxTheme.isDark() ? Color.web("#10151b")
                    : Color.web("#f4f7f9"));
            graphics.fillRect(0, 0, width, height);
            graphics.setStroke(FxTheme.isDark() ? Color.web("#34404c")
                    : Color.web("#d2dbe1"));
            for (int line = 1; line < 5; line++) {
                double y = line * height / 5;
                graphics.strokeLine(0, y, width, y);
            }
            Color[] colors = {Color.web("#0d948c"), Color.web("#d92632"),
                    Color.web("#3b82f6"), Color.web("#f59e0b")};
            int series = 0;
            for (LiveDataSample latest : selected) {
                List<LiveDataSample> values = history.get(latest.getParameterId());
                if (values == null || values.size() < 2) continue;
                double min = values.stream().mapToDouble(
                        LiveDataSample::getRawValue).min().orElse(0);
                double max = values.stream().mapToDouble(
                        LiveDataSample::getRawValue).max().orElse(min + 1);
                if (max == min) max = min + 1;
                graphics.setStroke(colors[series++ % colors.length]);
                graphics.setLineWidth(2);
                for (int index = 1; index < values.size(); index++) {
                    double x1 = (index - 1.0) / (values.size() - 1) * width;
                    double x2 = index / (double) (values.size() - 1) * width;
                    double y1 = height - (values.get(index - 1).getRawValue()
                            - min) / (max - min) * height;
                    double y2 = height - (values.get(index).getRawValue()
                            - min) / (max - min) * height;
                    graphics.strokeLine(x1, y1, x2, y2);
                }
            }
        }
    }
}
