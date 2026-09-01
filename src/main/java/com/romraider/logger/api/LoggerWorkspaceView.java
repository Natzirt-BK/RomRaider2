/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Persistable views exposed by the replacement Logger workspace. */
public enum LoggerWorkspaceView {
    OVERVIEW("Overview"),
    DATA("Data"),
    GRAPH("Graph"),
    DASHBOARD("Dashboard");

    private final String displayName;

    LoggerWorkspaceView(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LoggerWorkspaceView fromName(String name) {
        if (name == null) return OVERVIEW;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            return OVERVIEW;
        }
    }
}
