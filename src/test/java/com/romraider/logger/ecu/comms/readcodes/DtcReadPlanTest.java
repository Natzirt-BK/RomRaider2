/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.io.protocol.LoggerProtocol;
import com.romraider.logger.ecu.comms.manager.PollingStateImpl;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;

public class DtcReadPlanTest {
    private static final Module MODULE = new Module("ecu", new byte[] {0x10}, "Engine", new byte[] {(byte) 0xf0}, false);

    private static EcuSwitch code(String id) { return code(id, 0); }
    private static EcuSwitch code(String id, int bit) {
        return new EcuSwitchImpl(id, id, "Synthetic", new EcuAddressImpl("0x000100", 2, bit),
                null, null, null, new EcuDataConvertor[] {new EcuDtcConvertorImpl(bit)});
    }
    private static LoggerConnection connection(Consumer<Collection<EcuQuery>> read) {
        return connection(read, MODULE);
    }
    @SuppressWarnings("unchecked")
    private static LoggerConnection connection(Consumer<Collection<EcuQuery>> read, Module module) {
        return (LoggerConnection) Proxy.newProxyInstance(LoggerConnection.class.getClassLoader(),
                new Class<?>[] {LoggerConnection.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("sendAddressReads")) throw new AssertionError("Unexpected IO: " + method.getName());
                    assertSame(module, args[1]);
                    read.accept((Collection<EcuQuery>) args[0]);
                    return null;
                });
    }
    private interface CheckedAction { void run() throws Exception; }
    private static void assertThrows(Class<? extends Exception> expected, CheckedAction action) {
        try { action.run(); }
        catch (Exception failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("Expected " + expected.getName(), failure);
        }
        fail("Expected " + expected.getName());
    }
    private static List<String> selected(int length, String... ids) throws Exception {
        List<EcuSwitch> definitions = Arrays.stream(ids).map(DtcReadPlanTest::code).toList();
        List<String> readIds = new ArrayList<>();
        assertTrue(DtcReadPlan.prepare(definitions, length).read(connection(queries -> {
            for (EcuQuery query : queries) {
                readIds.add(query.getLoggerData().getId());
                query.setResponse(new byte[] {0, 0});
            }
        }), MODULE, () -> {}).isEmpty());
        return readIds;
    }

    @Test public void emptyMissingIdentityAndEmptySupportedRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DtcReadPlan.prepare(List.of(), 104));
        assertThrows(IllegalArgumentException.class, () -> DtcReadPlan.prepare(null, 104));
        assertThrows(IllegalArgumentException.class, () -> DtcReadPlan.prepare(List.of(code("D1")), 0));
        assertThrows(IllegalArgumentException.class, () -> DtcReadPlan.prepare(List.of(code("D256")), 55));
    }

    @Test public void sparseAndReorderedListsKeepExistingExclusiveLimits() throws Exception {
        assertEquals(List.of("D255", "D1"), selected(55, "D488", "D255", "D256", "D1"));
        assertEquals(List.of("D1", "D7"), selected(55, "D1", "D7"));
        for (int length : new int[] {56, 103})
            assertEquals(List.of("D487", "D256", "D1"), selected(length, "D488", "D487", "D256", "D1"));
    }

    @Test public void fullLengthIncludesTheFinalOrOnlyDefinition() throws Exception {
        assertEquals(List.of("D1"), selected(104, "D1"));
        assertEquals(List.of("D488", "custom-code", "D999"), selected(105, "D488", "custom-code", "D999"));
    }

    @Test public void malformedAndDuplicateLimitedIdsFailBeforeReads() {
        for (String id : new String[] {"custom", "D-1", "D01", "D999999999999999999999"})
            assertThrows(IllegalArgumentException.class, () -> DtcReadPlan.prepare(List.of(code(id)), 55));
        assertThrows(IllegalArgumentException.class, () -> DtcReadPlan.prepare(List.of(code("D1"), code("D1")), 104));
        assertThrows(IllegalArgumentException.class, () -> DtcReadPlan.prepare(Arrays.asList(code("D1"), null), 104));
    }

    @Test public void missingShortDuplicateAndUnavailableRepliesCannotReportClean() {
        List<Consumer<Collection<EcuQuery>>> failures = List.of(
                queries -> {},
                queries -> queries.iterator().next().setResponse(new byte[] {0}),
                queries -> queries.iterator().next().setResponse(null),
                queries -> queries.iterator().next().setResponse(new byte[] {0, 0, 0}),
                queries -> { EcuQuery q = queries.iterator().next(); q.setResponse(new byte[] {0, 0}); q.setResponse(new byte[] {0, 0}); },
                queries -> queries.iterator().next().setResponse(new byte[] {-1, -1}));
        for (Consumer<Collection<EcuQuery>> failure : failures)
            assertThrows(InvalidResponseException.class, () -> DtcReadPlan.prepare(List.of(code("D1")), 104)
                    .read(connection(failure), MODULE, () -> {}));
    }

    @Test public void currentMemorizedCombinedAndUnsupportedMarkersRetainMeaning() throws Exception {
        List<EcuSwitch> definitions = List.of(code("D0"), code("D1"), code("D2"), code("D3"), code("D4"));
        byte[][] replies = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {-1, -1}};
        ArrayList<EcuQuery> result = DtcReadPlan.prepare(definitions, 104).read(connection(queries -> {
            int i = 0;
            for (EcuQuery q : queries) q.setResponse(replies[i++]);
        }), MODULE, () -> {});
        assertEquals(List.of("D1", "D2", "D3"), result.stream().map(q -> q.getLoggerData().getId()).toList());
        assertEquals(List.of(1.0, 2.0, 3.0), result.stream().map(EcuQuery::getResponse).toList());
    }

    @Test public void batchBoundariesAre150AndNoEmptyRequestIsSent() throws Exception {
        for (int size : new int[] {1, 150, 151, 300, 301}) {
            List<EcuSwitch> definitions = new ArrayList<>();
            for (int i = 0; i < size; i++) definitions.add(code("D" + i));
            List<Integer> batches = new ArrayList<>();
            DtcReadPlan.prepare(definitions, 104).read(connection(queries -> {
                batches.add(queries.size());
                queries.forEach(q -> q.setResponse(new byte[] {0, 0}));
            }), MODULE, () -> {});
            assertEquals(size, batches.stream().mapToInt(Integer::intValue).sum());
            assertEquals((size + 149) / 150, batches.size());
            assertTrue(batches.stream().allMatch(n -> n > 0 && n <= 150));
        }
    }

    @Test public void ownerChangeAndCancellationStopBeforeAnotherBatch() {
        List<EcuSwitch> definitions = new ArrayList<>();
        for (int i = 0; i < 151; i++) definitions.add(code("D" + i));
        for (boolean interrupt : new boolean[] {false, true}) {
            AtomicInteger calls = new AtomicInteger();
            try {
                DtcReadPlan plan = DtcReadPlan.prepare(definitions, 104);
                assertThrows(interrupt ? InterruptedException.class : IllegalStateException.class, () -> plan.read(connection(queries -> {
                    calls.incrementAndGet();
                    queries.forEach(q -> q.setResponse(new byte[] {0, 0}));
                    if (interrupt) Thread.currentThread().interrupt();
                }), MODULE, () -> { if (!interrupt && calls.get() > 0) throw new IllegalStateException("changed"); }));
                assertEquals(1, calls.get());
            } finally { Thread.interrupted(); }
        }
    }

    @Test public void cancellationAndStaleOwnerBeforeFirstBatchCauseNoIo() {
        LoggerConnection noIo = connection(queries -> { throw new AssertionError("read"); });
        assertThrows(IllegalStateException.class, () -> DtcReadPlan.prepare(List.of(code("D1")), 104)
                .read(noIo, MODULE, () -> { throw new IllegalStateException("stale"); }));
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, () -> DtcReadPlan.prepare(List.of(code("D1")), 104).read(noIo, MODULE, () -> {}));
        } finally { Thread.interrupted(); }
    }

    @Test public void definitionAndAddressChangesCannotReplacePreparedQueries() throws Exception {
        EcuSwitch definition = code("D1");
        List<EcuSwitch> definitions = new ArrayList<>(List.of(definition));
        DtcReadPlan plan = DtcReadPlan.prepare(definitions, 104);
        definitions.clear();
        definition.setAddress(new EcuAddressImpl("0x000500", 2, 0));
        plan.read(connection(queries -> {
            EcuQuery query = queries.iterator().next();
            assertEquals("0x000100", query.getAddresses()[0]);
            query.getAddresses()[0] = "0x000900";
            query.getBytes()[0] = 99;
            assertEquals("0x000100", query.getAddresses()[0]);
            assertEquals(0, query.getBytes()[0]);
            query.setResponse(new byte[] {0, 0});
        }), MODULE, () -> {});
        assertThrows(IllegalStateException.class, () -> plan.read(connection(queries -> { throw new AssertionError("read"); }), MODULE, () -> {}));
    }

    @Test public void realSsmProtocolsDeduplicateAddressesAndPopulateEveryBitQuery() throws Exception {
        for (LoggerProtocol protocol : List.of(
                new com.romraider.io.protocol.ssm.iso9141.SSMLoggerProtocol(),
                new com.romraider.io.protocol.ssm.iso15765.SSMLoggerProtocol())) {
            boolean can = protocol instanceof com.romraider.io.protocol.ssm.iso15765.SSMLoggerProtocol;
            Module module = can ? new Module("ecu", new byte[] {0, 0, 7, (byte) 0xe0}, "Engine",
                    new byte[] {0, 0, 7, (byte) 0xe8}, false) : MODULE;
            EcuSwitch first = code("D1", 0);
            EcuSwitch second = code("D2", 1);
            byte[] original = first.getAddress().getBytes().clone();
            ArrayList<EcuQuery> result = DtcReadPlan.prepare(List.of(first, second), 104).read(connection(queries -> {
                byte[] combined = protocol.constructReadAddressRequest(module, queries);
                byte[] single = protocol.constructReadAddressRequest(module, List.of(queries.iterator().next()));
                assertArrayEquals(single, combined);
                byte[] response = can ? new byte[] {0, 0, 7, (byte) 0xe0, (byte) 0xe8, 1, 2}
                        : new byte[] {(byte) 0x80, (byte) 0xf0, 0x10, 3, (byte) 0xe8, 1, 2, 0x6e};
                protocol.processReadAddressResponses(queries, response, new PollingStateImpl());
            }, module), module, () -> {});
            assertEquals(List.of(1.0, 2.0), result.stream().map(EcuQuery::getResponse).toList());
            assertArrayEquals(original, first.getAddress().getBytes());
        }
    }
}
