/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.api.LoggerGaugeTheme;
import com.romraider.portable.gauge.GaugeFaceRenderer;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** Native legacy dial themes, shared by their gallery thumbnails and live cards. */
final class FxLegacyGaugeView extends Region {
    private final Canvas canvas = new Canvas();
    private final LoggerGaugeTheme theme;
    private GaugeFaceRenderer.Reading reading;
    private final Color override;
    FxLegacyGaugeView(LoggerGaugeTheme theme, GaugeFaceRenderer.Reading reading, Color override) {
        this.theme = theme; this.reading = reading; this.override = override;
        getChildren().add(canvas); setMinSize(120, 94); setPrefSize(220, 172);
        setAccessibleText(reading.name + ", " + (reading.available() ? reading.display + " " + reading.units
                : "no valid data") + ", " + reading.state);
    }
    void setReading(GaugeFaceRenderer.Reading next) {
        reading = next;
        setAccessibleText(next.name + ", " + (next.available() ? next.display + " " + next.units : "no valid data") + ", " + next.state);
        requestLayout();
    }
    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth()); canvas.setHeight(getHeight());
        var g = canvas.getGraphicsContext2D(); g.clearRect(0, 0, getWidth(), getHeight());
        double factor = Math.min(getWidth() / 320, getHeight() / 250);
        if (factor <= 0) return;
        g.save(); g.translate((getWidth() - 320 * factor) / 2, (getHeight() - 250 * factor) / 2); g.scale(factor, factor);
        boolean cream = theme == LoggerGaugeTheme.CENTRAL_TACH;
        boolean amber = theme == LoggerGaugeTheme.AMBER_GT;
        boolean neon = theme == LoggerGaugeTheme.NEON_CIRCUIT;
        boolean rally = theme == LoggerGaugeTheme.RALLY_HERITAGE;
        boolean handheld = theme == LoggerGaugeTheme.HANDHELD;
        Color primary = override != null ? override : Color.web(amber ? "#ffa31a" : neon ? "#22e8ff"
                : handheld ? "#66c0f4" : rally ? "#ff3447" : "#d71920");
        Color ink = Color.web(cream ? "#171717" : amber ? "#ffd68a" : neon ? "#9af5ff" : "#f2f5f8");
        g.setFill(Color.web(cream ? "#f2eee3" : amber ? "#090a0c" : neon ? "#06131d" : "#151c24"));
        g.fillOval(64, 24, 192, 192);
        g.setStroke(Color.web(neon ? "#43216b" : rally ? "#68717a" : "#34404c"));
        g.setLineWidth(rally ? 5 : 3); g.strokeOval(64, 24, 192, 192);
        int ticks = amber ? 16 : neon ? 37 : rally || cream ? 31 : 26;
        for (int i = 0; i < ticks; i++) {
            double a = Math.toRadians(145 + 250.0 * i / (ticks - 1));
            double inner = i % 5 == 0 ? 69 : 76;
            g.setStroke(ink.deriveColor(0, 1, 1, i % 5 == 0 ? .9 : .5)); g.setLineWidth(i % 5 == 0 ? 2 : 1);
            g.strokeLine(160 + Math.cos(a) * inner, 120 + Math.sin(a) * inner,
                    160 + Math.cos(a) * 83, 120 + Math.sin(a) * 83);
        }
        if (amber) {
            for (int i = 0; i <= 30; i++) {
                double a = Math.toRadians(145 + 250.0 * i / 30);
                g.setFill(reading.available() && reading.progress() >= i / 30.0 ? primary : Color.web("#463014"));
                g.fillOval(160 + Math.cos(a) * 90 - 3, 120 + Math.sin(a) * 90 - 3, 6, 6);
            }
        } else if (reading.available()) {
            g.setStroke(primary); g.setLineWidth(neon ? 6 : 4);
            g.strokeArc(70, 30, 180, 180, -145, -250 * reading.progress(), ArcType.OPEN);
            double a = Math.toRadians(145 + 250 * reading.progress());
            g.setLineWidth(2.5); g.strokeLine(160, 120, 160 + Math.cos(a) * 68, 120 + Math.sin(a) * 68);
        }
        g.setFill(primary); g.fillOval(156, 116, 8, 8);
        g.setTextAlign(TextAlignment.CENTER); g.setFill(ink);
        g.setFont(Font.font("Monospaced", FontWeight.BOLD, 26));
        g.fillText(reading.available() ? reading.display : "—", 160, 164, 145);
        g.setFont(Font.font("SansSerif", 11)); g.fillText(reading.units, 160, 181, 145);
        g.setFill(Color.web("#9cabb8")); g.setFont(Font.font("SansSerif", 10));
        g.fillText(reading.name, 160, 17, 300);
        g.fillText(reading.scaleLabel + " · " + GaugeFaceRenderer.compact(reading.minimum) + "–"
                + GaugeFaceRenderer.compact(reading.maximum), 160, 226, 300);
        g.setFill(reading.available() && reading.warning ? Color.web("#ff564e") : Color.web("#9cabb8"));
        g.fillText(reading.available() && reading.warning ? "LIMIT WARNING" : reading.state, 160, 243, 300);
        g.restore();
    }
}
