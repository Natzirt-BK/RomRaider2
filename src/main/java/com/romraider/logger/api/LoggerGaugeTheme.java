/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.Locale;

/** Original RR2 dashboard styles; no manufacturer artwork or logos. */
public enum LoggerGaugeTheme {
    RR2_CLASSIC("RR2 Classic"),
    RALLY_HERITAGE("Rally Heritage"),
    AMBER_GT("Amber GT"),
    CENTRAL_TACH("Central Tach"),
    NEON_CIRCUIT("Neon Circuit"),
    HANDHELD("Handheld");

    private final String displayName;

    LoggerGaugeTheme(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LoggerGaugeTheme fromName(String value) {
        if (value == null) return RR2_CLASSIC;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return RR2_CLASSIC;
        }
    }
}
