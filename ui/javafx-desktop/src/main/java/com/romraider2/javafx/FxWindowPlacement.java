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
        stage.show();
        if (!firstShow) return;

        // Native decorations are known after show(). Apply synchronously before
        // callers open owned dialogs; do not resize an already-visible window.
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
