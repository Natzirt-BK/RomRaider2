/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import java.awt.BasicStroke;
import java.awt.Color;
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
import com.romraider.logger.analysis.LogRange;
import com.romraider.logger.analysis.LogMarker;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Theme-aware, read-only time graph linked to a shared sample cursor. */
public final class LogTimeGraphPanel extends JPanel
        implements LogCursorModel.Listener {
    private static final long serialVersionUID = 1L;
    private static final int LEFT = 42;
    private static final int TOP = 18;
    private static final int BOTTOM = 28;
    private static final int LEGEND_WIDTH = 230;
    private static final int MAXIMUM_SERIES = 5;
    private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("0.####");

    private final LogCursorModel cursor;
    private final List<LogChannel> channels = new ArrayList<LogChannel>();
    private final List<LogMarker> markers = new ArrayList<LogMarker>();
    private LogDataset dataset;
    private LogRange range;
    private int sampleIndex = -1;

    public LogTimeGraphPanel(LogCursorModel cursor) {
        if (cursor == null) throw new IllegalArgumentException("cursor");
        this.cursor = cursor;
        cursor.addListener(this);
        setName("OFFLINE LOG TIME GRAPH");
        setOpaque(true);
        setBackground(UiThemeService.getInstance().color(ThemeToken.BACKGROUND));
        setPreferredSize(new Dimension(760, 290));
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
                seekFromMouse(event.getX());
            }
        });
    }

    public void setChannels(List<LogChannel> selected) {
        channels.clear();
        if (selected != null) {
            for (LogChannel channel : selected) {
                if (channel == null || channel.isTimeChannel()) continue;
                channels.add(channel);
                if (channels.size() == MAXIMUM_SERIES) break;
            }
        }
        repaint();
    }

    public List<LogChannel> getChannels() {
        return Collections.unmodifiableList(
                new ArrayList<LogChannel>(channels));
    }

    public void setMarkers(List<LogMarker> selected) {
        markers.clear();
        if (selected != null) markers.addAll(selected);
        Collections.sort(markers);
        repaint();
    }

    public List<LogMarker> getMarkers() {
        return Collections.unmodifiableList(new ArrayList<LogMarker>(markers));
    }

    public void cursorChanged(LogDataset dataset, LogRange range,
            int sampleIndex) {
        this.dataset = dataset;
        this.range = range;
        this.sampleIndex = sampleIndex;
        repaint();
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(UiThemeService.getInstance().color(ThemeToken.BACKGROUND));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int right = plotRight();
            int bottom = plotBottom();
            paintGrid(g, right, bottom);
            if (dataset == null) {
                paintMessage(g, "Load a log to graph captured channels");
            } else if (channels.isEmpty()) {
                paintMessage(g, "Select up to five statistic rows to graph");
            } else {
                paintSeries(g, right, bottom);
                paintMarkers(g, right, bottom);
                paintCursor(g, right, bottom);
                paintLegend(g, right);
                paintTimeBounds(g, right, bottom);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintMarkers(Graphics2D g, int right, int bottom) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.WARNING));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10.0f, new float[] {4.0f, 4.0f}, 0.0f));
        int labelRow = 0;
        for (LogMarker marker : markers) {
            int sample = marker.getSampleIndex();
            if (sample < range.getStartInclusive()
                    || sample >= range.getEndExclusive()) continue;
            int x = xForSample(sample, right);
            g.drawLine(x, TOP, x, bottom);
            String label = clip(marker.getDisplayName(), 18);
            int labelWidth = g.getFontMetrics().stringWidth(label);
            int labelX = Math.max(LEFT, Math.min(x + 3, right - labelWidth));
            g.drawString(label, labelX, TOP + 11 + (labelRow++ % 2) * 13);
        }
    }

    private void paintGrid(Graphics2D g, int right, int bottom) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.RAISED_SURFACE));
        for (int division = 0; division <= 5; division++) {
            int x = LEFT + division * Math.max(1, right - LEFT) / 5;
            g.drawLine(x, TOP, x, bottom);
        }
        for (int division = 0; division <= 4; division++) {
            int y = TOP + division * Math.max(1, bottom - TOP) / 4;
            g.drawLine(LEFT, y, right, y);
        }
    }

    private void paintSeries(Graphics2D g, int right, int bottom) {
        int pixels = Math.max(1, right - LEFT);
        int step = Math.max(1, range.size() / pixels);
        for (int series = 0; series < channels.size(); series++) {
            LogChannel channel = channels.get(series);
            double[] bounds = bounds(channel);
            if (!Double.isFinite(bounds[0])) continue;
            double span = bounds[1] - bounds[0];
            if (span == 0.0) span = 1.0;
            g.setColor(traceColor(series));
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            int previousX = -1;
            int previousY = -1;
            int end = range.getEndExclusive();
            for (int row = range.getStartInclusive(); row < end; row += step) {
                double value = dataset.getValue(row, channel.getIndex());
                if (!Double.isFinite(value)) {
                    previousX = -1;
                    continue;
                }
                int x = xForSample(row, right);
                int y = bottom - (int) Math.round((value - bounds[0])
                        * (bottom - TOP) / span);
                if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
                else g.fillOval(x - 2, y - 2, 4, 4);
                previousX = x;
                previousY = y;
            }
            int last = end - 1;
            if ((last - range.getStartInclusive()) % step != 0) {
                double value = dataset.getValue(last, channel.getIndex());
                if (Double.isFinite(value)) {
                    int x = xForSample(last, right);
                    int y = bottom - (int) Math.round((value - bounds[0])
                            * (bottom - TOP) / span);
                    if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
                }
            }
        }
    }

    private void paintCursor(Graphics2D g, int right, int bottom) {
        int x = xForSample(sampleIndex, right);
        g.setColor(UiThemeService.getInstance().color(ThemeToken.PRIMARY_TEXT));
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(x, TOP, x, bottom);
        g.fillPolygon(new int[] {x - 5, x + 5, x},
                new int[] {TOP, TOP, TOP + 7}, 3);
    }

    private void paintLegend(Graphics2D g, int right) {
        int x = right + 12;
        for (int series = 0; series < channels.size(); series++) {
            LogChannel channel = channels.get(series);
            int y = TOP + 16 + series * 46;
            g.setColor(traceColor(series));
            g.fillRoundRect(x, y - 10, 12, 4, 4, 4);
            g.setColor(UiThemeService.getInstance().color(ThemeToken.PRIMARY_TEXT));
            g.drawString(clip(channel.getName(), 25), x + 18, y);
            double value = sampleIndex < 0 ? Double.NaN
                    : dataset.getValue(sampleIndex, channel.getIndex());
            double[] bounds = bounds(channel);
            g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
            g.drawString(format(value) + unitSuffix(channel)
                    + "   [" + format(bounds[0]) + " … " + format(bounds[1]) + "]",
                    x + 18, y + 17);
        }
    }

    private void paintTimeBounds(Graphics2D g, int right, int bottom) {
        LogChannel time = dataset.getTimeChannel();
        if (time == null) return;
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        String start = format(dataset.getValue(range.getStartInclusive(),
                time.getIndex())) + unitSuffix(time);
        String end = format(dataset.getValue(range.getEndExclusive() - 1,
                time.getIndex())) + unitSuffix(time);
        g.drawString(start, LEFT, bottom + 18);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(end, right - metrics.stringWidth(end), bottom + 18);
    }

    private void paintMessage(Graphics2D g, String message) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(message,
                Math.max(8, (getWidth() - metrics.stringWidth(message)) / 2),
                Math.max(metrics.getAscent() + 8, getHeight() / 2));
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
        return new double[] {minimum, maximum};
    }

    private void seekFromMouse(int mouseX) {
        if (dataset == null || range == null) return;
        int right = plotRight();
        double fraction = (Math.max(LEFT, Math.min(mouseX, right)) - LEFT)
                / (double) Math.max(1, right - LEFT);
        int sample = range.getStartInclusive() + (int) Math.round(fraction
                * (range.size() - 1));
        cursor.seek(sample);
    }

    private int xForSample(int sample, int right) {
        if (range.size() <= 1) return LEFT;
        return LEFT + (int) Math.round((sample - range.getStartInclusive())
                * (right - LEFT) / (double) (range.size() - 1));
    }

    private int plotRight() {
        return Math.max(LEFT + 40, getWidth() - LEGEND_WIDTH);
    }

    private int plotBottom() {
        return Math.max(TOP + 30, getHeight() - BOTTOM);
    }

    private static Color traceColor(int index) {
        ThemeToken[] colors = {ThemeToken.ACCENT, ThemeToken.SUCCESS,
                ThemeToken.WARNING, ThemeToken.LIVE_TRACE, ThemeToken.DANGER};
        return UiThemeService.getInstance().color(colors[index % colors.length]);
    }

    private static String unitSuffix(LogChannel channel) {
        return channel.getUnits().isEmpty() ? "" : " " + channel.getUnits();
    }

    private static String format(double value) {
        return Double.isFinite(value) ? VALUE_FORMAT.format(value) : "—";
    }

    private static String clip(String value, int maximum) {
        return value.length() <= maximum ? value
                : value.substring(0, maximum - 1) + "…";
    }
}
