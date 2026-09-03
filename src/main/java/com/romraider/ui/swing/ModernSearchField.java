/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.JTextField;
import javax.swing.border.AbstractBorder;

import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Search field whose icon and hint render consistently across look-and-feels. */
public final class ModernSearchField extends JTextField {
    private static final long serialVersionUID = 1L;
    private final String placeholder;
    private final Icon searchIcon = ModernIconFactory.icon(Action.SEARCH);

    public ModernSearchField(String placeholder) {
        this(placeholder, 0);
    }

    public ModernSearchField(String placeholder, int columns) {
        super(columns);
        this.placeholder = placeholder == null ? "Search..." : placeholder;
        setOpaque(false);
        setBorder(new RoundedSearchBorder());
        putClientProperty("JTextField.showClearButton", true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D background = (Graphics2D) graphics.create();
        try {
            background.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            background.setColor(getBackground());
            background.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        } finally {
            background.dispose();
        }
        super.paintComponent(graphics);
        Insets insets = getInsets();
        int iconX = Math.max(6, insets.left - searchIcon.getIconWidth() - 7);
        int iconY = Math.max(0, (getHeight() - searchIcon.getIconHeight()) / 2);
        searchIcon.paintIcon(this, graphics, iconX, iconY);
        if (!getText().isEmpty()) return;

        graphics.setColor(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        FontMetrics metrics = graphics.getFontMetrics(getFont());
        int baseline = (getHeight() - metrics.getHeight()) / 2
                + metrics.getAscent();
        graphics.drawString(placeholder, insets.left, baseline);
    }

    private static final class RoundedSearchBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.set(5, 34, 5, 8);
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(5, 34, 5, 8);
        }

        @Override
        public void paintBorder(Component component, Graphics graphics,
                int x, int y, int width, int height) {
            Graphics2D paint = (Graphics2D) graphics.create();
            try {
                paint.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color border = UiThemeService.getInstance().color(
                        component.hasFocus() ? ThemeToken.ACCENT
                                : ThemeToken.RAISED_SURFACE);
                paint.setColor(border);
                paint.drawRoundRect(x, y, Math.max(0, width - 1),
                        Math.max(0, height - 1), 10, 10);
            } finally {
                paint.dispose();
            }
        }
    }
}
