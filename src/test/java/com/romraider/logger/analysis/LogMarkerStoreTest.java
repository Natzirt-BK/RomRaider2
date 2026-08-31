/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.Test;

public class LogMarkerStoreTest {
    @Test
    public void markerSidecarRoundTripsAndRejectsOutOfRangeRows()
            throws Exception {
        Path temporary = Files.createTempDirectory("romraider2-markers");
        File log = temporary.resolve("capture.csv").toFile();
        Files.write(log.toPath(), Arrays.asList("Time,RPM", "0,1000"));
        LogMarkerStore store = new LogMarkerStore();
        try {
            store.save(log, Arrays.asList(
                    new LogMarker(7, LogMarkerType.KNOCK, "FBKC"),
                    new LogMarker(2, LogMarkerType.SHIFT, "")));

            List<LogMarker> markers = store.load(log, 5);
            assertEquals(1, markers.size());
            assertEquals(2, markers.get(0).getSampleIndex());
            assertEquals(LogMarkerType.SHIFT, markers.get(0).getType());

            store.save(log, java.util.Collections.<LogMarker>emptyList());
            assertFalse(Files.exists(store.sidecar(log)));
        } finally {
            Files.deleteIfExists(store.sidecar(log));
            Files.deleteIfExists(log.toPath());
            Files.deleteIfExists(temporary);
        }
    }

    @Test
    public void completedCaptureIsOfferedToImmediatePlaybackListeners()
            throws Exception {
        Path file = Files.createTempFile("romraider2-last-log", ".csv");
        Files.write(file, Arrays.asList("Time,RPM", "0,1000"));
        RecentLogCaptureService service = RecentLogCaptureService.getInstance();
        AtomicReference<File> offered = new AtomicReference<File>();
        Consumer<File> listener = offered::set;
        service.addListener(listener);
        try {
            service.completed(file.toFile());
            assertEquals(file.toFile().getAbsoluteFile(),
                    service.getLastCompleted());
            assertSame(service.getLastCompleted(), offered.get());
        } finally {
            service.removeListener(listener);
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void failingRecentLogListenerDoesNotBlockOtherListeners()
            throws Exception {
        Path file = Files.createTempFile("romraider2-last-log", ".csv");
        Files.write(file, Arrays.asList("Time,RPM", "0,1000"));
        RecentLogCaptureService service = RecentLogCaptureService.getInstance();
        Consumer<File> failing = value -> {
            throw new IllegalStateException("test listener failure");
        };
        AtomicReference<File> offered = new AtomicReference<File>();
        Consumer<File> working = offered::set;
        service.addListener(failing);
        service.addListener(working);
        try {
            service.completed(file.toFile());
            assertEquals(file.toFile().getAbsoluteFile(), offered.get());
        } finally {
            service.removeListener(failing);
            service.removeListener(working);
            Files.deleteIfExists(file);
        }
    }
}
