/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

public enum ThemeMode {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System"),
    HIGH_CONTRAST("High Contrast");

    private final String displayName;

    ThemeMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
