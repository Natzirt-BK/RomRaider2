/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import org.junit.Test;

public class LoggerDefinitionSourceTest {
    @Test public void capturedBytesSurviveSourceReplacementAndReturnedArrayEdits() throws Exception {
        Path file = Files.createTempFile("rr2-definition-snapshot-", ".xml");
        try {
            Files.write(file, new byte[] {1, 2, 3});
            LoggerDefinitionSource source = LoggerDefinitionSource.read(file.toString());
            Files.write(file, new byte[] {4}); byte[] copy = source.bytes(); copy[0] = 99;
            assertArrayEquals(new byte[] {1, 2, 3}, source.bytes());
            assertEquals(file.toAbsolutePath().normalize().toString(), source.path);
        } finally { Files.delete(file); }
    }
    @Test public void streamLimitRejectsRatherThanTruncates() throws Exception {
        assertArrayEquals(new byte[] {1, 2}, LoggerDefinitionSource.read("fixture", new ByteArrayInputStream(new byte[] {1, 2}), 2).bytes());
        for (byte[] bytes : new byte[][] {new byte[0], new byte[3]}) {
            try { LoggerDefinitionSource.read("fixture", new ByteArrayInputStream(bytes), 2); fail("Accepted invalid size"); }
            catch (IOException expected) { }
        }
    }
    @Test public void interruptionStopsCapture() throws Exception {
        Thread.currentThread().interrupt();
        try {
            try { LoggerDefinitionSource.read("fixture", new ByteArrayInputStream(new byte[] {1}), 2); fail("Ignored cancellation"); }
            catch (InterruptedIOException expected) { }
        } finally { Thread.interrupted(); }
    }
}
