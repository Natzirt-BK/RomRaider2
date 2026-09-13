package com.romraider.editor.document;

import static org.junit.Assert.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.romraider.editor.io.*;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.*;
import com.romraider.swing.JProgressPane;

public class EditorDocumentSaveLifecycleTest {
    private interface CheckedAction { void run() throws Exception; }

    private static void assertThrows(Class<? extends Exception> type, CheckedAction action) throws Exception {
        try { action.run(); fail("Expected " + type.getSimpleName()); }
        catch (Exception failure) { if (!type.isInstance(failure)) throw failure; }
    }

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void convertedExportKeepsSourceAndDocumentSavedState() throws Exception {
        Rom rom = new Rom(new RomID());
        rom.populateTables(new byte[com.romraider.Settings.SIXTEENBIT_SMALL_SIZE], new JProgressPane());
        File source = temporary.newFile("original.bin");
        Files.write(source.toPath(), rom.getBinary());
        rom.setFullFileName(source);
        RomChangeService.rememberSavedBinary(rom);
        rom.getBinary()[0] = 42;
        File converted = new File(temporary.getRoot(), "converted.bin");
        try (EditorDocumentController controller = new EditorDocumentController()) {
            controller.getSession().openRom(rom);
            controller.exportConverted(rom, converted, true).get(10, TimeUnit.SECONDS);
            assertSame(source, rom.getFullFileName());
            assertEquals(0, Files.readAllBytes(source.toPath())[0]);
            byte[] output = Files.readAllBytes(converted.toPath());
            assertEquals(com.romraider.Settings.SIXTEENBIT_LARGE_SIZE, output.length);
            assertEquals(42, output[0]);
            assertTrue(controller.getSession().snapshot().getActiveDocument().isDirty());
            assertThrows(ExecutionException.class, () -> controller.exportConverted(rom, source, true).get());
            assertEquals(0, Files.readAllBytes(source.toPath())[0]);
        }
    }

    @Test public void legacyChecksumPreparationRemainsOnWorkerThread() throws Exception {
        Thread caller = Thread.currentThread();
        java.util.concurrent.atomic.AtomicReference<Thread> preparationThread =
                new java.util.concurrent.atomic.AtomicReference<Thread>();
        Rom rom = new Rom(new RomID()) {
            @Override public byte[] saveFile() {
                preparationThread.set(Thread.currentThread());
                return super.saveFile();
            }
        };
        rom.populateTables(new byte[] {10}, new JProgressPane());
        EditorDocumentController controller = new EditorDocumentController();
        controller.getSession().openRom(rom);
        try {
            controller.save(rom, temporary.newFile("legacy.bin")).get(10, TimeUnit.SECONDS);
            assertNotSame(caller, preparationThread.get());
            assertEquals("ROM document worker", preparationThread.get().getName());
        } finally { controller.close(); }
    }

    @Test public void pendingSaveBlocksCloseAndKeepsLaterEditsAfterOwnerCompletion() throws Exception {
        BlockingQueue<Runnable> completion = new LinkedBlockingQueue<Runnable>();
        RomFileService files = new RomFileService();
        EditorDocumentController controller = new EditorDocumentController(
                new EditorDocumentSession(), new RomLoadService(), files);
        Rom rom = new Rom(new RomID());
        rom.populateTables(new byte[] {10}, new JProgressPane());
        RomChangeService.rememberSavedBinary(rom);
        controller.getSession().openRom(rom);
        File target = temporary.newFile("snapshot.bin");
        CompletableFuture<File> saved = controller.save(rom, target, completion::add);
        try {
            Runnable publish = completion.poll(10, TimeUnit.SECONDS);
            assertNotNull(publish);
            assertTrue(controller.isSaving(rom));
            assertThrows(IllegalStateException.class, () -> controller.closeRom(rom));
            assertThrows(IllegalStateException.class, controller::close);
            assertTrue(controller.save(rom, target).isCompletedExceptionally());
            rom.getBinary()[0] = 30;
            publish.run();
            assertEquals(target, saved.get(10, TimeUnit.SECONDS));
            assertFalse(controller.hasPendingSaves());
            assertArrayEquals(new byte[] {10}, Files.readAllBytes(target.toPath()));
            assertTrue(controller.getSession().snapshot().getActiveDocument().isDirty());
            assertEquals(target.getName(), controller.getSession().snapshot().getActiveDocument().getName());
        } finally { controller.close(); }
    }

    @Test public void cancelledObserverCannotUnlockOrStrandSaveCleanup() throws Exception {
        BlockingQueue<Runnable> completion = new LinkedBlockingQueue<Runnable>();
        EditorDocumentController controller = new EditorDocumentController();
        Rom rom = new Rom(new RomID());
        rom.populateTables(new byte[] {10}, new JProgressPane());
        controller.getSession().openRom(rom);
        CompletableFuture<File> saved = controller.save(rom, temporary.newFile("cancel.bin"), completion::add);
        try {
            saved.cancel(true);
            assertTrue(controller.hasPendingSaves());
            Runnable publish = completion.poll(10, TimeUnit.SECONDS);
            assertNotNull(publish); publish.run();
            assertFalse(controller.hasPendingSaves());
        } finally { controller.close(); }
    }

    @Test public void writeFailureUnlocksDocumentAndDoesNotMarkItSaved() throws Exception {
        EditorDocumentController controller = new EditorDocumentController(
                new EditorDocumentSession(), new RomLoadService(),
                new RomFileService((target, bytes) -> { throw new IOException("Synthetic failure"); }));
        Rom rom = new Rom(new RomID());
        rom.populateTables(new byte[] {10}, new JProgressPane());
        RomChangeService.rememberSavedBinary(rom);
        rom.getBinary()[0] = 20;
        controller.getSession().openRom(rom);
        try {
            CompletableFuture<File> saved = controller.save(rom, temporary.newFile("failure.bin"));
            assertThrows(ExecutionException.class, () -> saved.get(10, TimeUnit.SECONDS));
            assertFalse(controller.hasPendingSaves());
            assertTrue(controller.getSession().snapshot().getActiveDocument().isDirty());
        } finally { controller.close(); }
    }
}
