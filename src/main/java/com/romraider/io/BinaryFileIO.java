/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Small toolkit-neutral binary file reader shared by ROM import paths. */
public final class BinaryFileIO {
    private BinaryFileIO() { }

    public static byte[] read(File file) throws IOException {
        return read(file, Integer.MAX_VALUE);
    }

    public static byte[] read(File file, long maximumBytes) throws IOException {
        if (file == null) throw new IllegalArgumentException("File is required");
        if (maximumBytes < 0 || maximumBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum size is invalid");
        }
        long length = file.length();
        if (length > maximumBytes) {
            throw new IOException("File is too large (maximum "
                    + maximumBytes + " bytes): " + file);
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != bytes.length || input.read() != -1) {
                throw new IOException("File changed while it was read: " + file);
            }
        }
        return bytes;
    }
}
