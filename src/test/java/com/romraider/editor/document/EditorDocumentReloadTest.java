/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.document;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.romraider.Settings;
import com.romraider.editor.io.*;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.*;
import com.romraider.swing.JProgressPane;

public class EditorDocumentReloadTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] DISK = new byte[] {'T', 'E', 'S', 'T', 10};
    private static final RomLoadInteraction INTERACTION = new RomLoadInteraction() {
        public void update(String message, int progress) { }
        public void missingDefinition(File file) { }
        public void definitionLoadFailed(File file, String message, Throwable failure) {
            throw new IllegalStateException(message, failure);
        }
        public File chooseDefinition(File image) { return null; }
        public boolean confirmForceLoad(File definition) { return false; }
    };

    private EditorDocumentController controller(boolean definitions) throws Exception {
        Settings settings = new Settings();
        if (definitions) {
            File definition = temporary.newFile("synthetic.xml");
            Files.write(definition.toPath(), ("<roms><rom><romid><xmlid>TEST</xmlid>"
                    + "<internalidaddress>0</internalidaddress><internalidstring>TEST</internalidstring>"
                    + "<filesize>5</filesize></romid></rom></roms>").getBytes(StandardCharsets.UTF_8));
            settings.getEcuDefinitionFiles().add(definition);
        }
        return new EditorDocumentController(new EditorDocumentSession(),
                new RomLoadService(settings), new RomFileService());
    }

    @Test public void definitionListReplacementIsSeenByAnExistingLoader() throws Exception {
        Settings settings = new Settings();
        RomLoadService loader = new RomLoadService(settings);
        File source = temporary.newFile("late-definition.bin");
        Files.write(source.toPath(), DISK);
        File definition = temporary.newFile("late-definition.xml");
        Files.writeString(definition.toPath(), "<roms><rom><romid><xmlid>TEST</xmlid>"
                + "<internalidaddress>0</internalidaddress><internalidstring>TEST</internalidstring>"
                + "<filesize>5</filesize></romid></rom></roms>");
        settings.setEcuDefinitionFiles(new java.util.Vector<>(java.util.List.of(definition)));
        assertTrue(loader.load(source, INTERACTION).isLoaded());
        settings.setEcuDefinitionFiles(new java.util.Vector<>());
        assertFalse(loader.load(source, INTERACTION).isLoaded());
    }

    private Rom openSynthetic(EditorDocumentController controller) throws Exception {
        File source = temporary.newFile("synthetic.bin");
        Files.write(source.toPath(), DISK);
        Rom rom = new Rom(new RomID());
        rom.populateTables(DISK.clone(), new JProgressPane());
        rom.setFullFileName(source);
        RomChangeService.rememberSavedBinary(rom);
        rom.getBinary()[4] = 20;
        controller.getSession().openRom(rom);
        return rom;
    }

    @Test public void loadsBeforeDiscardAndNeverWritesTheSavedFile() throws Exception {
        EditorDocumentController controller = controller(true);
        Rom old = openSynthetic(controller);
        File source = old.getFullFileName();
        BlockingQueue<Runnable> completion = new LinkedBlockingQueue<Runnable>();
        try {
            CompletableFuture<RomLoadResult> reload = controller.reload(old, INTERACTION, completion::add);
            Runnable publish = completion.poll(10, TimeUnit.SECONDS);
            if (publish == null) reload.get(1, TimeUnit.SECONDS);
            assertNotNull(publish);
            assertTrue(controller.isBusy(old));
            assertTrue(controller.hasPendingOperations());
            assertSame(old, controller.getSession().snapshot().getActiveRom());
            assertEquals(20, old.getBinary()[4]);
            assertTrue(controller.save(old, source).isCompletedExceptionally());
            assertTrue(controller.reload(old, INTERACTION, Runnable::run).isCompletedExceptionally());
            try { controller.closeRom(old); fail("Close allowed while reloading"); }
            catch (IllegalStateException expected) { }
            try { controller.close(); fail("Exit allowed while reloading"); }
            catch (IllegalStateException expected) { }
            publish.run();
            RomLoadResult loaded = reload.get(10, TimeUnit.SECONDS);
            assertTrue(loaded.isLoaded());
            assertNotSame(old, loaded.getRom());
            assertSame(loaded.getRom(), controller.getSession().snapshot().getActiveRom());
            assertEquals(1, controller.getSession().snapshot().getDocuments().size());
            assertArrayEquals(DISK, loaded.getRom().getBinary());
            assertArrayEquals(DISK, Files.readAllBytes(source.toPath()));
            assertFalse(controller.hasPendingOperations());
            assertFalse(controller.getSession().snapshot().getActiveDocument().isDirty());
        } finally { controller.close(); }
    }

    @Test public void newerEditsPreventReplacementAndRemainDirty() throws Exception {
        EditorDocumentController controller = controller(true);
        Rom old = openSynthetic(controller);
        BlockingQueue<Runnable> completion = new LinkedBlockingQueue<Runnable>();
        try {
            CompletableFuture<RomLoadResult> reload = controller.reload(old, INTERACTION, completion::add);
            Runnable publish = completion.poll(10, TimeUnit.SECONDS);
            if (publish == null) reload.get(1, TimeUnit.SECONDS);
            assertNotNull(publish);
            old.getBinary()[4] = 30;
            publish.run();
            try { reload.get(10, TimeUnit.SECONDS); fail("Newer edits were discarded"); }
            catch (ExecutionException expected) { assertTrue(expected.getCause().getMessage().contains("edits were kept")); }
            assertSame(old, controller.getSession().snapshot().getActiveRom());
            assertEquals(30, old.getBinary()[4]);
            assertTrue(controller.getSession().snapshot().getActiveDocument().isDirty());
            assertFalse(controller.hasPendingOperations());
        } finally { controller.close(); }
    }

    @Test public void unmatchedLoadAndMissingFileKeepOriginalDocument() throws Exception {
        EditorDocumentController controller = controller(false);
        Rom old = openSynthetic(controller);
        try {
            assertFalse(controller.reload(old, INTERACTION, Runnable::run).get(10, TimeUnit.SECONDS).isLoaded());
            assertSame(old, controller.getSession().snapshot().getActiveRom());
            old.setFullFileName(new File(temporary.getRoot(), "missing.bin"));
            assertTrue(controller.reload(old, INTERACTION, Runnable::run).isCompletedExceptionally());
            assertEquals(20, old.getBinary()[4]);
            assertFalse(controller.hasPendingOperations());
        } finally { controller.close(); }
    }

    @Test public void cancelledObserverDoesNotUnlockAnUnfinishedReload() throws Exception {
        EditorDocumentController controller = controller(true);
        Rom old = openSynthetic(controller);
        BlockingQueue<Runnable> completion = new LinkedBlockingQueue<Runnable>();
        try {
            CompletableFuture<RomLoadResult> observer = controller.reload(old, INTERACTION, completion::add);
            observer.cancel(true);
            assertTrue(controller.hasPendingOperations());
            Runnable publish = completion.poll(10, TimeUnit.SECONDS);
            assertNotNull(publish); publish.run();
            assertFalse(controller.hasPendingOperations());
            assertNotSame(old, controller.getSession().snapshot().getActiveRom());
        } finally { controller.close(); }
    }
}
