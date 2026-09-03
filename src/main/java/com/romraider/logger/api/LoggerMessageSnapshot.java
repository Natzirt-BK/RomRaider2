/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Latest human-readable status emitted by the Logger runtime. */
public final class LoggerMessageSnapshot {
    private final String message;
    private final String statistics;
    private final boolean error;

    public LoggerMessageSnapshot(String message, String statistics,
            boolean error) {
        this.message = message == null ? "" : message;
        this.statistics = statistics == null ? "" : statistics;
        this.error = error;
    }

    public String getMessage() { return message; }
    public String getStatistics() { return statistics; }
    public boolean isError() { return error; }
}
