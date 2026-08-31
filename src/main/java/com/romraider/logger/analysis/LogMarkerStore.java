/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Sidecar persistence for user-created log markers; captured CSV stays untouched. */
public final class LogMarkerStore {
    public List<LogMarker> load(File logFile, int sampleCount)
            throws IOException {
        Path sidecar = sidecar(logFile);
        if (!Files.isRegularFile(sidecar)) return Collections.emptyList();
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(sidecar)) {
            values.load(input);
        }
        if (!"1".equals(values.getProperty("format.version"))) {
            return Collections.emptyList();
        }
        int count = integer(values.getProperty("marker.count"), 0);
        List<LogMarker> markers = new ArrayList<LogMarker>();
        for (int index = 0; index < count; index++) {
            int sample = integer(values.getProperty(key(index, "sample")), -1);
            if (sample < 0 || sample >= sampleCount) continue;
            try {
                LogMarkerType type = LogMarkerType.valueOf(
                        values.getProperty(key(index, "type"), "CUSTOM"));
                markers.add(new LogMarker(sample, type,
                        values.getProperty(key(index, "label"), "")));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed marker rows without rejecting valid entries.
            }
        }
        Collections.sort(markers);
        return Collections.unmodifiableList(markers);
    }

    public void save(File logFile, List<LogMarker> markers) throws IOException {
        Path sidecar = sidecar(logFile);
        if (markers == null || markers.isEmpty()) {
            Files.deleteIfExists(sidecar);
            return;
        }
        Properties values = new Properties();
        values.setProperty("format.version", "1");
        values.setProperty("marker.count", Integer.toString(markers.size()));
        for (int index = 0; index < markers.size(); index++) {
            LogMarker marker = markers.get(index);
            values.setProperty(key(index, "sample"),
                    Integer.toString(marker.getSampleIndex()));
            values.setProperty(key(index, "type"), marker.getType().name());
            values.setProperty(key(index, "label"), marker.getLabel());
        }
        Path parent = sidecar.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = sidecar.resolveSibling(sidecar.getFileName() + ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "RomRaider2 log markers");
            }
            try {
                Files.move(temporary, sidecar, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, sidecar,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path sidecar(File logFile) {
        if (logFile == null) throw new IllegalArgumentException("logFile");
        return logFile.toPath().resolveSibling(logFile.getName()
                + ".rr2markers.properties");
    }

    private static String key(int index, String field) {
        return "marker." + index + "." + field;
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
