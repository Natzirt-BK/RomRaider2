/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui.swing;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JTextField;

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
        setBorder(BorderFactory.createCompoundBorder(getBorder(),
                BorderFactory.createEmptyBorder(0, 29, 0, 5)));
        putClientProperty("JTextField.showClearButton", true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
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
}
