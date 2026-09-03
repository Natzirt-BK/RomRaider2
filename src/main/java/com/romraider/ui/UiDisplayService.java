/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;

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
        DisplayMode mode = RuntimeUiProfile.displayMode(
                settings.getDisplayMode());
        if (refreshWindows) prepareOpenWindowsForModeChange(mode);
        UiThemeService themes = UiThemeService.getInstance();
        ThemePalette previousPalette = themes.getCurrentPalette();
        UiScaleService.getInstance().apply(settings.getUiScale(), mode);
        themes.apply(
                RuntimeUiProfile.theme(settings.getThemeMode()));
        if (refreshWindows) {
            refreshOpenWindows(previousPalette, themes.getCurrentPalette());
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
        ThemePalette palette = UiThemeService.getInstance().getCurrentPalette();
        refreshOpenWindows(palette, palette);
    }

    private void refreshOpenWindows(final ThemePalette previousPalette,
            final ThemePalette currentPalette) {
        Runnable refresh = new Runnable() {
            public void run() {
                for (Window window : Window.getWindows()) {
                    SwingUtilities.updateComponentTreeUI(window);
                    remapSemanticColors(window, previousPalette,
                            currentPalette);
                    TouchTargetService.apply(window,
                            RuntimeUiProfile.displayMode(SettingsManager
                                    .getSettings().getDisplayMode()));
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

    static void remapSemanticColors(Component component,
            ThemePalette previousPalette, ThemePalette currentPalette) {
        if (component == null || previousPalette == null
                || currentPalette == null) return;
        component.setBackground(remap(component.getBackground(),
                previousPalette, currentPalette));
        component.setForeground(remap(component.getForeground(),
                previousPalette, currentPalette));

        if (component instanceof JComponent) {
            JComponent swing = (JComponent) component;
            Border border = remapBorder(swing.getBorder(), previousPalette,
                    currentPalette);
            if (border != swing.getBorder()) swing.setBorder(border);
        }
        if (component instanceof JTable) {
            JTable table = (JTable) component;
            table.setGridColor(remap(table.getGridColor(), previousPalette,
                    currentPalette));
            table.setSelectionBackground(remap(table.getSelectionBackground(),
                    previousPalette, currentPalette));
            table.setSelectionForeground(remap(table.getSelectionForeground(),
                    previousPalette, currentPalette));
        }
        if (component instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) component;
            text.setCaretColor(remap(text.getCaretColor(), previousPalette,
                    currentPalette));
            text.setSelectionColor(remap(text.getSelectionColor(),
                    previousPalette, currentPalette));
            text.setSelectedTextColor(remap(text.getSelectedTextColor(),
                    previousPalette, currentPalette));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                remapSemanticColors(child, previousPalette, currentPalette);
            }
        }
    }

    private static Color remap(Color color, ThemePalette previousPalette,
            ThemePalette currentPalette) {
        if (color == null) return null;
        for (ThemeToken token : ThemeToken.values()) {
            if (color.equals(previousPalette.get(token))) {
                return currentPalette.get(token);
            }
        }
        return color;
    }

    private static Border remapBorder(Border border,
            ThemePalette previousPalette, ThemePalette currentPalette) {
        if (border instanceof LineBorder) {
            LineBorder line = (LineBorder) border;
            Color next = remap(line.getLineColor(), previousPalette,
                    currentPalette);
            if (!next.equals(line.getLineColor())) {
                return BorderFactory.createLineBorder(next,
                        line.getThickness(), line.getRoundedCorners());
            }
        } else if (border instanceof TitledBorder) {
            TitledBorder titled = (TitledBorder) border;
            Border inside = remapBorder(titled.getBorder(), previousPalette,
                    currentPalette);
            Color title = remap(titled.getTitleColor(), previousPalette,
                    currentPalette);
            if (inside != titled.getBorder()
                    || (title != null && !title.equals(titled.getTitleColor()))) {
                return BorderFactory.createTitledBorder(inside,
                        titled.getTitle(), titled.getTitleJustification(),
                        titled.getTitlePosition(), titled.getTitleFont(), title);
            }
        } else if (border instanceof CompoundBorder) {
            CompoundBorder compound = (CompoundBorder) border;
            Border outside = remapBorder(compound.getOutsideBorder(),
                    previousPalette, currentPalette);
            Border inside = remapBorder(compound.getInsideBorder(),
                    previousPalette, currentPalette);
            if (outside != compound.getOutsideBorder()
                    || inside != compound.getInsideBorder()) {
                return BorderFactory.createCompoundBorder(outside, inside);
            }
        }
        return border;
    }
}
