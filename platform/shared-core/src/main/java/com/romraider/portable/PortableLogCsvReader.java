/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/** Reads bounded RomRaider wide-column and portable long-form logger CSVs. */
public final class PortableLogCsvReader {
    private static final int MAX_CSV_CHARACTERS = 64 * 1024 * 1024;
    private static final int MAX_FIELD_CHARACTERS = 65_536;
    private static final int MAX_RECORD_CHARACTERS = 1024 * 1024;
    private static final int MAX_RECORDS = 1_000_001;
    public static final int MAX_CHANNELS = 256;
    public static final long MAX_SUMMARY_VALUES = 5_000_000;
    private static final List<String> HEADER = Arrays.asList(
            "timestamp_ms", "channel_id", "channel_name", "value", "units");

    private PortableLogCsvReader() { }

    public static PortableLogSession read(Reader reader) throws IOException {
        PortableLogSession session = new PortableLogSession();
        parse(reader, PortableLogSession.MAX_SAMPLES, session::append);
        return session;
    }

    /** Full-file summary, not a sample or truncated preview. Retains only per-channel state. */
    public static Summary summarize(Reader reader) throws IOException {
        Map<String, Accumulator> channels = new LinkedHashMap<>();
        long count = parse(reader, MAX_SUMMARY_VALUES, sample ->
                channels.computeIfAbsent(sample.getChannelId(), ignored -> new Accumulator()).add(sample));
        List<ChannelSummary> result = new ArrayList<>();
        for (Accumulator channel : channels.values()) result.add(new ChannelSummary(channel));
        return new Summary(count, result);
    }

    public static final class Summary {
        private final long values;
        private final List<ChannelSummary> channels;
        private Summary(long values, List<ChannelSummary> channels) {
            this.values = values;
            this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        }
        public long values() { return values; }
        public List<ChannelSummary> channels() { return channels; }
    }

    public static final class ChannelSummary {
        private final PortableLogSample latest;
        private final long finite;
        private final long missing;
        private final double minimum;
        private final double maximum;
        private ChannelSummary(Accumulator source) {
            latest = source.latest; finite = source.finite; missing = source.missing;
            minimum = finite == 0 ? Double.NaN : source.minimum;
            maximum = finite == 0 ? Double.NaN : source.maximum;
        }
        public PortableLogSample latest() { return latest; }
        public long finite() { return finite; }
        public long missing() { return missing; }
        public double minimum() { return minimum; }
        public double maximum() { return maximum; }
    }

    private static final class Accumulator {
        PortableLogSample latest;
        long finite, missing;
        double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        void add(PortableLogSample sample) {
            latest = sample;
            if (Double.isFinite(sample.getValue())) {
                finite++;
                minimum = Math.min(minimum, sample.getValue());
                maximum = Math.max(maximum, sample.getValue());
            } else missing++;
        }
    }

    private interface Sink { void accept(PortableLogSample sample); }

    private static long parse(Reader reader, long limit, Sink sink) throws IOException {
        if (reader == null) throw new IllegalArgumentException("A CSV reader is required");
        Records records = new Records(reader);
        List<String> header = records.next();
        if (header == null) throw new IOException("Unsupported logger CSV header");
        boolean longForm = HEADER.equals(header);
        if (!longForm && !isWideHeader(header)) throw new IOException("Unsupported logger CSV header");
        records.columns = header.size();
        List<ChannelHeader> wideChannels = new ArrayList<>();
        Map<String, ChannelHeader> channelIdentities = new LinkedHashMap<>();
        int metadataCharacters = 0;
        if (!longForm) for (int column = 1; column < header.size(); column++) {
            ChannelHeader channel = ChannelHeader.parse(header.get(column), column);
            metadataCharacters = metadataSize(metadataCharacters, channel);
            wideChannels.add(channel);
        }
        TimestampParser timestamps = new TimestampParser(header.get(0));
        long count = 0;
        List<String> row;
        while ((row = records.next()) != null) {
            if (row.size() == 1 && row.get(0).trim().isEmpty()) continue;
            if (!longForm && row.equals(header)) continue;
            if (row.size() != header.size()) {
                throw new IOException("Invalid logger CSV record " + records.number
                        + ": found " + row.size() + " fields; expected "
                        + header.size());
            }
            int values = longForm ? 1 : wideChannels.size();
            if (count > limit - values) throw new IOException("Portable log value limit reached (" + limit + ")");
            try {
                if (longForm) {
                    ChannelHeader channel = channelIdentities.get(row.get(1));
                    if (channel == null) {
                        if (channelIdentities.size() >= MAX_CHANNELS) throw new IOException("Too many logger CSV channels");
                        channel = new ChannelHeader(row.get(1), row.get(2), row.get(4));
                        metadataCharacters = metadataSize(metadataCharacters, channel);
                        channelIdentities.put(channel.id, channel);
                    } else if (!channel.name.equals(row.get(2)) || !channel.units.equals(row.get(4))) {
                        throw new IOException("Logger CSV channel metadata changed");
                    }
                    sink.accept(new PortableLogSample(Long.parseLong(row.get(0)), channel.id,
                            channel.name, Double.parseDouble(row.get(3)), channel.units));
                } else {
                    long timestamp = timestamps.parse(row.get(0), records.number);
                    for (int column = 1; column < row.size(); column++) {
                        String field = row.get(column).trim();
                        double value = field.isEmpty() ? Double.NaN : Double.parseDouble(field);
                        ChannelHeader channel = wideChannels.get(column - 1);
                        sink.accept(new PortableLogSample(timestamp, channel.id, channel.name, value, channel.units));
                    }
                }
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid logger CSV record " + records.number, ex);
            }
            count += values;
        }
        return count;
    }

    private static int metadataSize(int previous, ChannelHeader channel) throws IOException {
        int size = channel.id.length() + channel.name.length() + channel.units.length();
        if (size > 4096 || previous > 1024 * 1024 - size) throw new IOException("Logger CSV channel metadata is too large");
        return previous + size;
    }

    private static boolean isWideHeader(List<String> header) {
        if (header.size() < 2) return false;
        String time = ChannelHeader.parseLabel(header.get(0))[0];
        return "time".equalsIgnoreCase(time)
                || time.toLowerCase(Locale.ROOT).startsWith("time ");
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
            if (value.length() > 128) throw new IOException("Logger timestamp is too long on record " + row);
            if (value.indexOf(':') >= 0) return parseClock(value, row);
            try {
                BigDecimal numeric = new BigDecimal(value);
                if (numeric.signum() < 0 || Math.abs((long) numeric.scale()) > 1024) throw new NumberFormatException();
                BigDecimal millis = seconds ? numeric.multiply(BigDecimal.valueOf(1000)) : numeric;
                if (millis.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) throw new NumberFormatException();
                return millis.setScale(0, RoundingMode.HALF_UP).longValueExact();
            } catch (NumberFormatException | ArithmeticException ex) {
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
                        || !Double.isFinite(secondsValue) || secondsValue < 0 || secondsValue >= 60) {
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

    /** One record at a time; limits apply while tokenizing, before list growth. */
    private static final class Records {
        private final Reader input;
        private int characters;
        private int pending = -2;
        private int number;
        private int columns = MAX_CHANNELS + 1;
        Records(Reader input) { this.input = input instanceof BufferedReader ? input : new BufferedReader(input); }
        private int take() throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Logger CSV import cancelled");
            if (pending != -2) { int value = pending; pending = -2; return value; }
            int value = input.read();
            if (value >= 0 && ++characters > MAX_CSV_CHARACTERS) throw new IOException("Portable logger CSV size limit reached");
            return value;
        }
        List<String> next() throws IOException {
            int ch = take();
            if (number == 0 && ch == '\ufeff') ch = take();
            if (ch < 0) return null;
            if (++number > MAX_RECORDS) throw new IOException("Portable logger CSV record limit reached");
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            int state = 0; // field start, unquoted, quoted, closing quote
            int length = 0;
            while (true) {
                if (ch >= 0 && ++length > MAX_RECORD_CHARACTERS) throw new IOException("Portable logger CSV record is too large");
                if (state == 2) {
                    if (ch < 0) throw new IOException("Unterminated quoted CSV field");
                    if (ch == '"') state = 3;
                    else append(field, ch);
                } else if (state == 3 && ch == '"') { append(field, ch); state = 2; }
                else if (state == 0 && ch == '"') state = 2;
                else if (ch < 0 || ch == ',' || ch == '\n' || ch == '\r') {
                    if (row.size() >= columns) throw new IOException("Too many logger CSV columns on record " + number);
                    row.add(field.toString());
                    field.setLength(0);
                    state = 0;
                    if (ch != ',') {
                        if (ch == '\r') { int next = take(); if (next != '\n') pending = next; }
                        return row;
                    }
                    if (row.size() >= columns) throw new IOException("Too many logger CSV columns on record " + number);
                } else {
                    if (state == 3 || ch == '"') throw new IOException("Invalid CSV quote on record " + number);
                    append(field, ch); state = 1;
                }
                ch = take();
            }
        }
        private static void append(StringBuilder field, int value) throws IOException {
            if (field.length() >= MAX_FIELD_CHARACTERS) throw new IOException("Portable logger CSV field is too large");
            field.append((char) value);
        }
    }
}
