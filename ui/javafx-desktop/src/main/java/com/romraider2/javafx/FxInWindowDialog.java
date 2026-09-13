/* RomRaider2 - GPL 2.0 or later. */
package com.romraider2.javafx;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Modal content without a second native window, preserving the owner's fullscreen state. */
final class FxInWindowDialog implements AutoCloseable {
    private final Stage owner;
    private final Scene scene;
    private final Parent previous;
    private final Node previousFocus;
    private final boolean previousDisabled;
    private final StackPane wrapper;
    private final Runnable cleanup;
    private final KeyCombination previousExitKey;
    private final ChangeListener<Boolean> visibility;
    private boolean closed;

    FxInWindowDialog(Stage owner, Region content, Runnable cleanup) {
        this.owner = owner;
        this.cleanup = cleanup;
        scene = owner.getScene();
        previous = scene.getRoot();
        previousFocus = scene.getFocusOwner();
        previousDisabled = previous.isDisable();
        previousExitKey = owner.getFullScreenExitKeyCombination();
        owner.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        wrapper = new StackPane();
        StackPane shade = new StackPane(content);
        shade.setId("logger-setup-overlay");
        shade.setPadding(new Insets(16));
        shade.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        content.setMaxSize(780, 560);
        content.setMinSize(0, 0);
        content.setStyle("-fx-background-color: -rr-raised; -fx-background-radius: 10;");
        // Block shortcuts/default buttons belonging to the background logger,
        // after the dialog's own controls have handled their keyboard input.
        shade.addEventHandler(KeyEvent.ANY, KeyEvent::consume);
        shade.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) { close(); event.consume(); }
        });
        scene.setRoot(wrapper);
        wrapper.getProperties().put("rr-touch-override", previous.getProperties().getOrDefault("rr-touch-override", false));
        FxTheme.refresh(scene);
        wrapper.getChildren().addAll(previous, shade);
        previous.setDisable(true);
        visibility = (o, before, showing) -> { if (!showing) close(); };
        owner.showingProperty().addListener(visibility);
        content.setFocusTraversable(true);
        content.requestFocus();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        owner.showingProperty().removeListener(visibility);
        wrapper.getChildren().clear();
        if (scene.getRoot() == wrapper) scene.setRoot(previous);
        previous.setDisable(previousDisabled);
        owner.setFullScreenExitKeyCombination(previousExitKey);
        cleanup.run();
        if (previousFocus != null && owner.isShowing()) previousFocus.requestFocus();
    }
}
