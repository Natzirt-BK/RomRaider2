/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Reads bounded RomRaider wide-column and portable long-form logger CSVs. */
public final class PortableLogCsvReader {
    private static final int MAX_CSV_CHARACTERS = 64 * 1024 * 1024;
    private static final int MAX_FIELD_CHARACTERS = 1024 * 1024;
    private static final List<String> HEADER = Arrays.asList(
            "timestamp_ms", "channel_id", "channel_name", "value", "units");

    private PortableLogCsvReader() { }

    public static PortableLogSession read(Reader reader) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("A CSV reader is required");
        }
        List<List<String>> rows = rows(reader);
        if (rows.isEmpty()) {
            throw new IOException("Unsupported logger CSV header");
        }
        removeByteOrderMark(rows.get(0));
        if (HEADER.equals(rows.get(0))) return readLongForm(rows);
        if (isWideHeader(rows.get(0))) return readWideForm(rows);
        throw new IOException("Unsupported logger CSV header");
    }

    private static PortableLogSession readLongForm(List<List<String>> rows)
            throws IOException {
        PortableLogSession session = new PortableLogSession();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() == 1 && row.get(0).isEmpty()) continue;
            if (row.size() != HEADER.size()) {
                throw new IOException("Invalid logger CSV row " + (index + 1));
            }
            try {
                session.append(new PortableLogSample(
                        Long.parseLong(row.get(0)), row.get(1), row.get(2),
                        Double.parseDouble(row.get(3)), row.get(4)));
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid logger CSV row " + (index + 1), ex);
            }
        }
        return session;
    }

    private static PortableLogSession readWideForm(List<List<String>> rows)
            throws IOException {
        List<String> header = rows.get(0);
        int channelCount = header.size() - 1;
        long maximumSamples = (long) (rows.size() - 1) * channelCount;
        if (maximumSamples > PortableLogSession.MAX_SAMPLES) {
            throw new IOException("Portable log sample limit reached");
        }

        List<ChannelHeader> channels = new ArrayList<ChannelHeader>(channelCount);
        for (int column = 1; column < header.size(); column++) {
            channels.add(ChannelHeader.parse(header.get(column), column));
        }

        PortableLogSession session = new PortableLogSession();
        TimestampParser timestamps = new TimestampParser(header.get(0));
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() == 1 && row.get(0).trim().isEmpty()) continue;
            if (row.equals(header)) continue;
            if (row.size() != header.size()) {
                throw new IOException("Invalid logger CSV row " + (index + 1)
                        + ": found " + row.size() + " fields; expected "
                        + header.size());
            }
            long timestamp = timestamps.parse(row.get(0), index + 1);
            for (int column = 1; column < row.size(); column++) {
                String field = row.get(column).trim();
                double value;
                try {
                    value = field.isEmpty() ? Double.NaN
                            : Double.parseDouble(field);
                } catch (NumberFormatException ex) {
                    throw new IOException("Invalid logger CSV row "
                            + (index + 1) + ", column " + (column + 1), ex);
                }
                ChannelHeader channel = channels.get(column - 1);
                session.append(new PortableLogSample(timestamp, channel.id,
                        channel.name, value, channel.units));
            }
        }
        return session;
    }

    private static boolean isWideHeader(List<String> header) {
        if (header.size() < 2) return false;
        String time = ChannelHeader.parseLabel(header.get(0))[0];
        return "time".equalsIgnoreCase(time)
                || time.toLowerCase(Locale.ROOT).startsWith("time ");
    }

    private static void removeByteOrderMark(List<String> header) {
        if (header.isEmpty()) return;
        String first = header.get(0);
        if (!first.isEmpty() && first.charAt(0) == '\ufeff') {
            header.set(0, first.substring(1));
        }
    }

    private static final class ChannelHeader {
        private final String id;
        private final String name;
        private final String units;

        private ChannelHeader(String id, String name, String units) {
            this.id = id;
            this.name = name;
            this.units = units;
        }

        private static ChannelHeader parse(String label, int column)
                throws IOException {
            String[] parts = parseLabel(label);
            if (parts[0].isEmpty()) {
                throw new IOException("Logger CSV channel " + (column + 1)
                        + " has no name");
            }
            return new ChannelHeader("rr-column-" + column,
                    parts[0], parts[1]);
        }

        private static String[] parseLabel(String label) {
            String trimmed = label == null ? "" : label.trim();
            int unitsStart = trimmed.endsWith(")")
                    ? trimmed.lastIndexOf(" (") : -1;
            if (unitsStart < 0) return new String[] {trimmed, ""};
            return new String[] {
                    trimmed.substring(0, unitsStart).trim(),
                    trimmed.substring(unitsStart + 2,
                            trimmed.length() - 1).trim()
            };
        }
    }

    private static final class TimestampParser {
        private final boolean seconds;
        private Long firstClockMillis;
        private long previousClockMillis;
        private long dayOffset;

        private TimestampParser(String label) {
            String units = ChannelHeader.parseLabel(label)[1]
                    .toLowerCase(Locale.ROOT);
            seconds = "s".equals(units) || "sec".equals(units)
                    || "secs".equals(units) || "second".equals(units)
                    || "seconds".equals(units);
        }

        private long parse(String field, int row) throws IOException {
            String value = field.trim();
            if (value.indexOf(':') >= 0) return parseClock(value, row);
            try {
                double numeric = Double.parseDouble(value);
                if (!Double.isFinite(numeric) || numeric < 0) {
                    throw new NumberFormatException();
                }
                double millis = seconds ? numeric * 1000.0 : numeric;
                if (millis > Long.MAX_VALUE) throw new NumberFormatException();
                return Math.round(millis);
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid logger timestamp on row "
                        + row, ex);
            }
        }

        private long parseClock(String value, int row) throws IOException {
            try {
                String[] clock = value.split(":", -1);
                if (clock.length != 3) throw new NumberFormatException();
                int hours = Integer.parseInt(clock[0]);
                int minutes = Integer.parseInt(clock[1]);
                double secondsValue = Double.parseDouble(clock[2]);
                if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59
                        || secondsValue < 0 || secondsValue >= 60) {
                    throw new NumberFormatException();
                }
                long clockMillis = Math.round(((hours * 60L + minutes) * 60L
                        + secondsValue) * 1000.0);
                if (firstClockMillis == null) {
                    firstClockMillis = clockMillis;
                    previousClockMillis = clockMillis;
                } else if (clockMillis < previousClockMillis
                        && previousClockMillis - clockMillis > 12L * 60 * 60 * 1000) {
                    dayOffset += 24L * 60 * 60 * 1000;
                }
                previousClockMillis = clockMillis;
                return dayOffset + clockMillis - firstClockMillis;
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid logger timestamp on row "
                        + row, ex);
            }
        }
    }

    private static List<List<String>> rows(Reader reader) throws IOException {
        PushbackReader input = new PushbackReader(reader, 1);
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int value;
        int characters = 0;
        while ((value = input.read()) >= 0) {
            characters = Math.addExact(characters, 1);
            if (characters > MAX_CSV_CHARACTERS) {
                throw new IOException("Portable logger CSV size limit reached");
            }
            char character = (char) value;
            if (quoted) {
                if (character == '"') {
                    int next = input.read();
                    if (next == '"') field.append('"');
                    else {
                        quoted = false;
                        if (next >= 0) input.unread(next);
                    }
                } else {
                    field.append(character);
                }
            } else if (character == '"' && field.length() == 0) {
                quoted = true;
            } else if (character == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                row.add(trimCarriageReturn(field));
                rows.add(row);
                if (rows.size() > PortableLogSession.MAX_SAMPLES + 1) {
                    throw new IOException("Portable log sample limit reached");
                }
                row = new ArrayList<String>();
                field.setLength(0);
            } else {
                field.append(character);
            }
            if (field.length() > MAX_FIELD_CHARACTERS) {
                throw new IOException("Portable logger CSV field is too large");
            }
        }
        if (quoted) throw new IOException("Unterminated quoted CSV field");
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(trimCarriageReturn(field));
            rows.add(row);
            if (rows.size() > PortableLogSession.MAX_SAMPLES + 1) {
                throw new IOException("Portable log sample limit reached");
            }
        }
        return rows;
    }

    private static String trimCarriageReturn(StringBuilder field) {
        int length = field.length();
        if (length > 0 && field.charAt(length - 1) == '\r') {
            return field.substring(0, length - 1);
        }
        return field.toString();
    }
}
