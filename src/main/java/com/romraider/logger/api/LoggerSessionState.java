/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Connection state shared with editor surfaces without exposing logger UI. */
public enum LoggerSessionState {
    STOPPED("ECU OFFLINE"),
    CONNECTING("CONNECTING"),
    LIVE_ECU("ECU LIVE"),
    LIVE_EXTERNAL("EXTERNAL DATA LIVE"),
    RECORDING("RECORDING");

    private final String displayName;

    LoggerSessionState(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
    public boolean isLive() {
        return this == LIVE_ECU || this == LIVE_EXTERNAL || this == RECORDING;
    }
}
