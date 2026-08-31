/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

import java.awt.Window;

import javax.swing.SwingUtilities;

import com.romraider.Settings;
import com.romraider.util.SettingsManager;

public final class UiDisplayService {
    private static final UiDisplayService INSTANCE = new UiDisplayService();

    private UiDisplayService() {
    }

    public static UiDisplayService getInstance() {
        return INSTANCE;
    }

    public void applyFromSettings() {
        apply(SettingsManager.getSettings(), false);
    }

    public void apply(Settings settings, boolean refreshWindows) {
        if (settings == null) {
            throw new IllegalArgumentException("Settings are required");
        }
        if (refreshWindows) prepareOpenWindowsForModeChange(
                settings.getDisplayMode());
        UiScaleService.getInstance().apply(settings.getUiScale(), settings.getDisplayMode());
        UiThemeService.getInstance().apply(settings.getThemeMode());
        if (refreshWindows) {
            refreshOpenWindows();
        }
    }

    private void prepareOpenWindowsForModeChange(final DisplayMode nextMode) {
        Runnable prepare = new Runnable() {
            public void run() {
                for (Window window : Window.getWindows()) {
                    TouchTargetService.prepareForModeChange(window, nextMode);
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            prepare.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(prepare);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to prepare windows for a display-mode change",
                    exception);
        }
    }

    public void refreshOpenWindows() {
        Runnable refresh = new Runnable() {
            public void run() {
                for (Window window : Window.getWindows()) {
                    SwingUtilities.updateComponentTreeUI(window);
                    TouchTargetService.apply(window,
                            SettingsManager.getSettings().getDisplayMode());
                    window.invalidate();
                    window.validate();
                    window.repaint();
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            refresh.run();
        } else {
            SwingUtilities.invokeLater(refresh);
        }
    }
}
