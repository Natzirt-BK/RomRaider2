/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.definition.Module;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticReadRequestTest {
    private static final Module MODULE = new Module("ecu", new byte[] {0x10}, "Engine", new byte[] {(byte) 0xf0}, false);
    private static List<EcuSwitch> codes() {
        return List.of(new EcuSwitchImpl("D1", "Fixture code", "Synthetic", new EcuAddressImpl("0x000100", 2, 0),
                null, null, null, new EcuDataConvertor[] {new EcuDtcConvertorImpl(0)}));
    }
    private interface Call { Object run(String method, Object[] args); }
    private static LoggerConnection connection(Call call) {
        return (LoggerConnection) Proxy.newProxyInstance(LoggerConnection.class.getClassLoader(), new Class<?>[] {LoggerConnection.class},
                (proxy, method, args) -> {
                    if (!Set.of("sendAddressReads", "close").contains(method.getName())) throw new AssertionError("Unexpected IO: " + method.getName());
                    return call.run(method.getName(), args);
                });
    }
    @SuppressWarnings("unchecked") private static void reply(Object[] args) {
        ((Collection<EcuQuery>) args[0]).forEach(q -> q.setResponse(new byte[] {0, 0}));
    }
    private static DiagnosticReadRequest request(LoggerConnection connection, AtomicBoolean current) {
        return new DiagnosticReadRequest(codes(), 104, new DmRuntimeReadRequest(null, null, current::get), MODULE, () -> connection);
    }

    @Test public void successfulReadClosesBeforeReturningAndCannotBeRepeated() throws Exception {
        List<String> calls = new ArrayList<>();
        DiagnosticReadRequest request = request(connection((method, args) -> {
            calls.add(method); if (method.equals("sendAddressReads")) reply(args); return null;
        }), new AtomicBoolean(true));
        assertTrue(request.read().isEmpty());
        assertEquals(List.of("sendAddressReads", "close"), calls);
        try { request.read(); fail("reused"); } catch (IllegalStateException expected) { }
        assertEquals(2, calls.size());
    }

    @Test public void failingReadClosesAndPreservesCleanupFailureAsSuppressed() throws Exception {
        RuntimeException failure = new IllegalArgumentException("read"), closing = new IllegalStateException("close");
        DiagnosticReadRequest request = request(connection((method, args) -> {
            if (method.equals("close")) throw closing;
            throw failure;
        }), new AtomicBoolean(true));
        try { request.read(); fail("accepted"); }
        catch (RuntimeException actual) { assertSame(failure, actual); assertArrayEquals(new Throwable[] {closing}, actual.getSuppressed()); }
    }

    @Test public void closingFailureCannotBecomeSuccessfulRead() throws Exception {
        DiagnosticReadRequest request = request(connection((method, args) -> {
            if (method.equals("close")) throw new IllegalStateException("close failed");
            reply(args); return null;
        }), new AtomicBoolean(true));
        try { request.read(); fail("accepted"); }
        catch (IllegalStateException expected) { assertEquals("close failed", expected.getMessage()); }
    }

    @Test public void cancelledReplyClosesWithoutInterruptAndRestoresTheFlag() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        DiagnosticReadRequest request = request(connection((method, args) -> {
            if (method.equals("close")) { assertFalse(Thread.currentThread().isInterrupted()); closed.set(true); }
            else { reply(args); Thread.currentThread().interrupt(); }
            return null;
        }), new AtomicBoolean(true));
        try {
            try { request.read(); fail("accepted"); } catch (InterruptedException expected) { }
            assertTrue(closed.get()); assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test public void closedOwnerAndPreCancellationNeverOpenConnection() throws Exception {
        for (boolean interrupted : new boolean[] {false, true}) {
            DiagnosticReadRequest request = new DiagnosticReadRequest(codes(), 104,
                    new DmRuntimeReadRequest(null, null, () -> interrupted), MODULE, () -> { throw new AssertionError("opened"); });
            try {
                if (interrupted) Thread.currentThread().interrupt();
                try { request.read(); fail("accepted"); }
                catch (InterruptedException | IllegalStateException expected) { }
            } finally { Thread.interrupted(); }
        }
    }

    @Test public void ownerChangeDuringConnectionOpeningClosesWithoutReading() throws Exception {
        AtomicBoolean current = new AtomicBoolean(true), closed = new AtomicBoolean();
        DiagnosticReadRequest request = new DiagnosticReadRequest(codes(), 104,
                new DmRuntimeReadRequest(null, null, current::get), MODULE, () -> {
                    current.set(false);
                    return connection((method, args) -> { assertEquals("close", method); closed.set(true); return null; });
                });
        try { request.read(); fail("accepted"); } catch (IllegalStateException expected) { }
        assertTrue(closed.get());
    }

    @Test public void targetAddressIsCapturedBeforeWorkerSubmission() throws Exception {
        byte[] address = {0x10};
        Module module = new Module("ecu", address, "Engine", new byte[] {(byte) 0xf0}, false);
        DiagnosticReadRequest request = new DiagnosticReadRequest(codes(), 104,
                new DmRuntimeReadRequest(null, null, () -> true), module, () -> connection((method, args) -> {
                    if (method.equals("sendAddressReads")) { assertEquals(0x10, ((Module) args[1]).getAddress()[0]); reply(args); }
                    return null;
                }));
        address[0] = 0x20;
        assertTrue(request.read().isEmpty());
    }
}
