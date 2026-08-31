/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

public enum LogMarkerType {
    PULL_START("Pull Start"), SHIFT("Shift"), KNOCK("Knock"),
    LAUNCH("Launch"), STALL("Stall"), TIP_IN("Tip In"),
    BOOST_SPIKE("Boost Spike"), CUSTOM("Custom");

    private final String displayName;
    LogMarkerType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
    @Override public String toString() { return displayName; }
}
