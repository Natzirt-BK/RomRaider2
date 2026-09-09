/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.mobile.adapter;

import com.romraider.portable.logger.Elm327Session;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoundedElmStreamLinkTest {
    private static final class Endpoint implements BoundedElmStreamLink.Endpoint {
        final Queue<Integer> bytes = new ArrayDeque<>();
        final ByteArrayOutputStream written = new ByteArrayOutputStream();
        final CountDownLatch connecting = new CountDownLatch(1), writing = new CountDownLatch(1);
        final AtomicInteger closes = new AtomicInteger();
        boolean closed, blockConnect, blockWrite, eof, reply, rejectPermission;
        @Override public synchronized void connect() throws IOException {
            connecting.countDown();
            if (rejectPermission) throw new SecurityException("Permission revoked");
            while (blockConnect && !closed) await();
            if (closed) throw new IOException("Closed");
        }
        @Override public InputStream input() {
            return new InputStream() {
                @Override public int read() throws IOException {
                    byte[] one = new byte[1]; return read(one, 0, 1) == -1 ? -1 : one[0] & 255;
                }
                @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                    synchronized (Endpoint.this) {
                        while (bytes.isEmpty() && !closed && !eof) await();
                        if (closed) throw new IOException("Closed");
                        if (bytes.isEmpty()) return -1;
                        int count = Math.min(length, bytes.size());
                        for (int i = 0; i < count; i++) buffer[offset + i] = (byte) (int) bytes.remove();
                        return count;
                    }
                }
            };
        }
        @Override public OutputStream output() {
            return new OutputStream() {
                @Override public void write(int value) throws IOException { write(new byte[] {(byte) value}); }
                @Override public void write(byte[] command) throws IOException {
                    synchronized (Endpoint.this) {
                        writing.countDown();
                        while (blockWrite && !closed) await();
                        if (closed) throw new IOException("Closed");
                        written.write(command);
                        if (reply) {
                            String request = new String(command, StandardCharsets.US_ASCII);
                            if (request.equals("AT WS\r")) feed("STN test\r>");
                            else if (request.equals("0100\r")) feed("41 00 00 10 00 00\r>");
                            else if (request.equals("010C\r")) feed("41 0C 0F A0\r>");
                            else if (request.startsWith("AT ")) feed("OK\r>");
                            else feed("?\r>");
                        }
                    }
                }
            };
        }
        synchronized void feed(String text) {
            for (byte value : text.getBytes(StandardCharsets.US_ASCII)) bytes.add(value & 255);
            notifyAll();
        }
        synchronized void end() { eof = true; notifyAll(); }
        @Override public synchronized void close() { closed = true; closes.incrementAndGet(); notifyAll(); }
        private void await() throws IOException {
            try { wait(); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IOException("Interrupted", interrupted);
            }
        }
    }

    @Test public void readTimeoutDoesNotLoseTheNextReplyOrMutateAnOldBuffer() throws Exception {
        Endpoint endpoint = new Endpoint();
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            link.open(1000);
            byte[] old = {(byte) 99};
            long start = System.nanoTime();
            assertEquals(0, link.read(old, 30));
            assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(25));
            endpoint.feed("ABC");
            byte[] next = new byte[2];
            assertEquals(2, link.read(next, 1000)); assertArrayEquals(new byte[] {'A', 'B'}, next);
            assertEquals(1, link.read(next, 1000)); assertEquals('C', next[0]);
            assertEquals(99, old[0]);
        }
        assertEquals(1, endpoint.closes.get());
    }

    @Test public void connectionDeadlineClosesTheEndpointAndPreventsReuse() throws Exception {
        Endpoint endpoint = new Endpoint(); endpoint.blockConnect = true;
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            IOException failure = assertThrows(IOException.class, () -> link.open(30));
            assertTrue(failure.getMessage().contains("timed out"));
            assertEquals(1, endpoint.closes.get());
            assertThrows(IOException.class, () -> link.open(1000));
        }
        assertEquals(1, endpoint.closes.get());
    }

    @Test public void closeCancelsPendingConnectWithoutWaitingForItsDeadline() throws Exception {
        Endpoint endpoint = new Endpoint(); endpoint.blockConnect = true;
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            FutureTask<IOException> task = new FutureTask<>(() -> assertThrows(IOException.class, () -> link.open(60_000)));
            new Thread(task).start(); assertTrue(endpoint.connecting.await(1, TimeUnit.SECONDS));
            link.close(); assertNotNull(task.get(1, TimeUnit.SECONDS));
        }
        assertEquals(1, endpoint.closes.get());
    }

    @Test public void writeDeadlineClosesWithoutSendingALaterCommand() throws Exception {
        Endpoint endpoint = new Endpoint(); endpoint.blockWrite = true;
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            link.open(1000);
            assertThrows(IOException.class, () -> link.write(new byte[] {1}, 30));
            assertThrows(IOException.class, () -> link.write(new byte[] {2}, 1000));
            assertEquals(0, endpoint.written.size());
        }
        assertEquals(1, endpoint.closes.get());
    }

    @Test public void closeUnblocksReadAndWriteTogether() throws Exception {
        Endpoint endpoint = new Endpoint(); endpoint.blockWrite = true;
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            link.open(1000);
            FutureTask<IOException> write = new FutureTask<>(() -> assertThrows(IOException.class,
                    () -> link.write(new byte[] {1}, 60_000)));
            FutureTask<Integer> read = new FutureTask<>(() -> link.read(new byte[1], 60_000));
            new Thread(write).start(); new Thread(read).start();
            assertTrue(endpoint.writing.await(1, TimeUnit.SECONDS));
            link.close(); assertNotNull(write.get(1, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(-1), read.get(1, TimeUnit.SECONDS));
        }
    }

    @Test public void concurrentWritesAreRejectedInsteadOfQueued() throws Exception {
        Endpoint endpoint = new Endpoint(); endpoint.blockWrite = true;
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            link.open(1000);
            FutureTask<IOException> task = new FutureTask<>(() -> assertThrows(IOException.class,
                    () -> link.write(new byte[] {1}, 60_000)));
            new Thread(task).start(); assertTrue(endpoint.writing.await(1, TimeUnit.SECONDS));
            assertThrows(IOException.class, () -> link.write(new byte[] {2}, 1000));
            link.close(); task.get(1, TimeUnit.SECONDS);
            assertEquals(0, endpoint.written.size());
        }
    }

    @Test public void inputOverflowClosesRatherThanReturningTruncatedData() throws Exception {
        Endpoint endpoint = new Endpoint();
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            link.open(1000); endpoint.feed("x".repeat(5000));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (endpoint.closes.get() == 0 && System.nanoTime() < deadline) Thread.yield();
            assertEquals(1, endpoint.closes.get());
            IOException failure = assertThrows(IOException.class, () -> link.read(new byte[1], 1000));
            assertTrue(failure.getMessage().contains("overflow"));
        }
    }

    @Test public void remoteEofIsADisconnectNotAReadTimeout() throws Exception {
        Endpoint endpoint = new Endpoint();
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            link.open(1000); endpoint.end();
            assertEquals(-1, link.read(new byte[1], 1000));
            assertThrows(IOException.class, () -> link.write(new byte[] {1}, 1000));
        }
    }

    @Test public void permissionRevocationFailsAndClosesTheSocket() throws Exception {
        Endpoint endpoint = new Endpoint(); endpoint.rejectPermission = true;
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            IOException failure = assertThrows(IOException.class, () -> link.open(1000));
            assertTrue(failure.getCause() instanceof SecurityException);
            assertEquals(1, endpoint.closes.get());
        }
    }

    @Test public void interruptionClosesAndPreservesTheInterruptFlag() throws Exception {
        Endpoint endpoint = new Endpoint();
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            link.open(1000);
            FutureTask<Boolean> task = new FutureTask<>(() -> {
                Thread.currentThread().interrupt();
                assertThrows(IOException.class, () -> link.read(new byte[1], 1000));
                return Thread.currentThread().isInterrupted();
            });
            new Thread(task).start(); assertTrue(task.get(1, TimeUnit.SECONDS));
            assertEquals(1, endpoint.closes.get());
        }
    }

    @Test public void invalidArgumentsDoNotConnectAndClosedLinksCannotReopen() throws Exception {
        Endpoint endpoint = new Endpoint();
        BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint);
        assertThrows(IllegalArgumentException.class, () -> link.open(0));
        assertThrows(IOException.class, () -> link.read(new byte[1], 10));
        assertThrows(IOException.class, () -> link.write(new byte[] {1}, 10));
        assertEquals(1, endpoint.connecting.getCount());
        link.close(); assertThrows(IOException.class, () -> link.open(1000));
    }

    @Test public void sharedSessionValidatesPidSupportAndReadsAcrossTheStreamBridge() throws Exception {
        Endpoint endpoint = new Endpoint(); endpoint.reply = true;
        try (BoundedElmStreamLink link = new BoundedElmStreamLink(endpoint)) {
            link.open(1000);
            try (Elm327Session session = new Elm327Session(link)) {
                session.initialize(Elm327Session.Protocol.ISO9141_2, 5000, () -> false);
                assertTrue(session.supports(12)); assertFalse(session.supports(5));
                assertArrayEquals(new byte[] {15, (byte) 160}, session.readMode01(12, 2, 1000, () -> false));
                assertEquals("AT WS\rAT E0\rAT L0\rAT H0\rAT CAF1\rAT TP 3\r0100\r010C\r",
                        endpoint.written.toString("US-ASCII"));
            }
        }
        assertEquals(1, endpoint.closes.get());
    }
}
