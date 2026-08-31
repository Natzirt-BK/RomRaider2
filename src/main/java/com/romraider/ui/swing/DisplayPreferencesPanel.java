/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui.swing;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.romraider.Settings;
import com.romraider.ui.DisplayMode;
import com.romraider.ui.ThemeMode;
import com.romraider.ui.UiDisplayService;
import com.romraider.ui.UiScale;
import com.romraider.util.SettingsManager;

public final class DisplayPreferencesPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JComboBox scaleSelector = new JComboBox(UiScale.values());
    private final JComboBox modeSelector = new JComboBox(DisplayMode.values());
    private final JComboBox themeSelector = new JComboBox(ThemeMode.values());
    private boolean refreshing;

    public DisplayPreferencesPanel() {
        super(new GridLayout(1, 3, 10, 0));
        setName("DISPLAY PREFERENCES");
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 5));
        scaleSelector.setName("DISPLAY SCALE");
        modeSelector.setName("DISPLAY MODE");
        themeSelector.setName("DISPLAY THEME");
        add(labeledControl("Scale", scaleSelector));
        add(labeledControl("Display", modeSelector));
        add(labeledControl("Theme", themeSelector));

        scaleSelector.setToolTipText("Scale fonts, controls, rows, tabs, spacing, and scrollbars");
        modeSelector.setToolTipText(
                "Touch is a dedicated mode with larger targets, rows, tabs, and spacing");
        themeSelector.setToolTipText("Apply centralized semantic colors at runtime");

        ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (!refreshing) {
                    applySelection();
                }
            }
        };
        scaleSelector.addActionListener(listener);
        modeSelector.addActionListener(listener);
        themeSelector.addActionListener(listener);
        restoreSelection();
    }

    private static JPanel labeledControl(String label, JComboBox selector) {
        JPanel field = new JPanel(new BorderLayout(0, 3));
        field.setOpaque(false);
        field.add(new JLabel(label), BorderLayout.NORTH);
        field.add(selector, BorderLayout.CENTER);
        return field;
    }

    public void refreshFromSettings() {
        restoreSelection();
    }

    private void restoreSelection() {
        Settings settings = SettingsManager.getSettings();
        refreshing = true;
        try {
            scaleSelector.setSelectedItem(settings.getUiScale());
            modeSelector.setSelectedItem(settings.getDisplayMode());
            themeSelector.setSelectedItem(settings.getThemeMode());
        } finally {
            refreshing = false;
        }
    }

    private void applySelection() {
        Settings settings = SettingsManager.getSettings();
        settings.setUiScale((UiScale) scaleSelector.getSelectedItem());
        settings.setDisplayMode((DisplayMode) modeSelector.getSelectedItem());
        settings.setThemeMode((ThemeMode) themeSelector.getSelectedItem());
        UiDisplayService.getInstance().apply(settings, true);
    }
}
