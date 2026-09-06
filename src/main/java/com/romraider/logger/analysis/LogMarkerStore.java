/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Bounded sidecar persistence; captured CSV stays untouched. */
public final class LogMarkerStore {
    public static final int MAX_BYTES = 1_048_576, MAX_MARKERS = 4096, MAX_LABEL_CHARACTERS = 512;
    // Bounded lock stripes serialize cooperating writers without retaining every visited path.
    private static final Object[] SAVE_LOCKS = new Object[64];
    static { for (int i = 0; i < SAVE_LOCKS.length; i++) SAVE_LOCKS[i] = new Object(); }

    public static final class Snapshot {
        private final Path path;
        private final byte[] bytes; // null distinguishes an absent file from an empty/invalid one.
        private final List<LogMarker> markers;
        private final int sampleCount;
        private Snapshot(Path path, byte[] bytes, List<LogMarker> markers, int sampleCount) {
            this.path = path; this.bytes = bytes; this.markers = List.copyOf(markers); this.sampleCount = sampleCount;
        }
        public List<LogMarker> getMarkers() { return markers; }
    }

    public List<LogMarker> load(File logFile, int sampleCount) throws IOException {
        return loadSnapshot(logFile, sampleCount).getMarkers();
    }

    public Snapshot loadSnapshot(File logFile, int sampleCount) throws IOException {
        if (sampleCount < 1) throw new IllegalArgumentException("Sample count must be positive");
        Path path = sidecar(logFile).toAbsolutePath().normalize();
        byte[] bytes = readCurrent(path);
        return new Snapshot(path, bytes, bytes == null ? List.of() : decode(bytes, sampleCount), sampleCount);
    }

    /** Legacy callers also validate the existing document before replacing it. */
    public void save(File logFile, List<LogMarker> markers) throws IOException {
        saveIfUnchanged(loadSnapshot(logFile, Integer.MAX_VALUE), markers);
    }

    /** Optimistic conflict check immediately before required atomic replacement. */
    public Snapshot saveIfUnchanged(Snapshot expected, List<LogMarker> markers) throws IOException {
        Objects.requireNonNull(expected);
        List<LogMarker> checked = validateMarkers(markers);
        for (LogMarker marker : checked) if (marker.getSampleIndex() >= expected.sampleCount)
            throw new IOException("Marker is outside this log's sample range; no changes were saved");
        byte[] bytes = encode(checked); // Validate/serialize before any output is opened.
        synchronized (SAVE_LOCKS[(expected.path.hashCode() & Integer.MAX_VALUE) % SAVE_LOCKS.length]) {
            return replace(expected, checked, bytes);
        }
    }

    private static Snapshot replace(Snapshot expected, List<LogMarker> checked, byte[] bytes) throws IOException {
        requireUnchanged(expected);
        Path temporary = Files.createTempFile(expected.path.getParent(), ".rr2-markers-", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) { checkCancelled(); output.write(buffer); }
                output.force(true);
            }
            requireUnchanged(expected);
            checkCancelled();
            Files.move(temporary, expected.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new Snapshot(expected.path, bytes, checked, expected.sampleCount);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static List<LogMarker> validateMarkers(List<LogMarker> markers) throws IOException {
        if (markers == null || markers.size() > MAX_MARKERS) throw new IOException("At most 4,096 markers are supported");
        List<LogMarker> checked = new ArrayList<>(markers.size());
        for (LogMarker marker : markers) {
            checkCancelled();
            if (marker == null || marker.getLabel().length() > MAX_LABEL_CHARACTERS)
                throw new IOException("Marker labels must contain at most 512 characters");
            String label = marker.getLabel();
            for (int i = 0; i < label.length(); i++) {
                char value = label.charAt(i);
                if (Character.isHighSurrogate(value)) {
                    if (++i >= label.length() || !Character.isLowSurrogate(label.charAt(i))) throw new IOException("Malformed Unicode marker label");
                } else if (Character.isLowSurrogate(value)) throw new IOException("Malformed Unicode marker label");
            }
            checked.add(marker);
        }
        Collections.sort(checked);
        return List.copyOf(checked);
    }

    private static List<LogMarker> decode(byte[] bytes, int sampleCount) throws IOException {
        Properties values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate marker property");
                return super.put(key, value);
            }
        };
        try { values.load(new ByteArrayInputStream(bytes)); }
        catch (IllegalArgumentException failure) { throw new IOException("Malformed marker properties", failure); }
        if (!"1".equals(values.getProperty("format.version"))) throw new IOException("Unsupported or missing marker format version");
        int count = integer(values.getProperty("marker.count"));
        if (count < 0 || count > MAX_MARKERS) throw new IOException("Invalid marker count; at most 4,096 markers are supported");
        if (values.size() != 2 + 3 * count) throw new IOException("Marker file has missing or unknown properties");
        List<LogMarker> markers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            checkCancelled();
            int sample = integer(values.getProperty(key(index, "sample")));
            String type = values.getProperty(key(index, "type")), label = values.getProperty(key(index, "label"));
            if (sample < 0 || sample >= sampleCount || type == null || label == null)
                throw new IOException("Marker " + (index + 1) + " is incomplete or outside this log's sample range; no markers were imported");
            if (label.length() > MAX_LABEL_CHARACTERS) throw new IOException("Marker labels must contain at most 512 characters");
            if (type.length() > 64) throw new IOException("Unknown marker type at marker " + (index + 1));
            try { markers.add(new LogMarker(sample, LogMarkerType.valueOf(type), label)); }
            catch (IllegalArgumentException failure) { throw new IOException("Unknown marker type at marker " + (index + 1), failure); }
        }
        return validateMarkers(markers);
    }

    private static byte[] encode(List<LogMarker> markers) throws IOException {
        Properties values = new Properties();
        values.setProperty("format.version", "1"); values.setProperty("marker.count", Integer.toString(markers.size()));
        for (int index = 0; index < markers.size(); index++) {
            LogMarker marker = markers.get(index);
            values.setProperty(key(index, "sample"), Integer.toString(marker.getSampleIndex()));
            values.setProperty(key(index, "type"), marker.getType().name()); values.setProperty(key(index, "label"), marker.getLabel());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        values.store(new OutputStream() {
            @Override public void write(int value) throws IOException {
                checkCancelled(); if (bytes.size() >= MAX_BYTES) throw new IOException("Marker file exceeds 1 MiB"); bytes.write(value);
            }
            @Override public void write(byte[] buffer, int offset, int length) throws IOException {
                checkCancelled(); if ((long) bytes.size() + length > MAX_BYTES) throw new IOException("Marker file exceeds 1 MiB"); bytes.write(buffer, offset, length);
            }
        }, "RomRaider2 log markers");
        return bytes.toByteArray();
    }

    private static byte[] readCurrent(Path path) throws IOException {
        checkCancelled();
        BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return null; }
        if (!attributes.isRegularFile()) throw new IOException("Marker sidecar must be a regular file, not a directory or symbolic link");
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                checkCancelled();
                if ((long) bytes.size() + count > MAX_BYTES) throw new IOException("Marker sidecar exceeds 1 MiB");
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        }
    }
    private static void requireUnchanged(Snapshot expected) throws IOException {
        if (!Arrays.equals(expected.bytes, readCurrent(expected.path)))
            throw new IOException("Marker sidecar changed outside this workspace. Reload markers before editing; no changes were saved");
    }
    public Path sidecar(File logFile) {
        Objects.requireNonNull(logFile, "logFile");
        return logFile.toPath().resolveSibling(logFile.getName() + ".rr2markers.properties");
    }
    private static String key(int index, String field) { return "marker." + index + "." + field; }
    private static int integer(String value) throws IOException {
        if (value == null || value.length() > 11) throw new IOException("Invalid or missing marker integer");
        try { return Integer.parseInt(value); }
        catch (RuntimeException failure) { throw new IOException("Invalid or missing marker integer", failure); }
    }
    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Marker operation cancelled");
    }
}
