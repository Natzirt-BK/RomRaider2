/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

public final class PortableRecordingRecoveryCheck {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("rr2-recovery-check-");
        File source = folder.resolve("source.csv.part").toFile();
        File temporary = Files.createDirectory(folder.resolve("temporary")).toFile();
        String prefix = "500,a,Engine Speed,750,rpm\n500,b,Battery Voltage,13.2,V\n";
        String expected = "Time (msec),Engine Speed (rpm),Battery Voltage (V)\n0,750,13.2\n";
        try {
            succeeds(source, temporary, prefix.getBytes(StandardCharsets.UTF_8), 0, 2, expected);
            succeeds(source, temporary, prefix.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8), 0, 2, expected);
            // Every byte split, including escaped quotes, an embedded newline and split UTF-8.
            byte[] tail = "600,\"c\n\"\"entrée\"\"\",Air,1.25,%\n".getBytes(StandardCharsets.UTF_8);
            for (int count = 1; count < tail.length; count++) {
                byte[] complete = prefix.getBytes(StandardCharsets.UTF_8);
                byte[] input = Arrays.copyOf(complete, complete.length + count);
                System.arraycopy(tail, 0, input, complete.length, count);
                succeeds(source, temporary, input, count, 2, expected);
            }
            succeeds(source, temporary, (prefix + new String(tail, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8),
                    0, 3, "Time (msec),Engine Speed (rpm),Battery Voltage (V),Air (%)\n0,750,13.2,\n100,,,1.25\n");
            for (String corrupt : new String[] {"", "1,a,A,1,V", "1,a,A,1\n", "1,a,A,bad,V\n",
                    "1,a,A,1,V,extra\n", "1,a,A\"bad,1,V\n", "1,a,\"A\"junk,1,V\n",
                    "1,a,A,1,V\r2,a,A,2,V\n", "-1,a,A,1,V\n", "1,a,\"A\nB\",1,V\n",
                    "501,a,Changed,2,rpm\n", "499,a,Engine Speed,2,rpm\n"}) {
                rejects(source, temporary, corrupt.isEmpty() ? new byte[0] : (prefix + corrupt).getBytes(StandardCharsets.UTF_8),
                        corrupt.equals("1,a,A,1,V"));
            }
            rejects(source, temporary, "1,a,A,1,V".getBytes(StandardCharsets.UTF_8), false);
            byte[] invalid = (prefix + "600,c,C,1,V\n").getBytes(StandardCharsets.UTF_8);
            invalid[invalid.length - 2] = (byte) 0xff;
            rejects(source, temporary, invalid, false);
            Files.write(source.toPath(), invalid);
            try { PortableRomRaiderCsvWriter.writeSpool(source, new StringWriter()); throw new AssertionError("Strict export accepted invalid UTF-8"); }
            catch (IOException expectedFailure) { assertions++; }
            rejects(source, temporary, (prefix + "600,c," + "A".repeat(65_537) + ",1,V\n").getBytes(StandardCharsets.UTF_8), false);
            rejects(source, temporary, (prefix + "600,c,\"" + "A".repeat(1_400_000)).getBytes(StandardCharsets.UTF_8), false);

            Files.writeString(source.toPath(), prefix);
            try (PortableRecordingRecovery.Prepared prepared = PortableRecordingRecovery.prepare(source, temporary)) {
                Files.writeString(source.toPath(), "replacement");
                StringWriter output = new StringWriter(); prepared.writeTo(output);
                check(expected.equals(output.toString()), "Prepared export reread a changed source");
                try { prepared.writeTo(new Writer() {
                    public void write(char[] text, int start, int count) throws IOException { throw new IOException("Full destination"); }
                    public void flush() { }
                    public void close() { }
                }); throw new AssertionError("Destination error hidden"); }
                catch (IOException expectedFailure) { assertions++; }
            }
            check(Files.readString(source.toPath()).equals("replacement"), "Recovery overwrote the source");
            clean(temporary);
            Files.writeString(source.toPath(), prefix);
            PortableRecordingRecovery.Prepared closed = PortableRecordingRecovery.prepare(source, temporary);
            closed.close(); closed.close();
            try { closed.writeTo(new StringWriter()); throw new AssertionError("Closed export accepted"); }
            catch (IOException expectedFailure) { assertions++; }
            Thread.currentThread().interrupt();
            try { PortableRecordingRecovery.prepare(source, temporary); throw new AssertionError("Cancellation ignored"); }
            catch (InterruptedIOException expectedFailure) { check(Thread.currentThread().isInterrupted(), "Interrupt cleared"); }
            finally { Thread.interrupted(); }
            clean(temporary);
            Path link = folder.resolve("link.csv.part");
            try {
                Files.createSymbolicLink(link, source.toPath());
                try { PortableRecordingRecovery.prepare(link.toFile(), temporary); throw new AssertionError("Symlink accepted"); }
                catch (IOException expectedFailure) { assertions++; }
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
                // Windows developer accounts may not have symlink creation authority.
                System.out.println("Symlink fixture unavailable: " + unavailable.getClass().getSimpleName());
            } finally { Files.deleteIfExists(link); }
            clean(temporary);
            System.out.println("Recording recovery checks passed: " + assertions);
        } finally {
            Files.deleteIfExists(source.toPath());
            File[] leftovers = temporary.listFiles();
            if (leftovers != null) for (File file : leftovers) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(temporary.toPath()); Files.deleteIfExists(folder);
        }
    }
    private static void succeeds(File source, File temporary, byte[] bytes, long omitted, long values, String csv) throws Exception {
        Files.write(source.toPath(), bytes);
        try (PortableRecordingRecovery.Prepared prepared = PortableRecordingRecovery.prepare(source, temporary)) {
            check(prepared.sourceBytes() == bytes.length && prepared.omittedBytes() == omitted && prepared.values() == values,
                    "Incorrect recovery report");
            StringWriter output = new StringWriter(); prepared.writeTo(output);
            check(csv.equals(output.toString()), "Recovered CSV differs: " + output);
        }
        check(Arrays.equals(bytes, Files.readAllBytes(source.toPath())), "Recovery changed spool bytes");
        clean(temporary);
    }
    private static void rejects(File source, File temporary, byte[] bytes, boolean validTail) throws Exception {
        Files.write(source.toPath(), bytes);
        try (PortableRecordingRecovery.Prepared ignored = PortableRecordingRecovery.prepare(source, temporary)) {
            if (!validTail) throw new AssertionError("Damaged completed records accepted");
            check(ignored.omittedBytes() == "1,a,A,1,V".length(), "Unterminated record was not reported");
        } catch (IOException failure) { if (validTail) throw failure; assertions++; }
        check(Arrays.equals(bytes, Files.readAllBytes(source.toPath())), "Failed recovery changed source");
        clean(temporary);
    }
    private static void clean(File temporary) {
        check(temporary.list().length == 0, "Recovery temporary files leaked");
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
