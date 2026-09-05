/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Wide, relative-time CSV compatible with the desktop RomRaider logger.
 * Recovery spools remain unchanged; two streaming passes keep memory bounded.
 */
public final class PortableRomRaiderCsvWriter {
    private static final int MAX_CHANNELS = 4096;
    private static final int MAX_FIELD = 65536;
    private PortableRomRaiderCsvWriter() { }

    public static void writeSpool(File source, Writer output) throws IOException {
        write(() -> new SpoolCursor(source), output);
    }

    static void writeSamples(List<PortableLogSample> samples, Writer output) throws IOException {
        write(() -> new Cursor() {
            private final Iterator<PortableLogSample> iterator = samples.iterator();
            public PortableLogSample next() { return iterator.hasNext() ? iterator.next() : null; }
            public void close() { }
        }, output);
    }

    private interface Source { Cursor open() throws IOException; }
    private interface Cursor extends Closeable { PortableLogSample next() throws IOException; }

    private static void write(Source source, Writer output) throws IOException {
        if (output == null) throw new IllegalArgumentException("A CSV writer is required");
        Map<String, PortableLogSample> channels = new LinkedHashMap<>();
        long previousTime = -1;
        try (Cursor input = source.open()) {
            PortableLogSample sample;
            while ((sample = input.next()) != null) {
                if (sample.getTimestampMillis() < previousTime) {
                    throw new IOException("Recording timestamps run backwards");
                }
                previousTime = sample.getTimestampMillis();
                PortableLogSample previous = channels.get(sample.getChannelId());
                if (previous != null && (!previous.getChannelName().equals(sample.getChannelName())
                        || !previous.getUnits().equals(sample.getUnits()))) {
                    throw new IOException("Recording channel metadata changed");
                }
                if (previous == null) {
                    if (channels.size() >= MAX_CHANNELS) throw new IOException("Too many CSV channels");
                    channels.put(sample.getChannelId(), sample);
                }
            }
        }
        output.write("Time (msec)");
        Map<String, Integer> columns = new HashMap<>();
        for (PortableLogSample channel : channels.values()) {
            columns.put(channel.getChannelId(), columns.size());
            String label = channel.getChannelName() + " (" + channel.getUnits() + ")";
            // The desktop reader is line-oriented even for quoted CSV headers.
            if (label.indexOf('\n') >= 0 || label.indexOf('\r') >= 0) {
                throw new IOException("Channel labels cannot contain line breaks in desktop CSV");
            }
            output.write(',');
            output.write(csv(label));
        }
        output.write('\n');
        PortableLogSample[] row = new PortableLogSample[channels.size()];
        long origin = -1;
        long timestamp = -1;
        try (Cursor input = source.open()) {
            PortableLogSample sample;
            while ((sample = input.next()) != null) {
                Integer column = columns.get(sample.getChannelId());
                if (column == null) throw new IOException("Recording changed during export");
                if (origin < 0) origin = sample.getTimestampMillis();
                // Repeated channels at the same millisecond start another row,
                // rather than silently overwriting an earlier sample.
                if (timestamp >= 0 && (timestamp != sample.getTimestampMillis() || row[column] != null)) {
                    writeRow(output, timestamp - origin, row);
                    Arrays.fill(row, null);
                }
                timestamp = sample.getTimestampMillis();
                row[column] = sample;
            }
        }
        if (timestamp >= 0) writeRow(output, timestamp - origin, row);
    }

    private static void writeRow(Writer output, long elapsed, PortableLogSample[] row) throws IOException {
        output.write(Long.toString(elapsed));
        for (PortableLogSample sample : row) {
            output.write(',');
            if (sample != null && Double.isFinite(sample.getValue())) {
                output.write(BigDecimal.valueOf(sample.getValue()).stripTrailingZeros().toPlainString());
            }
        }
        output.write('\n');
    }

    private static String csv(String value) {
        return value.indexOf(',') < 0 && value.indexOf('"') < 0 ? value
                : '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Reads the existing headerless, five-field recovery format, including quoted fields. */
    private static final class SpoolCursor implements Cursor {
        private final PushbackReader input;
        SpoolCursor(File file) throws IOException {
            input = new PushbackReader(new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8)), 1);
        }
        public void close() throws IOException { input.close(); }
        public PortableLogSample next() throws IOException {
            int first = input.read();
            if (first == -1) return null;
            input.unread(first);
            List<String> fields = record();
            if (fields.size() != 5) throw new IOException("Incomplete recording row");
            try {
                return new PortableLogSample(Long.parseLong(fields.get(0)), fields.get(1),
                        fields.get(2), Double.parseDouble(fields.get(3)), fields.get(4));
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid recording row", ex);
            }
        }
        private List<String> record() throws IOException {
            List<String> fields = new ArrayList<>();
            while (true) {
                StringBuilder field = new StringBuilder();
                int ch = input.read();
                if (ch == '"') {
                    while (true) {
                        ch = input.read();
                        if (ch < 0) throw new IOException("Incomplete quoted recording field");
                        if (ch == '"') {
                            ch = input.read();
                            if (ch != '"') break;
                        }
                        append(field, ch);
                    }
                } else {
                    while (ch != ',' && ch != '\r' && ch != '\n' && ch >= 0) {
                        if (ch == '"') throw new IOException("Unexpected quote in recording field");
                        append(field, ch);
                        ch = input.read();
                    }
                }
                fields.add(field.toString());
                if (fields.size() > 5) throw new IOException("Too many recording fields");
                if (ch == ',') continue;
                if (ch == '\r') {
                    int following = input.read();
                    if (following != '\n' && following >= 0) input.unread(following);
                } else if (ch != '\n' && ch >= 0) {
                    throw new IOException("Unexpected data after quoted recording field");
                }
                return fields;
            }
        }
        private static void append(StringBuilder field, int ch) throws IOException {
            if (field.length() >= MAX_FIELD) throw new IOException("Recording field is too large");
            field.append((char) ch);
        }
    }
}
