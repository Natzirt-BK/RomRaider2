/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import static org.junit.Assert.*;
import com.romraider.logger.analysis.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class SwingLogLoadTaskTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File source(String name) throws Exception { File file = temporary.newFile(name); Files.writeString(file.toPath(), "Time,Value\n0,2\n100,\n200,8\n"); return file; }
    private static void drain(Queue<Runnable> ui) { Runnable next; while ((next = ui.poll()) != null) next.run(); }
    @Test public void boundedPreparationIncludesExactStatisticsAndMarkerSnapshotOffCallerThread() throws Exception {
        File source = source("valid.csv"); var store = new LogMarkerStore(); store.save(source, List.of(new LogMarker(1, LogMarkerType.CUSTOM, "saved")));
        var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<SwingLogLoadTask.PreparedLog>(); Thread caller = Thread.currentThread();
        try (var task = new SwingLogLoadTask(file -> { assertNotSame(caller, Thread.currentThread()); return SwingLogLoadTask.prepare(file); },
                ui::add, results::add, (file, failure) -> fail(failure.toString()))) {
            task.load(source); task.pending().get(5, TimeUnit.SECONDS); assertTrue(results.isEmpty()); drain(ui);
            var result = results.get(0); assertEquals(source.getAbsoluteFile(), result.source()); assertEquals(3, result.dataset().getRowCount());
            assertEquals(5, result.statistics().get(1).getMean(), 0); assertEquals(1, result.statistics().get(1).getMissingCount());
            assertEquals("saved", result.markers().getMarkers().get(0).getLabel()); assertNull(result.markerProblem());
        }
    }
    @Test public void rejectedCsvNeverReplacesAnEarlierResultAndMalformedMarkersDoNotRejectValidCsv() throws Exception {
        File valid = source("valid.csv"), bad = source("bad.csv"); Files.writeString(bad.toPath(), "X".repeat(513) + "\n1\n");
        var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<SwingLogLoadTask.PreparedLog>(); var failures = new ArrayList<Throwable>();
        try (var task = new SwingLogLoadTask(ui::add, results::add, (file, failure) -> failures.add(failure))) {
            task.load(valid); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            task.load(bad); task.pending().get(5, TimeUnit.SECONDS); drain(ui); assertEquals(1, results.size()); assertEquals(1, failures.size());
            var store = new LogMarkerStore(); Files.writeString(store.sidecar(valid), "format.version=99\nmarker.count=0\n");
            task.load(valid); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(2, results.size()); assertNull(results.get(1).markers()); assertNotNull(results.get(1).markerProblem());
            assertEquals(3, results.get(1).dataset().getRowCount()); assertTrue(Files.readString(store.sidecar(valid)).contains("99"));
        }
    }
    @Test public void queuedResultsErrorsAndCloseCannotSupersedeNewerRequests() throws Exception {
        File first = source("first.csv"), second = source("second.csv"), missing = new File(temporary.getRoot(), "missing.csv");
        var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<SwingLogLoadTask.PreparedLog>(); var failures = new ArrayList<Throwable>();
        try (var task = new SwingLogLoadTask(ui::add, results::add, (file, failure) -> failures.add(failure))) {
            task.load(first); task.pending().get(5, TimeUnit.SECONDS); task.load(second); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(1, results.size()); assertEquals(second, results.get(0).source());
            task.load(missing); task.pending().get(5, TimeUnit.SECONDS); task.load(first); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertTrue(failures.isEmpty()); assertEquals(first, results.get(1).source());
            task.load(second); task.pending().get(5, TimeUnit.SECONDS); task.close(); drain(ui); assertEquals(2, results.size());
            task.load(first); assertNull(task.pending());
        }
    }
    @Test public void replacementInterruptsActualWorkAndDropsCancelledQueuedRequests() throws Exception {
        File source = source("valid.csv"); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<SwingLogLoadTask.PreparedLog>();
        var prepared = SwingLogLoadTask.prepare(source); CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var task = new SwingLogLoadTask(file -> {
            if (calls.getAndIncrement() == 0) { started.countDown(); try { release.await(); } catch (InterruptedException expected) { interrupted.countDown(); release.await(); } }
            return prepared;
        }, ui::add, results::add, (file, failure) -> fail(failure.toString()))) {
            task.load(source); assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) task.load(source);
            assertTrue(interrupted.await(5, TimeUnit.SECONDS)); release.countDown(); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(2, calls.get()); assertEquals(1, results.size());
        } finally { release.countDown(); }
    }
    @Test public void nullChooserResultDoesNotCancelAcceptedLoad() throws Exception {
        File source = source("valid.csv"); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<SwingLogLoadTask.PreparedLog>();
        try (var task = new SwingLogLoadTask(ui::add, results::add, (file, failure) -> fail(failure.toString()))) {
            task.load(source); var accepted = task.pending(); task.load(null); assertSame(accepted, task.pending());
            accepted.get(5, TimeUnit.SECONDS); drain(ui); assertEquals(1, results.size());
        }
    }
}
