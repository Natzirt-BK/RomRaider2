/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.BinnedLogAnalysis;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/** Bounded native plot; missing/low-count cells are never interpolated. */
final class FxBinnedLogPlot extends Region {
    private final Canvas canvas = new Canvas();
    private BinnedLogAnalysis.Result result;
    private int minimumCount;
    private String label = "";
    FxBinnedLogPlot() { getChildren().add(canvas); setMinSize(0, 0); setPrefSize(700, 400); }
    void show(BinnedLogAnalysis.Result result, int minimumCount, String label) {
        this.result = result; this.minimumCount = minimumCount; this.label = label; draw();
    }
    @Override protected void layoutChildren() { canvas.setWidth(getWidth()); canvas.setHeight(getHeight()); draw(); }
    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D(); double w = canvas.getWidth(), h = canvas.getHeight();
        g.setFill(Color.web("#f6fafb")); g.fillRect(0, 0, w, h); g.setFill(Color.web("#25445b"));
        if (result == null) { g.fillText("Calculate bins to plot observed means.", 16, 26); return; }
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (var cell : result.cells()) if (cell.count() >= minimumCount) { low = Math.min(low, cell.mean()); high = Math.max(high, cell.mean()); }
        g.fillText(label.length() > 95 ? label.substring(0, 92) + "…" : label, 12, 20);
        if (!Double.isFinite(low)) { g.fillText("No bins meet the minimum count; no values are invented.", 16, 45); return; }
        if (result.isSurface()) draw3d(g, w, h, low, high); else draw2d(g, w, h, low, high);
    }
    private void draw2d(GraphicsContext g, double w, double h, double low, double high) {
        double left = 95, right = Math.max(left + 1, w - 30), top = 45, bottom = Math.max(top + 1, h - 60);
        g.setStroke(Color.web("#8296a8")); g.strokeLine(left, top, left, bottom); g.strokeLine(left, bottom, right, bottom);
        g.fillText(FxBinnedLogPane.number(high), 8, top + 5); g.fillText(FxBinnedLogPane.number(low), 8, bottom);
        g.fillText("X: bin centers · gaps remain disconnected", left, bottom + 40);
        boolean previous = false; double px = 0, py = 0;
        for (var cell : result.cells()) {
            if (cell.count() < minimumCount) { previous = false; continue; }
            double x = left + (right - left) * fraction(cell.column(), result.columns());
            double y = bottom - (bottom - top) * normalized(cell.mean(), low, high);
            g.setStroke(Color.web("#098578")); g.setFill(Color.web("#098578"));
            if (previous) g.strokeLine(px, py, x, y);
            g.fillOval(x - 3, y - 3, 6, 6); previous = true; px = x; py = y;
        }
        g.setFill(Color.web("#25445b"));
        g.fillText(FxBinnedLogPane.number(center(result.firstX())), left, bottom + 18);
        g.fillText(FxBinnedLogPane.number(center(result.firstX() + result.columns() - 1)), Math.max(left, right - 75), bottom + 18);
    }
    private double center(long index) { return result.x().edge(index) / 2 + result.x().edge(index + 1) / 2; }
    private void draw3d(GraphicsContext g, double w, double h, double low, double high) {
        double plotHeight = Math.max(1, h - 100);
        double ox = w * .13, oy = 55 + plotHeight * .94, xx = w * .57, xy = plotHeight * .05, yx = w * .20, yy = -plotHeight * .42, zh = plotHeight * .48;
        g.setStroke(Color.web("#8296a8")); g.strokeLine(ox, oy, ox + xx, oy + xy); g.strokeLine(ox, oy, ox + yx, oy + yy); g.strokeLine(ox, oy, ox, oy - zh);
        g.fillText("X", ox + xx + 8, oy + xy); g.fillText("Y", ox + yx + 8, oy + yy);
        g.fillText("Mean " + FxBinnedLogPane.number(high), 8, oy - zh - 8); g.fillText(FxBinnedLogPane.number(low), 8, oy + 5);
        for (int row = result.rows() - 1; row >= 0; row--) for (int column = 0; column < result.columns(); column++) {
            var cell = result.cellAt(row, column); if (cell.count() < minimumCount) continue;
            double x = fraction(column, result.columns()), y = fraction(row, result.rows()), z = normalized(cell.mean(), low, high);
            double px = ox + x * xx + y * yx, base = oy + x * xy + y * yy, py = base - z * zh;
            Color color = Color.web("#126f8c").interpolate(Color.web("#e4a246"), z);
            g.setStroke(color.deriveColor(0, 1, 1, .6)); g.strokeLine(px, base, px, py); g.setFill(color); g.fillOval(px - 3, py - 3, 6, 6);
        }
        g.setFill(Color.web("#25445b"));
        g.fillText("X centers: " + FxBinnedLogPane.number(center(result.firstX())) + " … " + FxBinnedLogPane.number(center(result.firstX() + result.columns() - 1))
                + " · Y centers: " + FxBinnedLogPane.number(result.y().edge(result.firstY()) / 2 + result.y().edge(result.firstY() + 1) / 2)
                + " … " + FxBinnedLogPane.number(result.y().edge(result.firstY() + result.rows() - 1) / 2 + result.y().edge(result.firstY() + result.rows()) / 2), 12, 39);
        g.fillText("3D bin means · base = minimum mean · empty/low-count bins omitted", 12, h - 25);
        g.fillText("No surface interpolation or tuning correction", 12, h - 8);
    }
    private static double fraction(int index, int size) { return size < 2 ? .5 : index / (double) (size - 1); }
    static double normalized(double value, double low, double high) {
        if (low == high) return .5;
        double difference = high - low;
        return Math.max(0, Math.min(1, Double.isFinite(difference) ? (value - low) / difference : (value / 2 - low / 2) / (high / 2 - low / 2)));
    }
}
