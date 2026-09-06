/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class BoundedLogMarkerStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final LogMarkerStore store = new LogMarkerStore();
    private File log() throws Exception { File file = temporary.newFile(); Files.writeString(file.toPath(), "Value\n1\n2\n"); return file; }
    private LogMarker marker(String label) { return new LogMarker(1, LogMarkerType.CUSTOM, label); }
    @FunctionalInterface interface Operation { void run() throws Exception; }
    private void rejects(Operation operation) throws Exception { try { operation.run(); fail("Expected marker rejection"); } catch (IOException expected) { } }

    @Test public void unicodeSnapshotRoundTripAndEmptySaveLeaveCsvAndUnrelatedTempUntouched() throws Exception {
        File log = log(); byte[] csv = Files.readAllBytes(log.toPath());
        Path unrelated = store.sidecar(log).resolveSibling(store.sidecar(log).getFileName() + ".tmp"); Files.writeString(unrelated, "unrelated");
        var original = store.loadSnapshot(log, 2);
        var saved = store.saveIfUnchanged(original, List.of(marker("日本語 • λ 🚗 = : \\")));
        assertEquals("日本語 • λ 🚗 = : \\" , store.load(log, 2).get(0).getLabel());
        try { saved.getMarkers().clear(); fail("Mutable snapshot"); } catch (UnsupportedOperationException expected) { }
        store.saveIfUnchanged(saved, List.of()); assertTrue(store.load(log, 2).isEmpty()); assertTrue(Files.isRegularFile(store.sidecar(log)));
        assertArrayEquals(csv, Files.readAllBytes(log.toPath())); assertEquals("unrelated", Files.readString(unrelated));
        try (var files = Files.list(temporary.getRoot().toPath())) { assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".rr2-markers-"))); }
    }
    @Test public void changedCreatedOrDeletedSidecarsRejectStaleSave() throws Exception {
        File log = log(); var absent = store.loadSnapshot(log, 2);
        store.save(log, List.of(marker("external"))); byte[] external = Files.readAllBytes(store.sidecar(log));
        rejects(() -> store.saveIfUnchanged(absent, List.of(marker("stale")))); assertArrayEquals(external, Files.readAllBytes(store.sidecar(log)));
        var deleted = store.loadSnapshot(log, 2); Files.delete(store.sidecar(log));
        rejects(() -> store.saveIfUnchanged(deleted, List.of())); assertFalse(Files.exists(store.sidecar(log)));
        store.save(log, List.of(marker("original"))); var snapshot = store.loadSnapshot(log, 2);
        store.save(log, List.of(marker("new external")));
        rejects(() -> store.saveIfUnchanged(snapshot, List.of(marker("stale")))); assertEquals("new external", store.load(log, 2).get(0).getLabel());
    }
    @Test public void malformedUnknownDuplicateAndIncompleteDocumentsCannotBeLoadedOrOverwritten() throws Exception {
        File log = log();
        for (String text : List.of("", "format.version=2\nmarker.count=0\n", "format.version=1\nmarker.count=2147483647\n",
                "format.version=1\nmarker.count=0\nmarker.count=0\n", "format.version=1\nmarker.count=0\nunknown=x\n",
                "format.version=1\nmarker.count=1\nmarker.0.sample=0\nmarker.0.type=CUSTOM\nmarker.0.label=\\uXX00\n",
                "format.version=1\nmarker.count=1\nmarker.0.sample=0\nmarker.0.type=UNKNOWN\nmarker.0.label=x\n")) {
            Files.writeString(store.sidecar(log), text); byte[] before = Files.readAllBytes(store.sidecar(log));
            rejects(() -> store.load(log, 2)); rejects(() -> store.save(log, List.of(marker("replacement"))));
            assertArrayEquals(before, Files.readAllBytes(store.sidecar(log)));
        }
    }
    @Test public void markerLabelCountByteAndSampleBoundsRejectWithoutReplacingExistingFile() throws Exception {
        File log = log(); store.save(log, List.of(marker("keep"))); var snapshot = store.loadSnapshot(log, 2);
        byte[] before = Files.readAllBytes(store.sidecar(log));
        rejects(() -> store.saveIfUnchanged(snapshot, Collections.nCopies(4097, marker("x"))));
        rejects(() -> store.saveIfUnchanged(snapshot, List.of(marker("x".repeat(513)))));
        rejects(() -> store.saveIfUnchanged(snapshot, Collections.nCopies(1024, marker("日".repeat(512)))));
        rejects(() -> store.saveIfUnchanged(snapshot, List.of(new LogMarker(2, LogMarkerType.CUSTOM, "outside"))));
        rejects(() -> store.saveIfUnchanged(snapshot, List.of(marker("\ud800"))));
        assertArrayEquals(before, Files.readAllBytes(store.sidecar(log)));
        Files.write(store.sidecar(log), new byte[LogMarkerStore.MAX_BYTES + 1]); rejects(() -> store.load(log, 2));
    }
    @Test public void exactCountAndLabelBoundsRoundTrip() throws Exception {
        File log = log(); store.save(log, Collections.nCopies(4096, marker("x"))); assertEquals(4096, store.load(log, 2).size());
        store.save(log, List.of(marker("x".repeat(512)))); assertEquals(512, store.load(log, 2).get(0).getLabel().length());
    }
    @Test public void untrustedScalarAndDuplicateKeyErrorsCannotFloodTheUiWithFileContents() throws Exception {
        File log = log(); String huge = "x".repeat(100_000);
        for (String text : List.of("format.version=1\nmarker.count=" + huge + "\n",
                "format.version=1\nmarker.count=1\nmarker.0.sample=0\nmarker.0.type=" + huge + "\nmarker.0.label=x\n",
                huge + "=1\n" + huge + "=2\n")) {
            Files.writeString(store.sidecar(log), text);
            try { store.load(log, 2); fail("Expected rejection"); }
            catch (IOException failure) {
                Throwable root = failure; while (root.getCause() != null) root = root.getCause();
                assertTrue(root.getMessage().length() < 200);
            }
        }
    }
    @Test public void simultaneousCooperatingWritersCannotBothReplaceTheSameSnapshot() throws Exception {
        File log = log(); var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 10; attempt++) {
                store.save(log, List.of(marker("base"))); var snapshot = store.loadSnapshot(log, 2);
                var start = new java.util.concurrent.CountDownLatch(1);
                List<java.util.concurrent.Future<Boolean>> writes = new ArrayList<>();
                for (String name : List.of("first", "second")) writes.add(workers.submit(() -> {
                    start.await();
                    try { new LogMarkerStore().saveIfUnchanged(snapshot, List.of(marker(name))); return true; }
                    catch (IOException conflict) { assertTrue(conflict.getMessage().contains("changed outside")); return false; }
                }));
                start.countDown(); int successes = 0;
                for (var write : writes) if (write.get(5, java.util.concurrent.TimeUnit.SECONDS)) successes++;
                assertEquals(1, successes); assertTrue(List.of("first", "second").contains(store.load(log, 2).get(0).getLabel()));
            }
        } finally { workers.shutdownNow(); }
    }
    @Test public void directoriesAndSymlinksAreNotMarkerDestinations() throws Exception {
        File log = log(); Files.createDirectory(store.sidecar(log)); rejects(() -> store.load(log, 2)); rejects(() -> store.save(log, List.of()));
        Files.delete(store.sidecar(log));
        Assume.assumeTrue(File.separatorChar == '/'); Path target = temporary.newFile().toPath(); Files.writeString(target, "protected");
        Files.createSymbolicLink(store.sidecar(log), target); rejects(() -> store.load(log, 2)); rejects(() -> store.save(log, List.of()));
        assertEquals("protected", Files.readString(target));
    }
    @Test public void interruptedReadsAndWritesDoNotReplaceExistingFile() throws Exception {
        File log = log(); store.save(log, List.of(marker("keep"))); var snapshot = store.loadSnapshot(log, 2); byte[] before = Files.readAllBytes(store.sidecar(log));
        try { Thread.currentThread().interrupt(); rejects(() -> store.load(log, 2)); rejects(() -> store.saveIfUnchanged(snapshot, List.of())); }
        finally { Thread.interrupted(); }
        assertArrayEquals(before, Files.readAllBytes(store.sidecar(log)));
    }
}
