/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.logger.Elm327Session;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deterministic transcripts only: never enumerates or opens a serial port. */
public final class PortableElm327SessionCheck {
    private static final class Fake implements Elm327Session.Link {
        long nanos;
        int closes;
        int chunk = 256;
        String overrideCommand;
        String overrideReply;
        IOException io;
        boolean closeFails;
        Runnable onRead;
        final ArrayDeque<Byte> bytes = new ArrayDeque<>();
        final List<String> commands = new ArrayList<>();
        void enqueue(String value) { for (byte b : value.getBytes(StandardCharsets.US_ASCII)) bytes.add(b); }
        @Override public void write(byte[] value, int timeout) throws IOException {
            if (io != null) throw io;
            String command = new String(value, StandardCharsets.US_ASCII);
            require(command.endsWith("\r") && !command.contains("\n"), "CR framing");
            command = command.substring(0, command.length() - 1);
            commands.add(command);
            String reply = command.equals("AT WS") ? "AT WS\rSTN1170 v5.6\r>"
                    : command.startsWith("AT ") ? command + "\rOK\r>"
                    : command.equals("0100") ? "SEARCHING...\rBUS INIT: ERROR\r>"
                    : "41 0C 1A F8\r>";
            // PIDs 05, 0C and 0D only.
            if (command.equals("0100")) reply = "BUS INIT: ...OK\r41 00 08 18 00 00\r>\r\n";
            if (command.equals(overrideCommand)) reply = overrideReply;
            if (reply != null) enqueue(reply);
        }
        @Override public int read(byte[] target, int timeout) throws IOException {
            if (io != null) throw io;
            if (onRead != null) onRead.run();
            if (bytes.isEmpty()) { nanos += timeout * 1_000_000L; return 0; }
            int count = Math.min(Math.min(bytes.size(), target.length), chunk);
            for (int i = 0; i < count; i++) target[i] = bytes.remove();
            return count;
        }
        @Override public void close() throws IOException {
            closes++;
            if (closeFails) throw new IOException("close fixture");
        }
        Elm327Session session() { return new Elm327Session(this, () -> nanos); }
    }
    public static void main(String[] args) throws Exception {
        for (int chunk : new int[] {1, 2, 7, 256}) {
            Fake link = new Fake(); link.chunk = chunk;
            link.enqueue("stale reply>");
            try (Elm327Session session = ready(link)) {
                require(link.commands.equals(Arrays.asList("AT WS", "AT E0", "AT L0", "AT H0", "AT CAF1", "AT TP 0", "0100")), "initialization order");
                require(session.supports(5) && session.supports(12) && session.supports(13), "supported mask");
                require(!session.supports(0) && !session.supports(32) && !session.supports(256), "unsupported mask");
                expect(Elm327Session.Reason.UNSUPPORTED_PID, () -> session.readMode01(6, 1, 1000, () -> false));
                require(link.commands.size() == 7, "unsupported PID sent");
                require(Arrays.equals(session.readMode01(12, 2, 1000, () -> false), new byte[] {0x1a, (byte) 0xf8}), "RPM data");
            }
            require(link.closes == 1, "close once");
        }
        for (String bad : new String[] {"NOT OK>", "AT E0>", "OK\rERROR>", "OK>late>", "OK>>"}) {
            Fake link = new Fake(); link.overrideCommand = "AT E0"; link.overrideReply = bad;
            Elm327Session session = link.session();
            expect(Elm327Session.Reason.INVALID_REPLY, () -> session.initialize(Elm327Session.Protocol.AUTOMATIC, 2000, () -> false));
            failed(session, link);
            require(!link.commands.contains("0100"), "ECU command after rejected settings");
        }
        for (String bad : new String[] {"41 0D 01>", "41 0C 01>", "41 0C 1A F8\r41 0C 1A F8>", "41 0C 1A F8>\r41 0C 1A F8>", "x".repeat(4097)}) {
            Fake link = new Fake(); Elm327Session session = ready(link);
            link.overrideCommand = "010C"; link.overrideReply = bad;
            expect(Elm327Session.Reason.INVALID_REPLY, () -> session.readMode01(12, 2, 1000, () -> false));
            failed(session, link);
        }
        for (String absent : new String[] {null, "41 0C 1A F8"}) {
            Fake link = new Fake(); Elm327Session session = ready(link);
            link.overrideCommand = "010C"; link.overrideReply = absent;
            expect(Elm327Session.Reason.TIMEOUT, () -> session.readMode01(12, 2, 150, () -> false));
            failed(session, link);
        }
        Fake synchronizedLink = new Fake();
        try (Elm327Session session = ready(synchronizedLink)) {
            for (String absent : new String[] {"NO DATA>", "7F 01 12>"}) {
                synchronizedLink.overrideCommand = "010C"; synchronizedLink.overrideReply = absent;
                expect(absent.startsWith("NO") ? Elm327Session.Reason.NO_DATA : Elm327Session.Reason.NEGATIVE_RESPONSE,
                        () -> session.readMode01(12, 2, 1000, () -> false));
                require(session.getState() == Elm327Session.State.READY, "synchronized reply retired session");
                synchronizedLink.overrideCommand = null;
                require(session.readMode01(12, 2, 1000, () -> false).length == 2, "recovery after no data");
            }
            synchronizedLink.enqueue("41 0C 00 00>");
            int writes = synchronizedLink.commands.size();
            expect(Elm327Session.Reason.INVALID_REPLY, () -> session.readMode01(12, 2, 1000, () -> false));
            require(synchronizedLink.commands.size() == writes, "sent command into stale input");
        }
        Fake cancel = new Fake(); Elm327Session cancelled = ready(cancel);
        expect(Elm327Session.Reason.CANCELLED, () -> cancelled.readMode01(12, 2, 1000, () -> true));
        require(cancel.commands.size() == 7, "cancelled command sent"); failed(cancelled, cancel);
        Fake mid = new Fake(); Elm327Session midSession = ready(mid);
        AtomicBoolean stop = new AtomicBoolean(); mid.onRead = () -> stop.set(true);
        expect(Elm327Session.Reason.CANCELLED, () -> midSession.readMode01(12, 2, 1000, stop::get));
        failed(midSession, mid);
        Fake interrupted = new Fake(); Elm327Session interruptedSession = ready(interrupted);
        Thread.currentThread().interrupt();
        try {
            expect(Elm327Session.Reason.CANCELLED, () -> interruptedSession.readMode01(12, 2, 1000, () -> false));
            require(Thread.currentThread().isInterrupted(), "interrupt swallowed");
        } finally { Thread.interrupted(); }
        failed(interruptedSession, interrupted);
        Fake io = new Fake(); Elm327Session ioSession = ready(io); io.io = new IOException("read fixture"); io.closeFails = true;
        try { ioSession.readMode01(12, 2, 1000, () -> false); throw new AssertionError("I/O accepted"); }
        catch (IOException failure) { require(failure == io.io && failure.getSuppressed().length == 1, "lost failure or close failure"); }
        require(io.closes == 1 && ioSession.getState() == Elm327Session.State.FAILED, "I/O cleanup");
        Fake deadline = new Fake(); Elm327Session deadlineSession = deadline.session();
        expect(Elm327Session.Reason.TIMEOUT, () -> deadlineSession.initialize(Elm327Session.Protocol.AUTOMATIC, 100, () -> false));
        failed(deadlineSession, deadline);
        Fake wrap = new Fake(); wrap.nanos = Long.MAX_VALUE - 30_000_000L;
        try (Elm327Session session = ready(wrap)) { require(session.supports(12), "clock wraparound"); }
        Fake concurrent = new Fake(); Elm327Session concurrentSession = ready(concurrent);
        concurrent.onRead = () -> {
            concurrent.onRead = null;
            try { expect(Elm327Session.Reason.BUSY, () -> concurrentSession.readMode01(12, 2, 100, () -> false)); }
            catch (Exception e) { throw new AssertionError(e); }
        };
        concurrentSession.readMode01(12, 2, 1000, () -> false);
        concurrentSession.close(); concurrentSession.close(); require(concurrent.closes == 1, "idempotent close");
        Fake callback = new Fake(); Elm327Session callbackSession = ready(callback);
        try { callbackSession.readMode01(12, 2, 1000, () -> { throw new IllegalStateException("fixture"); }); throw new AssertionError("callback accepted"); }
        catch (IOException expected) { require(expected.getCause() instanceof IllegalStateException, "callback cause"); }
        failed(callbackSession, callback);
        System.out.println("Portable ELM session checks passed");
    }
    private static Elm327Session ready(Fake link) throws IOException {
        Elm327Session session = link.session(); session.initialize(Elm327Session.Protocol.AUTOMATIC, 2000, () -> false); return session;
    }
    private static void failed(Elm327Session session, Fake link) throws Exception {
        require(session.getState() == Elm327Session.State.FAILED && link.closes == 1, "failed session not retired");
        int writes = link.commands.size();
        expect(Elm327Session.Reason.NOT_READY, () -> session.readMode01(12, 2, 1000, () -> false));
        require(writes == link.commands.size(), "failed session reused");
        session.close(); require(link.closes == 1, "failed session closed twice");
    }
    private interface Operation { void run() throws Exception; }
    private static void expect(Elm327Session.Reason reason, Operation action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + reason); }
        catch (Elm327Session.Failure failure) { require(failure.getReason() == reason, "Expected " + reason + ", got " + failure.getReason()); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
