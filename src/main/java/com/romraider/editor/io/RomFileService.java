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
    public void save(Rom rom, File target) throws Exception {
        if (rom == null || target == null) {
            throw new IllegalArgumentException("ROM and target are required");
        }
        byte[] output = rom.saveFile();
        write(target, output);
        rom.setFullFileName(target.getAbsoluteFile());
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null) SettingsManager.getSettings().setLastImageDir(parent);
        RomChangeService.markSaved(rom);
        RomRecoveryService.getInstance().markResolved(rom);
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
