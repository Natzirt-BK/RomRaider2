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

    static void show(Window owner, Runnable applied) {
        Settings settings = SettingsManager.getSettings();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Editor Settings");

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

        TabPane tabs = new TabPane(
                tab("Editor", editor), tab("Appearance", appearance));
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
            SettingsManager.save(settings);
            ApplicationThemeService.getInstance().apply(theme.getValue());
            applied.run();
            stage.close();
        });
        HBox actions = new HBox(8, fill, cancel, save);
        actions.setPadding(new Insets(10));
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane(
                tabs, null, null, actions, null);
        Scene scene = new Scene(root, 620, 320);
        FxTheme.apply(stage, scene);
        FxTheme.closeOnEscape(stage, scene);
        stage.setScene(scene);
        stage.show();
    }

    private static GridPane form() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.setPadding(new Insets(22));
        return grid;
    }

    private static Tab tab(String name, javafx.scene.Node content) {
        Tab tab = new Tab(name, content);
        tab.setClosable(false);
        return tab;
    }
}
