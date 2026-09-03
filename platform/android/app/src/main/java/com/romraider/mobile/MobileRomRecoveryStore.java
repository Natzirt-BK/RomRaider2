/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import com.romraider.portable.PortableRomDocument;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** App-private crash recovery for one unsaved Android ROM workspace. */
final class MobileRomRecoveryStore {
    private static final int MAGIC = 0x52523252;
    private static final int VERSION = 1;
    private static final String FILE_NAME = "unsaved-rom.workspace";

    private MobileRomRecoveryStore() { }

    static void save(File directory, PortableRomDocument document)
            throws IOException {
        if (directory == null) throw new IOException(
                "Recovery directory is unavailable");
        File target = new File(directory, FILE_NAME);
        if (document == null || !document.hasChanges()) {
            Files.deleteIfExists(target.toPath());
            return;
        }
        byte[] saved = document.savedSnapshot();
        byte[] current = document.snapshot();
        File temporary = File.createTempFile("unsaved-rom-", ".tmp", directory);
        boolean moved = false;
        try {
            try (FileOutputStream file = new FileOutputStream(temporary);
                 DataOutputStream output = new DataOutputStream(
                         new BufferedOutputStream(file))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeUTF(document.getName());
                output.writeInt(saved.length);
                output.write(saved);
                output.write(current);
                output.flush();
                file.getFD().sync();
            }
            try {
                Files.move(temporary.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary.toPath());
        }
    }

    static PortableRomDocument restore(File directory) throws IOException {
        File source = new File(directory, FILE_NAME);
        if (!source.isFile()) return null;
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new FileInputStream(source)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported Android recovery file");
            }
            String name = input.readUTF();
            int length = input.readInt();
            if (length < 1 || length > PortableRomDocument.MAX_ROM_BYTES) {
                throw new IOException("Android recovery ROM size is invalid");
            }
            byte[] saved = new byte[length];
            byte[] current = new byte[length];
            input.readFully(saved);
            input.readFully(current);
            if (input.read() != -1) {
                throw new IOException("Android recovery file has extra data");
            }
            return PortableRomDocument.recover(name, saved, current);
        }
    }
}
