/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.openport;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Fixed Mitsubishi engine startup. No arbitrary pins, voltages or ECU writes. */
public final class OpenPortMut2Startup {
    @FunctionalInterface public interface Exchange {
        void exchange(byte[] command, OpenPortStartupResponse response,
                int timeoutMs, boolean cancellable) throws IOException;
    }
    private int requestId;
    private boolean pinMayBeGrounded;

    public void start(Exchange exchange, BooleanSupplier cancelled) throws IOException {
        if (pinMayBeGrounded) throw new IOException("Release the previous MUT-II diagnostic pin before starting.");
        try {
            // Mode 3 omits the inverted-keyword/address exchange of generic OBD.
            ack(exchange, cancelled, "ats3 33 3", "MUT-II initialization mode");
            // SHORT_TO_GROUND=0xFFFFFFFE; the installed driver formats it signed.
            // Mark ownership before submission: even a lost ACK requires release.
            pinMayBeGrounded = true;
            ack(exchange, cancelled, "atv 1 -2", "MUT-II diagnostic pin setup");
            checkCancelled(cancelled);
            int id = nextId();
            send(exchange, "atw3 0 " + id, OpenPortStartupResponse.wake(id), 5000, true);
            checkCancelled(cancelled);
            // Restore the requested polling rate explicitly after autobaud startup.
            ack(exchange, cancelled, "ats3 1 15625", "MUT-II polling baud rate");
        } catch (IOException | RuntimeException failure) {
            try { release(exchange); }
            catch (IOException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public void release(Exchange exchange) throws IOException {
        if (!pinMayBeGrounded) return;
        int id = nextId();
        send(exchange, "atv 1 -1 " + id,
                OpenPortStartupResponse.ack("MUT-II diagnostic pin release", id), 2000, false);
        pinMayBeGrounded = false;
    }

    private void ack(Exchange exchange, BooleanSupplier cancelled, String command,
            String operation) throws IOException {
        checkCancelled(cancelled);
        int id = nextId();
        send(exchange, command + " " + id, OpenPortStartupResponse.ack(operation, id), 2000, true);
        checkCancelled(cancelled);
    }

    private static void send(Exchange exchange, String command, OpenPortStartupResponse response,
            int timeout, boolean cancellable) throws IOException {
        exchange.exchange((command + "\r\n").getBytes(StandardCharsets.US_ASCII), response, timeout, cancellable);
        if (!response.isComplete()) throw new IOException(response.timeoutMessage());
    }

    private int nextId() { requestId = requestId % 65535 + 1; return requestId; }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("Logger stopped");
    }
}
