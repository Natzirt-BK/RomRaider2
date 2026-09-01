/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Stable channel groups shared by Logger user interfaces. */
public enum LoggerChannelKind {
    PARAMETER("Parameters"),
    SWITCH("Switches"),
    EXTERNAL("External Sensors");

    private final String displayName;

    LoggerChannelKind(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
