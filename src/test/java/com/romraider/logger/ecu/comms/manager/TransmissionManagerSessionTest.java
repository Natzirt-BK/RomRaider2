/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.manager;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.EcuQueryImpl;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.NotConnectedException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Injected connections only; never invokes the adapter factory. */
public class TransmissionManagerSessionTest {
    private static EcuQuery query(Object source) {
        EcuDataConvertor converter = new EcuParameterConvertorImpl("raw", "x", "0", -1, "uint8",
                com.romraider.Settings.Endian.BIG, new java.util.HashMap<>(),
                new com.romraider.logger.ecu.ui.handler.dash.GaugeMinMax(0, 255, 1));
        return new EcuQueryImpl(new EcuParameterImpl(source, "test", "Test", "", new EcuAddressImpl("0x002000", 1, -1),
                null, null, null, new EcuDataConvertor[] {converter}));
    }
    private static TransmissionManagerImpl manager(FakeConnection connection) throws Exception {
        TransmissionManagerImpl manager = new TransmissionManagerImpl();
        var field = TransmissionManagerImpl.class.getDeclaredField("connection");
        field.setAccessible(true); field.set(manager, connection); return manager;
    }

    @Test public void unsupportedBackendRejectsDynamicRowsBeforeSending() throws Exception {
        FakeConnection connection = new FakeConnection(); TransmissionManagerImpl manager = manager(connection);
        EcuQuery query = query(new DmInit(new byte[] {3, 0, 0, 1}));
        try { manager.sendQueries(List.of(query), new PollingStateImpl()); fail("Sent stale DimeMod row"); }
        catch (UnsupportedOperationException expected) { }
        assertEquals(0, connection.reads);
        manager.sendQueries(List.of(query(null)), new PollingStateImpl());
        assertEquals(1, connection.reads);
        manager.stop(); assertEquals(1, connection.closes);
    }

    @Test public void unsupportedCachedInitializationCannotFallBackToLegacyDiscovery() throws Exception {
        FakeConnection connection = new FakeConnection();
        DmInit metadata = new DmInit(new byte[] {3, 0, 0, 1});
        DmInitCallback callback = new DmInitCallback() {
            public DmInit getDmInit() { return metadata; }
            public boolean needToInit() { return false; }
            public void callback(DmInit next, boolean force) { fail("Published unsupported metadata"); }
        };
        try { connection.initializeDmSession(callback, null, true); fail("Accepted unsupported cache"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void failedLineCleanupStillClosesAndDetachesConnection() throws Exception {
        FakeConnection connection = new FakeConnection(); connection.failClear = true;
        TransmissionManagerImpl manager = manager(connection);
        try { manager.stop(); fail("Expected synthetic clear failure"); }
        catch (IllegalStateException expected) { }
        assertEquals(1, connection.clears); assertEquals(1, connection.closes);
        manager.stop(); assertEquals(1, connection.closes);
        try { manager.sendQueries(List.of(query(null)), new PollingStateImpl()); fail("Used closed connection"); }
        catch (NotConnectedException expected) { }
    }

    private static final class FakeConnection implements LoggerConnection {
        int reads, clears, closes; boolean failClear;
        public void sendAddressReads(Collection<EcuQuery> queries, Module module, PollingState state) { reads++; }
        public void clearLine() { clears++; if (failClear) throw new IllegalStateException("synthetic clear failure"); }
        public void close() { closes++; }
        public void open(Module module) { throw new AssertionError("open"); }
        public void ecuInit(EcuInitCallback callback, Module module) { throw new AssertionError("identify"); }
        public void dmInit(DmInitCallback callback, Module module) { throw new AssertionError("discovery"); }
        public void ecuReset(Module module, int code) { throw new AssertionError("reset"); }
        public void sendAddressWrites(Map<EcuQuery, byte[]> queries, Module module) { throw new AssertionError("write"); }
    }
}
