/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in native-window test: run with a desktop or under xvfb-run. */
@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxWindowPlacementSmokeTest {
    @RepeatedTest(20) void modalWindowFitsAfterNativeDecorationIsKnown() throws Exception {
        FxTestRuntime.run(() -> {
            Stage stage = new Stage();
            StringBuilder transitions = new StringBuilder();
            stage.xProperty().addListener((observable, before, after) -> transitions.append(" x=").append(after));
            stage.yProperty().addListener((observable, before, after) -> transitions.append(" y=").append(after));
            stage.widthProperty().addListener((observable, before, after) -> transitions.append(" w=").append(after));
            stage.heightProperty().addListener((observable, before, after) -> transitions.append(" h=").append(after));
            Rectangle2D work = Screen.getPrimary().getVisualBounds();
            Rectangle2D[] visibleBounds = new Rectangle2D[1];
            stage.setScene(new Scene(new StackPane(), work.getWidth() + 200, work.getHeight() + 200));
            stage.setOnShown(event -> new javafx.animation.AnimationTimer() {
                private int pulses;
                @Override public void handle(long now) {
                    // Inspect after native size acknowledgements, not a closed
                    // window that disappeared in the same turn as WINDOW_SHOWN.
                    if (++pulses < 10) return;
                    visibleBounds[0] = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
                    transitions.append(" closing;");
                    stop();
                    stage.close();
                }
            }.start());
            try {
                FxWindowPlacement.showAndWait(stage);
                assertTrue(visibleBounds[0] != null, "Window closed before native geometry was observed");
                Rectangle2D observed = visibleBounds[0];
                String geometry = "Window " + observed + " outside " + work + transitions;
                assertTrue(observed.getMinX() >= work.getMinX() - 1, geometry);
                assertTrue(observed.getMinY() >= work.getMinY() - 1, geometry);
                assertTrue(observed.getMaxX() <= work.getMaxX() + 1, geometry);
                assertTrue(observed.getMaxY() <= work.getMaxY() + 1, geometry);
            } finally { stage.close(); }
        });
    }

    @Test
    void nativeWindowFitsAndRepeatedShowPreservesUserPlacement() throws Exception {
        FxTestRuntime.run(() -> {
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
            } finally {
                stage.close();
            }
        });
    }
}
