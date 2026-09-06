/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.api.LoggerGaugeTheme;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Searchable native face gallery; only an explicit choice mutates preferences. */
final class FxGaugeStylePicker extends Dialog<Void> {
    FxGaugeStylePicker(Window owner, String title, LoggerGaugeTheme selected,
            boolean allowDefault, Function<LoggerGaugeTheme, Node> preview,
            Consumer<LoggerGaugeTheme> onSelect) {
        initOwner(owner);
        setTitle(title);
        setResizable(true);
        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        getDialogPane().setStyle("-fx-base: #17212b; -fx-background: #0f151b; -fx-background-color: #0f151b; "
                + "-fx-control-inner-background: #10171d; -fx-text-base-color: #edf1f4; -fx-text-background-color: #edf1f4;");
        getDialogPane().lookupButton(ButtonType.CANCEL).setStyle("-fx-text-fill: #edf1f4;");
        TextField search = new TextField();
        search.setPromptText("Search 25 gauge styles");
        search.setAccessibleText("Search gauge styles");
        TilePane faces = new TilePane(10, 10);
        faces.setPrefColumns(3);
        faces.setPadding(new Insets(8));
        Label empty = new Label("No matching gauge styles");
        empty.setStyle("-fx-text-fill: #edf1f4;");
        ScrollPane scroll = new ScrollPane(faces);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(400, Math.max(160, owner.getHeight() - 210)));
        Runnable filter = () -> {
            faces.getChildren().clear();
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            for (LoggerGaugeTheme theme : LoggerGaugeTheme.selectableValues()) {
                if (!theme.getDisplayName().toLowerCase(Locale.ROOT).contains(query)) continue;
                Node drawing = preview.apply(theme);
                drawing.setMouseTransparent(true);
                Label name = new Label((theme == selected ? "✓ " : "") + theme.getDisplayName());
                name.setStyle("-fx-text-fill: #edf1f4;");
                VBox content = new VBox(4, name, drawing);
                Button choice = new Button();
                choice.setGraphic(content);
                choice.setAccessibleText(theme.getDisplayName() + (theme == selected ? ", selected" : "") + ", sample reading");
                choice.setOnAction(event -> { close(); onSelect.accept(theme); });
                faces.getChildren().add(choice);
            }
            empty.setVisible(faces.getChildren().isEmpty());
            empty.setManaged(empty.isVisible());
        };
        search.textProperty().addListener((observable, oldText, newText) -> filter.run());
        Label explanation = new Label("SAMPLE · Choose a style while parked");
        explanation.setStyle("-fx-text-fill: #edf1f4;");
        VBox body = new VBox(8, explanation, search, empty, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        if (allowDefault) {
            Button reset = new Button("Use default");
            reset.setStyle("-fx-text-fill: #edf1f4;");
            reset.setOnAction(event -> { close(); onSelect.accept(null); });
            body.getChildren().add(reset);
        }
        body.setPrefWidth(Math.min(750, Math.max(280, owner.getWidth() - 60)));
        getDialogPane().setContent(body);
        filter.run();
        setOnShown(event -> search.requestFocus());
    }
}
