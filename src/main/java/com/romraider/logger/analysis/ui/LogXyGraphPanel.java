/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

import com.romraider.logger.analysis.LogChannel;
import com.romraider.logger.analysis.LogCursorModel;
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.LogMarker;
import com.romraider.logger.analysis.LogRange;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Configurable X/Y capture plot linked to the shared offline cursor. */
public final class LogXyGraphPanel extends JPanel
        implements LogCursorModel.Listener {
    private static final long serialVersionUID = 1L;
    private static final int LEFT = 64;
    private static final int RIGHT = 22;
    private static final int TOP = 22;
    private static final int BOTTOM = 48;
    private static final DecimalFormat VALUE = new DecimalFormat("0.####");

    private final LogCursorModel cursor;
    private final List<LogMarker> markers = new ArrayList<LogMarker>();
    private LogDataset dataset;
    private LogRange range;
    private LogChannel xChannel;
    private LogChannel yChannel;
    private int sampleIndex = -1;

    public LogXyGraphPanel(LogCursorModel cursor) {
        if (cursor == null) throw new IllegalArgumentException("cursor");
        this.cursor = cursor;
        cursor.addListener(this);
        setName("OFFLINE LOG XY GRAPH");
        setOpaque(true);
        setPreferredSize(new Dimension(760, 290));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                seekNearest(event.getX(), event.getY());
            }
        });
    }

    public void setAxes(LogChannel xChannel, LogChannel yChannel) {
        this.xChannel = xChannel;
        this.yChannel = yChannel;
        repaint();
    }

    public LogChannel getXChannel() { return xChannel; }
    public LogChannel getYChannel() { return yChannel; }

    public void setMarkers(List<LogMarker> selected) {
        markers.clear();
        if (selected != null) markers.addAll(selected);
        Collections.sort(markers);
        repaint();
    }

    public void cursorChanged(LogDataset dataset, LogRange range,
            int sampleIndex) {
        this.dataset = dataset;
        this.range = range;
        this.sampleIndex = sampleIndex;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(UiThemeService.getInstance().color(ThemeToken.BACKGROUND));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int plotRight = Math.max(LEFT + 40, getWidth() - RIGHT);
            int plotBottom = Math.max(TOP + 40, getHeight() - BOTTOM);
            paintGrid(g, plotRight, plotBottom);
            if (dataset == null) {
                message(g, "Load a log to configure an X/Y graph");
            } else if (xChannel == null || yChannel == null) {
                message(g, "Choose X and Y channels");
            } else {
                double[] xBounds = bounds(xChannel);
                double[] yBounds = bounds(yChannel);
                if (!Double.isFinite(xBounds[0])
                        || !Double.isFinite(yBounds[0])) {
                    message(g, "Selected channels contain no numeric samples");
                } else {
                    paintSamples(g, plotRight, plotBottom, xBounds, yBounds);
                    paintLabels(g, plotRight, plotBottom, xBounds, yBounds);
                }
            }
        } finally {
            g.dispose();
        }
    }

    private void paintGrid(Graphics2D g, int right, int bottom) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.RAISED_SURFACE));
        for (int division = 0; division <= 5; division++) {
            int x = LEFT + division * (right - LEFT) / 5;
            int y = TOP + division * (bottom - TOP) / 5;
            g.drawLine(x, TOP, x, bottom);
            g.drawLine(LEFT, y, right, y);
        }
    }

    private void paintSamples(Graphics2D g, int right, int bottom,
            double[] xBounds, double[] yBounds) {
        int step = Math.max(1, range.size()
                / Math.max(1, (right - LEFT) * 2));
        g.setColor(UiThemeService.getInstance().color(ThemeToken.ACCENT));
        for (int row = range.getStartInclusive();
                row < range.getEndExclusive(); row += step) {
            int[] point = point(row, right, bottom, xBounds, yBounds);
            if (point != null) g.fillOval(point[0] - 2, point[1] - 2, 4, 4);
        }
        g.setColor(UiThemeService.getInstance().color(ThemeToken.WARNING));
        for (LogMarker marker : markers) {
            if (marker.getSampleIndex() < range.getStartInclusive()
                    || marker.getSampleIndex() >= range.getEndExclusive()) continue;
            int[] point = point(marker.getSampleIndex(), right, bottom,
                    xBounds, yBounds);
            if (point != null) g.drawOval(point[0] - 5, point[1] - 5, 10, 10);
        }
        int[] active = point(sampleIndex, right, bottom, xBounds, yBounds);
        if (active != null) {
            g.setColor(UiThemeService.getInstance().color(ThemeToken.PRIMARY_TEXT));
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(active[0] - 7, active[1] - 7, 14, 14);
            g.drawLine(active[0] - 10, active[1], active[0] + 10, active[1]);
            g.drawLine(active[0], active[1] - 10, active[0], active[1] + 10);
        }
    }

    private void paintLabels(Graphics2D g, int right, int bottom,
            double[] xBounds, double[] yBounds) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        String xLabel = xChannel.getName() + units(xChannel);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(xLabel, LEFT + Math.max(0,
                (right - LEFT - metrics.stringWidth(xLabel)) / 2),
                getHeight() - 9);
        g.drawString(VALUE.format(xBounds[0]), LEFT, bottom + 17);
        String maxX = VALUE.format(xBounds[1]);
        g.drawString(maxX, right - metrics.stringWidth(maxX), bottom + 17);
        g.drawString(yChannel.getName() + units(yChannel), LEFT, TOP - 7);
        g.drawString(VALUE.format(yBounds[1]), 4, TOP + metrics.getAscent());
        g.drawString(VALUE.format(yBounds[0]), 4, bottom);
    }

    private void seekNearest(int mouseX, int mouseY) {
        if (dataset == null || range == null || xChannel == null
                || yChannel == null) return;
        int right = Math.max(LEFT + 40, getWidth() - RIGHT);
        int bottom = Math.max(TOP + 40, getHeight() - BOTTOM);
        double[] xBounds = bounds(xChannel);
        double[] yBounds = bounds(yChannel);
        long bestDistance = Long.MAX_VALUE;
        int bestSample = -1;
        for (int row = range.getStartInclusive();
                row < range.getEndExclusive(); row++) {
            int[] point = point(row, right, bottom, xBounds, yBounds);
            if (point == null) continue;
            long dx = point[0] - mouseX;
            long dy = point[1] - mouseY;
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSample = row;
            }
        }
        if (bestSample >= 0) cursor.seek(bestSample);
    }

    private int[] point(int row, int right, int bottom, double[] xBounds,
            double[] yBounds) {
        if (row < 0 || row >= dataset.getRowCount()) return null;
        double xValue = dataset.getValue(row, xChannel.getIndex());
        double yValue = dataset.getValue(row, yChannel.getIndex());
        if (!Double.isFinite(xValue) || !Double.isFinite(yValue)) return null;
        double xSpan = Math.max(1.0e-12, xBounds[1] - xBounds[0]);
        double ySpan = Math.max(1.0e-12, yBounds[1] - yBounds[0]);
        int x = LEFT + (int) Math.round((xValue - xBounds[0])
                * (right - LEFT) / xSpan);
        int y = bottom - (int) Math.round((yValue - yBounds[0])
                * (bottom - TOP) / ySpan);
        return new int[] {x, y};
    }

    private double[] bounds(LogChannel channel) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int row = range.getStartInclusive();
                row < range.getEndExclusive(); row++) {
            double value = dataset.getValue(row, channel.getIndex());
            if (!Double.isFinite(value)) continue;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        if (minimum == maximum && Double.isFinite(minimum)) maximum = minimum + 1.0;
        return new double[] {minimum, maximum};
    }

    private void message(Graphics2D g, String text) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, Math.max(8,
                (getWidth() - metrics.stringWidth(text)) / 2),
                Math.max(metrics.getAscent() + 8, getHeight() / 2));
    }

    private static String units(LogChannel channel) {
        return channel.getUnits().isEmpty() ? "" : " (" + channel.getUnits() + ")";
    }
}
