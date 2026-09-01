/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;

/** Keyboard navigation for the main Logger workspaces. */
public final class LoggerWorkspaceShortcuts {
    private static final int MAX_NUMBERED_WORKSPACES = 9;

    private LoggerWorkspaceShortcuts() {
    }

    public static void install(final JTabbedPane tabs) {
        int shortcutCount = Math.min(tabs.getTabCount(),
                MAX_NUMBERED_WORKSPACES);
        for (int index = 0; index < shortcutCount; index++) {
            final int tabIndex = index;
            String actionName = "selectLoggerWorkspace" + (index + 1);
            KeyStroke shortcut = KeyStroke.getKeyStroke(
                    KeyEvent.VK_1 + index, CTRL_DOWN_MASK);
            tabs.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(shortcut, actionName);
            tabs.getActionMap().put(actionName, new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent event) {
                    if (tabIndex < tabs.getTabCount()
                            && tabs.isEnabledAt(tabIndex)) {
                        tabs.setSelectedIndex(tabIndex);
                    }
                }
            });
        }
    }
}
