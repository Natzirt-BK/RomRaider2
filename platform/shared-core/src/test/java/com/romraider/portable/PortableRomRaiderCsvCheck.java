/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

public final class PortableRomRaiderCsvCheck {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        PortableLogSession memory = new PortableLogSession();
        samples(memory);
        String expected = "Time (msec),Engine Speed (rpm),Battery Voltage (V)\n"
                + "0,750,13.24\n100,800,13.25\n";
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            equal(expected, export(memory));
        } finally { Locale.setDefault(previous); }
        PortableLogSession readBack = PortableLogCsvReader.read(new StringReader(expected));
        check(readBack.size() == 4, "Wide CSV round trip lost values");

        File file = Files.createTempFile("rr2-wide-check-", ".csv.part").toFile();
        try {
            PortableLogSession disk = PortableLogSession.streaming(file, 1);
            samples(disk);
            equal(expected, export(disk)); // flushes active spool
            disk.finish();
            byte[] original = Files.readAllBytes(file.toPath());
            StringWriter archive = new StringWriter();
            PortableRomRaiderCsvWriter.writeSpool(file, archive);
            equal(expected, archive.toString());
            check(disk.snapshot().size() == 1, "Test must exceed retained memory");
            try {
                PortableRomRaiderCsvWriter.writeSpool(file, new Writer() {
                    public void write(char[] text, int offset, int length) throws IOException {
                        throw new IOException("Destination full");
                    }
                    public void flush() { }
                    public void close() { }
                });
                throw new AssertionError("Destination failure was swallowed");
            } catch (IOException expectedFailure) { assertions++; }
            check(Arrays.equals(original, Files.readAllBytes(file.toPath())), "Export changed recovery data");
            disk.discard();

            // More values than the in-memory reader permits; only one is retained.
            disk = PortableLogSession.streaming(file, 1);
            for (int i = 0; i < PortableLogSession.MAX_SAMPLES + 1; i++) {
                disk.append(new PortableLogSample(i, "rpm", "Engine Speed", i, "rpm"));
            }
            disk.finish();
            final long[] lines = {0};
            disk.writeRomRaiderCsv(new Writer() {
                public void write(char[] text, int offset, int length) {
                    for (int i = offset; i < offset + length; i++) if (text[i] == '\n') lines[0]++;
                }
                public void flush() { }
                public void close() { }
            });
            check(lines[0] == PortableLogSession.MAX_SAMPLES + 2L, "Long export was truncated");
            disk.discard();
        } finally { Files.deleteIfExists(file.toPath()); }

        PortableLogSession gaps = new PortableLogSession();
        gaps.append(new PortableLogSample(20, "a", "A", 1, ""));
        gaps.append(new PortableLogSample(20, "a", "A", 2, ""));
        gaps.append(new PortableLogSample(21, "b", "B", Double.NaN, "V"));
        equal("Time (msec),A (),B (V)\n0,1,\n0,2,\n1,,\n", export(gaps));
        PortableLogSession quoted = new PortableLogSession();
        quoted.append(new PortableLogSample(99, "a", "Air, \"entrée\"", 1.125, "%"));
        equal("Time (msec),\"Air, \"\"entrée\"\" (%)\"\n0,1.125\n", export(quoted));
        quoted.append(new PortableLogSample(100, "a", "Changed label", 1, "%"));
        rejects(quoted);
        PortableLogSession backwards = new PortableLogSession();
        backwards.append(new PortableLogSample(2, "a", "A", 1, ""));
        backwards.append(new PortableLogSample(1, "a", "A", 1, ""));
        rejects(backwards);
        PortableLogSession multiline = new PortableLogSession();
        multiline.append(new PortableLogSample(0, "a", "A\nB", 1, ""));
        rejects(multiline);
        equal("Time (msec)\n", export(new PortableLogSession()));

        spoolCase("10,a,\"Air, \"\"entrée\"\"\",1.25,%\r\n11,a,\"Air, \"\"entrée\"\"\",2,%",
                "Time (msec),\"Air, \"\"entrée\"\" (%)\"\n0,1.25\n1,2\n");
        for (String corrupt : new String[] {"1,a,A,1", "1,a,\"A,1,V", "1,a,A,bad,V\n",
                "-1,a,A,1,V\n", "1,a,A,1,V,extra\n", "1,a,\"A\"junk,1,V\n",
                "1,a,A,1,V\n2,a,A", "1,a,A\"oops,1,V\n"}) spoolCase(corrupt, null);
        System.out.println("RomRaider CSV checks passed: " + assertions);
    }
    private static void samples(PortableLogSession session) {
        session.append(new PortableLogSample(500, "rpm", "Engine Speed", 750, "rpm"));
        session.append(new PortableLogSample(500, "volts", "Battery Voltage", 13.24, "V"));
        session.append(new PortableLogSample(600, "rpm", "Engine Speed", 800, "rpm"));
        session.append(new PortableLogSample(600, "volts", "Battery Voltage", 13.25, "V"));
    }
    private static String export(PortableLogSession session) throws IOException {
        StringWriter output = new StringWriter();
        session.writeRomRaiderCsv(output);
        return output.toString();
    }
    private static void rejects(PortableLogSession session) throws IOException {
        try { export(session); throw new AssertionError("Invalid recording accepted"); }
        catch (IOException expected) { assertions++; }
    }
    private static void spoolCase(String input, String expected) throws IOException {
        File file = Files.createTempFile("rr2-spool-check-", ".csv.part").toFile();
        try {
            Files.write(file.toPath(), input.getBytes(StandardCharsets.UTF_8));
            StringWriter output = new StringWriter();
            try {
                PortableRomRaiderCsvWriter.writeSpool(file, output);
                if (expected == null) throw new AssertionError("Corrupt spool accepted");
                equal(expected, output.toString());
            } catch (IOException ex) {
                if (expected != null) throw ex;
                assertions++;
            }
            equal(input, Files.readString(file.toPath()));
        } finally { Files.deleteIfExists(file.toPath()); }
    }
    private static void equal(String expected, String actual) {
        check(expected.equals(actual), "CSV mismatch: " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
