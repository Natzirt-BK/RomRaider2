/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Strict parser for numeric RomRaider/RomRaider2 CSV capture files. */
public final class RomRaiderCsvLogParser {
    /** Opt-in limits for additional review imports; existing callers retain their behavior. */
    public record Limits(int rows, int channels, long cells, int lineCharacters, long totalCharacters) {
        public Limits {
            if (rows < 1 || channels < 1 || cells < 1 || lineCharacters < 1 || totalCharacters < 1)
                throw new IllegalArgumentException("CSV limits must be positive.");
        }
    }
    public static final Limits REVIEW_LIMITS = new Limits(1_000_000, 256, 8_000_000, 1_048_576, 33_554_432);
    public LogDataset parse(File file) throws IOException {
        return parse(file, null);
    }
    public LogDataset parse(File file, Limits limits) throws IOException {
        if (file == null) throw new IllegalArgumentException("file");
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(),
                StandardCharsets.UTF_8)) {
            return parse(file.getName(), reader, limits);
        }
    }

    public LogDataset parse(String sourceName, Reader source)
            throws IOException {
        return parse(sourceName, source, null);
    }
    public LogDataset parse(String sourceName, Reader source, Limits limits)
            throws IOException {
        if (sourceName == null || source == null) {
            throw new IllegalArgumentException("sourceName and source are required");
        }
        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source : new BufferedReader(source);
        ReadBudget budget = limits == null ? null : new ReadBudget(reader, limits);
        String headerLine = budget == null ? reader.readLine() : budget.line();
        if (headerLine == null) throw new IOException("Log file is empty");
        if (!headerLine.isEmpty() && headerLine.charAt(0) == '\ufeff') {
            headerLine = headerLine.substring(1);
        }

        List<String> headers = parseRecord(headerLine, 1);
        if (limits != null && headers.size() > limits.channels()) throw new IOException("CSV channel count exceeds the review limit; no partial log was loaded.");
        if (headers.isEmpty()) throw new IOException("Log header is empty");
        List<LogChannel> channels = new ArrayList<LogChannel>(headers.size());
        for (int index = 0; index < headers.size(); index++) {
            if (limits != null && headers.get(index).length() > 512) throw new IOException("CSV channel label exceeds 512 characters; no partial log was loaded.");
            try {
                channels.add(new LogChannel(index, headers.get(index)));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid channel at column "
                        + (index + 1) + ": " + e.getMessage(), e);
            }
        }

        List<double[]> rows = new ArrayList<double[]>();
        String line;
        int lineNumber = 1;
        while ((line = budget == null ? reader.readLine() : budget.line()) != null) {
            lineNumber++;
            if (line.trim().isEmpty()) continue;
            if (limits != null && (rows.size() >= limits.rows() || ((long) rows.size() + 1) * channels.size() > limits.cells()))
                throw new IOException("CSV sample/cell count exceeds the review limit; no partial log was loaded.");
            List<String> fields = parseRecord(line, lineNumber);
            if (fields.size() != channels.size()) {
                throw new IOException("Line " + lineNumber + " has "
                        + fields.size() + " fields; expected "
                        + channels.size());
            }
            double[] values = new double[fields.size()];
            for (int column = 0; column < fields.size(); column++) {
                String field = fields.get(column).trim();
                if (limits != null && field.length() > 1024) throw new IOException("CSV numeric field exceeds 1,024 characters; no partial log was loaded.");
                if (field.isEmpty()) {
                    values[column] = Double.NaN;
                    continue;
                }
                try {
                    values[column] = Double.parseDouble(field);
                } catch (NumberFormatException e) {
                    throw new IOException("Line " + lineNumber + ", column "
                            + (column + 1) + " is not numeric: " + (limits != null && field.length() > 120 ? field.substring(0, 120) + "…" : field), e);
                }
            }
            rows.add(values);
        }
        if (rows.isEmpty()) throw new IOException("Log contains no samples");
        return new LogDataset(sourceName, channels, rows);
    }

    private static final class ReadBudget {
        private final BufferedReader reader;
        private final Limits limits;
        private long characters;
        ReadBudget(BufferedReader reader, Limits limits) { this.reader = reader; this.limits = limits; }
        private int read() throws IOException {
            int value = reader.read();
            if (value >= 0 && ++characters > limits.totalCharacters()) throw new IOException("CSV character count exceeds the review limit; no partial log was loaded.");
            if ((characters & 4095) == 0 && Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            return value;
        }
        String line() throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            StringBuilder line = new StringBuilder();
            while (true) {
                int value = read();
                if (value < 0) return line.length() == 0 ? null : line.toString();
                if (value == '\n') return line.toString();
                if (value == '\r') {
                    reader.mark(1); int next = read();
                    if (next >= 0 && next != '\n') { reader.reset(); characters--; }
                    return line.toString();
                }
                if (line.length() >= limits.lineCharacters()) throw new IOException("CSV line length exceeds the review limit; no partial log was loaded.");
                line.append((char) value);
            }
        }
    }

    private static List<String> parseRecord(String line, int lineNumber)
            throws IOException {
        List<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (value == '"') {
                if (quoted && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (value == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(value);
            }
        }
        if (quoted) throw new IOException("Unclosed quote on line " + lineNumber);
        fields.add(field.toString());
        return fields;
    }
}
