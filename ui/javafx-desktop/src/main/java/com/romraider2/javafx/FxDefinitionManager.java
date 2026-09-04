/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;
import java.util.List;
import java.util.Vector;

import com.romraider.Settings;
import com.romraider.editor.io.DefinitionFileSupport;
import com.romraider.util.SettingsManager;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

final class FxDefinitionManager {
    private FxDefinitionManager() { }

    static void show(Window owner, Runnable saved) {
        Settings settings = SettingsManager.getSettings();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("ECU Definitions Manager");

        ListView<File> files = new ListView<>(FXCollections.observableArrayList(
                settings.getEcuDefinitionFiles()));
        files.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName()
                        + "\n" + item.getAbsolutePath());
            }
        });

        Button add = new Button("Add definitions…");
        add.setDefaultButton(true);
        add.setOnAction(event -> {
            List<File> selected = FxDialogs.chooseDefinitions(stage,
                    settings.getLastDefinitionDir());
            if (selected == null) return;
            selected.stream().filter(DefinitionFileSupport::isSupported)
                    .filter(file -> !files.getItems().contains(file))
                    .forEach(files.getItems()::add);
            if (!selected.isEmpty()) {
                settings.setLastDefinitionDir(selected.get(0).getParentFile());
            }
        });
        Button remove = new Button("Remove");
        remove.disableProperty().bind(
                files.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> files.getItems().remove(
                files.getSelectionModel().getSelectedItem()));
        Button up = new Button("Move up");
        Button down = new Button("Move down");
        up.setOnAction(event -> move(files, -1));
        down.setOnAction(event -> move(files, 1));
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> stage.close());
        Button save = new Button("Save definitions");
        save.setDefaultButton(true);
        save.setOnAction(event -> {
            settings.setEcuDefinitionFiles(new Vector<>(files.getItems()));
            SettingsManager.save(settings);
            saved.run();
            stage.close();
        });
        HBox actions = new HBox(8, add, remove, up, down, fill, cancel, save);
        actions.setPadding(new Insets(10));

        Label heading = new Label("Definition priority");
        heading.getStyleClass().add("title");
        Label detail = new Label("Definitions are tried from top to bottom. "
                + "Already loaded files remain visible and can be reordered.");
        detail.getStyleClass().add("muted");
        javafx.scene.layout.VBox top = new javafx.scene.layout.VBox(5,
                heading, detail);
        top.setPadding(new Insets(14));
        BorderPane root = new BorderPane(files, top, null, actions, null);
        Scene scene = new Scene(root, 820, 520);
        FxTheme.apply(stage, scene);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static void move(ListView<File> list, int offset) {
        int source = list.getSelectionModel().getSelectedIndex();
        int target = source + offset;
        if (source < 0 || target < 0 || target >= list.getItems().size()) return;
        File item = list.getItems().remove(source);
        list.getItems().add(target, item);
        list.getSelectionModel().select(target);
    }
}
