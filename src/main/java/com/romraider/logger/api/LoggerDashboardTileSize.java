/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.Locale;

/** Responsive footprint assigned to one saved dashboard tile. */
public enum LoggerDashboardTileSize {
    STANDARD("Standard"),
    WIDE("Wide"),
    LARGE("Large");

    private final String displayName;

    LoggerDashboardTileSize(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LoggerDashboardTileSize next() {
        LoggerDashboardTileSize[] sizes = values();
        return sizes[(ordinal() + 1) % sizes.length];
    }

    public static LoggerDashboardTileSize fromName(String value) {
        if (value == null) return STANDARD;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return STANDARD;
        }
    }
}
