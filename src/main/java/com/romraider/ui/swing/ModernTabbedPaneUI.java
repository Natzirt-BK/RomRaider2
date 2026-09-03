/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui.swing;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Flat cross-platform tabs with a clear RR2 selection marker. */
public final class ModernTabbedPaneUI extends BasicTabbedPaneUI {
    public static ComponentUI createUI(JComponent component) {
        return new ModernTabbedPaneUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabInsets = new Insets(10, 15, 9, 15);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabAreaInsets = new Insets(3, 5, 0, 5);
        contentBorderInsets = new Insets(1, 0, 0, 0);
        tabPane.setOpaque(false);
    }

    @Override
    protected int calculateTabHeight(int placement, int index, int fontHeight) {
        return Math.max(39, super.calculateTabHeight(
                placement, index, fontHeight));
    }

    @Override
    protected void paintTabBackground(Graphics graphics, int placement,
            int index, int x, int y, int width, int height,
            boolean selected) {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill = UiThemeService.getInstance().color(selected
                ? ThemeToken.SURFACE : ThemeToken.BACKGROUND);
        if (!selected && index == getRolloverTab()) {
            fill = UiThemeService.getInstance().color(
                    ThemeToken.RAISED_SURFACE);
        }
        copy.setColor(fill);
        copy.fill(new RoundRectangle2D.Float(x + 2, y + 2,
                width - 4, height - 2, 9, 9));
        if (selected) {
            copy.setColor(UiThemeService.getInstance().color(
                    ThemeToken.ACCENT));
            int markerY = placement == SwingConstants.BOTTOM
                    ? y + 1 : y + height - 3;
            copy.fillRoundRect(x + 8, markerY, Math.max(8, width - 16),
                    3, 3, 3);
        }
        copy.dispose();
    }

    @Override
    protected void paintTabBorder(Graphics graphics, int placement,
            int index, int x, int y, int width, int height,
            boolean selected) {
        // The selection marker replaces the traditional etched tab border.
    }

    @Override
    protected void paintContentBorder(Graphics graphics, int placement,
            int selectedIndex) {
        graphics.setColor(UiThemeService.getInstance().color(
                ThemeToken.RAISED_SURFACE));
        if (placement == SwingConstants.BOTTOM) {
            graphics.drawLine(0, tabPane.getHeight() - calculateTabAreaHeight(
                    placement, runCount, maxTabHeight) - 1,
                    tabPane.getWidth(), tabPane.getHeight()
                            - calculateTabAreaHeight(placement, runCount,
                                    maxTabHeight) - 1);
        } else {
            int y = calculateTabAreaHeight(placement, runCount, maxTabHeight);
            graphics.drawLine(0, y, tabPane.getWidth(), y);
        }
    }

    @Override
    protected void paintFocusIndicator(Graphics graphics, int placement,
            Rectangle[] rectangles, int index, Rectangle iconRect,
            Rectangle textRect, boolean selected) {
        if (!selected || !tabPane.hasFocus()) return;
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setColor(UiThemeService.getInstance().color(ThemeToken.ACCENT));
        copy.drawRoundRect(textRect.x - 4, textRect.y - 2,
                textRect.width + 8, textRect.height + 4, 6, 6);
        copy.dispose();
    }

    @Override
    protected void paintText(Graphics graphics, int placement, Font font,
            FontMetrics metrics, int index, String title,
            Rectangle textRect, boolean selected) {
        Font tabFont = selected ? font.deriveFont(Font.BOLD) : font;
        graphics.setFont(tabFont);
        graphics.setColor(UiThemeService.getInstance().color(selected
                ? ThemeToken.ACCENT : ThemeToken.PRIMARY_TEXT));
        BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, title,
                tabPane.getDisplayedMnemonicIndexAt(index), textRect.x,
                textRect.y + metrics.getAscent());
    }
}
