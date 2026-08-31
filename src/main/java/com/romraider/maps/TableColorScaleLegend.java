/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.maps;

import static com.romraider.util.ColorScaler.getScaledColor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.JComponent;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Compact heatmap key for the active table's current value range. */
final class TableColorScaleLegend extends JComponent {
    private static final long serialVersionUID = 1L;
    private final Table table;

    TableColorScaleLegend(Table table) {
        this.table = table;
        setOpaque(true);
        setBackground(UiThemeService.getInstance().color(ThemeToken.SURFACE));
        setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        setBorder(BorderFactory.createEmptyBorder(5, 12, 7, 12));
        setPreferredSize(new Dimension(240, 40));
        setName("Table color scale");
        setToolTipText("Minimum, current unit, and maximum table values");
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        if (width < 2) return;

        int barX = insets.left;
        int barY = insets.top;
        int barHeight = 9;
        for (int x = 0; x < width; x++) {
            graphics.setColor(getScaledColor((double) x / (width - 1)));
            graphics.drawLine(barX + x, barY, barX + x,
                    barY + barHeight - 1);
        }
        graphics.setColor(UiThemeService.getInstance().color(
                ThemeToken.RAISED_SURFACE));
        graphics.drawRect(barX, barY, width - 1, barHeight - 1);

        graphics.setColor(getForeground());
        FontMetrics metrics = graphics.getFontMetrics();
        int textY = getHeight() - insets.bottom;
        double minimum = table.getMinReal();
        double range = table.getMaxReal() - minimum;
        final int labelCount = 5;
        for (int i = 0; i < labelCount; i++) {
            double fraction = (double) i / (labelCount - 1);
            String label = format(minimum + range * fraction);
            int center = barX + (int) Math.round(width * fraction);
            int labelX = center - metrics.stringWidth(label) / 2;
            if (i == 0) labelX = barX;
            if (i == labelCount - 1) {
                labelX = barX + width - metrics.stringWidth(label);
            }
            graphics.drawString(label, labelX, textY);
        }
    }

    private String format(double value) {
        try {
            String pattern = table.getCurrentScale() == null
                    ? "0.###" : table.getCurrentScale().getFormat();
            return new DecimalFormat(pattern).format(value);
        } catch (RuntimeException ignored) {
            return new DecimalFormat("0.###").format(value);
        }
    }
}
