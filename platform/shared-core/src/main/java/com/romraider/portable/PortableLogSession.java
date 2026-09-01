/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded in-memory logger session shared by early portable clients. */
public final class PortableLogSession {
    public static final int MAX_SAMPLES = 250_000;
    private final List<PortableLogSample> samples =
            new ArrayList<PortableLogSample>();

    public synchronized void append(PortableLogSample sample) {
        if (sample == null) throw new IllegalArgumentException(
                "A logger sample is required");
        if (samples.size() >= MAX_SAMPLES) {
            throw new IllegalStateException("Portable log sample limit reached");
        }
        samples.add(sample);
    }

    public synchronized int size() { return samples.size(); }

    public synchronized List<PortableLogSample> snapshot() {
        return Collections.unmodifiableList(
                new ArrayList<PortableLogSample>(samples));
    }

    public synchronized void writeLongFormCsv(Writer writer) throws IOException {
        if (writer == null) throw new IllegalArgumentException(
                "A CSV writer is required");
        writer.write("timestamp_ms,channel_id,channel_name,value,units\n");
        for (PortableLogSample sample : samples) {
            writer.write(Long.toString(sample.getTimestampMillis()));
            writer.write(',');
            writer.write(csv(sample.getChannelId()));
            writer.write(',');
            writer.write(csv(sample.getChannelName()));
            writer.write(',');
            writer.write(Double.toString(sample.getValue()));
            writer.write(',');
            writer.write(csv(sample.getUnits()));
            writer.write('\n');
        }
    }

    private static String csv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
