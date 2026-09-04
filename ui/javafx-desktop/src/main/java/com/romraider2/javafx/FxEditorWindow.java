/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.romraider.Settings;
import com.romraider.Version;
import com.romraider.editor.compare.RomComparisonResult;
import com.romraider.editor.compare.RomComparisonService;
import com.romraider.editor.compare.TableComparison;
import com.romraider.editor.document.EditorDocument;
import com.romraider.editor.document.EditorDocumentController;
import com.romraider.editor.document.EditorDocumentSession;
import com.romraider.editor.document.EditorDocumentSnapshot;
import com.romraider.editor.io.RomLoadResult;
import com.romraider.editor.recovery.RecoverySnapshot;
import com.romraider.maps.Rom;
import com.romraider.maps.RomUserInteraction;
import com.romraider.maps.RomUserInteractionService;
import com.romraider.maps.Table;
import com.romraider.util.SettingsManager;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** JavaFX Editor shell backed only by toolkit-neutral document services. */
final class FxEditorWindow {
    private static final String[] LEVEL_NAMES = {
        "Beginner", "Intermediate", "Advanced", "Highest", "Debug Mode"
    };

    private final Stage stage = new Stage();
    private final Runnable closed;
    private final Runnable openLogger;
    private final EditorDocumentController controller =
            new EditorDocumentController();
    private final EditorDocumentSession.Listener documentListener;
    private final RomUserInteraction romInteraction;
    private final BorderPane root = new BorderPane();
    private final BorderPane center = new BorderPane();
    private final TreeView<NavigationItem> navigation = new TreeView<>();
    private final TextField search = new TextField();
    private final TabPane romTabs = new TabPane();
    private final TabPane calibrationTabs = new TabPane();
    private final Label activeRom = new Label("No ROM open");
    private final Label openMetric = metric("0 OPEN");
    private final Label dirtyMetric = metric("0 UNSAVED");
    private final Label levelMetric = metric("");
    private final Label status = new Label("Ready");
    private final Label documentCount = new Label("0 ROMs");
    private final ProgressBar progress = new ProgressBar(0);
    private final Map<Table, FxCalibrationPane> calibrationPanes =
            new HashMap<>();
    private EditorDocumentSnapshot snapshot;
    private boolean rebuilding;
    private boolean closing;
    private boolean recoveryInspected;

    FxEditorWindow(Runnable closed, Runnable openLogger) {
        this.closed = closed;
        this.openLogger = openLogger;
        snapshot = controller.getSession().snapshot();
        documentListener = next -> Platform.runLater(() -> render(next));
        controller.getSession().addListener(documentListener);
        romInteraction = createRomInteraction();
        RomUserInteractionService.addHandler(romInteraction);

        romTabs.getSelectionModel().selectedItemProperty().addListener(
                (value, previous, selected) -> {
                    if (!rebuilding && selected != null
                            && selected.getUserData() instanceof Rom rom) {
                        controller.activateRom(rom);
                    }
                });
        calibrationTabs.getSelectionModel().selectedItemProperty().addListener(
                (value, previous, selected) -> {
                    if (!rebuilding && selected != null
                            && selected.getUserData() instanceof Table table
                            && snapshot.getActiveRom() != null) {
                        controller.activateTable(snapshot.getActiveRom(), table);
                    }
                });

        root.setTop(new VBox(menuBar(), brandHeader(), commandDeck()));
        root.setCenter(workspace());
        root.setBottom(statusBar());
        Scene scene = new Scene(root, 1360, 860);
        FxTheme.apply(stage, scene);
        stage.setScene(scene);
        stage.setTitle(editorTitle());
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setOnCloseRequest(event -> {
            if (!confirmClose()) event.consume();
            else closing = true;
        });
        stage.setOnHidden(event -> dispose());
        render(snapshot);
    }

    private RomUserInteraction createRomInteraction() {
        return new RomUserInteraction() {
            @Override public void definitionError(Rom rom, Table table,
                    String title, String message, Throwable failure) {
                FxDialogs.error(stage, title, message);
            }

            @Override public boolean confirmChecksumFix(Rom rom, Table table,
                    String title, String message) {
                return FxDialogs.confirm(stage, title, message, "Apply fix");
            }

            @Override public void checksumValidationFailed(Rom rom,
                    String title, String message) {
                FxDialogs.error(stage, title, message);
            }

            @Override public void checksumUpdated(Rom rom, String message) {
                FxDialogs.info(stage, "Checksum updated", message);
            }
        };
    }

    private MenuBar menuBar() {
        Menu file = new Menu("File", null,
                shortcutItem("Open ROM…", "Shortcut+O", event -> chooseRom()),
                shortcutItem("Save", "Shortcut+S", event -> save(false)),
                shortcutItem("Save As…", "Shortcut+Shift+S",
                        event -> save(true)),
                new SeparatorMenuItem(),
                item("Close ROM", event -> closeActiveRom()),
                item("Exit", event -> stage.close()));
        Menu edit = new Menu("Edit", null,
                shortcutItem("Undo", "Shortcut+Z", event -> history(false)),
                shortcutItem("Redo", "Shortcut+Y", event -> history(true)));

        Menu view = new Menu("View");
        CheckMenuItem showHigher = new CheckMenuItem(
                "List tables above current user level");
        showHigher.setSelected(SettingsManager.getSettings().isDisplayHighTables());
        showHigher.setOnAction(event -> {
            Settings settings = SettingsManager.getSettings();
            settings.setDisplayHighTables(showHigher.isSelected());
            SettingsManager.save(settings);
            rebuildNavigation();
        });
        view.getItems().add(showHigher);

        Menu levels = new Menu("User Level");
        ToggleGroup group = new ToggleGroup();
        for (int level = 1; level <= 5; level++) {
            final int selected = level;
            RadioMenuItem levelItem = new RadioMenuItem(level + " "
                    + LEVEL_NAMES[level - 1]);
            levelItem.setToggleGroup(group);
            levelItem.setSelected(SettingsManager.getSettings().getUserLevel()
                    == level);
            levelItem.setOnAction(event -> {
                Settings settings = SettingsManager.getSettings();
                settings.setUserLevel(selected);
                SettingsManager.save(settings);
                rebuildNavigation();
                updateMetrics();
            });
            levels.getItems().add(levelItem);
        }

        Menu settings = new Menu("Settings", null,
                item("Editor Settings…", event -> FxSettingsWindow.show(stage,
                        this::settingsApplied)));
        Menu tools = new Menu("Tools", null,
                item("Compare open ROMs", event -> showComparison()),
                item("Live Tune preview", event -> showLiveTunePreview()));
        Menu logger = new Menu("Logger", null,
                item("Open Logger", event -> openLogger.run()));
        return new MenuBar(file, edit, view, levels, settings, tools, logger);
    }

    private Node brandHeader() {
        Label studio = new Label("ECU CALIBRATION STUDIO · JAVAFX DESKTOP");
        studio.getStyleClass().add("studio-kicker");
        VBox brand = new VBox(0, logo(174), studio);
        Button open = new Button("Open ROM");
        open.setDefaultButton(true);
        open.setOnAction(event -> chooseRom());
        MenuButton save = new MenuButton("Save As ▾", null,
                item("Save Now", event -> save(false)),
                item("Save As…", event -> save(true)));
        Button definitions = new Button("Definitions Manager");
        definitions.setOnAction(event -> FxDefinitionManager.show(stage,
                () -> setStatus("ECU definitions updated", 100)));
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        VBox context = new VBox(1,
                styled("ACTIVE ROM", "studio-kicker"), activeRom,
                styled("RomRaider2 " + Version.VERSION, "header-version"));
        context.setAlignment(Pos.CENTER_RIGHT);
        context.setMaxWidth(320);
        HBox header = new HBox(10, brand, separator(), open, save,
                definitions, fill, context);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("brand-header");
        return header;
    }

    private Node commandDeck() {
        Label title = styled("ROM WORKSPACE", "section-kicker");
        dirtyMetric.getStyleClass().add("danger");
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        HBox deck = new HBox(8, title, openMetric, dirtyMetric,
                levelMetric, fill,
                deckButton("Compare ROMs", this::showComparison),
                deckButton("Live Tune", this::showLiveTunePreview),
                deckButton("Logger", openLogger));
        deck.setAlignment(Pos.CENTER_LEFT);
        deck.getStyleClass().add("command-deck");
        return deck;
    }

    private Node workspace() {
        navigation.setShowRoot(false);
        navigation.setCellFactory(view -> new TreeCell<>() {
            @Override protected void updateItem(NavigationItem item,
                    boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label);
                setGraphic(null);
                setStyle(item != null && item.table != null
                        && item.table == snapshot.getActiveTable()
                        ? "-fx-font-weight: bold;" : "");
            }
        });
        navigation.setOnMouseClicked(event -> {
            if (rebuilding) return;
            TreeItem<NavigationItem> selected = navigation.getSelectionModel()
                    .getSelectedItem();
            if (selected != null && selected.getValue().table != null
                    && snapshot.getActiveRom() != null) {
                controller.openTable(snapshot.getActiveRom(),
                        selected.getValue().table);
            }
        });
        navigation.setOnKeyPressed(event -> {
            if (event.getCode() != javafx.scene.input.KeyCode.ENTER
                    || rebuilding) return;
            TreeItem<NavigationItem> selected = navigation.getSelectionModel()
                    .getSelectedItem();
            if (selected != null && selected.getValue().table != null
                    && snapshot.getActiveRom() != null) {
                controller.openTable(snapshot.getActiveRom(),
                        selected.getValue().table);
                event.consume();
            }
        });
        search.setPromptText("Search maps, categories, or DTC codes");
        search.textProperty().addListener((value, oldText, newText) ->
                rebuildNavigation());
        Label heading = styled("CALIBRATION MAP CATALOG", "section-kicker");
        VBox nav = new VBox(9, heading, search, navigation);
        VBox.setVgrow(navigation, Priority.ALWAYS);
        nav.setPadding(new Insets(12));
        nav.setPrefWidth(310);
        nav.getStyleClass().add("nav-pane");

        calibrationTabs.setTabClosingPolicy(
                TabPane.TabClosingPolicy.ALL_TABS);
        romTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        romTabs.setMaxHeight(46);
        center.setTop(romTabs);
        center.setCenter(emptyWorkspace());
        SplitPane split = new SplitPane(nav, center);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(.24);
        return split;
    }

    private Node emptyWorkspace() {
        Label title = new Label(snapshot.getActiveRom() == null
                ? "Professional ECU calibration workspace"
                : "Choose a calibration from the map catalog");
        title.getStyleClass().add("title");
        Label detail = new Label(snapshot.getActiveRom() == null
                ? "Open a ROM to inspect maps, compare revisions, manage "
                    + "changes, and prepare a calibrated image."
                : snapshot.getActiveRom().getFileName());
        detail.getStyleClass().add("muted");
        Button open = new Button("Open ROM");
        open.setDefaultButton(true);
        open.setOnAction(event -> chooseRom());
        VBox box = new VBox(14, logo(350), title, detail, open);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("workspace-empty");
        return new StackPane(box);
    }

    private Node statusBar() {
        progress.setPrefWidth(120);
        progress.setVisible(false);
        progress.setManaged(false);
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        HBox bar = new HBox(10, status, fill, progress, documentCount);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void chooseRom() {
        Settings settings = SettingsManager.getSettings();
        File image = FxDialogs.chooseRom(stage, settings.getLastImageDir());
        if (image == null) return;
        settings.setLastImageDir(image.getParentFile());
        SettingsManager.save(settings);
        openFiles(List.of(image));
    }

    void openFiles(List<File> files) {
        if (files == null) return;
        for (File file : files) {
            if (file == null || !file.isFile()) continue;
            setStatus("Opening " + file.getName() + "…", 0);
            controller.open(file, new FxRomLoadInteraction(stage,
                    this::setStatus)).whenComplete((result, failure) ->
                    Platform.runLater(() -> finishOpen(file, result, failure)));
        }
    }

    private void finishOpen(File file, RomLoadResult result,
            Throwable failure) {
        if (failure != null) {
            FxDialogs.error(stage, "Unable to open ROM",
                    FxDialogs.rootMessage(failure));
            setStatus("Unable to open " + file.getName(), 0);
        } else if (result != null && result.isLoaded()) {
            setStatus(result.getTotalChecksums() == 0 ? "Ready"
                    : result.getValidChecksums() + "/"
                    + result.getTotalChecksums() + " checksums are correct", 100);
        } else setStatus("ROM was not opened", 0);
    }

    private void save(boolean saveAs) {
        Rom rom = snapshot.getActiveRom();
        if (rom == null) return;
        File target = saveAs || rom.getFullFileName() == null
                ? FxDialogs.saveRom(stage,
                    rom.getFullFileName() == null
                        ? SettingsManager.getSettings().getLastImageDir()
                        : rom.getFullFileName().getParentFile(), rom.getFileName())
                : rom.getFullFileName();
        if (target == null) return;
        if (saveAs && target.isFile() && !FxDialogs.confirm(stage,
                "Replace existing ROM?", target.getName()
                        + " already exists. Replace it with the current ROM?",
                "Replace file")) return;
        setStatus("Saving " + target.getName() + "…", 0);
        controller.save(rom, target).whenComplete((saved, failure) ->
                Platform.runLater(() -> {
                    if (failure == null) setStatus("Saved " + saved.getName(), 100);
                    else FxDialogs.error(stage, "Unable to save ROM",
                            FxDialogs.rootMessage(failure));
                }));
    }

    private void history(boolean redo) {
        try {
            if (redo) controller.getSession().redo();
            else controller.getSession().undo();
        } catch (Exception failure) {
            FxDialogs.error(stage, redo ? "Redo failed" : "Undo failed",
                    FxDialogs.rootMessage(failure));
        }
    }

    private void closeActiveRom() {
        EditorDocument document = snapshot.getActiveDocument();
        if (document == null) return;
        if (document.isDirty() && !FxDialogs.confirm(stage,
                "Unsaved ROM changes", document.getName()
                        + " has unsaved changes. Close without saving?",
                "Discard changes")) return;
        controller.closeRom(document.getRom());
    }

    private boolean confirmClose() {
        long dirty = snapshot.getDocuments().stream()
                .filter(EditorDocument::isDirty).count();
        return dirty == 0 || FxDialogs.confirm(stage, "Unsaved ROM changes",
                dirty + " ROM workspace" + (dirty == 1 ? " has" : "s have")
                        + " unsaved changes. Exit without saving?",
                "Discard and exit");
    }

    private void render(EditorDocumentSnapshot next) {
        snapshot = next;
        stage.setTitle(editorTitle());
        activeRom.setText(next.getActiveRom() == null ? "No ROM open"
                : next.getActiveRom().getFileName());
        rebuildRomTabs();
        rebuildCalibrationTabs();
        rebuildNavigation();
        updateMetrics();
    }

    private void rebuildRomTabs() {
        rebuilding = true;
        try {
            romTabs.getTabs().clear();
            for (EditorDocument document : snapshot.getDocuments()) {
                Tab tab = new Tab(document.getName()
                        + (document.isDirty() ? "  •" : ""));
                tab.setUserData(document.getRom());
                tab.setOnCloseRequest(event -> {
                    event.consume();
                    controller.activateRom(document.getRom());
                    closeActiveRom();
                });
                romTabs.getTabs().add(tab);
                if (document.getRom() == snapshot.getActiveRom()) {
                    romTabs.getSelectionModel().select(tab);
                }
            }
        } finally {
            rebuilding = false;
        }
    }

    private void rebuildCalibrationTabs() {
        rebuilding = true;
        try {
            calibrationTabs.getTabs().clear();
            EditorDocument document = snapshot.getActiveDocument();
            if (document == null || document.getOpenTables().isEmpty()) {
                center.setCenter(emptyWorkspace());
                return;
            }
            for (Table table : document.getOpenTables()) {
                FxCalibrationPane pane = calibrationPanes.computeIfAbsent(
                        table, FxCalibrationPane::new);
                Tab tab = new Tab(table.getName(), pane);
                tab.setUserData(table);
                tab.setOnCloseRequest(event -> {
                    pane.close();
                    calibrationPanes.remove(table);
                    controller.closeTable(document.getRom(), table);
                });
                calibrationTabs.getTabs().add(tab);
                if (table == document.getActiveTable()) {
                    calibrationTabs.getSelectionModel().select(tab);
                }
            }
            center.setCenter(calibrationTabs);
        } finally {
            rebuilding = false;
        }
    }

    private void rebuildNavigation() {
        rebuilding = true;
        try {
            TreeItem<NavigationItem> rootItem = new TreeItem<>(
                    new NavigationItem("root", null));
            Rom rom = snapshot.getActiveRom();
            if (rom != null) {
                String query = search.getText() == null ? ""
                        : search.getText().trim().toLowerCase(Locale.ROOT);
                Settings settings = SettingsManager.getSettings();
                Map<String, TreeItem<NavigationItem>> categories =
                        new LinkedHashMap<>();
                for (Table table : rom.getTableCatalog()) {
                    if (!settings.isDisplayHighTables()
                            && table.getUserLevel() > settings.getUserLevel()) continue;
                    String haystack = table.getName() + " " + table.getCategory();
                    if (!query.isEmpty()
                            && !haystack.toLowerCase(Locale.ROOT).contains(query)) continue;
                    TreeItem<NavigationItem> parent = rootItem;
                    String path = "";
                    String category = table.getCategory();
                    String[] segments = category == null || category.isBlank()
                            ? new String[] {"Uncategorized"}
                            : category.split("//");
                    for (String raw : segments) {
                        String segment = raw.trim();
                        path += "//" + segment;
                        TreeItem<NavigationItem> existing = categories.get(path);
                        if (existing == null) {
                            existing = new TreeItem<>(new NavigationItem(segment, null));
                            categories.put(path, existing);
                            parent.getChildren().add(existing);
                        }
                        parent = existing;
                    }
                    TreeItem<NavigationItem> entry = new TreeItem<>(
                            new NavigationItem(table.getName(), table));
                    parent.getChildren().add(entry);
                    if (table == snapshot.getActiveTable()) expandParents(entry);
                }
                if (!query.isEmpty()) expandAll(rootItem);
            }
            navigation.setRoot(rootItem);
        } finally {
            rebuilding = false;
        }
    }

    private void updateMetrics() {
        int open = snapshot.getDocuments().size();
        long dirty = snapshot.getDocuments().stream()
                .filter(EditorDocument::isDirty).count();
        openMetric.setText(open + " OPEN");
        dirtyMetric.setText(dirty + " UNSAVED");
        dirtyMetric.setVisible(dirty > 0);
        dirtyMetric.setManaged(dirty > 0);
        int level = SettingsManager.getSettings().getUserLevel();
        levelMetric.setText("LEVEL " + level + " · "
                + LEVEL_NAMES[level - 1].toUpperCase(Locale.ROOT));
        documentCount.setText(open + " ROM" + (open == 1 ? "" : "s"));
    }

    private void settingsApplied() {
        FxTheme.refresh(stage.getScene());
        rebuildNavigation();
        updateMetrics();
        setStatus("Editor settings applied", 100);
    }

    private void showComparison() {
        if (snapshot.getDocuments().size() < 2) {
            FxDialogs.info(stage, "Compare ROMs",
                    "Open at least two ROMs to compare them.");
            return;
        }
        Rom left = snapshot.getDocuments().get(0).getRom();
        Rom right = snapshot.getDocuments().get(1).getRom();
        RomComparisonResult result = RomComparisonService.compare(left, right);
        javafx.scene.control.TableView<TableComparison> table =
                new javafx.scene.control.TableView<>(FXCollections
                        .observableArrayList(result.getTables()));
        javafx.scene.control.TableColumn<TableComparison, String> name =
                new javafx.scene.control.TableColumn<>("Calibration");
        name.setCellValueFactory(value -> new javafx.beans.property
                .ReadOnlyStringWrapper(value.getValue().getTableName()));
        javafx.scene.control.TableColumn<TableComparison, String> outcome =
                new javafx.scene.control.TableColumn<>("Result");
        outcome.setCellValueFactory(value -> new javafx.beans.property
                .ReadOnlyStringWrapper(value.getValue().getStatus().toString()));
        table.getColumns().addAll(name, outcome);
        name.setPrefWidth(430);
        outcome.setPrefWidth(170);
        Label summary = new Label(result.getEqualCount() + " equal  ·  "
                + result.getDifferentCount() + " different  ·  "
                + result.getMissingCount() + " missing");
        summary.getStyleClass().add("subtitle");
        BorderPane content = new BorderPane(table, summary, null, null, null);
        content.setPadding(new Insets(14));
        Stage compare = new Stage();
        compare.setTitle("Compare ROMs · " + left.getFileName() + " ↔ "
                + right.getFileName());
        Scene scene = new Scene(content, 680, 620);
        FxTheme.apply(compare, scene);
        compare.setScene(scene);
        compare.show();
    }

    private void showLiveTunePreview() {
        FxDialogs.info(stage, "Live Tune preview",
                "Live Tune remains a staged, read-only preview until a "
                        + "verified ECU connection and preflight are available.");
    }

    private void setStatus(String text, Integer percent) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setStatus(text, percent));
            return;
        }
        status.setText(text == null ? "" : text);
        int safe = percent == null ? 0 : Math.max(0, Math.min(100, percent));
        progress.setProgress(safe / 100.0);
        progress.setVisible(safe > 0 && safe < 100);
        progress.setManaged(progress.isVisible());
    }

    private String editorTitle() {
        return Version.PRODUCT_NAME + " " + Version.VERSION
                + " | JavaFX ECU Studio" + (snapshot.getActiveRom() == null
                ? "" : " — " + snapshot.getActiveRom().getFileName());
    }

    void show() {
        stage.show();
        stage.toFront();
        if (SettingsManager.getSettings().getEcuDefinitionFiles().isEmpty()) {
            FxDefinitionManager.show(stage, () ->
                    setStatus("ECU definitions configured", 100));
        }
        inspectRecovery();
    }

    private void inspectRecovery() {
        if (recoveryInspected) return;
        recoveryInspected = true;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return controller.discoverRecoverySnapshots();
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }).whenComplete((recoveries, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                setStatus("Unable to inspect recovery files: "
                        + FxDialogs.rootMessage(failure), 0);
            } else promptRecovery(recoveries, 0);
        }));
    }

    private void promptRecovery(List<RecoverySnapshot> recoveries, int index) {
        if (recoveries == null || index >= recoveries.size() || closing) return;
        RecoverySnapshot recovery = recoveries.get(index);
        String source = recovery.getSourcePath().isBlank()
                ? "Original location unavailable" : recovery.getSourcePath();
        FxDialogs.RecoveryDecision decision = FxDialogs.chooseRecovery(stage,
                "RomRaider2 found work from an abnormal exit.\n\n"
                        + recovery.getSourceName() + "\n" + source + "\n"
                        + recovery.getChangedCells() + " changed "
                        + (recovery.getChangedCells() == 1 ? "cell" : "cells")
                        + "\n\nRestoring opens an unsaved workspace and never "
                        + "overwrites the original ROM.");
        if (decision == FxDialogs.RecoveryDecision.DISCARD) {
            try {
                controller.discardRecovery(recovery);
            } catch (Exception failure) {
                FxDialogs.error(stage, "Unable to discard recovery",
                        FxDialogs.rootMessage(failure));
            }
            promptRecovery(recoveries, index + 1);
        } else if (decision == FxDialogs.RecoveryDecision.RESTORE) {
            setStatus("Restoring " + recovery.getSourceName() + "…", 0);
            controller.openRecovered(recovery, new FxRomLoadInteraction(stage,
                    this::setStatus)).whenComplete((result, failure) ->
                    Platform.runLater(() -> {
                        if (failure != null) {
                            FxDialogs.error(stage, "Recovery failed",
                                    FxDialogs.rootMessage(failure));
                        } else if (result != null && result.isLoaded()) {
                            setStatus("Recovered ROM opened as unsaved", 100);
                        }
                        promptRecovery(recoveries, index + 1);
                    }));
        } else promptRecovery(recoveries, index + 1);
    }

    void close() {
        closing = true;
        stage.close();
    }

    private void dispose() {
        if (!closing) closing = true;
        controller.getSession().removeListener(documentListener);
        RomUserInteractionService.removeHandler(romInteraction);
        calibrationPanes.values().forEach(FxCalibrationPane::close);
        calibrationPanes.clear();
        controller.close();
        closed.run();
    }

    private static MenuItem item(String text,
            javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(action);
        return item;
    }

    private static MenuItem shortcutItem(String text, String accelerator,
            javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem item = item(text, action);
        item.setAccelerator(javafx.scene.input.KeyCombination
                .keyCombination(accelerator));
        return item;
    }

    private static Button deckButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Label metric(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("metric");
        return label;
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

    private static ImageView logo(double width) {
        Image image = FxTheme.logo();
        ImageView view = image == null ? new ImageView() : new ImageView(image);
        view.setFitWidth(width);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static void expandParents(TreeItem<?> item) {
        for (TreeItem<?> cursor = item.getParent(); cursor != null;
                cursor = cursor.getParent()) cursor.setExpanded(true);
    }

    private static void expandAll(TreeItem<?> item) {
        item.setExpanded(true);
        item.getChildren().forEach(FxEditorWindow::expandAll);
    }

    private record NavigationItem(String label, Table table) {
        @Override public String toString() { return label; }
    }
}
