/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui.swing;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import com.romraider.platform.ui.PlatformSelectorPanel;

public final class ApplicationControlsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public ApplicationControlsPanel() {
        this(true);
    }

    public ApplicationControlsPanel(boolean includeDisplayPreferences) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        PlatformSelectorPanel platform = new PlatformSelectorPanel(
                !includeDisplayPreferences);
        platform.setAlignmentX(LEFT_ALIGNMENT);
        add(platform);
        if (includeDisplayPreferences) {
            DisplayPreferencesPanel display = new DisplayPreferencesPanel();
            display.setAlignmentX(LEFT_ALIGNMENT);
            add(display);
        }
    }
}
