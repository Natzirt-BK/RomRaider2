package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ElmSerialLinkTest {
    private static final class Port implements ElmSerialLink.Port {
        boolean open = true;
        boolean stalled;
        boolean bad;
        int closes;
        long nanos;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        public boolean isOpen() { return open; }
        public int read(byte[] value) { return bad ? -1 : 0; }
        public int write(byte[] value, int offset) {
            if (bad) return value.length + 1;
            if (stalled) return 0;
            output.write(value[offset]); return 1;
        }
        public boolean close() { open = false; closes++; return true; }
        ElmSerialLink link() { return new ElmSerialLink(this, () -> nanos, millis -> nanos += millis * 1_000_000L); }
    }
    @Test void partialWritesRetainOffsetsAndCr() throws Exception {
        var port = new Port();
        try (var link = port.link()) { link.write("010C\r".getBytes(StandardCharsets.US_ASCII), 30); }
        assertEquals("010C\r", port.output.toString(StandardCharsets.US_ASCII)); assertEquals(1, port.closes);
    }
    @Test void noDataReadConsumesDeadlineAndStalledWriteTimesOut() throws Exception {
        var port = new Port();
        try (var link = port.link()) {
            assertEquals(0, link.read(new byte[16], 23)); assertEquals(23_000_000L, port.nanos);
            port.stalled = true;
            assertThrows(IOException.class, () -> link.write(new byte[] {1}, 17));
            assertEquals(40_000_000L, port.nanos);
        }
    }
    @Test void disconnectAndImpossibleCountsFail() throws Exception {
        var port = new Port(); var link = port.link(); port.bad = true;
        assertThrows(IOException.class, () -> link.read(new byte[10], 30));
        assertThrows(IOException.class, () -> link.write(new byte[] {1}, 30));
        link.close(); link.close(); assertEquals(1, port.closes);
        assertThrows(IOException.class, () -> link.read(new byte[10], 30));
    }
    @Test void interruptionAndCloseDuringPollStopImmediately() throws Exception {
        var port = new Port(); var link = port.link();
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class, () -> link.read(new byte[1], 100)); assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); link.close(); }
        var closingPort = new Port(); ElmSerialLink[] closing = new ElmSerialLink[1];
        closing[0] = new ElmSerialLink(closingPort, () -> closingPort.nanos, millis -> {
            closingPort.nanos += millis * 1_000_000L;
            try { closing[0].close(); } catch (IOException e) { throw new AssertionError(e); }
        });
        assertThrows(IOException.class, () -> closing[0].read(new byte[1], 100)); assertEquals(1, closingPort.closes);
    }
}
