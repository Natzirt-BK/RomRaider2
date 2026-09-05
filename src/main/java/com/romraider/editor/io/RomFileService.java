/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.romraider.editor.recovery.RomRecoveryService;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.Rom;
import com.romraider.util.SettingsManager;

/** Toolkit-neutral persistence operations for an open ROM document. */
public final class RomFileService {
    @FunctionalInterface
    public interface OutputWriter {
        void write(File target, byte[] output) throws IOException;
    }

    private final OutputWriter writer;

    public RomFileService() { this(RomFileService::write); }

    public RomFileService(OutputWriter writer) {
        this.writer = java.util.Objects.requireNonNull(writer);
    }

    /** Synchronous convenience for callers already owning the document thread. */
    public void save(Rom rom, File target) throws Exception {
        PreparedSave prepared = prepare(rom, target);
        writePrepared(prepared);
        complete(prepared);
    }

    /** Capture checksums, immutable output, and revert points before async I/O. */
    public PreparedSave prepare(Rom rom, File target) {
        if (rom == null || target == null) {
            throw new IllegalArgumentException("ROM and target are required");
        }
        byte[] output = rom.saveFile().clone();
        return new PreparedSave(rom, target.getAbsoluteFile(), output,
                RomChangeService.captureSavedState(rom, output));
    }

    /** Only this phase runs on the background I/O worker. */
    public void writePrepared(PreparedSave prepared) throws IOException {
        writer.write(prepared.target, prepared.output.clone());
        prepared.written = true;
    }

    /** Publish only the state that actually reached disk, on the owner thread. */
    public void complete(PreparedSave prepared) {
        if (!prepared.written) throw new IllegalStateException("Save did not complete");
        Rom rom = prepared.rom;
        File target = prepared.target;
        rom.setFullFileName(target.getAbsoluteFile());
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null) SettingsManager.getSettings().setLastImageDir(parent);
        RomChangeService.markSaved(rom, prepared.savedState);
        // schedule() resolves a clean document and retains recovery for later edits.
        RomRecoveryService.getInstance().schedule(rom);
    }

    public static final class PreparedSave {
        private final Rom rom;
        private final File target;
        private final byte[] output;
        private final RomChangeService.SavedState savedState;
        private volatile boolean written;

        private PreparedSave(Rom rom, File target, byte[] output,
                RomChangeService.SavedState savedState) {
            this.rom = rom;
            this.target = target;
            this.output = output;
            this.savedState = savedState;
        }
    }

    private static void write(File target, byte[] output) throws IOException {
        File absolute = target.getAbsoluteFile();
        File parent = absolute.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("ROM destination folder is unavailable: "
                    + parent);
        }
        Path temporary = Files.createTempFile(parent.toPath(),
                "." + absolute.getName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileOutputStream stream = new FileOutputStream(
                    temporary.toFile())) {
                stream.write(output);
                stream.getFD().sync();
            }
            try {
                Files.move(temporary, absolute.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }
}
