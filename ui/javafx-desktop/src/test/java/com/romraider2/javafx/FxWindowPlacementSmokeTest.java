/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in native-window test: run with a desktop or under xvfb-run. */
@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxWindowPlacementSmokeTest {
    @Test
    void nativeWindowFitsAndRepeatedShowPreservesUserPlacement() throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Stage stage = new Stage();
            try {
                Rectangle2D work = Screen.getPrimary().getVisualBounds();
                stage.setScene(new Scene(new StackPane(),
                        work.getWidth() + 200, work.getHeight() + 200));
                stage.setMinWidth(work.getWidth() + 100);
                stage.setMinHeight(work.getHeight() + 100);
                FxWindowPlacement.show(stage);
                assertTrue(stage.getX() >= work.getMinX() - 1);
                assertTrue(stage.getY() >= work.getMinY() - 1);
                assertTrue(stage.getX() + stage.getWidth() <= work.getMaxX() + 1);
                assertTrue(stage.getY() + stage.getHeight() <= work.getMaxY() + 1);
                assertEquals(work.getWidth(), stage.getMinWidth());
                assertEquals(work.getHeight(), stage.getMinHeight());
                stage.setMinWidth(100);
                stage.setMinHeight(100);
                stage.setWidth(500);
                stage.setHeight(300);
                stage.setX(work.getMinX() + 20);
                stage.setY(work.getMinY() + 30);
                FxWindowPlacement.show(stage);
                assertEquals(500, stage.getWidth());
                assertEquals(300, stage.getHeight());
                assertEquals(work.getMinX() + 20, stage.getX());
                assertEquals(work.getMinY() + 30, stage.getY());
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                stage.close();
            }
        });
        try {
            result.get(20, TimeUnit.SECONDS);
        } finally {
            Platform.exit();
        }
    }
}
