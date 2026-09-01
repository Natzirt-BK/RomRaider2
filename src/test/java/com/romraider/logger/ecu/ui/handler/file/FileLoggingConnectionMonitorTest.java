/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.handler.file;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.romraider.logger.api.LoggerStatusListener;
import com.romraider.logger.ecu.comms.query.Response;
import com.romraider.logger.ecu.definition.LoggerData;

public class FileLoggingConnectionMonitorTest {
    @Test
    public void stopsFileCaptureAndResetsItsControlWhenConnectionStops() {
        RecordingFileUpdateHandler handler = new RecordingFileUpdateHandler();
        AtomicInteger resets = new AtomicInteger();
        FileLoggingConnectionMonitor monitor = new FileLoggingConnectionMonitor(
                handler, resets::incrementAndGet);

        monitor.connecting();
        monitor.readingData();
        monitor.loggingData();
        assertEquals(0, handler.stopCount);

        monitor.stopped();

        assertEquals(1, handler.stopCount);
        assertEquals(1, resets.get());
    }

    private static final class RecordingFileUpdateHandler
            implements FileUpdateHandler {
        private int stopCount;

        @Override public void stop() { stopCount++; }
        @Override public void start() { }
        @Override public void addListener(LoggerStatusListener listener) { }
        @Override public void registerData(LoggerData loggerData) { }
        @Override public void handleDataUpdate(Response response) { }
        @Override public void deregisterData(LoggerData loggerData) { }
        @Override public void cleanUp() { }
        @Override public void reset() { }
    }
}
