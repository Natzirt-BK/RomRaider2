package com.romraider2.javafx;

import com.romraider.logger.api.LiveDataSample;
import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/** Timestamp-aligned live traces. Each legend explicitly identifies its numeric scale. */
final class FxLiveGraph extends BorderPane {
    private final Canvas canvas = new Canvas();
    private final FlowPane legend = new FlowPane(16, 6);
    private List<Series> series = List.of();
    private long start, end;

    FxLiveGraph() {
        Label help = new Label("Live history · shared time axis · each channel uses its labeled range · gaps over 2 seconds are not joined");
        help.setWrapText(true); help.setPadding(new Insets(10)); setTop(help);
        StackPane plot = new StackPane(canvas); plot.setMinSize(0, 0); setCenter(plot);
        canvas.widthProperty().bind(plot.widthProperty()); canvas.heightProperty().bind(plot.heightProperty());
        canvas.widthProperty().addListener(o -> draw()); canvas.heightProperty().addListener(o -> draw());
        legend.setPadding(new Insets(10));
        ScrollPane key = new ScrollPane(legend); key.setFitToWidth(true); key.setMaxHeight(130); setBottom(key);
    }

    void setData(Map<String, List<LiveDataSample>> history, List<LiveDataSample> selected) {
        List<Series> next = new ArrayList<>();
        for (LiveDataSample channel : selected) next.add(series(channel, history.getOrDefault(channel.getParameterId(), List.of())));
        series = next;
        start = series.stream().flatMap(s -> s.samples.stream()).mapToLong(LiveDataSample::getTimestampMillis).min().orElse(0);
        end = series.stream().flatMap(s -> s.samples.stream()).mapToLong(LiveDataSample::getTimestampMillis).max().orElse(start + 1);
        if (end <= start) end = start + 1;
        legend.getChildren().clear();
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            Label label = new Label(s.name + " [" + s.units + "] · " + (s.samples.stream().noneMatch(v -> Double.isFinite(v.getRawValue())) ? "no valid data"
                    : String.format(Locale.ROOT, "%.3f–%.3f", s.min, s.max)));
            label.setWrapText(true);
            javafx.scene.shape.Line swatch = new javafx.scene.shape.Line(0, 0, 28, 0);
            swatch.setStroke(color(i)); swatch.setStrokeWidth(3);
            if (i % 3 == 1) swatch.getStrokeDashArray().setAll(7.0, 4.0);
            if (i % 3 == 2) swatch.getStrokeDashArray().setAll(2.0, 4.0);
            label.setGraphic(swatch); legend.getChildren().add(label);
        }
        draw();
    }

    static Series series(LiveDataSample channel, List<LiveDataSample> history) {
        List<LiveDataSample> samples = history.stream().filter(s -> s.getConversionIdentity().equals(channel.getConversionIdentity())
                && s.getUnits().equals(channel.getUnits())).sorted(Comparator.comparingLong(LiveDataSample::getTimestampMillis)).toList();
        double min = samples.stream().mapToDouble(LiveDataSample::getRawValue).filter(Double::isFinite).min().orElse(0);
        double max = samples.stream().mapToDouble(LiveDataSample::getRawValue).filter(Double::isFinite).max().orElse(1);
        if (max <= min) { min -= .5; max += .5; }
        return new Series(channel.getName(), channel.getUnits(), min, max, samples);
    }

    static double fraction(long timestamp, long start, long end) {
        return ((double) timestamp - start) / Math.max(1, (double) end - start);
    }

    static boolean connected(LiveDataSample a, LiveDataSample b) {
        double gap = (double) b.getTimestampMillis() - a.getTimestampMillis();
        return gap > 0 && gap <= 2000 && Double.isFinite(a.getRawValue()) && Double.isFinite(b.getRawValue());
    }

    private static Color color(int index) { return Color.hsb((index * 137.508 + 170) % 360, .85, FxTheme.isDark() ? .95 : .65); }

    private void draw() {
        double w = canvas.getWidth(), h = canvas.getHeight(), left = 48, top = 20, pw = w - 75, ph = h - 68;
        var g = canvas.getGraphicsContext2D();
        g.setFill(FxTheme.isDark() ? Color.web("#10151b") : Color.web("#f4f7f9")); g.fillRect(0, 0, w, h);
        if (pw <= 0 || ph <= 0) return;
        g.setFill(FxTheme.isDark() ? Color.LIGHTGRAY : Color.DIMGRAY);
        g.setStroke(FxTheme.isDark() ? Color.web("#34404c") : Color.web("#d2dbe1"));
        for (int i = 0; i <= 4; i++) {
            double y = top + ph * i / 4;
            g.strokeLine(left, y, left + pw, y); g.fillText((100 - i * 25) + "%", 5, y + 4);
            double x = left + pw * i / 4;
            g.fillText(String.format(Locale.ROOT, "%.1f", ((double) end - start) / 1000 * i / 4), x - 10, top + ph + 20);
        }
        g.fillText("Elapsed seconds in visible history · vertical % of each labeled range", left, h - 8);
        for (int n = 0; n < series.size(); n++) {
            Series s = series.get(n); g.setStroke(color(n)); g.setLineWidth(2);
            g.setLineDashes(n % 3 == 1 ? new double[]{7, 4} : n % 3 == 2 ? new double[]{2, 4} : new double[]{});
            for (int i = 1; i < s.samples.size(); i++) {
                LiveDataSample a = s.samples.get(i - 1), b = s.samples.get(i);
                if (!connected(a, b)) continue;
                g.strokeLine(left + fraction(a.getTimestampMillis(), start, end) * pw,
                        top + ph * (1 - (a.getRawValue() - s.min) / (s.max - s.min)),
                        left + fraction(b.getTimestampMillis(), start, end) * pw,
                        top + ph * (1 - (b.getRawValue() - s.min) / (s.max - s.min)));
            }
        }
        g.setLineDashes();
        if (series.isEmpty()) g.fillText("Select channels to display live history", left + 10, top + 30);
    }

    record Series(String name, String units, double min, double max, List<LiveDataSample> samples) { }
}
