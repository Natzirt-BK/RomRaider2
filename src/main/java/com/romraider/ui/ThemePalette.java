/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ThemePalette {
    private final Map<ThemeToken, Color> colors;

    public ThemePalette(Map<ThemeToken, Color> colors) {
        EnumMap<ThemeToken, Color> copy = new EnumMap<ThemeToken, Color>(ThemeToken.class);
        copy.putAll(colors);
        for (ThemeToken token : ThemeToken.values()) {
            if (!copy.containsKey(token) || copy.get(token) == null) {
                throw new IllegalArgumentException("Missing theme token: " + token);
            }
        }
        this.colors = Collections.unmodifiableMap(copy);
    }

    public Color get(ThemeToken token) {
        Color color = colors.get(token);
        if (color == null) {
            throw new IllegalArgumentException("Unknown theme token: " + token);
        }
        return color;
    }

    public Map<ThemeToken, Color> asMap() {
        return colors;
    }
}
