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
    public LogDataset parse(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file");
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(),
                StandardCharsets.UTF_8)) {
            return parse(file.getName(), reader);
        }
    }

    public LogDataset parse(String sourceName, Reader source)
            throws IOException {
        if (sourceName == null || source == null) {
            throw new IllegalArgumentException("sourceName and source are required");
        }
        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source : new BufferedReader(source);
        String headerLine = reader.readLine();
        if (headerLine == null) throw new IOException("Log file is empty");
        if (!headerLine.isEmpty() && headerLine.charAt(0) == '\ufeff') {
            headerLine = headerLine.substring(1);
        }

        List<String> headers = parseRecord(headerLine, 1);
        if (headers.isEmpty()) throw new IOException("Log header is empty");
        List<LogChannel> channels = new ArrayList<LogChannel>(headers.size());
        for (int index = 0; index < headers.size(); index++) {
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
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.trim().isEmpty()) continue;
            List<String> fields = parseRecord(line, lineNumber);
            if (fields.size() != channels.size()) {
                throw new IOException("Line " + lineNumber + " has "
                        + fields.size() + " fields; expected "
                        + channels.size());
            }
            double[] values = new double[fields.size()];
            for (int column = 0; column < fields.size(); column++) {
                String field = fields.get(column).trim();
                if (field.isEmpty()) {
                    values[column] = Double.NaN;
                    continue;
                }
                try {
                    values[column] = Double.parseDouble(field);
                } catch (NumberFormatException e) {
                    throw new IOException("Line " + lineNumber + ", column "
                            + (column + 1) + " is not numeric: " + field, e);
                }
            }
            rows.add(values);
        }
        if (rows.isEmpty()) throw new IOException("Log contains no samples");
        return new LogDataset(sourceName, channels, rows);
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
