/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

import java.awt.Color;
import java.util.EnumMap;

import javax.swing.UIManager;

public final class ThemePalettes {
    private ThemePalettes() {
    }

    public static ThemePalette forMode(ThemeMode mode) {
        switch (mode) {
            case LIGHT:
                return light();
            case SYSTEM:
                return system();
            case HIGH_CONTRAST:
                return highContrast();
            case DARK:
            default:
                return dark();
        }
    }

    public static ThemePalette dark() {
        return palette(
                new Color(16, 21, 27), new Color(23, 30, 38),
                new Color(32, 41, 52), new Color(228, 232, 237),
                new Color(169, 177, 186), new Color(217, 38, 50),
                new Color(220, 155, 55), new Color(215, 25, 32),
                new Color(59, 176, 112), new Color(91, 35, 43),
                new Color(116, 79, 30), new Color(92, 47, 51),
                new Color(86, 174, 166));
    }

    public static ThemePalette light() {
        return palette(
                new Color(242, 245, 248), Color.WHITE,
                new Color(225, 230, 236), new Color(28, 34, 40),
                new Color(91, 103, 115), new Color(217, 38, 50),
                new Color(185, 112, 0), new Color(190, 42, 42),
                new Color(20, 130, 72), new Color(255, 214, 218),
                new Color(255, 226, 158), new Color(221, 201, 255),
                new Color(0, 140, 132));
    }

    /** Dark handheld palette used only by the SteamOS bundle profile. */
    public static ThemePalette handheld() {
        return palette(
                new Color(23, 26, 33), new Color(27, 40, 56),
                new Color(42, 71, 94), new Color(239, 246, 251),
                new Color(163, 182, 196), new Color(102, 192, 244),
                new Color(238, 170, 68), new Color(234, 74, 86),
                new Color(88, 190, 132), new Color(34, 76, 104),
                new Color(102, 74, 30), new Color(79, 51, 65),
                new Color(71, 178, 208));
    }

    public static ThemePalette highContrast() {
        return palette(
                Color.BLACK, new Color(10, 10, 10), new Color(35, 35, 35),
                Color.WHITE, new Color(225, 225, 225), Color.CYAN,
                Color.YELLOW, new Color(255, 64, 64), new Color(72, 255, 72),
                new Color(0, 70, 170), new Color(160, 100, 0),
                new Color(120, 0, 180), new Color(0, 255, 220));
    }

    public static ThemePalette system() {
        Color background = color("Panel.background", new Color(238, 238, 238));
        Color surface = color("TextField.background", Color.WHITE);
        Color text = color("Label.foreground", Color.BLACK);
        Color selection = color("Table.selectionBackground", new Color(51, 153, 255));
        return palette(
                background, surface, color("controlShadow", background.darker()),
                text, color("textInactiveText", text.brighter()),
                color("textHighlight", selection), new Color(185, 112, 0),
                new Color(190, 42, 42), new Color(20, 130, 72), selection,
                new Color(255, 226, 158), new Color(221, 201, 255),
                new Color(0, 140, 132));
    }

    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }

    private static ThemePalette palette(Color background, Color surface,
            Color raisedSurface, Color primaryText, Color secondaryText,
            Color accent, Color warning, Color danger, Color success,
            Color selection, Color modified, Color realtime, Color liveTrace) {
        EnumMap<ThemeToken, Color> colors = new EnumMap<ThemeToken, Color>(ThemeToken.class);
        colors.put(ThemeToken.BACKGROUND, background);
        colors.put(ThemeToken.SURFACE, surface);
        colors.put(ThemeToken.RAISED_SURFACE, raisedSurface);
        colors.put(ThemeToken.PRIMARY_TEXT, primaryText);
        colors.put(ThemeToken.SECONDARY_TEXT, secondaryText);
        colors.put(ThemeToken.ACCENT, accent);
        colors.put(ThemeToken.WARNING, warning);
        colors.put(ThemeToken.DANGER, danger);
        colors.put(ThemeToken.SUCCESS, success);
        colors.put(ThemeToken.SELECTION, selection);
        colors.put(ThemeToken.MODIFIED_CELL, modified);
        colors.put(ThemeToken.REALTIME_CELL, realtime);
        colors.put(ThemeToken.LIVE_TRACE, liveTrace);
        return new ThemePalette(colors);
    }
}
