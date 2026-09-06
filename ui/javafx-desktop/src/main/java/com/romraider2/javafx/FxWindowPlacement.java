/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Fits the decorated initial window into a screen's logical work area. */
final class FxWindowPlacement {
    private FxWindowPlacement() { }

    static void show(Stage stage) {
        boolean firstShow = !stage.isShowing();
        if (firstShow) fitBeforeShow(stage);
        stage.show();
        if (!firstShow) return;
        fitVisible(stage);
    }

    static void showAndWait(Stage stage) {
        if (!stage.isShowing()) fitBeforeShow(stage);
        // The initial native request is already bounded. A queued second fit
        // can run after a shown handler/user supplies a different placement,
        // or even after this invocation has failed or its window has closed.
        stage.showAndWait();
    }

    private static void fitBeforeShow(Stage stage) {
        // Sending an oversized initial request and shrinking after show() can
        // race a late native acknowledgement of that original request. Bound
        // the first outer-window request instead; no timer or persistent size
        // restriction should fight subsequent user resizing.
        double width = stage.getWidth(), height = stage.getHeight();
        if (stage.getScene() != null) {
            javafx.scene.Scene scene = stage.getScene();
            scene.getRoot().applyCss();
            scene.getRoot().autosize();
            if (!positive(width)) width = positive(scene.getWidth())
                    ? scene.getWidth() : scene.getRoot().getLayoutBounds().getWidth();
            if (!positive(height)) height = positive(scene.getHeight())
                    ? scene.getHeight() : scene.getRoot().getLayoutBounds().getHeight();
        }
        // An unspecified empty stage has no meaningful preferred size yet.
        if (!positive(width) || !positive(height)) return;
        javafx.stage.Window anchor = stage.getOwner();
        Rectangle2D primary = Screen.getPrimary().getVisualBounds();
        if (!Double.isFinite(stage.getX())) stage.setX(anchor != null && Double.isFinite(anchor.getX())
                ? anchor.getX() : primary.getMinX());
        if (!Double.isFinite(stage.getY())) stage.setY(anchor != null && Double.isFinite(anchor.getY())
                ? anchor.getY() : primary.getMinY());
        // With no explicit Stage size, the Scene preference is an initial outer
        // budget. Decorations are included, not added outside the work area.
        stage.setWidth(width);
        stage.setHeight(height);
        fitVisible(stage);
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static void fitVisible(Stage stage) {
        // Stage dimensions describe the outer frame, including decorations.
        // Apply synchronously before callers open owned dialogs.
        Screen screen = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                stage.getWidth(), stage.getHeight()).stream()
                .max(java.util.Comparator.comparingDouble(candidate ->
                        intersectionArea(candidate.getVisualBounds(), stage)))
                .orElse(Screen.getPrimary());
        Rectangle2D workArea = screen.getVisualBounds();
        Rectangle2D placement = fit(workArea, stage.getWidth(), stage.getHeight());
        stage.setMinWidth(Math.min(stage.getMinWidth(), workArea.getWidth()));
        stage.setMinHeight(Math.min(stage.getMinHeight(), workArea.getHeight()));
        stage.setWidth(placement.getWidth());
        stage.setHeight(placement.getHeight());
        stage.setX(placement.getMinX());
        stage.setY(placement.getMinY());
    }

    private static double intersectionArea(Rectangle2D bounds, Stage stage) {
        double width = Math.max(0, Math.min(bounds.getMaxX(),
                stage.getX() + stage.getWidth()) - Math.max(bounds.getMinX(), stage.getX()));
        double height = Math.max(0, Math.min(bounds.getMaxY(),
                stage.getY() + stage.getHeight()) - Math.max(bounds.getMinY(), stage.getY()));
        return width * height;
    }

    static Rectangle2D fit(Rectangle2D workArea, double width, double height) {
        double fittedWidth = Math.min(width, workArea.getWidth());
        double fittedHeight = Math.min(height, workArea.getHeight());
        return new Rectangle2D(
                workArea.getMinX() + (workArea.getWidth() - fittedWidth) / 2,
                workArea.getMinY() + (workArea.getHeight() - fittedHeight) / 2,
                fittedWidth, fittedHeight);
    }
}
