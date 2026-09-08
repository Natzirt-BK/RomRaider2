/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.fazecast.jSerialComm.SerialPort;
import com.romraider.portable.logger.Elm327Session;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Explicit serial endpoint; no discovery, baud probing, elevation or automatic retry. */
final class ElmSerialLink implements Elm327Session.Link {
    interface Port {
        boolean isOpen();
        int read(byte[] buffer);
        int write(byte[] buffer, int offset);
        boolean close();
    }
    interface Pause { void sleep(int millis) throws InterruptedException; }
    private final Port port;
    private final LongSupplier clock;
    private final Pause pause;
    private final AtomicBoolean closed = new AtomicBoolean();

    static ElmSerialLink open(String endpoint, int baud) throws IOException {
        if (endpoint == null || endpoint.isBlank() || endpoint.chars().anyMatch(Character::isISOControl))
            throw new IOException("Choose the adapter's serial port explicitly");
        if (baud < 1200 || baud > 2_000_000) throw new IOException("Invalid serial baud rate");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Opening cancelled");
        final SerialPort serial;
        try { serial = SerialPort.getCommPort(endpoint.trim()); }
        catch (RuntimeException failure) { throw new IOException("Invalid serial port: " + endpoint, failure); }
        boolean opened = false;
        try {
            if (!serial.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
                    || !serial.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
                    || !serial.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0))
                throw new IOException("Serial driver rejected 8-N-1, baud or nonblocking mode");
            // Retain jSerialComm's default exclusive lock. The OS controls openPort latency.
            if (!serial.openPort(0)) throw new IOException("Cannot open " + endpoint + "; check permissions and close other adapter applications");
            opened = true;
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Opening cancelled");
            return new ElmSerialLink(new Port() {
                public boolean isOpen() { return serial.isOpen(); }
                public int read(byte[] buffer) { return serial.readBytes(buffer, buffer.length); }
                public int write(byte[] buffer, int offset) { return serial.writeBytes(buffer, buffer.length - offset, offset); }
                public boolean close() { return serial.closePort(); }
            }, System::nanoTime, Thread::sleep);
        } catch (IOException | RuntimeException failure) {
            if ((opened || serial.isOpen()) && !serial.closePort()) failure.addSuppressed(new IOException("Serial port close failed"));
            if (failure instanceof IOException io) throw io;
            throw new IOException("Serial driver failed", failure);
        }
    }
    ElmSerialLink(Port port, LongSupplier clock, Pause pause) {
        this.port = Objects.requireNonNull(port); this.clock = Objects.requireNonNull(clock); this.pause = Objects.requireNonNull(pause);
    }
    @Override public int read(byte[] buffer, int timeoutMillis) throws IOException {
        if (buffer.length == 0) throw new IllegalArgumentException("Empty read buffer");
        long start = clock.getAsLong(); long duration = duration(timeoutMillis);
        do {
            check();
            int count = port.read(buffer);
            check();
            if (count < 0 || count > buffer.length) throw new IOException("Adapter disconnected or invalid serial read");
            if (count > 0) return count;
            rest(start, duration);
        } while (clock.getAsLong() - start < duration);
        return 0;
    }
    @Override public void write(byte[] command, int timeoutMillis) throws IOException {
        long start = clock.getAsLong(); long duration = duration(timeoutMillis);
        int offset = 0;
        while (offset < command.length) {
            check();
            if (clock.getAsLong() - start >= duration) throw new IOException("Serial write deadline expired");
            int count = port.write(command, offset);
            check();
            if (count < 0 || count > command.length - offset) throw new IOException("Adapter disconnected or invalid serial write");
            offset += count;
            if (count == 0) rest(start, duration);
        }
    }
    private void check() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Adapter operation cancelled");
        if (closed.get() || !port.isOpen()) throw new IOException("Serial adapter is closed");
    }
    private void rest(long start, long duration) throws InterruptedIOException {
        long remaining = duration - (clock.getAsLong() - start);
        if (remaining <= 0) return;
        try { pause.sleep((int) Math.min(5, Math.max(1, (remaining + 999_999) / 1_000_000))); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new InterruptedIOException("Adapter operation cancelled");
        }
    }
    private static long duration(int millis) {
        if (millis < 1 || millis > 120_000) throw new IllegalArgumentException("Invalid I/O timeout");
        return millis * 1_000_000L;
    }
    @Override public void close() throws IOException {
        if (closed.compareAndSet(false, true) && !port.close()) throw new IOException("Serial port close failed; unplug adapter before retrying");
    }
}
