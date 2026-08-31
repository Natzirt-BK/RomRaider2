/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JPanel;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Lightweight bounded trace renderer shared by integrated logging surfaces. */
final class LiveTracePlot extends JPanel {
    private static final long serialVersionUID = 1L;
    private String message = "No live or recorded samples";
    private int maximumSeries = 5;
    private final Map<String, LinkedList<LiveDataSample>> series =
            new LinkedHashMap<String, LinkedList<LiveDataSample>>();
    private final Set<String> visibleSeries = new LinkedHashSet<String>();

    LiveTracePlot() {
        setName("LIVE TRACE PLOT");
        setOpaque(true);
        setBackground(UiThemeService.getInstance().color(ThemeToken.BACKGROUND));
    }

    void setMaximumSeries(int maximumSeries) {
        this.maximumSeries = Math.max(1, maximumSeries);
        repaint();
    }

    void setMessage(String message) {
        this.message = message == null ? "" : message;
        repaint();
    }

    void setSeries(Map<String, List<LiveDataSample>> series) {
        this.series.clear();
        if (series != null) {
            for (Map.Entry<String, List<LiveDataSample>> entry : series.entrySet()) {
                this.series.put(entry.getKey(),
                        new LinkedList<LiveDataSample>(entry.getValue()));
            }
        }
        repaint();
    }

    void appendSample(LiveDataSample sample) {
        LinkedList<LiveDataSample> samples = series.get(sample.getParameterId());
        if (samples == null) {
            samples = new LinkedList<LiveDataSample>();
            series.put(sample.getParameterId(), samples);
        }
        samples.addLast(sample);
        while (samples.size() > 240) samples.removeFirst();
        repaint();
    }

    void removeSeries(String parameterId) {
        series.remove(parameterId);
        visibleSeries.remove(parameterId);
        repaint();
    }

    void setVisibleSeries(Set<String> parameterIds) {
        visibleSeries.clear();
        if (parameterIds != null) visibleSeries.addAll(parameterIds);
        repaint();
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            paintGrid(g);
            if (series.isEmpty()) paintMessage(g); else paintSeries(g);
        } finally {
            g.dispose();
        }
    }

    private void paintGrid(Graphics2D graphics) {
        graphics.setColor(UiThemeService.getInstance().color(
                ThemeToken.RAISED_SURFACE));
        for (int x = 40; x < getWidth(); x += 80) {
            graphics.drawLine(x, 8, x, getHeight() - 8);
        }
        for (int y = 24; y < getHeight(); y += 36) {
            graphics.drawLine(8, y, getWidth() - 8, y);
        }
    }

    private void paintMessage(Graphics2D graphics) {
        graphics.setColor(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(message,
                Math.max(8, (getWidth() - metrics.stringWidth(message)) / 2),
                Math.max(metrics.getAscent() + 8, getHeight() / 2));
    }

    private void paintSeries(Graphics2D graphics) {
        int left = 14;
        int right = Math.max(left + 40, getWidth() - 155);
        int top = 18;
        int bottom = Math.max(top + 30, getHeight() - 18);
        int seriesIndex = 0;
        for (Map.Entry<String, LinkedList<LiveDataSample>> entry
                : series.entrySet()) {
            if (!visibleSeries.isEmpty()
                    && !visibleSeries.contains(entry.getKey())) continue;
            List<LiveDataSample> samples = entry.getValue();
            if (samples == null || samples.isEmpty()) continue;
            if (seriesIndex >= maximumSeries) break;
            Color color = traceColor(seriesIndex);
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (LiveDataSample sample : samples) {
                minimum = Math.min(minimum, sample.getRawValue());
                maximum = Math.max(maximum, sample.getRawValue());
            }
            if (Double.compare(minimum, maximum) == 0) {
                minimum -= 0.5;
                maximum += 0.5;
            }
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(1.7f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int previousX = -1;
            int previousY = -1;
            for (int index = 0; index < samples.size(); index++) {
                LiveDataSample sample = samples.get(index);
                int x = samples.size() == 1 ? right
                        : left + (int) Math.round(index * (right - left)
                                / (double) (samples.size() - 1));
                double normalized = (sample.getRawValue() - minimum)
                        / (maximum - minimum);
                int y = bottom - (int) Math.round(normalized * (bottom - top));
                if (previousX >= 0) graphics.drawLine(previousX, previousY, x, y);
                else graphics.fillOval(x - 2, y - 2, 4, 4);
                previousX = x;
                previousY = y;
            }
            LiveDataSample latest = samples.get(samples.size() - 1);
            graphics.drawString(clip(latest.getName(), 18) + "  "
                    + latest.getDisplayValue() + (latest.getUnits().isEmpty()
                            ? "" : " " + latest.getUnits()),
                    right + 8, 17 + (seriesIndex * 16));
            seriesIndex++;
        }
    }

    private static Color traceColor(int index) {
        ThemeToken[] colors = {ThemeToken.ACCENT, ThemeToken.SUCCESS,
                ThemeToken.WARNING, ThemeToken.LIVE_TRACE, ThemeToken.DANGER};
        return UiThemeService.getInstance().color(colors[index % colors.length]);
    }

    private static String clip(String value, int maximum) {
        if (value == null) return "Parameter";
        return value.length() <= maximum ? value
                : value.substring(0, Math.max(1, maximum - 1)) + "…";
    }
}
