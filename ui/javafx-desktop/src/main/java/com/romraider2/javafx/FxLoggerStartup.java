package com.romraider2.javafx;

import com.romraider.logger.api.LoggerSessionState;

/** One startup attempt; missing setup can be completed before it is consumed. */
final class FxLoggerStartup {
    private boolean considered;

    void consider(boolean enabled, boolean configured, LoggerSessionState state, Runnable connect) {
        if (considered || !configured) return;
        considered = true;
        if (enabled && state == LoggerSessionState.STOPPED) connect.run();
    }
}
