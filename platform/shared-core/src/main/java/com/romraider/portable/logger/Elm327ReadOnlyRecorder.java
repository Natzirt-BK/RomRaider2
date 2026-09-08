/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Small qualification recorder, deliberately separate from enhanced SSM/MUT-II logging. */
public final class Elm327ReadOnlyRecorder {
    public enum Channel {
        RPM(12, 2, "Engine Speed", "rpm"),
        COOLANT(5, 1, "Coolant Temperature", "C"),
        SPEED(13, 1, "Vehicle Speed", "km/h");
        public final int pid;
        public final int width;
        public final String label;
        public final String units;
        Channel(int pid, int width, String label, String units) {
            this.pid = pid; this.width = width; this.label = label; this.units = units;
        }
        public double decode(byte[] bytes) {
            if (bytes.length != width) throw new IllegalArgumentException("Wrong PID width");
            int a = bytes[0] & 255;
            switch (this) {
                case RPM: return (a * 256 + (bytes[1] & 255)) / 4.0;
                case COOLANT: return a - 40;
                default: return a;
            }
        }
    }
    public static final class Progress {
        public final long elapsedMillis;
        public final int rows;
        public final int validValues;
        public final int missingValues;
        public final List<Channel> channels;
        /** Missing replies are NaN, never zero or a previous reading. */
        public final List<Double> values;
        Progress(long elapsed, int rows, int valid, int missing, List<Channel> channels, List<Double> values) {
            this.elapsedMillis = elapsed; this.rows = rows; this.validValues = valid; this.missingValues = missing;
            this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }
    }
    public interface Pause { void sleep(int millis) throws InterruptedException; }
    private final LongSupplier clock;
    private final Pause pause;
    public Elm327ReadOnlyRecorder() { this(System::nanoTime, Thread::sleep); }
    public Elm327ReadOnlyRecorder(LongSupplier clock, Pause pause) {
        this.clock = Objects.requireNonNull(clock); this.pause = Objects.requireNonNull(pause);
    }
    /** Caller owns the READY session and writer. Each completed row is flushed before notification. */
    public Progress record(Elm327Session session, Writer writer, int seconds,
            BooleanSupplier cancelled, Consumer<Progress> updates) throws IOException {
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("Record for 1–300 seconds");
        if (session.getState() != Elm327Session.State.READY) throw new IOException("Adapter session is not ready");
        List<Channel> channels = new ArrayList<>();
        for (Channel channel : Channel.values()) if (session.supports(channel.pid)) channels.add(channel);
        if (channels.isEmpty()) throw new IOException("ECU advertises none of the test PIDs: RPM, coolant or speed");
        writer.write("Time (msec)");
        for (Channel channel : channels) writer.write("," + channel.label + " (" + channel.units + ")");
        writer.write("\r\n"); writer.flush();
        long start = clock.getAsLong();
        int rows = 0, valid = 0, missing = 0, emptyCycles = 0;
        Progress last = new Progress(0, 0, 0, 0, channels, Collections.nCopies(channels.size(), Double.NaN));
        updates.accept(last);
        recording: while (!cancelled.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
            long cycle = clock.getAsLong();
            if (cycle - start >= seconds * 1_000_000_000L) break;
            List<Double> values = new ArrayList<>();
            int cycleValid = 0;
            for (Channel channel : channels) {
                long remaining = seconds * 1000L - (clock.getAsLong() - start) / 1_000_000;
                // Do not start a query at the recording deadline or write a partial row.
                if (remaining < 100) break recording;
                try {
                    values.add(channel.decode(session.readMode01(channel.pid, channel.width,
                            (int) Math.min(1500, remaining), cancelled)));
                    cycleValid++;
                } catch (Elm327Session.Failure failure) {
                    if (failure.getReason() != Elm327Session.Reason.NO_DATA
                            && failure.getReason() != Elm327Session.Reason.NEGATIVE_RESPONSE) throw failure;
                    values.add(Double.NaN);
                }
            }
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) break;
            long elapsed = (clock.getAsLong() - start) / 1_000_000;
            StringBuilder line = new StringBuilder(Long.toString(elapsed));
            for (double value : values) {
                line.append(',');
                if (Double.isFinite(value)) line.append(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString());
            }
            writer.write(line.append("\r\n").toString()); writer.flush();
            rows++; valid += cycleValid; missing += channels.size() - cycleValid;
            last = new Progress(elapsed, rows, valid, missing, channels, values);
            updates.accept(last);
            emptyCycles = cycleValid == 0 ? emptyCycles + 1 : 0;
            if (emptyCycles >= 5) throw new IOException("Five consecutive samples contained no valid ECU data; recording stopped");
            long delay = 250 - (clock.getAsLong() - cycle) / 1_000_000;
            if (delay > 0) try { pause.sleep((int) delay); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
        return last;
    }
}
