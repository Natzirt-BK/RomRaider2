/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui.swing;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** Arrow-free scrollbar with a larger, rounded touch target. */
public final class ModernScrollBarUI extends BasicScrollBarUI {
    public static ComponentUI createUI(JComponent component) {
        return new ModernScrollBarUI();
    }

    @Override
    protected void configureScrollBarColors() {
        trackColor = UiThemeService.getInstance().color(ThemeToken.BACKGROUND);
        thumbColor = UiThemeService.getInstance().color(
                ThemeToken.RAISED_SURFACE);
        thumbHighlightColor = UiThemeService.getInstance().color(
                ThemeToken.ACCENT);
        thumbDarkShadowColor = thumbColor;
        thumbLightShadowColor = thumbColor;
        int width = Math.max(12, UIManager.getInt("ScrollBar.width"));
        scrollBarWidth = width;
        minimumThumbSize = new Dimension(width, width * 2);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zeroButton();
    }

    private JButton zeroButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        return button;
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component,
            Rectangle bounds) {
        graphics.setColor(trackColor);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component,
            Rectangle bounds) {
        if (!component.isEnabled() || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        Color color = isDragging
                ? UiThemeService.getInstance().color(ThemeToken.ACCENT)
                : thumbColor;
        copy.setColor(color);
        int inset = bounds.width >= 18 ? 3 : 2;
        int arc = Math.max(7, Math.min(bounds.width, bounds.height));
        copy.fillRoundRect(bounds.x + inset, bounds.y + inset,
                Math.max(3, bounds.width - inset * 2),
                Math.max(3, bounds.height - inset * 2), arc, arc);
        copy.dispose();
    }
}
