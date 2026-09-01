/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.tab;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Consistent identity row for the Logger's specialized workspaces. */
public final class LoggerTabHeader extends JPanel {
    private static final long serialVersionUID = 1L;

    public LoggerTabHeader(String titleText, String descriptionText) {
        super(new BorderLayout(0, 2));
        setName("LOGGER " + titleText + " HEADER");
        setBorder(BorderFactory.createEmptyBorder(2, 2, 8, 2));
        JLabel title = new JLabel(titleText);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JLabel description = new JLabel(descriptionText);
        description.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        add(title, BorderLayout.NORTH);
        add(description, BorderLayout.SOUTH);
    }
}
