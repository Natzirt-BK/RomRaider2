/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Single-owner, read-only Mode 01 session. No port discovery or automatic retry. */
public final class Elm327Session implements Closeable {
    /** Implementations must bound every operation and unblock reads/writes on close. */
    public interface Link extends Closeable {
        void write(byte[] command, int timeoutMillis) throws IOException;
        /** 0 means no data during the entire timeout; -1 means disconnected. */
        int read(byte[] buffer, int timeoutMillis) throws IOException;
    }
    public enum Protocol {
        AUTOMATIC(0), J1850_PWM(1), J1850_VPW(2), ISO9141_2(3),
        KWP_5_BAUD(4), KWP_FAST(5), CAN_11_500(6), CAN_29_500(7),
        CAN_11_250(8), CAN_29_250(9);
        private final int number;
        Protocol(int number) { this.number = number; }
    }
    public enum State { NEW, READY, FAILED, CLOSED }
    public enum Reason { TIMEOUT, CANCELLED, DISCONNECTED, INVALID_REPLY, BUSY,
        NOT_READY, UNSUPPORTED_PID, NO_DATA, NEGATIVE_RESPONSE, BUS_ERROR }
    public static final class Failure extends IOException {
        private final Reason reason;
        private final boolean terminal;
        private Failure(Reason reason, String message, boolean terminal) {
            super(message); this.reason = reason; this.terminal = terminal;
        }
        public Reason getReason() { return reason; }
    }
    private static final int QUIET_MS = 20;
    private static final int READ_SLICE_MS = 25;
    private final Link link;
    private final LongSupplier clock;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean linkClosed = new AtomicBoolean();
    private volatile long supported;

    public Elm327Session(Link link) { this(link, System::nanoTime); }
    /** The injectable clock must have System.nanoTime's monotonic/wraparound semantics. */
    public Elm327Session(Link link, LongSupplier clock) {
        this.link = Objects.requireNonNull(link); this.clock = Objects.requireNonNull(clock);
    }
    public State getState() { return state.get(); }

    /** Warm reset preserves host baud. Only adapter settings and Mode 01 PID 00 are sent. */
    public void initialize(Protocol protocol, int timeoutMillis, BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(protocol);
        Budget budget = new Budget(timeoutMillis, cancelled);
        enter(State.NEW);
        try {
            quiet(budget, null, true);
            List<String> banner = lines(exchange("AT WS", budget), "AT WS");
            if (banner.size() != 1 || banner.get(0).equals("?") || banner.get(0).equals("OK")
                    || banner.get(0).contains("ERROR") || banner.get(0).contains("RESET")) {
                throw invalid("Adapter did not acknowledge its warm reset");
            }
            // Banner text is not a capability check. Every required setting must acknowledge.
            for (String command : new String[] {"AT E0", "AT L0", "AT H0", "AT CAF1", "AT TP " + protocol.number}) {
                List<String> reply = lines(exchange(command, budget), command);
                if (reply.size() != 1 || !reply.get(0).equals("OK")) {
                    throw invalid("Adapter rejected required setting: " + command);
                }
            }
            byte[] mask = decode(exchange("0100", budget), 0, 4);
            long next = 0;
            for (byte value : mask) next = (next << 8) | (value & 255);
            supported = next;
            budget.check();
            if (!state.compareAndSet(State.NEW, State.READY)) throw cancelled("Adapter session closed");
        } catch (IOException failure) { throw fail(failure, true); }
        catch (RuntimeException failure) { throw fail(new IOException("Adapter initialization failed", failure), true); }
        finally { active.set(false); }
    }

    /** First supported-PID group only; no assumed channels or invented ECU identity. */
    public boolean supports(int pid) {
        return state.get() == State.READY && pid >= 1 && pid <= 32
                && (supported & (1L << (32 - pid))) != 0;
    }

    public byte[] readMode01(int pid, int width, int timeoutMillis, BooleanSupplier cancelled) throws IOException {
        byte[] command = Elm327Transcript.mode01Command(pid);
        if (width < 1 || width > 32) throw new IllegalArgumentException("Invalid PID width");
        Budget budget = new Budget(timeoutMillis, cancelled);
        enter(State.READY);
        try {
            budget.check();
            if (!supports(pid)) throw new Failure(Reason.UNSUPPORTED_PID, "PID is not advertised by this ECU", false);
            return decode(exchange(new String(command, 0, command.length - 1,
                    java.nio.charset.StandardCharsets.US_ASCII), budget), pid, width);
        } catch (IOException failure) { throw fail(failure, false); }
        catch (RuntimeException failure) { throw fail(new IOException("Adapter operation failed", failure), false); }
        finally { active.set(false); }
    }

    private Elm327Transcript exchange(String command, Budget budget) throws IOException {
        quiet(budget, null, false);
        budget.check();
        link.write(Elm327Transcript.command(command), budget.slice(250));
        budget.check();
        Elm327Transcript reply = new Elm327Transcript();
        byte[] buffer = new byte[256];
        while (!reply.isComplete()) {
            int count = read(buffer, budget, READ_SLICE_MS);
            append(reply, buffer, count);
        }
        // Consume trailing CR/LF and reject delayed/coalesced extra responses.
        quiet(budget, reply, false);
        return reply;
    }

    private void quiet(Budget budget, Elm327Transcript reply, boolean discard) throws IOException {
        long lastData = clock.getAsLong();
        int drained = 0;
        byte[] buffer = new byte[256];
        while (clock.getAsLong() - lastData < QUIET_MS * 1_000_000L) {
            long remaining = QUIET_MS * 1_000_000L - (clock.getAsLong() - lastData);
            int count = read(buffer, budget, (int) Math.max(1, (remaining + 999_999) / 1_000_000));
            if (count == 0) continue;
            lastData = clock.getAsLong();
            drained += count;
            if (drained > Elm327Transcript.MAX_RESPONSE_CHARS) throw invalid("Adapter input did not settle");
            if (discard) continue;
            if (reply != null) append(reply, buffer, count);
            else for (int i = 0; i < count; i++) {
                if (buffer[i] != '\r' && buffer[i] != '\n' && buffer[i] != ' ' && buffer[i] != '\t') {
                    throw invalid("Unexpected adapter data before a command; reconnect required");
                }
            }
        }
        budget.check();
    }

    private int read(byte[] buffer, Budget budget, int maximumWait) throws IOException {
        int count = link.read(buffer, budget.slice(maximumWait));
        budget.check();
        if (count == -1) throw new Failure(Reason.DISCONNECTED, "Adapter disconnected", true);
        if (count < 0 || count > buffer.length) throw invalid("Invalid adapter read length");
        return count;
    }

    private static void append(Elm327Transcript reply, byte[] buffer, int count) throws Failure {
        StringBuilder text = new StringBuilder(count);
        for (int i = 0; i < count; i++) text.append((char) (buffer[i] & 255));
        reply.accept(text);
        if (reply.isMalformed()) throw invalid("Malformed or oversized adapter response; reconnect required");
    }

    private static byte[] decode(Elm327Transcript reply, int pid, int width) throws Failure {
        Elm327Transcript.Result result = reply.mode01(pid, width);
        switch (result.getStatus()) {
            case DATA: return result.getData();
            case NO_DATA: throw new Failure(Reason.NO_DATA, "ECU returned no data", false);
            case NEGATIVE_RESPONSE: throw new Failure(Reason.NEGATIVE_RESPONSE, "ECU rejected this PID", false);
            case BUS_ERROR: throw new Failure(Reason.BUS_ERROR, "Adapter reported a bus error", true);
            case STOPPED: throw cancelled("Adapter stopped the request");
            case AMBIGUOUS: throw invalid("Multiple ECU replies; explicit responder selection is required");
            default: throw invalid("ECU reply did not match the requested service, PID and width");
        }
    }

    private static List<String> lines(Elm327Transcript reply, String command) {
        List<String> lines = new ArrayList<>();
        String echo = command.replace(" ", "");
        for (String line : reply.responseText().toUpperCase(Locale.ROOT).split("[\r\n]+")) {
            String normalized = line.replace(" ", "").replace("\t", "");
            if (!normalized.isEmpty() && !normalized.equals(echo)) lines.add(normalized);
        }
        return lines;
    }

    private void enter(State expected) throws Failure {
        if (!active.compareAndSet(false, true)) throw new Failure(Reason.BUSY, "An adapter operation is already running", false);
        if (state.get() != expected) {
            active.set(false);
            throw new Failure(Reason.NOT_READY, "Adapter session is not " + expected + "; create a new session", false);
        }
    }
    private IOException fail(IOException failure, boolean initializing) {
        if (initializing || !(failure instanceof Failure) || ((Failure) failure).terminal) {
            state.updateAndGet(before -> before == State.CLOSED ? before : State.FAILED);
            try { closeLink(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
        }
        return failure;
    }
    private static Failure invalid(String message) { return new Failure(Reason.INVALID_REPLY, message, true); }
    private static Failure cancelled(String message) { return new Failure(Reason.CANCELLED, message, true); }
    private void closeLink() throws IOException { if (linkClosed.compareAndSet(false, true)) link.close(); }
    @Override public void close() throws IOException { state.set(State.CLOSED); closeLink(); }

    private final class Budget {
        private final long start = clock.getAsLong();
        private final long duration;
        private final BooleanSupplier cancellation;
        Budget(int millis, BooleanSupplier cancellation) {
            if (millis < 1 || millis > 120_000) throw new IllegalArgumentException("Deadline must be 1–120000 ms");
            this.duration = millis * 1_000_000L;
            this.cancellation = Objects.requireNonNull(cancellation);
        }
        void check() throws Failure {
            if (state.get() == State.CLOSED || Thread.currentThread().isInterrupted() || cancellation.getAsBoolean()) {
                throw cancelled("Adapter operation cancelled");
            }
            if (clock.getAsLong() - start >= duration) throw new Failure(Reason.TIMEOUT, "Adapter deadline expired; reconnect required", true);
        }
        int slice(int maximum) throws Failure {
            check();
            long remaining = duration - (clock.getAsLong() - start);
            return (int) Math.max(1, Math.min(maximum, (remaining + 999_999) / 1_000_000));
        }
    }
}
