/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui.swing;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Rounded, palette-aware button surface shared by the Swing workspaces. */
public final class RoundedButtonUI extends BasicButtonUI {
    private static final int ARC = 10;

    public static ComponentUI createUI(JComponent component) {
        return new RoundedButtonUI();
    }

    @Override
    protected void installDefaults(AbstractButton button) {
        super.installDefaults(button);
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
    }

    @Override
    public void paint(Graphics graphics, JComponent component) {
        AbstractButton button = (AbstractButton) component;
        if (button.isContentAreaFilled()) {
            Graphics2D paint = (Graphics2D) graphics.create();
            try {
                paint.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = button.getBackground();
                if (fill == null) {
                    fill = UiThemeService.getInstance().color(
                            ThemeToken.RAISED_SURFACE);
                }
                if (!button.isEnabled()) {
                    fill = blend(fill, UiThemeService.getInstance().color(
                            ThemeToken.BACKGROUND), 0.52f);
                } else if (button.getModel().isPressed()) {
                    fill = blend(fill, UiThemeService.getInstance().color(
                            ThemeToken.PRIMARY_TEXT), 0.14f);
                } else if (button.getModel().isSelected()) {
                    fill = blend(fill, UiThemeService.getInstance().color(
                            ThemeToken.SELECTION), 0.42f);
                } else if (button.getModel().isRollover()) {
                    fill = blend(fill, UiThemeService.getInstance().color(
                            ThemeToken.ACCENT), 0.12f);
                }

                int width = Math.max(0, component.getWidth() - 2);
                int height = Math.max(0, component.getHeight() - 2);
                paint.setColor(fill);
                paint.fillRoundRect(1, 1, width, height, ARC, ARC);
                Color border = button.isFocusOwner()
                        ? UiThemeService.getInstance().color(ThemeToken.ACCENT)
                        : blend(fill, UiThemeService.getInstance().color(
                                ThemeToken.PRIMARY_TEXT), 0.18f);
                paint.setColor(border);
                paint.drawRoundRect(1, 1, width, height, ARC, ARC);
            } finally {
                paint.dispose();
            }
        }
        super.paint(graphics, component);
    }

    @Override
    protected void paintButtonPressed(Graphics graphics,
            AbstractButton button) {
        // The pressed surface is painted above without the rectangular Basic
        // look-and-feel fill.
    }

    @Override
    protected void paintFocus(Graphics graphics, AbstractButton button,
            java.awt.Rectangle view, java.awt.Rectangle text,
            java.awt.Rectangle icon) {
        // Focus is represented by the accent outline around the full target.
    }

    static Color blend(Color base, Color overlay, float overlayAmount) {
        float amount = Math.max(0f, Math.min(1f, overlayAmount));
        float keep = 1f - amount;
        return new Color(
                Math.round(base.getRed() * keep + overlay.getRed() * amount),
                Math.round(base.getGreen() * keep + overlay.getGreen() * amount),
                Math.round(base.getBlue() * keep + overlay.getBlue() * amount));
    }
}
