/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** UI-free ROM byte document for desktop and mobile offline editing. */
public final class PortableRomDocument {
    public static final int MAX_ROM_BYTES = 16 * 1024 * 1024;

    private final String name;
    private byte[] saved;
    private byte[] current;

    public PortableRomDocument(String name, byte[] binary) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A document name is required");
        }
        if (binary == null || binary.length == 0
                || binary.length > MAX_ROM_BYTES) {
            throw new IllegalArgumentException(
                    "ROM data must be between 1 byte and "
                    + MAX_ROM_BYTES + " bytes");
        }
        this.name = name.trim();
        this.saved = binary.clone();
        this.current = binary.clone();
    }

    public static PortableRomDocument read(String name, InputStream input)
            throws IOException {
        if (input == null) throw new IllegalArgumentException(
                "A ROM input stream is required");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) >= 0) {
            total = Math.addExact(total, count);
            if (total > MAX_ROM_BYTES) {
                throw new IOException("ROM exceeds the portable size limit");
            }
            output.write(buffer, 0, count);
        }
        return new PortableRomDocument(name, output.toByteArray());
    }

    public static PortableRomDocument recover(String name, byte[] saved,
            byte[] current) {
        if (saved == null || current == null || saved.length != current.length) {
            throw new IllegalArgumentException(
                    "Saved and current ROM data must have the same length");
        }
        PortableRomDocument document = new PortableRomDocument(name, saved);
        document.current = current.clone();
        return document;
    }

    public String getName() { return name; }
    public synchronized int size() { return current.length; }
    public synchronized boolean hasChanges() {
        return !Arrays.equals(saved, current);
    }
    public synchronized byte byteAt(int offset) {
        return current[checkedOffset(offset)];
    }
    public synchronized byte[] snapshot() { return current.clone(); }
    public synchronized byte[] savedSnapshot() { return saved.clone(); }

    public synchronized void replace(int offset, byte[] replacement) {
        if (replacement == null || replacement.length == 0) {
            throw new IllegalArgumentException("Replacement bytes are required");
        }
        if (offset < 0 || offset > current.length - replacement.length) {
            throw new IndexOutOfBoundsException(
                    "Replacement falls outside the ROM document");
        }
        System.arraycopy(replacement, 0, current, offset, replacement.length);
    }

    public synchronized void reset() {
        current = saved.clone();
    }

    public synchronized void markSaved() {
        saved = current.clone();
    }

    /** Marks only the exact bytes written by an asynchronous save as clean. */
    public synchronized boolean markSavedIfCurrent(byte[] written) {
        if (written == null || !Arrays.equals(current, written)) return false;
        saved = written.clone();
        return true;
    }

    public synchronized void write(OutputStream output) throws IOException {
        if (output == null) throw new IllegalArgumentException(
                "A ROM output stream is required");
        output.write(current);
    }

    public synchronized List<PortableByteChange> changes() {
        if (!hasChanges()) return Collections.emptyList();
        List<PortableByteChange> changes =
                new ArrayList<PortableByteChange>();
        int cursor = 0;
        while (cursor < current.length) {
            while (cursor < current.length
                    && saved[cursor] == current[cursor]) cursor++;
            if (cursor >= current.length) break;
            int start = cursor;
            while (cursor < current.length
                    && saved[cursor] != current[cursor]) cursor++;
            changes.add(new PortableByteChange(start,
                    Arrays.copyOfRange(saved, start, cursor),
                    Arrays.copyOfRange(current, start, cursor)));
        }
        return Collections.unmodifiableList(changes);
    }

    private int checkedOffset(int offset) {
        if (offset < 0 || offset >= current.length) {
            throw new IndexOutOfBoundsException("ROM offset: " + offset);
        }
        return offset;
    }
}
