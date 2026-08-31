/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.recovery;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.Test;

import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.swing.JProgressPane;

public class RomRecoveryServiceTest {
    @Test
    public void snapshotPreservesACloneWithoutTouchingOriginalRomFile()
            throws Exception {
        Path temporary = Files.createTempDirectory("romraider2-recovery-test");
        RomRecoveryService service = new RomRecoveryService(
                temporary.resolve("recovery"), 5, 0L);
        try {
            Path original = temporary.resolve("source.bin");
            Files.write(original, new byte[] {9, 9, 9});
            Rom rom = rom(original, new byte[] {1, 2, 3});

            RecoverySnapshot snapshot = service.snapshotNow(rom);
            rom.getBinary()[0] = 8;

            assertArrayEquals(new byte[] {1, 2, 3},
                    Files.readAllBytes(snapshot.getBinaryPath()));
            assertArrayEquals(new byte[] {9, 9, 9},
                    Files.readAllBytes(original));
            Properties metadata = new Properties();
            try (InputStream input = Files.newInputStream(
                    snapshot.getMetadataPath())) {
                metadata.load(input);
            }
            assertEquals("source.bin", metadata.getProperty("source.name"));
            assertEquals("3", metadata.getProperty("binary.size"));
            assertEquals(64, metadata.getProperty("binary.sha256").length());
            assertEquals(original.toAbsolutePath().toString(),
                    snapshot.getSourcePath());
            assertEquals(RecoveryState.SAVED, service.getState(rom));
        } finally {
            service.shutdown();
            deleteTree(temporary);
        }
    }

    @Test
    public void boundedHistoryUsesStableIdentityAndClearsAfterSaveAs()
            throws Exception {
        Path temporary = Files.createTempDirectory("romraider2-recovery-test");
        RomRecoveryService service = new RomRecoveryService(
                temporary.resolve("recovery"), 2, 0L);
        try {
            Rom rom = rom(temporary.resolve("original.bin"), new byte[] {1});
            for (int value = 1; value <= 12; value++) {
                rom.getBinary()[0] = (byte) value;
                service.snapshotNow(rom);
            }
            List<RecoverySnapshot> snapshots = service.listSnapshots(rom);
            assertEquals(2, snapshots.size());
            assertArrayEquals(new byte[] {12},
                    Files.readAllBytes(snapshots.get(0).getBinaryPath()));

            rom.setFullFileName(new File(temporary.toFile(), "saved-as.bin"));
            service.markResolved(rom);
            assertEquals(RecoveryState.IDLE, service.getState(rom));
            assertEquals(0, regularFileCount(service.getRoot()));
        } finally {
            service.shutdown();
            deleteTree(temporary);
        }
    }

    @Test
    public void changedRomIsDebouncedAndReportsSavedState() throws Exception {
        Path temporary = Files.createTempDirectory("romraider2-recovery-test");
        RomRecoveryService service = new RomRecoveryService(
                temporary.resolve("recovery"), 3, 20L);
        try {
            Rom rom = rom(temporary.resolve("source.bin"), new byte[] {1, 2});
            RomChangeService.rememberSavedBinary(rom);
            rom.getBinary()[1] = 7;
            CountDownLatch saved = new CountDownLatch(1);
            service.addListener((changedRom, state, snapshot, failure) -> {
                if (changedRom == rom && state == RecoveryState.SAVED) {
                    saved.countDown();
                }
            });

            service.schedule(rom);
            service.schedule(rom);

            assertEquals(RecoveryState.SCHEDULED, service.getState(rom));
            assertTrue(saved.await(3, TimeUnit.SECONDS));
            assertEquals(RecoveryState.SAVED, service.getState(rom));
            assertEquals(1, service.listSnapshots(rom).size());
            RomChangeService.forget(rom);
        } finally {
            service.shutdown();
            deleteTree(temporary);
        }
    }

    @Test
    public void corruptedSnapshotIsNotOfferedAsRecoverable() throws Exception {
        Path temporary = Files.createTempDirectory("romraider2-recovery-test");
        RomRecoveryService service = new RomRecoveryService(
                temporary.resolve("recovery"), 3, 0L);
        try {
            Rom rom = rom(temporary.resolve("source.bin"), new byte[] {4, 5});
            RecoverySnapshot snapshot = service.snapshotNow(rom);
            Files.write(snapshot.getBinaryPath(), new byte[] {4, 6});

            assertTrue(service.listSnapshots(rom).isEmpty());
        } finally {
            service.shutdown();
            deleteTree(temporary);
        }
    }

    @Test
    public void startupDiscoveryReturnsNewestValidSnapshotPerRom()
            throws Exception {
        Path temporary = Files.createTempDirectory("romraider2-recovery-test");
        RomRecoveryService service = new RomRecoveryService(
                temporary.resolve("recovery"), 5, 0L);
        try {
            Rom first = rom(temporary.resolve("first.bin"), new byte[] {1});
            RecoverySnapshot older = service.snapshotNow(first);
            first.getBinary()[0] = 2;
            RecoverySnapshot newer = service.snapshotNow(first);
            Rom second = rom(temporary.resolve("second.bin"), new byte[] {3});
            RecoverySnapshot other = service.snapshotNow(second);

            assertEquals(3, service.discoverSnapshots().size());
            List<RecoverySnapshot> latest = service.discoverLatestSnapshots();
            assertEquals(2, latest.size());
            assertTrue(containsPath(latest, newer.getBinaryPath()));
            assertTrue(containsPath(latest, other.getBinaryPath()));
            assertFalse(containsPath(latest, older.getBinaryPath()));

            service.discardAll(newer);
            assertEquals(1, service.discoverSnapshots().size());
            assertEquals("second.bin",
                    service.discoverLatestSnapshots().get(0).getSourceName());
        } finally {
            service.shutdown();
            deleteTree(temporary);
        }
    }

    private static boolean containsPath(List<RecoverySnapshot> snapshots,
            Path path) {
        for (RecoverySnapshot snapshot : snapshots) {
            if (snapshot.getBinaryPath().equals(path)) return true;
        }
        return false;
    }

    private static Rom rom(Path path, byte[] binary) {
        Rom rom = new Rom(new RomID());
        rom.setFullFileName(path.toFile());
        rom.populateTables(binary, new JProgressPane());
        return rom;
    }

    private static long regularFileCount(Path root) throws Exception {
        if (!Files.exists(root)) return 0L;
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
        }
        assertFalse(Files.exists(root));
    }
}
