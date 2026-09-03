/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.Locale;

/** Saved responsive density for dashboard gauge cards. */
public enum LoggerGaugeLayout {
    COMPACT("Compact"),
    STANDARD("Standard"),
    LARGE("Large");

    private final String displayName;

    LoggerGaugeLayout(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    public static LoggerGaugeLayout fromName(String value) {
        if (value == null) return STANDARD;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return STANDARD;
        }
    }
}
