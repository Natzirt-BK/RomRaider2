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
            Rectangle2D[] firstBounds = new Rectangle2D[1];
            stage.setScene(new Scene(new StackPane(), work.getWidth() + 200, work.getHeight() + 200));
            stage.setOnShown(event -> {
                firstBounds[0] = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
                new javafx.animation.AnimationTimer() {
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
                }.start();
            });
            try {
                FxWindowPlacement.showAndWait(stage);
                assertTrue(visibleBounds[0] != null, "Window closed before native geometry was observed");
                Rectangle2D observed = visibleBounds[0];
                String geometry = "Window " + observed + " outside " + work + transitions;
                assertTrue(observed.getMinX() >= work.getMinX() - 1, geometry);
                assertTrue(observed.getMinY() >= work.getMinY() - 1, geometry);
                assertTrue(observed.getMaxX() <= work.getMaxX() + 1, geometry);
                assertTrue(observed.getMaxY() <= work.getMaxY() + 1, geometry);
                assertFits(firstBounds[0], work, "The first native request must already fit");
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

    @Test void ordinaryWindowIsBoundedBeforeShownHandlersRun() throws Exception {
        FxTestRuntime.run(() -> {
            Stage stage = new Stage();
            Rectangle2D work = Screen.getPrimary().getVisualBounds();
            Rectangle2D[] first = new Rectangle2D[1];
            stage.setScene(new Scene(new StackPane(), work.getWidth() + 200, work.getHeight() + 200));
            stage.setMinWidth(work.getWidth() + 100);
            stage.setMinHeight(work.getHeight() + 100);
            stage.setOnShown(event -> first[0] = bounds(stage));
            try {
                FxWindowPlacement.show(stage);
                assertFits(first[0], work, "Oversized minimums must not reach the first native request");
            } finally { stage.close(); }
        });
    }

    @Test void inferredSceneSizeAndExplicitStageSizeAreBounded() throws Exception {
        FxTestRuntime.run(() -> {
            Rectangle2D work = Screen.getPrimary().getVisualBounds();
            for (boolean explicit : new boolean[] {false, true}) {
                Stage stage = new Stage();
                StackPane root = new StackPane();
                root.setPrefSize(work.getWidth() + 200, work.getHeight() + 200);
                stage.setScene(new Scene(root));
                if (explicit) { stage.setWidth(500); stage.setHeight(300); }
                Rectangle2D[] first = new Rectangle2D[1];
                stage.setOnShown(event -> first[0] = bounds(stage));
                try {
                    FxWindowPlacement.show(stage);
                    assertFits(first[0], work, "Inferred content is bounded before showing");
                    if (explicit) {
                        assertEquals(500, first[0].getWidth());
                        assertEquals(300, first[0].getHeight());
                    }
                } finally { stage.close(); }
            }
        });
    }

    @RepeatedTest(20) void modalUserPlacementRemainsAfterInitialFit() throws Exception {
        FxTestRuntime.run(() -> {
            Stage stage = new Stage();
            Rectangle2D work = Screen.getPrimary().getVisualBounds();
            Rectangle2D[] observed = new Rectangle2D[1];
            Rectangle2D[] accepted = new Rectangle2D[1];
            StringBuilder transitions = new StringBuilder();
            stage.widthProperty().addListener((o, before, after) -> transitions.append(" w=").append(after).append(placementCaller()));
            stage.heightProperty().addListener((o, before, after) -> transitions.append(" h=").append(after).append(placementCaller()));
            stage.setScene(new Scene(new StackPane(), work.getWidth() + 200, work.getHeight() + 200));
            stage.setOnShown(event -> new javafx.animation.AnimationTimer() {
                private int pulses;
                @Override public void handle(long now) {
                    if (++pulses == 10) {
                        // A window manager may restore remembered maximization.
                        // Restore first, as a user would before manual resizing.
                        transitions.append(" restore; maximized=").append(stage.isMaximized());
                        stage.setMaximized(false);
                    }
                    if (pulses == 20) {
                        transitions.append(" shrink; maximized=").append(stage.isMaximized());
                        stage.setWidth(500); stage.setHeight(300);
                    }
                    if (pulses == 30) {
                        // Separate shrink and move requests so a pending
                        // screen-sized frame does not confound this check.
                        stage.setX(work.getMinX() + 20); stage.setY(work.getMinY() + 30);
                    }
                    if (pulses < 40) return;
                    if (pulses == 40) {
                        // Some window managers ignore requested coordinates.
                        // Retain the placement they actually acknowledged.
                        accepted[0] = bounds(stage);
                        FxWindowPlacement.show(stage);
                    }
                    if (pulses < 50) return;
                    observed[0] = bounds(stage);
                    stop(); stage.close();
                }
            }.start());
            try {
                FxWindowPlacement.showAndWait(stage);
                assertTrue(accepted[0] != null, "Native user placement was observed");
                assertEquals(500, accepted[0].getWidth(), transitions.toString());
                assertEquals(300, accepted[0].getHeight(), transitions.toString());
                assertEquals(accepted[0], observed[0],
                        "Initial fitting must not later recenter or resize a user's window");
                assertEquals(Double.MAX_VALUE, stage.getMaxWidth(), "No lasting maximum-width restriction");
                assertEquals(Double.MAX_VALUE, stage.getMaxHeight(), "No lasting maximum-height restriction");
            } finally { stage.close(); }
        });
    }

    @Test void shownHandlerPlacementIsNotOverwrittenByADeferredFit() throws Exception {
        FxTestRuntime.run(() -> {
            Stage stage = new Stage();
            Rectangle2D work = Screen.getPrimary().getVisualBounds();
            boolean[] userPlaced = {false};
            StringBuilder lateRequests = new StringBuilder();
            javafx.beans.value.ChangeListener<Number> changes = (o, before, after) -> {
                if (userPlaced[0] && !placementCaller().isEmpty()) lateRequests.append(" ").append(before).append(" -> ").append(after);
            };
            stage.xProperty().addListener(changes); stage.yProperty().addListener(changes);
            stage.widthProperty().addListener(changes); stage.heightProperty().addListener(changes);
            stage.setScene(new Scene(new StackPane(), work.getWidth() + 200, work.getHeight() + 200));
            stage.setOnShown(event -> {
                stage.setWidth(500); stage.setHeight(300);
                stage.setX(work.getMinX() + 20); stage.setY(work.getMinY() + 30);
                userPlaced[0] = true;
                new javafx.animation.AnimationTimer() {
                    private int pulses;
                    @Override public void handle(long now) {
                        if (++pulses < 10) return;
                        stop(); stage.close();
                    }
                }.start();
            });
            try {
                FxWindowPlacement.showAndWait(stage);
                assertEquals("", lateRequests.toString(), "Startup helper changed geometry after the shown handler supplied placement");
            } finally { stage.close(); }
        });
    }

    // Native geometry acknowledgements can legitimately change properties.
    // Distinguish them from additional application-owned placement requests.
    private static String placementCaller() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().equals(FxWindowPlacement.class.getName())
                    && frame.getMethodName().equals("fitVisible")) return " [startup fit]";
        }
        return "";
    }

    private static Rectangle2D bounds(Stage stage) {
        return new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    private static void assertFits(Rectangle2D bounds, Rectangle2D work, String message) {
        assertTrue(bounds != null, message + ": WINDOW_SHOWN was not observed");
        assertTrue(bounds.getMinX() >= work.getMinX() - 1 && bounds.getMinY() >= work.getMinY() - 1
                && bounds.getMaxX() <= work.getMaxX() + 1 && bounds.getMaxY() <= work.getMaxY() + 1,
                message + ": " + bounds + " outside " + work);
    }
}
