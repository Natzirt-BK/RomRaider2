/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import static org.junit.Assert.*;
import com.romraider.logger.analysis.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class SwingMarkerSaveTaskTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final LogMarkerStore store = new LogMarkerStore();
    private LogMarker marker(String label) { return new LogMarker(0, LogMarkerType.CUSTOM, label); }
    private void drain(Queue<Runnable> ui) { Runnable next; while ((next = ui.poll()) != null) next.run(); }
    @Test public void proposalIsFrozenAndNewEditsWaitForUiDelivery() throws Exception {
        File file = temporary.newFile(); var expected = store.loadSnapshot(file, 2);
        var ui = new ConcurrentLinkedQueue<Runnable>(); var saved = new ArrayList<LogMarkerStore.Snapshot>();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1); Thread caller = Thread.currentThread();
        try (var task = new SwingMarkerSaveTask((snapshot, markers) -> {
            assertNotSame(caller, Thread.currentThread()); started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test gate timed out"); } catch (InterruptedException failure) { throw new IOException(failure); }
            return store.saveIfUnchanged(snapshot, markers);
        }, ui::add, saved::add, failure -> fail(failure.toString()))) {
            var proposed = new ArrayList<>(List.of(marker("accepted"))); task.save(expected, proposed);
            assertTrue(started.await(5, TimeUnit.SECONDS)); proposed.clear(); release.countDown(); task.pending().get(5, TimeUnit.SECONDS);
            assertTrue(saved.isEmpty());
            try { task.save(expected, List.of()); fail("Overlapping edit accepted"); } catch (IllegalStateException correct) { }
            drain(ui); assertEquals("accepted", saved.get(0).getMarkers().get(0).getLabel());
            assertEquals("accepted", store.load(file, 2).get(0).getLabel());
        } finally { release.countDown(); }
    }
    @Test public void externalConflictRetainsSidecarAndReportsFailure() throws Exception {
        File file = temporary.newFile(); var expected = store.loadSnapshot(file, 2); store.save(file, List.of(marker("external")));
        var ui = new ConcurrentLinkedQueue<Runnable>(); var failures = new ArrayList<Throwable>();
        try (var task = new SwingMarkerSaveTask(ui::add, result -> fail("Unexpected save"), failures::add)) {
            task.save(expected, List.of(marker("stale"))); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(1, failures.size()); assertEquals("external", store.load(file, 2).get(0).getLabel());
        }
    }
    @Test public void closedViewsAllowAcceptedSaveToFinishAndNeverReceiveLateSuccessOrFailure() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            File file = temporary.newFile(); var expected = store.loadSnapshot(file, 2); var ui = new ConcurrentLinkedQueue<Runnable>();
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
            try (var task = new SwingMarkerSaveTask((snapshot, markers) -> {
                started.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test gate timed out"); }
                catch (InterruptedException cancelled) { throw new IOException("Accepted save cancelled", cancelled); }
                if (fail) throw new IOException("Synthetic post-close failure");
                return store.saveIfUnchanged(snapshot, markers);
            }, ui::add, result -> fail("Closed callback"), failure -> fail("Closed callback"))) {
                task.save(expected, List.of(marker("accepted"))); assertTrue(started.await(5, TimeUnit.SECONDS)); task.close();
                assertFalse(task.pending().isCancelled()); release.countDown(); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
                if (fail) assertFalse(Files.exists(store.sidecar(file))); else assertEquals("accepted", store.load(file, 2).get(0).getLabel());
            } finally { release.countDown(); }
        }
    }
}
