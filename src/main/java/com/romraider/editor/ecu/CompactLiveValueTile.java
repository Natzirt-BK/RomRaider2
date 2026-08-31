/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.romraider.logger.api.LiveDataSample;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Compact truthful live-value card backed only by samples already received. */
final class CompactLiveValueTile extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MAX_SAMPLES = 60;
    private final JLabel name = new JLabel("Parameter");
    private final JLabel value = new JLabel("—");
    private final JLabel units = new JLabel("");
    private final Trace trace = new Trace();
    private final LinkedList<Double> samples = new LinkedList<Double>();

    CompactLiveValueTile() {
        super(new BorderLayout(4, 3));
        setName("COMPACT LIVE VALUE");
        setMinimumSize(new Dimension(90, 72));
        setPreferredSize(new Dimension(120, 82));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiThemeService.getInstance()
                        .color(ThemeToken.RAISED_SURFACE)),
                BorderFactory.createEmptyBorder(7, 8, 6, 8)));
        name.setName("COMPACT LIVE VALUE NAME");
        name.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        value.setName("COMPACT LIVE VALUE READING");
        value.setFont(value.getFont().deriveFont(java.awt.Font.BOLD,
                value.getFont().getSize2D() + 4.0f));
        units.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        JPanel reading = new JPanel(new BorderLayout(4, 0));
        reading.add(value, BorderLayout.CENTER);
        reading.add(units, BorderLayout.EAST);
        add(name, BorderLayout.NORTH);
        add(reading, BorderLayout.CENTER);
        add(trace, BorderLayout.SOUTH);
    }

    void addSample(LiveDataSample sample) {
        if (sample == null) return;
        name.setText(sample.getName());
        value.setText(sample.getDisplayValue());
        units.setText(sample.getUnits());
        samples.addLast(sample.getRawValue());
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
        trace.setSamples(samples);
        String suffix = sample.getUnits().isEmpty() ? "" : " " + sample.getUnits();
        setToolTipText(sample.getName() + ": " + sample.getDisplayValue() + suffix
                + " • trace uses received samples only");
    }

    private static final class Trace extends JPanel {
        private static final long serialVersionUID = 1L;
        private List<Double> samples = new LinkedList<Double>();

        Trace() {
            setOpaque(false);
            setPreferredSize(new Dimension(80, 22));
            setMinimumSize(new Dimension(40, 16));
        }

        void setSamples(List<Double> values) {
            samples = new LinkedList<Double>(values);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (samples.isEmpty()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(UiThemeService.getInstance().color(
                        ThemeToken.RAISED_SURFACE));
                int middle = Math.max(1, getHeight() / 2);
                g.drawLine(0, middle, getWidth(), middle);
                g.setColor(UiThemeService.getInstance().color(
                        ThemeToken.LIVE_TRACE));
                g.setStroke(new BasicStroke(1.6f));
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (Double sample : samples) {
                    if (sample == null || !Double.isFinite(sample)) continue;
                    min = Math.min(min, sample);
                    max = Math.max(max, sample);
                }
                if (!Double.isFinite(min) || !Double.isFinite(max)) return;
                double range = Math.max(0.000001d, max - min);
                int previousX = 0;
                int previousY = y(samples.get(0), min, range);
                for (int index = 1; index < samples.size(); index++) {
                    int x = Math.round(index * (getWidth() - 1f)
                            / Math.max(1, samples.size() - 1));
                    int y = y(samples.get(index), min, range);
                    g.drawLine(previousX, previousY, x, y);
                    previousX = x;
                    previousY = y;
                }
            } finally {
                g.dispose();
            }
        }

        private int y(Double value, double min, double range) {
            if (value == null || !Double.isFinite(value)) return getHeight() / 2;
            double normalized = (value - min) / range;
            return Math.max(1, Math.min(getHeight() - 2,
                    (int) Math.round((getHeight() - 3) * (1.0d - normalized))));
        }
    }
}
