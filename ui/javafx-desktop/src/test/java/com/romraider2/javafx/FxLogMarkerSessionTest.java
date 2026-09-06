/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.analysis.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FxLogMarkerSessionTest {
    @TempDir Path directory;
    private final LogMarkerStore store = new LogMarkerStore();
    private File source() throws Exception { Path file = directory.resolve("synthetic.csv"); Files.writeString(file, "Value\n1\n2\n"); return file.toFile(); }
    private LogMarker marker(String name) { return new LogMarker(0, LogMarkerType.CUSTOM, name); }
    private static void drain(Queue<Runnable> ui) { Runnable next; while ((next = ui.poll()) != null) next.run(); }
    private static FxLogMarkerSession.State last(List<FxLogMarkerSession.State> states) { return states.get(states.size() - 1); }
    private void finish(FxLogMarkerSession session, Queue<Runnable> ui) throws Exception { session.pending().get(5, TimeUnit.SECONDS); drain(ui); }

    @Test void editsApplyOnlyAfterSuccessfulSaveAndConflictRequiresReload() throws Exception {
        File source = source(); store.save(source, List.of(marker("original")));
        var ui = new ConcurrentLinkedQueue<Runnable>(); var states = new ArrayList<FxLogMarkerSession.State>();
        try (var session = new FxLogMarkerSession(source, 2, ui::add, states::add)) {
            session.load(); assertTrue(last(states).busy()); assertFalse(last(states).editable()); finish(session, ui);
            assertEquals("original", last(states).markers().get(0).getLabel());
            session.replace(List.of(marker("saved"))); assertEquals("original", last(states).markers().get(0).getLabel());
            assertThrows(IllegalStateException.class, () -> session.replace(List.of())); finish(session, ui);
            assertTrue(last(states).editable()); assertEquals("saved", store.load(source, 2).get(0).getLabel());
            store.save(source, List.of(marker("external"))); session.replace(List.of(marker("stale"))); finish(session, ui);
            assertFalse(last(states).editable()); assertEquals("saved", last(states).markers().get(0).getLabel());
            assertEquals("external", store.load(source, 2).get(0).getLabel());
            session.load(); finish(session, ui); assertTrue(last(states).editable()); assertEquals("external", last(states).markers().get(0).getLabel());
        }
    }
    @Test void badSidecarAndInvalidEditDoNotEnableDestructiveRecovery() throws Exception {
        File source = source(); Files.writeString(store.sidecar(source), "format.version=99\nmarker.count=0\n");
        var ui = new ConcurrentLinkedQueue<Runnable>(); var states = new ArrayList<FxLogMarkerSession.State>();
        try (var session = new FxLogMarkerSession(source, 2, ui::add, states::add)) {
            session.load(); finish(session, ui); assertFalse(last(states).editable());
            assertThrows(IllegalStateException.class, () -> session.replace(List.of()));
            assertTrue(Files.readString(store.sidecar(source)).contains("99"));
            Files.delete(store.sidecar(source)); session.load(); finish(session, ui);
            session.replace(List.of(marker("x".repeat(513)))); finish(session, ui);
            assertFalse(last(states).editable()); assertTrue(last(states).markers().isEmpty()); assertFalse(Files.exists(store.sidecar(source)));
        }
    }
    @Test void loadingIsOffUiAndCloseInterruptsWithoutLatePublication() throws Exception {
        File source = source(); var ui = new ConcurrentLinkedQueue<Runnable>(); var states = new ArrayList<FxLogMarkerSession.State>();
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1); Thread caller = Thread.currentThread();
        var persistence = new FxLogMarkerSession.Persistence() {
            public LogMarkerStore.Snapshot load(File file, int samples) throws IOException {
                assertNotSame(caller, Thread.currentThread()); started.countDown();
                try { new CountDownLatch(1).await(); } catch (InterruptedException expected) { interrupted.countDown(); throw new InterruptedIOException(); }
                return store.loadSnapshot(file, samples);
            }
            public LogMarkerStore.Snapshot save(LogMarkerStore.Snapshot expected, List<LogMarker> next) { throw new AssertionError("Unexpected save"); }
        };
        try (var session = new FxLogMarkerSession(source, 2, persistence, ui::add, states::add)) {
            session.load(); assertTrue(started.await(5, TimeUnit.SECONDS)); session.close();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS)); assertTrue(session.pending().isCancelled()); drain(ui);
            assertEquals(1, states.size()); session.load(); assertEquals(1, states.size());
        }
    }
    @Test void closingLetsAnAcceptedSaveFinishButSuppressesClosedViewCallbacks() throws Exception {
        File source = source(); var ui = new ConcurrentLinkedQueue<Runnable>(); var states = new ArrayList<FxLogMarkerSession.State>();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        var persistence = new FxLogMarkerSession.Persistence() {
            public LogMarkerStore.Snapshot load(File file, int samples) throws IOException { return store.loadSnapshot(file, samples); }
            public LogMarkerStore.Snapshot save(LogMarkerStore.Snapshot expected, List<LogMarker> next) throws IOException {
                started.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test gate timed out"); }
                catch (InterruptedException cancelled) { throw new IOException("Accepted save was cancelled", cancelled); }
                return store.saveIfUnchanged(expected, next);
            }
        };
        try (var session = new FxLogMarkerSession(source, 2, persistence, ui::add, states::add)) {
            session.load(); finish(session, ui); session.replace(List.of(marker("accepted")));
            assertTrue(started.await(5, TimeUnit.SECONDS)); session.close(); int published = states.size();
            release.countDown(); finish(session, ui); assertEquals(published, states.size());
            assertEquals("accepted", store.load(source, 2).get(0).getLabel()); assertFalse(session.pending().isCancelled());
        } finally { release.countDown(); }
    }
    @Test void alreadyQueuedLoadCannotPublishAfterCloseAndSessionOnlyMarkersNeverNeedAFile() throws Exception {
        File source = source(); var ui = new ConcurrentLinkedQueue<Runnable>(); var states = new ArrayList<FxLogMarkerSession.State>();
        try (var session = new FxLogMarkerSession(source, 2, ui::add, states::add)) {
            session.load(); session.pending().get(5, TimeUnit.SECONDS); session.close(); drain(ui); assertEquals(1, states.size());
        }
        states.clear();
        try (var session = new FxLogMarkerSession(null, 2, ui::add, states::add)) {
            session.load(); assertTrue(last(states).editable()); session.replace(List.of(marker("session"))); finish(session, ui);
            assertEquals("session", last(states).markers().get(0).getLabel()); assertTrue(last(states).message().contains("no file written"));
            assertFalse(Files.exists(store.sidecar(source)));
        }
    }
}
