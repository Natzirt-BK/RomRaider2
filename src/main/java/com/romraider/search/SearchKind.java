/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.search;

/** Categories understood by the application-wide search index. */
public enum SearchKind {
    TABLE("Map"),
    LOGGER_PARAMETER("Logger parameter"),
    DTC("Diagnostic code"),
    SETTING("Setting"),
    COMMAND("Command");

    private final String displayName;

    SearchKind(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
