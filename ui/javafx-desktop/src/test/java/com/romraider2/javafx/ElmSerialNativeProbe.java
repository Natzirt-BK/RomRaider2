package com.romraider2.javafx;

import com.romraider.portable.logger.Elm327Session;
import java.util.concurrent.atomic.AtomicBoolean;

/** Invoked only by the owned Linux PTY emulator, never with a hardware port. */
public final class ElmSerialNativeProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("/dev/pts/[0-9]+"))
            throw new IllegalArgumentException("Only an emulator-created /dev/pts endpoint is allowed");
        for (int attempt = 1; attempt <= 6; attempt++) {
            try (var session = new Elm327Session(ElmSerialLink.open(args[0], 38400))) {
                session.initialize(attempt % 2 == 0 ? Elm327Session.Protocol.ISO9141_2 : Elm327Session.Protocol.AUTOMATIC, 4000, () -> false);
                if (!session.supports(12)) throw new AssertionError("Synthetic RPM support missing");
                if (attempt == 3 || attempt == 4) {
                    try { session.readMode01(12, 2, 300, () -> false); throw new AssertionError("Invalid fixture accepted"); }
                    catch (Elm327Session.Failure failure) {
                        var expected = attempt == 3 ? Elm327Session.Reason.INVALID_REPLY : Elm327Session.Reason.TIMEOUT;
                        if (failure.getReason() != expected) throw new AssertionError(failure);
                        if (session.getState() != Elm327Session.State.FAILED) throw new AssertionError("Session not retired");
                    }
                } else if (attempt == 5) {
                    AtomicBoolean cancelled = new AtomicBoolean();
                    Thread timer = new Thread(() -> {
                        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        cancelled.set(true);
                    });
                    timer.setDaemon(true); timer.start();
                    try { session.readMode01(12, 2, 1000, cancelled::get); throw new AssertionError("Cancellation fixture accepted"); }
                    catch (Elm327Session.Failure failure) {
                        if (failure.getReason() != Elm327Session.Reason.CANCELLED) throw new AssertionError(failure);
                    } finally { timer.join(2000); }
                } else {
                    byte[] rpm = session.readMode01(12, 2, 1000, () -> false);
                    if (rpm.length != 2 || rpm[0] != 0x1a || (rpm[1] & 255) != 0xf8) throw new AssertionError("Synthetic RPM mismatch");
                }
            }
        }
        System.out.println("ELM_NATIVE_PTY_PASS: fragmented replies, close/reopen, malformed frame, timeout, cancellation and fresh reconnect (no vehicle)");
    }
}
