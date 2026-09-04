/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.romraider.editor.calibration.CalibrationAdjustment;
import com.romraider.editor.calibration.CalibrationCellEdit;
import com.romraider.editor.calibration.CalibrationCellSnapshot;
import com.romraider.editor.calibration.CalibrationEditController;
import com.romraider.editor.calibration.CalibrationGridSnapshot;
import com.romraider.editor.calibration.TableCalibrationEditController;
import com.romraider.maps.Table;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

final class FxCalibrationPane extends BorderPane implements AutoCloseable {
    private static final Pattern DTC = Pattern.compile(
            "^\\s*\\(([PBCU][0-9A-Fa-f]{4})\\)");
    private static final int HEAT_LEVELS = 8;

    private final Table table;
    private final CalibrationEditController controller;
    private CalibrationGridSnapshot snapshot;
    private int selectedRow;
    private int selectedColumn;
    private final Label changed = new Label();
    private final Label status = new Label();
    private final Button undo = new Button("Undo");
    private final Button redo = new Button("Redo");
    private TableView<RowValues> grid;
    private VBox inspector;
    private FxSurfaceCanvas surface;
    private ToggleButton dtcToggle;
    private Label dtcState;

    FxCalibrationPane(Table table) {
        this.table = table;
        controller = new TableCalibrationEditController(table);
        snapshot = controller.getSnapshot();
        getStyleClass().add("surface");
        setTop(header());
        setCenter(isDiagnosticTroubleCode(snapshot)
                ? diagnosticControl() : calibrationWorkspace());
        controller.addListener(next -> Platform.runLater(() -> refresh(next)));
    }

    private Node header() {
        Label kind = new Label(snapshot.getTableType().replace('_', ' '));
        kind.getStyleClass().add("section-kicker");
        Label title = new Label(snapshot.getTableName());
        title.getStyleClass().add("title");
        title.setWrapText(true);
        title.setTooltip(new Tooltip(snapshot.getTableName()));
        Label detail = new Label(String.format(Locale.ROOT,
                "%d × %d  ·  %s  ·  address 0x%X",
                snapshot.getColumns(), snapshot.getRows(),
                snapshot.getUnit().isBlank() ? "raw scale" : snapshot.getUnit(),
                table.getStorageAddress()));
        detail.getStyleClass().add("muted");
        detail.setWrapText(true);
        VBox identity = new VBox(1, kind, title, detail);
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);
        changed.getStyleClass().add("metric");
        undo.setOnAction(event -> runEdit(() -> controller.undo(), "Undo applied"));
        redo.setOnAction(event -> runEdit(() -> controller.redo(), "Redo applied"));
        HBox row = new HBox(9, identity, changed, undo, redo);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 14, 12, 14));
        updateChangedLabel();
        return row;
    }

    private Node calibrationWorkspace() {
        grid = createGrid();
        inspector = createInspector();
        TabPane views = new TabPane();
        views.getTabs().add(fixedTab("Table", tableWorkspace()));
        if (snapshot.getRows() > 1 && snapshot.getColumns() > 1) {
            surface = new FxSurfaceCanvas(snapshot);
            views.getTabs().add(fixedTab("3D Surface", surface));
        }
        BorderPane content = new BorderPane();
        content.setCenter(views);
        content.setRight(inspector);
        BorderPane.setMargin(inspector, new Insets(0, 0, 0, 10));
        content.setPadding(new Insets(10));
        return content;
    }

    private Node tableWorkspace() {
        VBox content = new VBox(axisStrip(), grid);
        VBox.setVgrow(grid, Priority.ALWAYS);
        return content;
    }

    private Node axisStrip() {
        HBox strip = new HBox(16);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.getStyleClass().add("axis-strip");
        if (snapshot.getRows() > 1) {
            strip.getChildren().add(axisSummary("Y", snapshot.getRowAxisName(),
                    snapshot.getRowAxisUnit()));
        }
        if (!snapshot.getColumnAxisName().isBlank()) {
            strip.getChildren().add(axisSummary("X",
                    snapshot.getColumnAxisName(), snapshot.getColumnAxisUnit()));
        }
        strip.setVisible(!strip.getChildren().isEmpty());
        strip.setManaged(strip.isVisible());
        return strip;
    }

    private static Label axisSummary(String axis, String name, String unit) {
        Label label = new Label(axis + "  ·  " + axisName(name, unit));
        label.getStyleClass().add("axis-summary");
        label.setTooltip(new Tooltip(axis + " axis · " + axisName(name, unit)));
        return label;
    }

    @SuppressWarnings("rawtypes")
    private TableView<RowValues> createGrid() {
        TableView<RowValues> view = new TableView<>();
        view.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        view.getSelectionModel().setCellSelectionEnabled(true);
        view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        if (snapshot.getRows() > 1) {
            TableColumn<RowValues, String> axis = new TableColumn<>(
                    snapshot.getRowAxisName().isBlank() ? "Y axis"
                            : snapshot.getRowAxisName());
            axis.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                    snapshot.getRowLabels().get(value.getValue().row)));
            axis.setPrefWidth(100);
            axis.setSortable(false);
            view.getColumns().add(axis);
        }
        for (int column = 0; column < snapshot.getColumns(); column++) {
            final int index = column;
            String label = snapshot.getColumnLabels().size() > column
                    ? snapshot.getColumnLabels().get(column) : Integer.toString(column);
            TableColumn<RowValues, String> valueColumn = new TableColumn<>(label);
            valueColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                    value.getValue().values.get(index)));
            Label header = new Label(label);
            header.setTooltip(new Tooltip(label));
            valueColumn.setText(null);
            valueColumn.setGraphic(header);
            valueColumn.setPrefWidth(124);
            valueColumn.setSortable(false);
            valueColumn.setCellFactory(ignored -> valueCell(index));
            view.getColumns().add(valueColumn);
        }
        view.setItems(FXCollections.observableArrayList(rows(snapshot)));
        view.getSelectionModel().getSelectedCells().addListener(
                (javafx.collections.ListChangeListener<
                        javafx.scene.control.TablePosition>) change -> {
                    int last = view.getSelectionModel().getSelectedCells().size() - 1;
                    javafx.scene.control.TablePosition position = last < 0
                            ? null : view.getSelectionModel().getSelectedCells().get(last);
                    if (position == null) return;
                    int valueColumn = position.getColumn()
                            - (snapshot.getRows() > 1 ? 1 : 0);
                    if (position.getRow() >= 0 && valueColumn >= 0
                            && valueColumn < snapshot.getColumns()) {
                        selectedRow = position.getRow();
                        selectedColumn = valueColumn;
                        rebuildInspector();
                    }
                });
        view.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.C) {
                copySelection();
                event.consume();
                return;
            }
            if (event.isShortcutDown() && event.getCode() == KeyCode.V) {
                pasteSelection();
                event.consume();
                return;
            }
            if (event.isShortcutDown() && event.getCode() == KeyCode.A) {
                view.getSelectionModel().selectAll();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ENTER) {
                inspector.requestFocus();
                event.consume();
            }
        });
        return view;
    }

    @SuppressWarnings("rawtypes")
    private void copySelection() {
        List<int[]> selected = new ArrayList<>();
        int axisOffset = snapshot.getRows() > 1 ? 1 : 0;
        for (Object value : grid.getSelectionModel().getSelectedCells()) {
            javafx.scene.control.TablePosition position =
                    (javafx.scene.control.TablePosition) value;
            int column = position.getColumn() - axisOffset;
            if (position.getRow() >= 0 && column >= 0
                    && column < snapshot.getColumns()) {
                selected.add(new int[] {position.getRow(), column});
            }
        }
        if (selected.isEmpty()) selected.add(new int[] {
                selectedRow, selectedColumn});
        String values = FxCalibrationClipboard.serialize(snapshot, selected);
        ClipboardContent content = new ClipboardContent();
        content.putString(values);
        Clipboard.getSystemClipboard().setContent(content);
        status.setText(selected.size() == 1 ? "Cell value copied"
                : selected.size() + " values copied as a block");
    }

    private void pasteSelection() {
        String values = Clipboard.getSystemClipboard().getString();
        try {
            List<CalibrationCellEdit> edits = FxCalibrationClipboard.parse(
                    snapshot, selectedRow, selectedColumn, values);
            runEdit(() -> controller.setCellValues(edits).getSnapshot(),
                    edits.size() == 1 ? "Value pasted as one change"
                            : edits.size() + " values pasted as one change");
        } catch (Exception failure) {
            status.setText(FxDialogs.rootMessage(failure));
            status.getStyleClass().add("danger");
        }
    }

    private TableCell<RowValues, String> valueCell(int column) {
        return new TableCell<>() {
            private final Tooltip fullValue = new Tooltip();
            {
                getStyleClass().add("calibration-cell");
                setOnMouseClicked(event -> {
                    if (getIndex() >= 0 && getIndex() < snapshot.getRows()) {
                        selectedRow = getIndex();
                        selectedColumn = column;
                        rebuildInspector();
                    }
                });
            }

            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                fullValue.setText(empty || item == null ? "" : item);
                setTooltip(empty || item == null ? null : fullValue);
                getStyleClass().remove("calibration-cell-changed");
                for (int level = 0; level < HEAT_LEVELS; level++) {
                    getStyleClass().remove("calibration-heat-" + level);
                }
                if (!empty && getIndex() >= 0 && getIndex() < snapshot.getRows()) {
                    getStyleClass().add("calibration-heat-" + heatLevel(
                            snapshot.cellAt(getIndex(), column).getRealValue()));
                }
                if (!empty && getIndex() >= 0 && getIndex() < snapshot.getRows()
                        && snapshot.cellAt(getIndex(), column).isChanged()) {
                    getStyleClass().add("calibration-cell-changed");
                }
            }
        };
    }

    private VBox createInspector() {
        VBox box = new VBox(8);
        box.setPrefWidth(275);
        box.setPadding(new Insets(14));
        box.getStyleClass().add("raised");
        rebuildInspector(box);
        return box;
    }

    private void rebuildInspector() {
        if (inspector != null) rebuildInspector(inspector);
    }

    private void rebuildInspector(VBox box) {
        box.getChildren().clear();
        CalibrationCellSnapshot cell = snapshot.cellAt(selectedRow, selectedColumn);
        Label kicker = new Label("SELECTED CELL");
        kicker.getStyleClass().add("section-kicker");
        Label value = new Label(cell.getDisplayValue());
        value.getStyleClass().add("title");
        Label unit = new Label(snapshot.getUnit().isBlank()
                ? "Raw value" : snapshot.getUnit());
        unit.getStyleClass().add("muted");
        unit.setWrapText(true);
        Label coordinate = new Label(selectedCoordinate());
        coordinate.getStyleClass().add("muted");
        coordinate.setWrapText(true);
        coordinate.setMaxWidth(Double.MAX_VALUE);
        coordinate.setTooltip(new Tooltip(selectedCoordinate()));
        TextField editor = new TextField(cell.getDisplayValue());
        editor.setPromptText("New value");
        Button apply = new Button("Apply value");
        apply.setDefaultButton(true);
        apply.setMaxWidth(Double.MAX_VALUE);
        apply.setOnAction(event -> applyValue(editor.getText()));
        editor.setOnAction(event -> apply.fire());

        HBox fine = adjustmentRow("− Fine", CalibrationAdjustment.FINE_DECREASE,
                "+ Fine", CalibrationAdjustment.FINE_INCREASE);
        HBox coarse = adjustmentRow("− Coarse",
                CalibrationAdjustment.COARSE_DECREASE,
                "+ Coarse", CalibrationAdjustment.COARSE_INCREASE);
        Label increments = new Label("DEFINITION INCREMENTS");
        increments.getStyleClass().add("section-kicker");
        box.getChildren().addAll(kicker, value, unit, coordinate, editor, apply,
                increments, fine, coarse);
        if (cell.isChanged()) {
            Button restore = new Button("Restore saved value");
            restore.setMaxWidth(Double.MAX_VALUE);
            restore.setOnAction(event -> runEdit(() -> controller
                    .restoreCellValue(selectedRow, selectedColumn).getSnapshot(),
                    "Saved value restored"));
            box.getChildren().add(restore);
        }
        status.getStyleClass().add("muted");
        status.setWrapText(true);
        box.getChildren().add(status);
    }

    private String selectedCoordinate() {
        List<String> parts = new ArrayList<>();
        if (snapshot.getRows() > 1 && selectedRow < snapshot.getRowLabels().size()) {
            parts.add("Y · " + axisValue(snapshot.getRowAxisName(),
                    snapshot.getRowLabels().get(selectedRow),
                    snapshot.getRowAxisUnit()));
        }
        if (!snapshot.getColumnAxisName().isBlank()
                && selectedColumn < snapshot.getColumnLabels().size()) {
            parts.add("X · " + axisValue(snapshot.getColumnAxisName(),
                    snapshot.getColumnLabels().get(selectedColumn),
                    snapshot.getColumnAxisUnit()));
        }
        return parts.isEmpty() ? "Row " + (selectedRow + 1) + "  ·  Column "
                + (selectedColumn + 1) : String.join("  ·  ", parts);
    }

    private static String axisValue(String name, String value, String unit) {
        StringBuilder result = new StringBuilder();
        if (!name.isBlank()) result.append(name).append(' ');
        result.append(value);
        if (!unit.isBlank()) result.append(' ').append(unit);
        return result.toString();
    }

    private static String axisName(String name, String unit) {
        String normalizedName = name == null || name.isBlank() ? "Axis" : name;
        return unit == null || unit.isBlank() ? normalizedName
                : normalizedName + " [" + unit + "]";
    }

    private int heatLevel(double value) {
        double min = snapshot.getCells().stream()
                .mapToDouble(CalibrationCellSnapshot::getRealValue).min().orElse(0);
        double max = snapshot.getCells().stream()
                .mapToDouble(CalibrationCellSnapshot::getRealValue).max().orElse(min);
        if (!Double.isFinite(value) || max <= min) return 0;
        double fraction = Math.max(0, Math.min(1, (value - min) / (max - min)));
        return Math.min(HEAT_LEVELS - 1, (int) Math.floor(fraction * HEAT_LEVELS));
    }

    private HBox adjustmentRow(String leftText, CalibrationAdjustment left,
            String rightText, CalibrationAdjustment right) {
        Button leftButton = new Button(leftText);
        Button rightButton = new Button(rightText);
        leftButton.setMaxWidth(Double.MAX_VALUE);
        rightButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(leftButton, Priority.ALWAYS);
        HBox.setHgrow(rightButton, Priority.ALWAYS);
        leftButton.setOnAction(event -> adjust(left));
        rightButton.setOnAction(event -> adjust(right));
        return new HBox(7, leftButton, rightButton);
    }

    private Node diagnosticControl() {
        CalibrationCellSnapshot cell = snapshot.cellAt(0, 0);
        String code = DTC.matcher(snapshot.getTableName()).find()
                ? snapshot.getTableName().substring(1, 6).toUpperCase(Locale.ROOT)
                : "DTC";
        Label kicker = new Label("DIAGNOSTIC TROUBLE CODE");
        kicker.getStyleClass().add("section-kicker");
        Label codeLabel = new Label(code);
        codeLabel.getStyleClass().add("title");
        dtcState = new Label();
        dtcState.getStyleClass().add("subtitle");
        dtcToggle = new ToggleButton();
        dtcToggle.getStyleClass().add("dtc-switch");
        updateDiagnosticState();
        dtcToggle.setOnAction(event -> runEdit(() -> controller.setCellValue(0, 0,
                dtcToggle.isSelected() ? "1" : "0").getSnapshot(),
                code + (dtcToggle.isSelected() ? " enabled" : " disabled")));
        Button restore = new Button("Restore saved state");
        restore.setOnAction(event -> runEdit(() -> controller
                .restoreCellValue(0, 0).getSnapshot(), "Saved DTC state restored"));
        VBox card = new VBox(13, kicker, codeLabel, dtcState, dtcToggle,
                restore, status);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(460);
        card.getStyleClass().add("dtc-card");
        StackPane center = new StackPane(card);
        center.setPadding(new Insets(30));
        return center;
    }

    private void applyValue(String value) {
        runEdit(() -> controller.setCellValue(selectedRow, selectedColumn,
                value).getSnapshot(), "Value applied to ROM history");
    }

    private void adjust(CalibrationAdjustment adjustment) {
        runEdit(() -> controller.adjustCellValue(selectedRow, selectedColumn,
                adjustment).getSnapshot(), adjustment.isCoarse()
                ? "Coarse adjustment applied" : "Fine adjustment applied");
    }

    private void runEdit(EditOperation operation, String message) {
        try {
            refresh(operation.run());
            status.setText(message);
        } catch (Exception failure) {
            status.setText(FxDialogs.rootMessage(failure));
            status.getStyleClass().add("danger");
        }
    }

    private void refresh(CalibrationGridSnapshot next) {
        snapshot = next;
        updateChangedLabel();
        if (grid != null) {
            grid.setItems(FXCollections.observableArrayList(rows(snapshot)));
            grid.refresh();
        }
        if (surface != null) surface.setSnapshot(snapshot);
        rebuildInspector();
        if (isDiagnosticTroubleCode(snapshot)) updateDiagnosticState();
    }

    private void updateChangedLabel() {
        changed.setText(snapshot.getChangedValueCount() + " CHANGED");
        changed.setVisible(snapshot.getChangedValueCount() > 0);
        changed.setManaged(changed.isVisible());
        undo.setDisable(!controller.canUndo());
        redo.setDisable(!controller.canRedo());
    }

    private void updateDiagnosticState() {
        if (dtcToggle == null || dtcState == null) return;
        boolean enabled = snapshot.cellAt(0, 0).getRawValue() != 0.0;
        dtcToggle.setSelected(enabled);
        dtcToggle.setText(enabled ? "Enabled" : "Disabled");
        dtcState.setText(enabled ? "Code enabled" : "Code disabled");
    }

    static boolean isDiagnosticTroubleCode(CalibrationGridSnapshot value) {
        return "SWITCH".equalsIgnoreCase(value.getTableType())
                && value.getRows() == 1 && value.getColumns() == 1
                && DTC.matcher(value.getTableName()).find();
    }

    private static List<RowValues> rows(CalibrationGridSnapshot value) {
        List<RowValues> rows = new ArrayList<>();
        for (int row = 0; row < value.getRows(); row++) {
            List<String> cells = new ArrayList<>();
            for (int column = 0; column < value.getColumns(); column++) {
                cells.add(value.cellAt(row, column).getDisplayValue());
            }
            rows.add(new RowValues(row, cells));
        }
        return rows;
    }

    private static Tab fixedTab(String name, Node content) {
        Tab tab = new Tab(name, content);
        tab.setClosable(false);
        return tab;
    }

    @Override public void close() {
        controller.close();
    }

    private record RowValues(int row, List<String> values) { }

    @FunctionalInterface
    private interface EditOperation {
        CalibrationGridSnapshot run() throws Exception;
    }

    private static final class FxSurfaceCanvas extends StackPane {
        private final Canvas canvas = new Canvas();
        private CalibrationGridSnapshot snapshot;
        private double yaw = -.68;
        private double pitch = .62;
        private double previousX;
        private double previousY;

        FxSurfaceCanvas(CalibrationGridSnapshot snapshot) {
            this.snapshot = snapshot;
            getChildren().add(canvas);
            canvas.widthProperty().bind(widthProperty());
            canvas.heightProperty().bind(heightProperty());
            widthProperty().addListener((value, oldWidth, newWidth) -> draw());
            heightProperty().addListener((value, oldHeight, newHeight) -> draw());
            setOnMousePressed(event -> {
                previousX = event.getX();
                previousY = event.getY();
            });
            setOnMouseDragged(event -> {
                yaw += (event.getX() - previousX) * .012;
                pitch = Math.max(-1.30, Math.min(1.30,
                        pitch + (event.getY() - previousY) * .012));
                previousX = event.getX();
                previousY = event.getY();
                draw();
            });
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    yaw = -.68;
                    pitch = .62;
                    draw();
                }
            });
        }

        void setSnapshot(CalibrationGridSnapshot value) {
            snapshot = value;
            draw();
        }

        private void draw() {
            double width = canvas.getWidth();
            double height = canvas.getHeight();
            if (width <= 0 || height <= 0) return;
            GraphicsContext graphics = canvas.getGraphicsContext2D();
            graphics.clearRect(0, 0, width, height);
            graphics.setFill(FxTheme.isDark() ? Color.web("#10151b")
                    : Color.web("#f4f7f9"));
            graphics.fillRect(0, 0, width, height);
            double min = snapshot.getCells().stream()
                    .mapToDouble(CalibrationCellSnapshot::getRealValue).min().orElse(0);
            double max = snapshot.getCells().stream()
                    .mapToDouble(CalibrationCellSnapshot::getRealValue).max().orElse(min + 1);
            if (max == min) max = min + 1;
            Point[][] points = new Point[snapshot.getRows()][snapshot.getColumns()];
            for (int row = 0; row < snapshot.getRows(); row++) {
                for (int column = 0; column < snapshot.getColumns(); column++) {
                    double value = snapshot.cellAt(row, column).getRealValue();
                    points[row][column] = project(column, row,
                            (value - min) / (max - min), width, height);
                }
            }
            graphics.setLineWidth(1.5);
            for (int row = 0; row < snapshot.getRows(); row++) {
                for (int column = 0; column < snapshot.getColumns(); column++) {
                    Point point = points[row][column];
                    double value = snapshot.cellAt(row, column).getRealValue();
                    double fraction = (value - min) / (max - min);
                    graphics.setStroke(Color.color(.08 + .75 * fraction,
                            .38 + .25 * (1 - fraction), .56 - .34 * fraction));
                    if (column + 1 < snapshot.getColumns()) {
                        Point next = points[row][column + 1];
                        graphics.strokeLine(point.x, point.y, next.x, next.y);
                    }
                    if (row + 1 < snapshot.getRows()) {
                        Point next = points[row + 1][column];
                        graphics.strokeLine(point.x, point.y, next.x, next.y);
                    }
                }
            }
            graphics.setFill(FxTheme.isDark() ? Color.LIGHTGRAY : Color.DARKSLATEGRAY);
            graphics.fillText("Drag to rotate · double-click to reset",
                    16, height - 16);
        }

        private Point project(int column, int row, double value,
                double width, double height) {
            double x = snapshot.getColumns() <= 1 ? 0
                    : column / (double) (snapshot.getColumns() - 1) - .5;
            double z = snapshot.getRows() <= 1 ? 0
                    : row / (double) (snapshot.getRows() - 1) - .5;
            double y = (value - .5) * .72;
            double rotatedX = x * Math.cos(yaw) - z * Math.sin(yaw);
            double rotatedZ = x * Math.sin(yaw) + z * Math.cos(yaw);
            double rotatedY = y * Math.cos(pitch) - rotatedZ * Math.sin(pitch);
            double depth = y * Math.sin(pitch) + rotatedZ * Math.cos(pitch);
            double scale = Math.min(width, height) * .72 * (1 + depth * .18);
            return new Point(width / 2 + rotatedX * scale,
                    height / 2 - rotatedY * scale);
        }

        private record Point(double x, double y) { }
    }
}
