/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.IOException;
import java.io.Writer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.List;

/** Bounded in-memory logger session shared by early portable clients. */
public final class PortableLogSession {
    public static final int MAX_SAMPLES = 250_000;
    private final Deque<PortableLogSample> samples =
            new ArrayDeque<PortableLogSample>();
    private final File spoolFile;
    private final int retainedSamples;
    private Writer spoolWriter;
    private int sampleCount;
    private boolean finished;

    public PortableLogSession() {
        this(null, MAX_SAMPLES);
    }

    private PortableLogSession(File spoolFile, int retainedSamples) {
        this.spoolFile = spoolFile;
        this.retainedSamples = retainedSamples;
    }

    /** Creates an uncapped disk-backed session with bounded recent memory. */
    public static PortableLogSession streaming(File spoolFile,
            int retainedSamples) throws IOException {
        if (spoolFile == null) {
            throw new IllegalArgumentException("A spool file is required");
        }
        if (retainedSamples < 1) {
            throw new IllegalArgumentException(
                    "At least one recent sample must be retained");
        }
        File absolute = spoolFile.getAbsoluteFile();
        File parent = absolute.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Log spool folder is unavailable: " + parent);
        }
        if (absolute.exists() && !absolute.delete()) {
            throw new IOException("Old log spool could not be replaced");
        }
        return new PortableLogSession(absolute, retainedSamples);
    }

    public synchronized void append(PortableLogSample sample) {
        if (finished) throw new IllegalStateException("The recording is finished");
        if (sample == null) throw new IllegalArgumentException(
                "A logger sample is required");
        if (spoolFile == null && sampleCount >= MAX_SAMPLES) {
            throw new IllegalStateException("Portable log sample limit reached");
        }
        if (spoolFile != null) {
            try {
                ensureSpoolWriter();
                writeSample(spoolWriter, sample);
                if (sampleCount % 100 == 99) spoolWriter.flush();
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "The live log could not be written to storage", failure);
            }
        }
        samples.addLast(sample);
        sampleCount++;
        if (samples.size() > retainedSamples) samples.removeFirst();
    }

    public synchronized int size() { return sampleCount; }

    public synchronized List<PortableLogSample> snapshot() {
        return Collections.unmodifiableList(
                new ArrayList<PortableLogSample>(samples));
    }

    public synchronized void writeLongFormCsv(Writer writer) throws IOException {
        if (writer == null) throw new IllegalArgumentException(
                "A CSV writer is required");
        writer.write("timestamp_ms,channel_id,channel_name,value,units\n");
        if (spoolFile == null) {
            for (PortableLogSample sample : samples) writeSample(writer, sample);
        } else {
            if (spoolWriter != null) spoolWriter.flush();
            try (BufferedReader input = new BufferedReader(
                    new InputStreamReader(new FileInputStream(spoolFile),
                            StandardCharsets.UTF_8))) {
                char[] buffer = new char[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    writer.write(buffer, 0, count);
                }
            }
        }
    }

    public synchronized void discard() throws IOException {
        IOException failure = null;
        if (spoolWriter != null) {
            try {
                spoolWriter.close();
            } catch (IOException closeFailure) {
                failure = closeFailure;
            }
            spoolWriter = null;
        }
        if (spoolFile != null && spoolFile.exists() && !spoolFile.delete()
                && failure == null) {
            failure = new IOException("Log spool could not be removed");
        }
        if (failure != null) throw failure;
    }

    /** Flush and release the recording without deleting its data. */
    public synchronized void finish() throws IOException {
        finished = true;
        if (spoolWriter != null) {
            spoolWriter.close();
            spoolWriter = null;
        }
    }

    public synchronized void flush() throws IOException {
        if (spoolWriter != null) spoolWriter.flush();
    }

    private void ensureSpoolWriter() throws IOException {
        if (spoolWriter != null) return;
        spoolWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(spoolFile, true),
                StandardCharsets.UTF_8));
    }

    private static void writeSample(Writer writer, PortableLogSample sample)
            throws IOException {
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

    private static String csv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
