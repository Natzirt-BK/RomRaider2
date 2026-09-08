package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.analysis.RomRaiderCsvLogParser;
import com.romraider.portable.logger.Elm327ReadOnlyRecorder;
import com.romraider.portable.logger.Elm327Session;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ElmAdapterTestRunTest {
    @TempDir Path directory;
    private ElmAdapterTestRun.Configuration config(Path path) {
        return new ElmAdapterTestRun.Configuration("synthetic-only", 38400, Elm327Session.Protocol.AUTOMATIC, 2, path);
    }
    @Test void csvUsesRomRaiderHeadersUnitsScalingAndMissingValues() throws Exception {
        var link = new ElmAdapterTestFixture(); link.intermittent = true;
        Path output = directory.resolve("log.csv");
        var run = new ElmAdapterTestRun(config(output), (port, baud) -> link.session(), link.recorder());
        Locale old = Locale.getDefault();
        try { Locale.setDefault(Locale.GERMANY); run.run(); } finally { Locale.setDefault(old); }
        assertTrue(run.isFinished()); assertTrue(run.isSuccessful(), run.status()); assertEquals(1, link.closes);
        String csv = Files.readString(output);
        assertTrue(csv.startsWith("Time (msec),Engine Speed (rpm),Coolant Temperature (C),Vehicle Speed (km/h)\r\n"));
        var data = new RomRaiderCsvLogParser().parse(output.toFile());
        assertEquals(4, data.getChannelCount()); assertEquals(run.progress().rows, data.getRowCount());
        assertEquals(1726.25, data.getValue(0, 1)); assertEquals(83, data.getValue(0, 2)); assertEquals(0, data.getValue(0, 3));
        assertTrue(Double.isNaN(data.getValue(1, 1))); assertEquals(1726.25, data.getValue(2, 1));
        assertEquals(1, run.progress().missingValues);
        for (int i = 1; i < data.getRowCount(); i++) assertTrue(data.getValue(i, 0) > data.getValue(i - 1, 0));
        assertTrue(link.commands.stream().allMatch(command -> command.startsWith("AT ") || java.util.Set.of("0100", "010C", "0105", "010D").contains(command)));
    }
    @Test void unsupportedChannelsAreNotPolledOrIncludedInHeader() throws Exception {
        var link = new ElmAdapterTestFixture(); link.mask = "00 10 00 00";
        Path output = directory.resolve("rpm.csv");
        var run = new ElmAdapterTestRun(config(output), (port, baud) -> link.session(), link.recorder()); run.run();
        assertTrue(run.isSuccessful(), run.status());
        assertTrue(Files.readString(output).startsWith("Time (msec),Engine Speed (rpm)\r\n"));
        assertFalse(link.commands.contains("0105")); assertFalse(link.commands.contains("010D"));
    }
    @Test void emptyMaskAndRepeatedNoDataCannotPass() throws Exception {
        var link = new ElmAdapterTestFixture(); link.mask = "00 00 00 00";
        var run = new ElmAdapterTestRun(config(directory.resolve("none.csv")), (port, baud) -> link.session(), link.recorder()); run.run();
        assertFalse(run.isSuccessful()); assertTrue(run.status().contains("none of the test PIDs")); assertEquals(1, link.closes);
        var absent = new ElmAdapterTestFixture(); absent.noData = true;
        var absentRun = new ElmAdapterTestRun(config(directory.resolve("absent.csv")), (port, baud) -> absent.session(), absent.recorder()); absentRun.run();
        assertFalse(absentRun.isSuccessful()); assertEquals(5, absentRun.progress().rows); assertEquals(0, absentRun.progress().validValues);
        assertTrue(Files.readString(directory.resolve("absent.csv")).contains(",,,\r\n"));
    }
    @Test void existingFileAndSymlinkAreNeverOverwrittenAndNeverOpenAdapter() throws Exception {
        Path target = directory.resolve("existing.csv"); Files.writeString(target, "keep me");
        AtomicInteger opens = new AtomicInteger();
        ElmAdapterTestRun.Factory factory = (port, baud) -> { opens.incrementAndGet(); throw new IOException("must not open"); };
        var run = new ElmAdapterTestRun(config(target), factory, new Elm327ReadOnlyRecorder()); run.run();
        assertEquals(0, opens.get()); assertFalse(run.isCreated()); assertEquals("keep me", Files.readString(target));
        assertTrue(run.status().contains("CSV already exists"));
        if (!System.getProperty("os.name").startsWith("Windows")) {
            Path symlink = directory.resolve("link.csv"); Files.createSymbolicLink(symlink, target);
            var linked = new ElmAdapterTestRun(config(symlink), factory, new Elm327ReadOnlyRecorder()); linked.run();
            assertEquals(0, opens.get()); assertEquals("keep me", Files.readString(target));
        }
    }
    @Test void cancelBeforeStartDoesNotCreateFileOrOpenDevice() {
        var link = new ElmAdapterTestFixture(); Path target = directory.resolve("cancel.csv");
        var run = new ElmAdapterTestRun(config(target), (port, baud) -> { fail("Opened after cancellation"); return link.session(); }, link.recorder());
        run.cancel(); run.run(); assertTrue(run.isFinished()); assertFalse(Files.exists(target));
    }
    @Test void cancellationDuringOpenClosesLateSessionWithoutSendingCommands() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var link = new ElmAdapterTestFixture();
        var run = new ElmAdapterTestRun(config(directory.resolve("late.csv")), (port, baud) -> {
            entered.countDown();
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { /* Simulate noninterruptible driver open. */ }
            return link.session();
        }, link.recorder());
        run.start(); assertTrue(entered.await(3, TimeUnit.SECONDS)); run.cancel(); release.countDown();
        await(run); assertEquals(1, link.closes); assertTrue(link.commands.isEmpty()); assertFalse(run.isSuccessful());
        assertThrows(IllegalStateException.class, run::start);
    }
    @Test void malformedResponseAndCloseFailureAreNotReportedAsSuccess() {
        var link = new ElmAdapterTestFixture(); link.rpmReply = "41 0D 22>"; link.closeFails = true;
        var run = new ElmAdapterTestRun(config(directory.resolve("bad.csv")), (port, baud) -> link.session(), link.recorder()); run.run();
        assertFalse(run.isSuccessful()); assertEquals(1, link.closes); assertTrue(run.status().contains("Cleanup"));
    }
    @Test void recorderPropagatesStorageFailureBeforeMoreQueries() throws Exception {
        var link = new ElmAdapterTestFixture();
        try (var session = link.session()) {
            session.initialize(Elm327Session.Protocol.AUTOMATIC, 2000, () -> false);
            Writer broken = new Writer() {
                public void write(char[] value, int offset, int count) throws IOException { throw new IOException("disk full"); }
                public void flush() {} public void close() {}
            };
            assertThrows(IOException.class, () -> link.recorder().record(session, broken, 2, () -> false, p -> {}));
            assertEquals(0, link.rpmQueries);
        }
    }
    @Test void scalingEndpointsAndWidthsAreExplicit() {
        assertEquals(16383.75, Elm327ReadOnlyRecorder.Channel.RPM.decode(new byte[] {-1, -1}));
        assertEquals(-40, Elm327ReadOnlyRecorder.Channel.COOLANT.decode(new byte[] {0}));
        assertEquals(215, Elm327ReadOnlyRecorder.Channel.COOLANT.decode(new byte[] {-1}));
        assertEquals(255, Elm327ReadOnlyRecorder.Channel.SPEED.decode(new byte[] {-1}));
        assertThrows(IllegalArgumentException.class, () -> Elm327ReadOnlyRecorder.Channel.RPM.decode(new byte[] {1}));
    }
    @Test void unavailableNativeLibraryIsReportedAndFinishes() {
        var run = new ElmAdapterTestRun(config(directory.resolve("native.csv")), (port, baud) -> {
            throw new UnsatisfiedLinkError("native fixture unavailable");
        }, new Elm327ReadOnlyRecorder());
        run.run(); assertTrue(run.isFinished()); assertFalse(run.isSuccessful()); assertTrue(run.status().contains("native fixture unavailable"));
    }
    static void await(ElmAdapterTestRun run) throws Exception {
        long start = System.nanoTime();
        while (!run.isFinished() && System.nanoTime() - start < 3_000_000_000L) Thread.sleep(5);
        assertTrue(run.isFinished(), "Adapter worker did not finish");
    }
}
