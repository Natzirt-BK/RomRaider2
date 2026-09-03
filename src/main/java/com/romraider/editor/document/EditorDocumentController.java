/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.romraider.editor.io.RomFileService;
import com.romraider.editor.io.RomLoadInteraction;
import com.romraider.editor.io.RomLoadResult;
import com.romraider.editor.io.RomLoadService;
import com.romraider.editor.recovery.RomRecoveryService;
import com.romraider.editor.recovery.RecoverySnapshot;
import com.romraider.editor.workspace.EditorWorkspaceService;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.maps.history.EditHistoryListener;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.RomPlatformResolver;
import com.romraider.platform.VehicleModule;

/**
 * UI-neutral application controller for Editor documents.
 *
 * It is the ownership boundary used by the Compose desktop shell. Views never
 * need an ECUEditor/JFrame instance to open, activate, save, or close a ROM.
 */
public final class EditorDocumentController implements AutoCloseable {
    private final EditorDocumentSession session;
    private final RomLoadService loader;
    private final RomFileService files;
    private final ExecutorService work;
    private final EditHistoryListener recoveryHistoryListener;
    private boolean closed;

    public EditorDocumentController() {
        this(new EditorDocumentSession(), new RomLoadService(),
                new RomFileService());
    }

    EditorDocumentController(EditorDocumentSession session,
            RomLoadService loader, RomFileService files) {
        if (session == null || loader == null || files == null) {
            throw new IllegalArgumentException(
                    "Session, loader and file service are required");
        }
        this.session = session;
        this.loader = loader;
        this.files = files;
        this.recoveryHistoryListener = rom -> {
            if (owns(rom)) RomRecoveryService.getInstance().schedule(rom);
        };
        RomEditHistory.getInstance().addListener(recoveryHistoryListener);
        this.work = Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "ROM document worker");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public EditorDocumentSession getSession() { return session; }

    public CompletableFuture<RomLoadResult> open(File image,
            RomLoadInteraction interaction) {
        requireOpen();
        return CompletableFuture.supplyAsync(() -> {
            try {
                RomLoadResult result = loader.load(image, interaction);
                if (result.isLoaded()) {
                    registerLoadedRom(result.getRom());
                }
                return result;
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, work);
    }

    public List<RecoverySnapshot> discoverRecoverySnapshots()
            throws IOException {
        requireOpen();
        return RomRecoveryService.getInstance().discoverLatestSnapshots();
    }

    public CompletableFuture<RomLoadResult> openRecovered(
            RecoverySnapshot snapshot, RomLoadInteraction interaction) {
        requireOpen();
        if (snapshot == null) {
            throw new IllegalArgumentException("Recovery snapshot is required");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                RomLoadResult result = loader.load(
                        snapshot.getBinaryPath().toFile(), interaction);
                if (result.isLoaded()) {
                    Rom rom = result.getRom();
                    rom.setFullFileName(null);
                    rom.setFileName("Recovered - " + snapshot.getSourceName());
                    registerLoadedRom(rom);
                    RomChangeService.markUnsaved(rom);
                    RomRecoveryService.getInstance().discardAll(snapshot);
                    RomRecoveryService.getInstance().schedule(rom);
                }
                return result;
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, work);
    }

    public void discardRecovery(RecoverySnapshot snapshot) throws IOException {
        requireOpen();
        RomRecoveryService.getInstance().discardAll(snapshot);
    }

    private void registerLoadedRom(Rom rom) {
        RomChangeService.rememberSavedBinary(rom);
        session.openRom(rom);
        session.activateRom(rom);
        applyPlatformContext(rom);
        EditorWorkspaceService.getInstance().indexRom(rom);
    }

    public void activateRom(Rom rom) {
        requireOpen();
        session.activateRom(rom);
        applyPlatformContext(rom);
    }

    public void openTable(Rom rom, Table table) {
        requireOpen();
        session.openTable(rom, table);
        applyPlatformContext(rom);
        EditorWorkspaceService.getInstance().tableOpened(rom, table);
    }

    public void activateTable(Rom rom, Table table) {
        requireOpen();
        session.activateTable(rom, table);
        applyPlatformContext(rom);
        EditorWorkspaceService.getInstance().tableActivated(rom, table);
    }

    public void closeTable(Rom rom, Table table) {
        requireOpen();
        session.closeTable(rom, table);
        EditorWorkspaceService.getInstance().tableClosed(rom, table);
    }

    public CompletableFuture<File> save(Rom rom, File target) {
        requireOpen();
        return CompletableFuture.supplyAsync(() -> {
            try {
                files.save(rom, target);
                return target;
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, work);
    }

    public void closeRom(Rom rom) {
        if (rom == null) return;
        requireOpen();
        EditorWorkspaceService.getInstance().removeRomFromIndex(rom);
        RomEditHistory.getInstance().clear(rom);
        RomChangeService.forget(rom);
        RomRecoveryService.getInstance().markResolved(rom);
        session.closeRom(rom);
        rom.clearData();
        applyPlatformContext(session.snapshot().getActiveRom());
    }

    private boolean owns(Rom rom) {
        if (rom == null) return false;
        for (EditorDocument document : session.snapshot().getDocuments()) {
            if (document.getRom() == rom) return true;
        }
        return false;
    }

    private static void applyPlatformContext(Rom rom) {
        if (rom == null) return;
        RomPlatformResolver.resolve(rom.getRomID()).ifPresent(platform -> {
            PlatformContext context = PlatformContext.getInstance();
            context.setPlatform(platform);
            context.setModule(VehicleModule.ENGINE_ECU);
        });
    }

    private synchronized void requireOpen() {
        if (closed) throw new IllegalStateException("Controller is closed");
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        RomEditHistory.getInstance().removeListener(recoveryHistoryListener);
        EditorDocumentSnapshot snapshot = session.snapshot();
        for (EditorDocument document :
                new ArrayList<EditorDocument>(snapshot.getDocuments())) {
            Rom rom = document.getRom();
            EditorWorkspaceService.getInstance().removeRomFromIndex(rom);
            RomEditHistory.getInstance().clear(rom);
            RomChangeService.forget(rom);
            RomRecoveryService.getInstance().markResolved(rom);
            rom.clearData();
        }
        session.close();
        work.shutdownNow();
    }
}
