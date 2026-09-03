/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.Locale;

/** Visual role assigned to one saved dashboard tile. */
public enum LoggerDashboardTileRole {
    GAUGE("Analog"),
    VALUE("Digital"),
    TREND("Trend"),
    ALARM("Alarm");

    private final String displayName;

    LoggerDashboardTileRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LoggerDashboardTileRole next() {
        LoggerDashboardTileRole[] roles = values();
        return roles[(ordinal() + 1) % roles.length];
    }

    public static LoggerDashboardTileRole fromName(String value) {
        if (value == null) return GAUGE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return GAUGE;
        }
    }
}
