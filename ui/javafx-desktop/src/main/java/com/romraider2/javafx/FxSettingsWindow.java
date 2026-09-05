/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.Settings;
import com.romraider.ui.ApplicationThemeService;
import com.romraider.ui.ThemeMode;
import com.romraider.util.SettingsManager;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.ColorPicker;
import javafx.scene.text.Font;
import com.romraider.ui.UiScale;
import com.romraider.ui.DisplayMode;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

final class FxSettingsWindow {
    private static final String[] LEVELS = {
        "1 Beginner", "2 Intermediate", "3 Advanced",
        "4 Highest", "5 Debug Mode"
    };

    private FxSettingsWindow() { }

    static Stage show(Window owner, Runnable applied) {
        Settings settings = SettingsManager.getSettings();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Editor Settings");
        List<Runnable> changes = new ArrayList<>();

        ComboBox<String> userLevel = new ComboBox<>(
                FXCollections.observableArrayList(LEVELS));
        userLevel.getSelectionModel().select(settings.getUserLevel() - 1);
        CheckBox showHigher = new CheckBox(
                "List tables that are above my user level");
        showHigher.setSelected(settings.isDisplayHighTables());
        CheckBox valueWarning = new CheckBox(
                "Warn when calibration values exceed definition limits");
        valueWarning.setSelected(settings.isValueLimitWarning());
        GridPane editor = form();
        editor.addRow(0, new Label("User level"), userLevel);
        editor.add(showHigher, 1, 1);
        editor.add(valueWarning, 1, 2);
        editor.add(check("Warn about obsolete definitions", settings.isObsoleteWarning(), settings::setObsoleteWarning, changes), 1, 3);
        editor.add(check("Warn about calculation conflicts", settings.isCalcConflictWarning(), settings::setCalcConflictWarning, changes), 1, 4);
        editor.add(check("Save debug tables", settings.isSaveDebugTables(), settings::setSaveDebugTables, changes), 1, 5);
        editor.add(check("Enable diagnostic logging", settings.isDebug(), settings::setDebug, changes), 1, 6);
        editor.add(check("Sort the calibration catalog", settings.isTableTreeSorted(), settings::setTableTreeSorted, changes), 1, 7);
        editor.add(check("Open ROM categories expanded", settings.isOpenExpanded(), settings::setOpenExpanded, changes), 1, 8);
        ComboBox<Integer> clicks = new ComboBox<>(FXCollections.observableArrayList(1, 2));
        clicks.setValue(settings.getTableClickCount());
        editor.addRow(9, new Label("Clicks to open a table"), clicks);
        changes.add(() -> settings.setTableClickCount(clicks.getValue()));
        editor.add(check("Click an open table to close it", settings.getTableClickBehavior() == 0,
                value -> settings.setTableClickBehavior(value ? 0 : 1), changes), 1, 10);
        editor.add(check("Use US numeric formatting (restart required)", settings.isUsNumberFormat(),
                value -> settings.setLocale(value ? "en_US" : "system"), changes), 1, 11);

        ComboBox<ThemeMode> theme = new ComboBox<>(
                FXCollections.observableArrayList(ThemeMode.LIGHT,
                        ThemeMode.DARK));
        theme.setValue(settings.getThemeMode() == ThemeMode.LIGHT
                ? ThemeMode.LIGHT : ThemeMode.DARK);
        ComboBox<String> scale = new ComboBox<>(FXCollections.observableArrayList(
                "Metric", "Standard", "Default"));
        scale.setEditable(true);
        scale.setValue(settings.getDefaultScale());
        GridPane appearance = form();
        appearance.addRow(0, new Label("Theme"), theme);
        appearance.addRow(1, new Label("Default scale"), scale);
        ComboBox<UiScale> uiScale = new ComboBox<>(FXCollections.observableArrayList(UiScale.values()));
        uiScale.setValue(settings.getUiScale());
        ComboBox<DisplayMode> display = new ComboBox<>(FXCollections.observableArrayList(DisplayMode.values()));
        display.setValue(settings.getDisplayMode());
        appearance.addRow(2, new Label("Interface scale"), uiScale);
        appearance.addRow(3, new Label("Display profile"), display);
        changes.add(() -> { settings.setUiScale(uiScale.getValue()); settings.setDisplayMode(display.getValue()); });

        GridPane table = form();
        ComboBox<String> font = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
        font.setValue(settings.getTableFont().getName());
        Spinner<Integer> fontSize = new Spinner<>(8, 48, Math.max(8, Math.min(48, settings.getTableFont().getSize())));
        CheckBox bold = new CheckBox("Bold"); bold.setSelected(settings.getTableFont().isBold());
        CheckBox italic = new CheckBox("Italic"); italic.setSelected(settings.getTableFont().isItalic());
        Spinner<Integer> width = new Spinner<>(60, 500, settings.getJavaFxCellSize().width);
        Spinner<Integer> height = new Spinner<>(26, 120, settings.getJavaFxCellSize().height);
        table.addRow(0, new Label("Table font"), font);
        table.addRow(1, new Label("Font size"), fontSize);
        table.addRow(2, new Label("Style"), new HBox(8, bold, italic));
        table.addRow(3, new Label("JavaFX column width"), width);
        table.addRow(4, new Label("JavaFX row height"), height);
        changes.add(() -> {
            settings.setTableFont(new java.awt.Font(font.getValue(),
                    (bold.isSelected() ? java.awt.Font.BOLD : 0) | (italic.isSelected() ? java.awt.Font.ITALIC : 0), fontSize.getValue()));
            settings.setJavaFxCellSize(new java.awt.Dimension(width.getValue(), height.getValue()));
        });
        table.add(check("Color axis headers", settings.isColorAxis(), settings::setColorAxis, changes), 1, 5);
        table.add(check("Scale headers and values together", settings.isScaleHeadersAndData(), settings::setScaleHeadersAndData, changes), 1, 6);

        GridPane colors = form();
        color(colors, 0, "Low values", settings.getMinColor(), settings::setMinColor, changes);
        color(colors, 1, "High values", settings.getMaxColor(), settings::setMaxColor, changes);
        color(colors, 2, "Selection", settings.getSelectColor(), settings::setSelectColor, changes);
        color(colors, 3, "Axis", settings.getAxisColor(), settings::setAxisColor, changes);
        color(colors, 4, "Increased values", settings.getIncreaseBorder(), settings::setIncreaseBorder, changes);
        color(colors, 5, "Decreased values", settings.getDecreaseBorder(), settings::setDecreaseBorder, changes);
        color(colors, 6, "Value warnings", settings.getWarningColor(), settings::setWarningColor, changes);

        GridPane compatibility = form();
        Label retained = new Label("Retained Swing editor preferences. These are preserved for compatibility; JavaFX uses tabs, interface scale, and its offline preview instead of floating frames or live overlays.");
        retained.setWrapText(true);
        compatibility.add(retained, 0, 0, 2, 1);
        compatibility.add(check("Open floating tables at the origin", settings.isAlwaysOpenTableAtZero(), settings::setAlwaysOpenTableAtZero, changes), 1, 1);
        compatibility.add(check("Show table toolbar border", settings.isShowTableToolbarBorder(), settings::setShowTableToolbarBorder, changes), 1, 2);
        Spinner<Integer> editorIcons = new Spinner<>(8, 128, Math.max(8, Math.min(128, settings.getEditorIconScale())));
        Spinner<Integer> tableIcons = new Spinner<>(8, 128, Math.max(8, Math.min(128, settings.getTableIconScale())));
        compatibility.addRow(3, new Label("Editor icon size"), editorIcons);
        compatibility.addRow(4, new Label("Table icon size"), tableIcons);
        changes.add(() -> { settings.setEditorIconScale(editorIcons.getValue()); settings.setTableIconScale(tableIcons.getValue()); });
        color(compatibility, 5, "Legacy highlight", settings.getHighlightColor(), settings::setHighlightColor, changes);
        color(compatibility, 6, "Legacy live values", settings.getliveValueColor(), settings::setLiveValueColor, changes);
        color(compatibility, 7, "Legacy current live value", settings.getCurLiveValueColor(), settings::setCurLiveValueColor, changes);

        GridPane clipboard = form();
        Label clipboardHelp = new Label("Selected cells use spreadsheet TSV. Copy full table includes axes and the headers below.");
        clipboardHelp.setWrapText(true);
        clipboard.add(clipboardHelp, 0, 0, 2, 1);
        ComboBox<String> format = new ComboBox<>(FXCollections.observableArrayList(
                Settings.DEFAULT_CLIPBOARD_FORMAT, Settings.AIRBOYS_CLIPBOARD_FORMAT, Settings.CUSTOM_CLIPBOARD_FORMAT));
        format.setValue(settings.getTableClipboardFormat());
        TextArea header = header(settings.getTableHeader()), one = header(settings.getTable1DHeader()),
                two = header(settings.getTable2DHeader()), three = header(settings.getTable3DHeader());
        clipboard.addRow(1, new Label("Full-table format"), format);
        clipboard.addRow(2, new Label("Scalar header"), header);
        clipboard.addRow(3, new Label("1D header"), one);
        clipboard.addRow(4, new Label("2D header"), two);
        clipboard.addRow(5, new Label("3D header"), three);
        format.setOnAction(event -> {
            if (Settings.CUSTOM_CLIPBOARD_FORMAT.equals(format.getValue())) return;
            Settings defaults = new Settings();
            if (Settings.AIRBOYS_CLIPBOARD_FORMAT.equals(format.getValue())) defaults.setAirboysFormat();
            else defaults.setDefaultFormat();
            header.setText(defaults.getTableHeader()); one.setText(defaults.getTable1DHeader());
            two.setText(defaults.getTable2DHeader()); three.setText(defaults.getTable3DHeader());
        });
        changes.add(() -> {
            settings.setTableClipboardFormat(format.getValue()); settings.setTableHeader(header.getText());
            settings.setTable1DHeader(one.getText()); settings.setTable2DHeader(two.getText()); settings.setTable3DHeader(three.getText());
        });

        TabPane tabs = new TabPane(
                tab("Editor", editor), tab("Appearance", appearance), tab("Tables", table),
                tab("Colors", colors), tab("Clipboard", clipboard), tab("Compatibility", compatibility));
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> stage.close());
        Button save = new Button("Apply settings");
        save.setDefaultButton(true);
        save.setOnAction(event -> {
            settings.setUserLevel(userLevel.getSelectionModel()
                    .getSelectedIndex() + 1);
            settings.setDisplayHighTables(showHigher.isSelected());
            settings.setValueLimitWarning(valueWarning.isSelected());
            settings.setThemeMode(theme.getValue());
            settings.setDefaultScale(scale.getValue());
            changes.forEach(Runnable::run);
            SettingsManager.save(settings);
            ApplicationThemeService.getInstance().apply(theme.getValue());
            applied.run();
            stage.close();
        });
        HBox actions = new HBox(8, fill, cancel, save);
        actions.setPadding(new Insets(10));
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane(
                tabs, null, null, actions, null);
        Scene scene = new Scene(root, 720, 580);
        FxTheme.apply(stage, scene);
        FxTheme.closeOnEscape(stage, scene);
        stage.setScene(scene);
        FxWindowPlacement.show(stage);
        return stage;
    }

    private static CheckBox check(String label, boolean initial, java.util.function.Consumer<Boolean> apply,
            List<Runnable> changes) {
        CheckBox box = new CheckBox(label); box.setSelected(initial);
        changes.add(() -> apply.accept(box.isSelected())); return box;
    }

    private static void color(GridPane grid, int row, String label, java.awt.Color initial,
            java.util.function.Consumer<java.awt.Color> apply, List<Runnable> changes) {
        ColorPicker picker = new ColorPicker(javafx.scene.paint.Color.rgb(initial.getRed(), initial.getGreen(), initial.getBlue()));
        grid.addRow(row, new Label(label), picker);
        changes.add(() -> apply.accept(new java.awt.Color((float) picker.getValue().getRed(),
                (float) picker.getValue().getGreen(), (float) picker.getValue().getBlue())));
    }

    private static TextArea header(String value) {
        TextArea field = new TextArea(value); field.setPrefRowCount(2); return field;
    }

    private static GridPane form() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.setPadding(new Insets(22));
        return grid;
    }

    private static Tab tab(String name, javafx.scene.Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        Tab tab = new Tab(name, scroll);
        tab.setClosable(false);
        return tab;
    }
}
