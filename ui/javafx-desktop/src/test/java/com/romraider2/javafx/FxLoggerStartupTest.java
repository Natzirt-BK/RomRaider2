package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.romraider.logger.api.LoggerSessionState;
import org.junit.jupiter.api.Test;

class FxLoggerStartupTest {
    @Test void waitsForConfigurationAndOnlyConnectsOnce() {
        FxLoggerStartup startup = new FxLoggerStartup();
        AtomicInteger calls = new AtomicInteger();
        startup.consider(true, false, LoggerSessionState.STOPPED, calls::incrementAndGet);
        assertEquals(0, calls.get());
        startup.consider(true, true, LoggerSessionState.STOPPED, calls::incrementAndGet);
        startup.consider(true, true, LoggerSessionState.STOPPED, calls::incrementAndGet);
        assertEquals(1, calls.get());
    }
    @Test void uncheckedPreferenceAndActiveSessionNeverConnect() {
        AtomicInteger calls = new AtomicInteger();
        new FxLoggerStartup().consider(false, true, LoggerSessionState.STOPPED, calls::incrementAndGet);
        new FxLoggerStartup().consider(true, true, LoggerSessionState.RECORDING, calls::incrementAndGet);
        assertEquals(0, calls.get());
    }
}
