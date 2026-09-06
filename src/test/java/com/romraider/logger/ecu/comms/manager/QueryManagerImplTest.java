/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QueryManagerImplTest {
    @Test public void queuedSelectionUsesTheLastIntentBeforePolling() throws Exception {
        QueryManagerImpl manager = manager();
        var data = parameter("P1");
        manager.addQuery("fixture", data); manager.removeQuery("fixture", data); flush(manager);
        assertTrue(queries(manager).isEmpty());
        manager.removeQuery("fixture", data); manager.addQuery("fixture", data); flush(manager);
        assertEquals(1, queries(manager).size());
        manager.removeQuery("fixture", data); flush(manager);
        assertTrue(queries(manager).isEmpty());
    }
    @Test public void sameNamedChannelsAndSeparateOwnersDoNotCollide() throws Exception {
        QueryManagerImpl manager = manager();
        var first = parameter("P1"); var second = parameter("P2");
        manager.addQuery("a", first); manager.addQuery("a", second); manager.addQuery("b", first); flush(manager);
        assertEquals(3, queries(manager).size());
        manager.removeQuery("a", first); flush(manager);
        assertEquals(2, queries(manager).size());
    }
    @Test public void reloadedIdReplacesTheOldQueryObjectEvenWhenItsLabelChanges() throws Exception {
        QueryManagerImpl manager = manager();
        var before = parameter("P1");
        var after = new com.romraider.logger.ecu.definition.EcuParameterImpl("P1", "Renamed", "Synthetic",
                new com.romraider.logger.ecu.definition.EcuAddressImpl("000002", 1, -1), null, null, null,
                new com.romraider.logger.ecu.definition.EcuDataConvertor[] {new com.romraider.logger.ecu.definition.EcuParameterConvertorImpl()});
        manager.addQuery("fixture", before); flush(manager);
        manager.removeQuery("fixture", before); manager.addQuery("fixture", after); flush(manager);
        assertEquals(1, queries(manager).size());
        assertTrue(((com.romraider.logger.ecu.comms.query.EcuQuery) queries(manager).values().iterator().next()).getLoggerData() == after);
    }
    private static QueryManagerImpl manager() {
        return new QueryManagerImpl(ecu -> {}, null, new com.romraider.logger.ecu.ui.MessageListener() {
            public void reportStats(String message) {}
            public void reportMessage(String message) {}
            public void reportMessageInTitleBar(String message) {}
            public void reportError(String error) {}
            public void reportError(Exception error) {}
            public void reportError(String error, Exception failure) {}
        }); // Never start a controller, connection, or query-manager thread.
    }
    private static com.romraider.logger.ecu.definition.EcuParameterImpl parameter(String id) {
        return new com.romraider.logger.ecu.definition.EcuParameterImpl(id, "Same display name", "Synthetic",
                new com.romraider.logger.ecu.definition.EcuAddressImpl("000001", 1, -1), null, null, null,
                new com.romraider.logger.ecu.definition.EcuDataConvertor[] {new com.romraider.logger.ecu.definition.EcuParameterConvertorImpl()});
    }
    private static void flush(QueryManagerImpl manager) throws Exception {
        var method = QueryManagerImpl.class.getDeclaredMethod("updateQueryList"); method.setAccessible(true); method.invoke(manager);
    }
    private static java.util.Map<?, ?> queries(QueryManagerImpl manager) throws Exception {
        var field = QueryManagerImpl.class.getDeclaredField("queryMap"); field.setAccessible(true); return (java.util.Map<?, ?>) field.get(manager);
    }
    @Test
    public void serialFallbackRequiresAConfiguredPort() {
        assertFalse(QueryManagerImpl.shouldTrySerialConnection(null));
        assertFalse(QueryManagerImpl.shouldTrySerialConnection(""));
        assertTrue(QueryManagerImpl.shouldTrySerialConnection("/dev/ttyUSB0"));
    }

    @Test
    public void reconnectDelayBacksOffAndStopsAtFiveSeconds() {
        assertEquals(1000L, QueryManagerImpl.nextRetryDelay(0L));
        assertEquals(2000L, QueryManagerImpl.nextRetryDelay(1000L));
        assertEquals(4000L, QueryManagerImpl.nextRetryDelay(2000L));
        assertEquals(5000L, QueryManagerImpl.nextRetryDelay(4000L));
        assertEquals(5000L, QueryManagerImpl.nextRetryDelay(5000L));
    }
}
