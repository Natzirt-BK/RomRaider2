package com.romraider.logger.ecu.ui.handler.file;

import static org.junit.Assert.*;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.comms.query.*;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.ui.EcuRelatedMessageListener;
import com.romraider.util.SettingsManager;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class RecordingReliabilityTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void elapsedTimerFollowsCaptureLifecycleNotSamplesOrWallClock() throws Exception {
        var settings = SettingsManager.getSettings();
        String oldDirectory = settings.getLoggerOutputDirPath(), oldName = settings.getLogfileNameText();
        var nanos = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
        var wall = new java.util.concurrent.atomic.AtomicLong(100_000);
        FileLoggerImpl logger = new FileLoggerImpl(messages(), () -> new Date(wall.get()), nanos::get);
        try {
            settings.setLoggerOutputDirPath(temporary.getRoot().getAbsolutePath()); settings.setLogfileNameText("timer");
            assertEquals(0, logger.getRecordingElapsedMillis());
            logger.start(); nanos.addAndGet(65_000_000_000L); wall.set(-100_000);
            assertEquals(65_000, logger.getRecordingElapsedMillis());
            logger.start(); // Duplicate start must not reset the clock.
            assertEquals(65_000, logger.getRecordingElapsedMillis());
            logger.writeHeaders(",Value"); logger.writeLine(",1", 9_000_000);
            assertEquals(65_000, logger.getRecordingElapsedMillis());
            logger.stop(); nanos.addAndGet(60_000_000_000L);
            assertEquals(65_000, logger.getRecordingElapsedMillis());
            logger.stop(); assertEquals(65_000, logger.getRecordingElapsedMillis());
            logger.start(); assertEquals(0, logger.getRecordingElapsedMillis());
            nanos.addAndGet(2_000_000_000L); logger.stop();
            assertEquals(2_000, logger.getRecordingElapsedMillis());
            settings.setLoggerOutputDirPath(temporary.getRoot().toPath().resolve("missing").toString());
            try { logger.start(); fail("Expected failed open"); } catch (com.romraider.logger.ecu.exception.FileLoggerException expected) { }
            nanos.addAndGet(3_000_000_000L);
            assertEquals(2_000, logger.getRecordingElapsedMillis());
        } finally {
            logger.stop(); settings.setLoggerOutputDirPath(oldDirectory); settings.setLogfileNameText(oldName);
        }
    }

    @Test public void monotonicCounterHandlesSignedWrapAndRepeatedStops() {
        var nanos = new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE - 1_000_000_000L);
        var timer = new RecordingDuration(nanos::get);
        timer.start(); nanos.addAndGet(2_000_000_000L);
        assertEquals(2_000, timer.elapsedMillis());
        timer.stop(); nanos.addAndGet(1_000_000_000L); timer.stop();
        assertEquals(2_000, timer.elapsedMillis());
    }

    @Test public void repeatedCapturesNeverOverwriteAndKeepEveryCompleteRow() throws Exception {
        var settings = SettingsManager.getSettings();
        String oldDirectory = settings.getLoggerOutputDirPath(), oldName = settings.getLogfileNameText();
        boolean oldAbsolute = settings.isFileLoggingAbsoluteTimestamp();
        FileLoggerImpl logger = new FileLoggerImpl(messages(), () -> new Date(0));
        try {
            settings.setLoggerOutputDirPath(temporary.getRoot().getAbsolutePath()); settings.setLogfileNameText("synthetic");
            settings.setFileLoggingAbsoluteTimestamp(false);
            for (int capture = 0; capture < 20; capture++) {
                logger.start(); logger.writeHeaders(",Synthetic RPM");
                for (int row = 0; row < 1000; row++) logger.writeLine("," + (capture * 1000 + row), row * 10);
                logger.stop();
            }
            assertEquals(20, temporary.getRoot().listFiles().length);
            var values = new HashSet<String>();
            for (var file : temporary.getRoot().listFiles()) {
                var lines = Files.readAllLines(file.toPath());
                assertEquals(1001, lines.size()); assertEquals("Time (msec),Synthetic RPM", lines.get(0));
                assertTrue(lines.get(1).startsWith("0,")); assertTrue(lines.get(2).startsWith("10,"));
                assertTrue(lines.get(1000).startsWith("9990,"));
                for (int row = 1; row < lines.size(); row++) values.add(lines.get(row).split(",")[1]);
            }
            assertEquals(20000, values.size());
        } finally {
            logger.stop(); settings.setLoggerOutputDirPath(oldDirectory); settings.setLogfileNameText(oldName);
            settings.setFileLoggingAbsoluteTimestamp(oldAbsolute);
        }
    }

    @Test public void partialRowsCannotCrossRecordingBoundariesAndHeadersAreEscaped() {
        Sink sink = new Sink(); var handler = new FileUpdateHandlerImpl(sink);
        var a = parameter("a", "AF, sensor \"1\""); var b = parameter("b", "Speed");
        handler.registerData(a); handler.registerData(b); handler.start();
        assertTrue(sink.header.contains("\"\"1\"\""));
        handler.handleDataUpdate(response(a, 11)); handler.stop(); handler.start();
        handler.handleDataUpdate(response(b, 22)); assertEquals(0, sink.lines.size());
        handler.handleDataUpdate(response(a, 33)); assertEquals(1, sink.lines.size());
        assertTrue(sink.lines.get(0).contains("33")); assertFalse(sink.lines.get(0).contains("11"));
        handler.stop(); handler.deregisterData(a); handler.deregisterData(b); handler.start();
        handler.handleDataUpdate(response(a, 44)); assertEquals(1, sink.lines.size()); handler.stop();
    }

    @Test public void writeFailureEndsRecordingWithoutInventingAConnection() {
        Sink sink = new Sink(); var handler = new FileUpdateHandlerImpl(sink);
        LoggerLiveDataBus bus = LoggerLiveDataBus.getInstance(); bus.readingDataExternal();
        var listener = new LoggerRecordingStatusListener(bus); handler.addListener(listener);
        var channel = parameter("a", "Value"); handler.registerData(channel);
        try {
            handler.start(); assertEquals(LoggerSessionState.RECORDING, bus.getState());
            sink.fail = true;
            try { handler.handleDataUpdate(response(channel, 1)); fail("Expected synthetic disk failure"); }
            catch (IllegalStateException expected) { }
            assertFalse(sink.started); assertEquals(LoggerSessionState.LIVE_EXTERNAL, bus.getState());
            sink.fail = false; handler.start(); bus.stopped(); handler.stop();
            assertEquals(LoggerSessionState.STOPPED, bus.getState());
        } finally { handler.stop(); bus.stopped(); }
    }

    private static EcuParameterImpl parameter(String id, String name) {
        return new EcuParameterImpl(id, name, "Synthetic", new EcuAddressImpl("000001", 1, -1), null, null, null,
                new EcuDataConvertor[]{new EcuParameterConvertorImpl()});
    }
    private static Response response(LoggerData data, double value) { var r = new ResponseImpl(); r.setDataValue(data, value); return r; }
    private static final class Sink implements FileLogger {
        boolean started, fail; String header; List<String> lines = new ArrayList<>();
        public void start() { started = true; } public void stop() { started = false; }
        public boolean isStarted() { return started; } public void writeHeaders(String text) { header = text; }
        public void writeLine(String text, long timestamp) { if (fail) { started = false; throw new IllegalStateException("Synthetic disk failure"); } lines.add(text); }
    }
    private static EcuRelatedMessageListener messages() {
        return new EcuRelatedMessageListener() {
            public EcuInit getEcuInit() { return null; }
            public void reportStats(String s) {} public void reportMessage(String s) {} public void reportMessageInTitleBar(String s) {}
            public void reportError(String s) {} public void reportError(Exception e) {} public void reportError(String s, Exception e) {}
        };
    }
}
