/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Prepares an immutable CSV export without modifying the app-private recovery spool.
 * Only LF/CRLF-terminated records are committed; an unfinished last record is reported,
 * never silently accepted as a completed value. This cannot recover unwritten bytes or
 * infer whether all channels of the last cycle were written.
 */
public final class PortableRecordingRecovery {
    // Five bounded UTF-8 fields, including quotes and separators; no whole-log heap copy.
    private static final int MAX_RECORD_BYTES = 5 * 65_536 * 4 + 1024;
    private PortableRecordingRecovery() { }

    public static final class Prepared implements AutoCloseable {
        private final File csv;
        private final long sourceBytes;
        private final long omittedBytes;
        private final long values;
        private boolean closed;

        private Prepared(File csv, long sourceBytes, long omittedBytes, long values) {
            this.csv = csv;
            this.sourceBytes = sourceBytes;
            this.omittedBytes = omittedBytes;
            this.values = values;
        }
        public long sourceBytes() { return sourceBytes; }
        public long omittedBytes() { return omittedBytes; }
        /** Completed long-form sample records, not wide CSV rows or complete cycles. */
        public long values() { return values; }

        /** Destination authority remains with the caller, after any recovery review. */
        public synchronized void writeTo(Writer destination) throws IOException {
            if (closed) throw new IOException("Prepared recovery is closed");
            Objects.requireNonNull(destination, "destination");
            try (Reader input = new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8)) {
                char[] buffer = new char[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkInterrupted();
                    destination.write(buffer, 0, count);
                }
            }
        }
        @Override public synchronized void close() throws IOException {
            if (!closed) {
                Files.deleteIfExists(csv.toPath());
                closed = true;
            }
        }
    }

    /** Call off the UI thread with a private temporary directory and an idle spool.
     * The captured source length bounds the read. Concurrent size/metadata changes are
     * rejected; this is not a locking protocol for arbitrary external file writers.
     * Conversion and validation finish before a destination needs to be opened.
     */
    public static Prepared prepare(File source, File temporaryDirectory) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(temporaryDirectory, "temporaryDirectory");
        Path path = source.toPath();
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) throw new IOException("Recovery source must be a regular file, not a link");
        File snapshot = File.createTempFile("rr2-recovery-", ".part", temporaryDirectory);
        File csv = null;
        boolean retained = false;
        try {
            Boundary boundary = new Boundary();
            try (SeekableByteChannel input = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(snapshot))) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                long remaining = before.size();
                while (remaining > 0) {
                    checkInterrupted();
                    buffer.clear();
                    buffer.limit((int) Math.min(buffer.capacity(), remaining));
                    int count = input.read(buffer);
                    if (count < 0) throw new IOException("Recording changed while preparing recovery");
                    for (int index = 0; index < count; index++) boundary.accept(buffer.array()[index] & 255);
                    output.write(buffer.array(), 0, count);
                    remaining -= count;
                }
            }
            BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile() || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())) {
                throw new IOException("Recording changed while preparing recovery");
            }
            if (boundary.values == 0) throw new IOException("Recording contains no complete sample records to recover");
            try (SeekableByteChannel output = Files.newByteChannel(snapshot.toPath(), StandardOpenOption.WRITE)) {
                output.truncate(boundary.completeBytes);
            }
            csv = File.createTempFile("rr2-recovery-", ".csv", temporaryDirectory);
            try (Writer output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csv), StandardCharsets.UTF_8))) {
                PortableRomRaiderCsvWriter.writeSpool(snapshot, new FilterWriter(output) {
                    @Override public void write(int value) throws IOException { checkInterrupted(); out.write(value); }
                    @Override public void write(String text, int offset, int count) throws IOException {
                        checkInterrupted(); out.write(text, offset, count);
                    }
                    @Override public void write(char[] text, int offset, int count) throws IOException {
                        checkInterrupted(); out.write(text, offset, count);
                    }
                });
            }
            Prepared prepared = new Prepared(csv, before.size(), before.size() - boundary.completeBytes, boundary.values);
            Files.deleteIfExists(snapshot.toPath());
            retained = true;
            return prepared;
        } finally {
            try { Files.deleteIfExists(snapshot.toPath()); }
            finally { if (!retained && csv != null) Files.deleteIfExists(csv.toPath()); }
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Recording recovery cancelled");
    }

    /** Byte-level CSV framing precedes UTF-8 decoding, so a split final code point
     * cannot corrupt earlier records. UTF-8 and values of the retained prefix are
     * subsequently validated by the normal strict CSV converter.
     */
    private static final class Boundary {
        private int state; // 0 field start, 1 unquoted, 2 quoted, 3 quote end, 4 CR
        private int fields = 1;
        private long bytes;
        private long completeBytes;
        private long values;
        void accept(int ch) throws IOException {
            bytes++;
            if (bytes - completeBytes > MAX_RECORD_BYTES) throw new IOException("Recovery record is too large");
            if (state == 2) { if (ch == '"') state = 3; return; }
            if (state == 4 && ch != '\n') throw new IOException("Recovery records require LF or CRLF endings");
            if (state == 3 && ch == '"') { state = 2; return; }
            if (state == 0 && ch == '"') { state = 2; return; }
            if (ch == '\n') {
                if (fields != 5) throw new IOException("Incomplete completed recovery record");
                completeBytes = bytes;
                values++;
                state = 0;
                fields = 1;
            } else if (ch == ',') {
                if (++fields > 5) throw new IOException("Too many recovery fields");
                state = 0;
            } else if (ch == '\r') state = 4;
            else {
                if (state == 3 || ch == '"') throw new IOException("Invalid recovery quoting");
                state = 1;
            }
        }
    }
}
