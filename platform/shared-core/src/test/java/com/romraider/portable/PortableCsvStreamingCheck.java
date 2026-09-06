/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Also run with a 64 MiB heap: millions of imported values must not be retained. */
public final class PortableCsvStreamingCheck {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        String header = "Time (msec),RPM (rpm),Voltage (V)\n";
        PortableLogCsvReader.Summary summary = summary(header + "0,900,13\n100,,NaN\n200,800,Infinity\n");
        check(summary.values() == 6 && summary.channels().size() == 2, "Summary counts differ");
        PortableLogCsvReader.ChannelSummary rpm = summary.channels().get(0);
        check(rpm.latest().getValue() == 800 && rpm.latest().getTimestampMillis() == 200
                && rpm.finite() == 2 && rpm.missing() == 1 && rpm.minimum() == 800 && rpm.maximum() == 900,
                "Latest/finite/missing/extrema summary differs");
        check(!Double.isFinite(summary.channels().get(1).latest().getValue())
                && summary.channels().get(1).finite() == 1 && summary.channels().get(1).missing() == 2,
                "Unavailable latest value was replaced by an older finite value");
        try { summary.channels().clear(); throw new AssertionError("Mutable summary list"); }
        catch (UnsupportedOperationException expected) { assertions++; }
        check(summary(header).values() == 0, "Header-only file invented values");
        check(summary(header + "0,,\n").channels().stream().allMatch(c -> Double.isNaN(c.minimum()) && Double.isNaN(c.maximum())),
                "Empty measurements invented extrema");
        String longHeader = "timestamp_ms,channel_id,channel_name,value,units\n";
        summary = summary(longHeader + "10,\"a\n1\",\"Air, \"\"entrée\"\"\",1.25,%\r\n"
                + "20,\"a\n1\",\"Air, \"\"entrée\"\"\",2,%");
        check(summary.values() == 2 && summary.channels().get(0).latest().getChannelId().equals("a\n1"), "Quoted long-form import changed");
        check(summary.channels().get(0).minimum() == 1.25 && summary.channels().get(0).maximum() == 2, "Long-form extrema differ");
        PortableLogSession shared = PortableLogCsvReader.read(new StringReader(longHeader + "0,a,A,1,V\n1,a,A,2,V\n"));
        List<PortableLogSample> samples = shared.snapshot();
        check(samples.get(0).getChannelName() == samples.get(1).getChannelName(), "Repeated metadata is not canonicalized");
        check(summary("\ufeff\"Time (sec)\",\"RPM (rpm)\"\r1.0005,10\r2,20\r").channels().get(0).latest().getTimestampMillis() == 2000,
                "BOM/quoted header/CR separators failed");
        check(summary("Time (s),RPM\n1.0005,10\n").channels().get(0).latest().getTimestampMillis() == 1001, "Decimal time rounding changed");
        check(summary("Time,RPM\n9223372036854775807,1\n").channels().get(0).latest().getTimestampMillis() == Long.MAX_VALUE,
                "Exact integer timestamp lost precision");
        check(summary("Time,RPM\n23:59:59.900,1\n00:00:00.100,2\n").channels().get(0).latest().getTimestampMillis() == 200,
                "Midnight rollover changed");
        check(summary(header + "\n" + header + "0,1,2\n \n").values() == 2, "Blank/repeated headers counted as data");
        check(summary("Time,A (V),A (V)\n0,1,2\n").channels().size() == 2, "Duplicate column labels lost distinct identities");

        for (String bad : new String[] {"", "A,B\n1,2", "Time,A\n0,\"1\"junk", "Time,A\n0,1\"2", "Time,A\n0,\"1",
                "Time,A\n0,1,2", "Time,A\n0", "Time,A\nNaN,1", "Time,A\n12:00:NaN,1",
                "Time,A\n9223372036854775808,1", "Time,A\n1e1000000000,1", "Time,A\n1e-1000000000,1",
                "Time,A\n-1,1", "Time,A\n0,bad", "Time,\n0,1",
                longHeader + "0,a,A,1,V\n1,a,Changed,2,V\n", longHeader + "0,a,A,1,V\n1,a,A,2,psi\n"}) rejects(new StringReader(bad));
        rejects(new StringReader("Time," + "A".repeat(4097) + "\n0,1\n"));
        rejects(new StringReader("Time,A\n0," + "1".repeat(65_537)));
        rejects(new Repeated("Time,A\n0,", ",", 1_000_000)); // Must reject before collecting unbounded empty fields.
        StringBuilder tooMany = new StringBuilder("Time");
        for (int i = 0; i < 257; i++) tooMany.append(",A").append(i);
        rejects(new StringReader(tooMany + "\n"));
        StringBuilder longChannels = new StringBuilder(longHeader);
        for (int i = 0; i < 257; i++) longChannels.append("0,id").append(i).append(",A,1,V\n");
        rejects(new StringReader(longChannels.toString()));
        Repeated excessiveColumns = new Repeated("Time,", "A,", 1_000_000);
        rejects(excessiveColumns);
        check(excessiveColumns.readCharacters <= 8192, "Column limit was checked after excess allocation/input work");

        // At the existing full-sample cap, blank lines and repeated headers do not consume values.
        PortableLogSession capped = PortableLogCsvReader.read(new Repeated("Time,A\n", "0,1\n", PortableLogSession.MAX_SAMPLES));
        check(capped.size() == PortableLogSession.MAX_SAMPLES, "Full-sample cap lost valid data");
        capped = null;
        try { PortableLogCsvReader.read(new Repeated("Time,A\n", "0,1\n", PortableLogSession.MAX_SAMPLES + 1L));
            throw new AssertionError("Full-sample value cap ignored"); }
        catch (IOException expected) { assertions++; }
        StringBuilder wideHeader = new StringBuilder("Time");
        StringBuilder wideRow = new StringBuilder("0");
        for (int i = 0; i < 40; i++) { wideHeader.append(",C").append(i); wideRow.append(',').append(i); }
        wideHeader.append('\n'); wideRow.append('\n');
        summary = PortableLogCsvReader.summarize(new Repeated(wideHeader.toString(), wideRow.toString(), 125_000));
        check(summary.values() == 5_000_000 && summary.channels().size() == 40, "Large summary was truncated");
        check(summary.channels().get(39).finite() == 125_000 && summary.channels().get(39).latest().getValue() == 39,
                "Large summary counts/last values differ");
        rejects(new Repeated(wideHeader.toString(), wideRow.toString(), 125_001));
        rejects(new Repeated("Time,A\n", "\n", 1_000_001));
        // Count escaped quotes and every consumed UTF-16 code unit toward the input bound.
        String bigHeader = "Time," + "\"" + "\"\"".repeat(2000) + "\"\n";
        Repeated oversized = new Repeated(bigHeader, bigHeader, 20_000);
        rejects(oversized);
        check(oversized.readCharacters >= 64 * 1024 * 1024L && oversized.readCharacters <= 64 * 1024 * 1024L + 8192,
                "Input bound failed to count escaped quotes or performed excess reads");

        AtomicInteger closed = new AtomicInteger();
        StringReader borrowed = new StringReader("Time,A\n0,1\n") { @Override public void close() { closed.incrementAndGet(); super.close(); } };
        PortableLogCsvReader.summarize(borrowed);
        check(closed.get() == 0, "Parser closed the caller-owned reader"); borrowed.close();
        Thread.currentThread().interrupt();
        try { summary("Time,A\n0,1\n"); throw new AssertionError("Cancellation ignored"); }
        catch (InterruptedIOException expected) { check(Thread.currentThread().isInterrupted(), "Interrupt flag cleared"); }
        finally { Thread.interrupted(); }
        try { PortableLogCsvReader.summarize(new Reader() {
            public int read(char[] b, int offset, int length) throws IOException { throw new IOException("Provider failed"); }
            public void close() { }
        }); throw new AssertionError("Provider failure hidden"); }
        catch (IOException expected) { assertions++; }
        System.out.println("Portable streaming CSV checks passed: " + assertions + " (5,000,000 values; 64 MiB heap task)");
    }

    private static PortableLogCsvReader.Summary summary(String csv) throws IOException {
        return PortableLogCsvReader.summarize(new StringReader(csv));
    }
    private static void rejects(Reader reader) throws IOException {
        try { PortableLogCsvReader.summarize(reader); throw new AssertionError("Invalid/oversized CSV accepted"); }
        catch (IOException expected) { assertions++; }
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static final class Repeated extends Reader {
        private final String prefix, row;
        private final long length;
        private long position;
        long readCharacters;
        Repeated(String prefix, String row, long count) {
            this.prefix = prefix; this.row = row; length = prefix.length() + row.length() * count;
        }
        @Override public int read(char[] target, int offset, int count) {
            if (position == length) return -1;
            int size = (int) Math.min(count, length - position);
            for (int i = 0; i < size; i++, position++) target[offset + i] = position < prefix.length()
                    ? prefix.charAt((int) position) : row.charAt((int) ((position - prefix.length()) % row.length()));
            readCharacters += size;
            return size;
        }
        @Override public void close() { }
    }
}
