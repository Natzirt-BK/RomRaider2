/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import java.io.*;
import java.nio.file.*;

/** Exact bounded bytes used for one desktop definition load, never a later file reread. */
final class LoggerDefinitionSource {
    static final int MAX_BYTES = 32 * 1024 * 1024;
    final String path;
    private final byte[] bytes;
    private LoggerDefinitionSource(String path, byte[] bytes) { this.path = path; this.bytes = bytes; }
    byte[] bytes() { return bytes.clone(); }
    static LoggerDefinitionSource read(String path) throws IOException {
        Path file = Paths.get(path).toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(file)) { return read(file.toString(), input, MAX_BYTES); }
    }
    static LoggerDefinitionSource read(String path, InputStream input, int maximum) throws IOException {
        if (input == null || maximum < 1 || maximum > MAX_BYTES) throw new IOException("Invalid logger definition source");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Definition load cancelled");
            if (output.size() > maximum - count) throw new IOException("Logger definition exceeds the 32 MiB limit");
            output.write(buffer, 0, count);
        }
        if (output.size() == 0) throw new IOException("Logger definition is empty");
        return new LoggerDefinitionSource(path, output.toByteArray());
    }
}
