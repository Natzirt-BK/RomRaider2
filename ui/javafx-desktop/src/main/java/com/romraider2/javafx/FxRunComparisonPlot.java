/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.LogRunComparison;
import java.util.function.ToDoubleFunction;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/** Shared-scale overlays with explicit gaps; no time alignment or resampling. */
final class FxRunComparisonPlot extends Region {
    private final Canvas canvas = new Canvas();
    private LogRunComparison.Result result;
    private boolean difference;
    FxRunComparisonPlot() { getChildren().add(canvas); setMinSize(0, 0); setPrefSize(700, 350); }
    void show(LogRunComparison.Result value, boolean delta) { result = value; difference = delta; draw(); }
    @Override protected void layoutChildren() { canvas.setWidth(getWidth()); canvas.setHeight(getHeight()); draw(); }
    private void draw() {
        var g = canvas.getGraphicsContext2D(); double w = canvas.getWidth(), h = canvas.getHeight();
        g.setFill(Color.web("#f6fafb")); g.fillRect(0, 0, w, h); g.setFill(Color.web("#25445b"));
        if (result == null || result.rows().isEmpty()) { g.fillText("Compare runs to plot qualified bin means.", 12, 22); return; }
        double low = difference ? 0 : Double.POSITIVE_INFINITY, high = difference ? 0 : Double.NEGATIVE_INFINITY; boolean available = false;
        for (var row : result.rows()) for (double value : difference ? new double[] {row.difference()} : new double[] {row.meanA(), row.meanB()})
            if (Double.isFinite(value)) { available = true; low = Math.min(low, value); high = Math.max(high, value); }
        g.fillText(difference ? "B − A · only shared qualified bins · zero is included" : "A: teal dots · B: amber rings · shared scale · gaps remain disconnected", 12, 22);
        if (!available) { g.fillText("No qualified values for this plot.", 12, 44); return; }
        double left = 100, right = Math.max(left + 1, w - 25), top = 55, bottom = Math.max(top + 1, h - 55);
        g.setStroke(Color.web("#8296a8")); g.strokeLine(left, top, left, bottom); g.strokeLine(left, bottom, right, bottom);
        g.fillText(FxBinnedLogPane.number(high), 8, top + 3); g.fillText(FxBinnedLogPane.number(low), 8, bottom);
        if (difference) {
            double zero = bottom - (bottom - top) * FxBinnedLogPlot.normalized(0, low, high);
            g.setLineDashes(4); g.strokeLine(left, zero, right, zero); g.setLineDashes();
            series(LogRunComparison.Row::difference, Color.web("#7563aa"), low, high, left, right, top, bottom, false);
        } else {
            series(LogRunComparison.Row::meanA, Color.web("#098578"), low, high, left, right, top, bottom, false);
            series(LogRunComparison.Row::meanB, Color.web("#c68722"), low, high, left, right, top, bottom, true);
        }
        var first = result.rows().get(0); var last = result.rows().get(result.rows().size() - 1);
        g.setFill(Color.web("#25445b"));
        g.fillText(FxBinnedLogPane.number(first.lower() / 2 + first.upper() / 2), left, bottom + 18);
        g.fillText(FxBinnedLogPane.number(last.lower() / 2 + last.upper() / 2), Math.max(left, right - 80), bottom + 18);
        g.fillText("X bin centers (" + result.xUnits() + ") · values (" + result.valueUnits() + ") · not a causal or tuning recommendation", left, bottom + 40);
    }
    private void series(ToDoubleFunction<LogRunComparison.Row> value, Color color, double low, double high, double left, double right, double top, double bottom, boolean hollow) {
        var g = canvas.getGraphicsContext2D(); g.setStroke(color); g.setFill(color); g.setLineWidth(1.7);
        boolean previous = false; double px = 0, py = 0; int count = result.rows().size();
        for (int index = 0; index < count; index++) {
            double reading = value.applyAsDouble(result.rows().get(index)); if (!Double.isFinite(reading)) { previous = false; continue; }
            double x = left + (right - left) * (count == 1 ? .5 : index / (double) (count - 1));
            double y = bottom - (bottom - top) * FxBinnedLogPlot.normalized(reading, low, high);
            if (previous) g.strokeLine(px, py, x, y);
            if (hollow) g.strokeOval(x - 5, y - 5, 10, 10); else g.fillOval(x - 3, y - 3, 6, 6);
            previous = true; px = x; py = y;
        }
        g.setLineWidth(1);
    }
}
