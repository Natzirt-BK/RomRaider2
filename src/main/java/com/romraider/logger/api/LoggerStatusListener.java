/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Toolkit-neutral observer for Logger connection and recording state. */
public interface LoggerStatusListener {
    void connecting();

    default void reconnecting() {
        connecting();
    }

    void readingData();

    void readingDataExternal();

    void loggingData();

    void stopped();
}
