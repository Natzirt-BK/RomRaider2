/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.io.connection;

import com.romraider.Settings;
import com.romraider.util.SettingsManager;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.manager.PollingStateImpl;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.EcuQueryImpl;
import com.romraider.logger.ecu.comms.query.SSMEcuInit;
import com.romraider.logger.ecu.comms.query.InitializationAttempt;
import com.romraider.logger.ecu.comms.query.dimemod.DmCacheBinding;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.romraider.logger.ecu.comms.io.connection.SSMDmDiscoveryTest.*;

/** Real native framing, synthetic ECU replies only; polling must verify its own connection. */
public class SSMDmSessionTest {
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    private static void assertThrows(Class<? extends Exception> expected, CheckedAction action) throws Exception {
        try { action.run(); }
        catch (Exception failure) { if (expected.isInstance(failure)) return; throw failure; }
        fail("Expected " + expected.getSimpleName());
    }
    private static Fixture fixture(boolean can, int minor) {
        Fixture f = new Fixture(can); f.block = discovery(minor); return f;
    }
    private static DmInit cached(Fixture f) {
        String protocol = f.can ? "com.romraider.io.protocol.ssm.iso15765.SSMProtocol"
                : "com.romraider.io.protocol.ssm.iso9141.SSMProtocol";
        return new DmInit(f.block, new DmCacheBinding(DmCacheBinding.route(SettingsManager.getSettings(), protocol),
                new SSMEcuInit(f.identity), f.module, f.startAddress, f.block.length));
    }

    @Test public void initialDiscoveryCarriesFrozenProvenanceOnBothTransports() throws Exception {
        for (boolean can : new boolean[] {false, true}) {
            Fixture f = fixture(can, 3); f.handshake = true;
            f.connection.ecuInit(next -> {}, f.module);
            Callback result = new Callback(null);
            f.connection.initializeDmSession(result, f.module, true);
            assertEquals(2, f.writes); assertEquals(1, f.runtimeReads);
            assertNotNull(result.value.getCacheBinding());
            assertEquals(f.startAddress, result.value.getCacheBinding().address());
            assertArrayEquals(f.block, result.value.getDmInitBytes());
            assertTrue(result.value.getCacheBinding().matchesEcu(new SSMEcuInit(f.identity)));
            f.identity[10] = 7;
            assertFalse(result.value.getCacheBinding().matchesEcu(new SSMEcuInit(f.identity)));
            assertSame(result.value.getCacheBinding(), result.value.metadataSnapshot().getCacheBinding());
        }
    }

    @Test public void validReconnectVerifiesMetadataWithoutDiscoveryWrites() throws Exception {
        for (boolean can : new boolean[] {false, true}) for (int minor : new int[] {0, 3}) {
            Fixture f = fixture(can, minor); DmInit previous = cached(f);
            int[] errors = new int[minor == 0 ? 1 : 8]; errors[0] = 1;
            previous.updateRuntimeData(0, 1, errors, errors);
            f.connection.ecuInit(next -> {}, f.module);
            Callback result = new Callback(previous);
            f.connection.initializeDmSession(result, f.module, false);
            assertEquals(0, f.writes); assertEquals(1, f.runtimeReads);
            assertEquals(f.block.length, f.readOffset);
            assertNotSame(previous, result.value);
            assertSame(previous.getCacheBinding(), result.value.getCacheBinding());
            assertArrayEquals(errors, previous.getRuntimeCurrentErrors());
            assertEquals(1, previous.getRuntimeActiveInputs());
        }
    }

    @Test public void changedIdOrSameIdPayloadRejectsBeforeMetadataAndRuntime() throws Exception {
        for (boolean can : new boolean[] {false, true}) for (int index : new int[] {3, 10}) {
            Fixture f = fixture(can, 3); DmInit previous = cached(f); f.identity[index] = 1;
            assertThrows(InvalidResponseException.class, () -> f.connection.verifyDmSession(previous, f.module));
            assertEquals(1, f.requests.size()); assertEquals(0, f.readOffset);
            assertEquals(0, f.runtimeReads); assertEquals(0, f.writes);
        }
    }

    @Test public void changedMetadataRejectsWithoutRuntimeOrImplicitRediscovery() throws Exception {
        for (boolean can : new boolean[] {false, true}) {
            Fixture f = fixture(can, 3); DmInit previous = cached(f); f.block[3] ^= 1;
            f.connection.ecuInit(next -> {}, f.module);
            assertThrows(InvalidResponseException.class,
                    () -> f.connection.initializeDmSession(new Callback(previous), f.module, true));
            assertEquals(0, f.runtimeReads); assertEquals(0, f.writes);
        }
    }

    @Test public void wrongModuleTransportAndUnboundMetadataRejectBeforeIo() throws Exception {
        Fixture f = fixture(false, 3); DmInit previous = cached(f);
        Module other = new Module("TCU", new byte[] {0x18}, "Other", new byte[] {(byte) 0xf0}, false);
        assertThrows(InvalidResponseException.class, () -> f.connection.verifyDmSession(previous, other));
        assertThrows(InvalidResponseException.class, () -> f.connection.verifyDmSession(new DmInit(f.block), f.module));
        Fixture can = fixture(true, 3);
        assertThrows(InvalidResponseException.class, () -> can.connection.verifyDmSession(previous, can.module));
        assertTrue(f.requests.isEmpty()); assertTrue(can.requests.isEmpty());
    }

    @Test public void adapterChangeRejectsFrozenRouteBeforeIo() throws Exception {
        Settings settings = SettingsManager.getSettings(); String port = settings.getLoggerPort();
        Fixture f = fixture(false, 3); DmInit previous = cached(f);
        try {
            settings.setLoggerPort("synthetic-other-adapter");
            assertThrows(IllegalStateException.class, () -> f.connection.verifyDmSession(previous, f.module));
            Fixture next = fixture(false, 3);
            assertThrows(InvalidResponseException.class, () -> next.connection.verifyDmSession(previous, next.module));
            assertTrue(f.requests.isEmpty()); assertTrue(next.requests.isEmpty());
        } finally { settings.setLoggerPort(port); }
    }

    @Test public void absentCacheWithoutDiscoveryPermissionDoesNotNegotiate() throws Exception {
        Fixture f = fixture(false, 3); f.connection.ecuInit(next -> {}, f.module);
        f.requests.clear(); Callback result = new Callback(null);
        f.connection.initializeDmSession(result, f.module, false);
        assertEquals(0, result.calls); assertNull(result.value); assertTrue(f.requests.isEmpty());
    }

    @Test public void changedModuleAfterIdentificationCannotStartDiscovery() throws Exception {
        Fixture f = fixture(false, 3); f.connection.ecuInit(next -> {}, f.module); f.requests.clear();
        Module other = new Module("TCU", new byte[] {0x18}, "Other", new byte[] {(byte) 0xf0}, false);
        assertThrows(InvalidResponseException.class,
                () -> f.connection.initializeDmSession(new Callback(null), other, true));
        assertTrue(f.requests.isEmpty());
    }

    @Test public void pollingVerifiesEachConnectionOnceAndPreservesFastPolling() throws Exception {
        for (boolean can : new boolean[] {false, true}) {
            Fixture first = fixture(can, 3); DmInit metadata = cached(first);
            metadata.updateRuntimeData(0, 1, new int[8], new int[8]);
            EcuQuery query = new EcuQueryImpl(metadata.getEcuParams().iterator().next());
            for (Fixture f : new Fixture[] {first, fixture(can, 3)}) {
                f.pollAllowed = true;
                PollingState state = new PollingStateImpl(); state.setFastPoll(true);
                f.connection.sendAddressReads(List.of(query), f.module, state);
                int verificationRequests = f.requests.size();
                assertTrue(verificationRequests > 1); assertEquals(1, f.polls);
                assertTrue(state.isFastPoll());
                f.connection.sendAddressReads(List.of(query), f.module, state);
                assertEquals(verificationRequests, f.requests.size()); assertEquals(2, f.polls);
                f.connection.close();
                assertThrows(IllegalStateException.class, () -> f.connection.sendAddressReads(List.of(query), f.module, state));
                assertEquals(2, f.polls); assertEquals(0, f.writes);
            }
        }
    }

    @Test public void changedMetadataOnPollingConnectionCannotSendDynamicQueries() throws Exception {
        Fixture f = fixture(false, 3); DmInit previous = cached(f);
        previous.updateRuntimeData(0, 1, new int[8], new int[8]); f.block[3] ^= 1;
        EcuQuery query = new EcuQueryImpl(previous.getEcuParams().iterator().next());
        assertThrows(InvalidResponseException.class,
                () -> f.connection.sendAddressReads(List.of(query), f.module, new PollingStateImpl()));
        assertEquals(0, f.polls); assertEquals(0, f.runtimeReads); assertEquals(0, f.writes);
    }

    @Test public void expiredAttemptDuringVerificationCannotReadRuntimeOrPublish() throws Exception {
        Fixture f = fixture(false, 3); DmInit previous = cached(f);
        f.connection.ecuInit(next -> {}, f.module);
        InitializationAttempt attempt = new InitializationAttempt();
        f.onMetadataRead = attempt::close; Callback result = new Callback(previous);
        assertThrows(IllegalStateException.class,
                () -> f.connection.initializeDmSession(attempt.bind(result), f.module, false));
        assertEquals(0, result.calls); assertSame(previous, result.value);
        assertEquals(0, f.runtimeReads); assertEquals(0, f.writes);
    }

    @Test public void interruptedVerificationStopsBeforeIo() throws Exception {
        Fixture f = fixture(false, 3); DmInit previous = cached(f);
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedException.class, () -> f.connection.verifyDmSession(previous, f.module)); }
        finally { Thread.interrupted(); }
        assertTrue(f.requests.isEmpty());
    }

    @Test public void malformedVerificationReplyCannotAuthorizeRuntimeOrPolling() throws Exception {
        for (boolean can : new boolean[] {false, true}) {
            Fixture f = fixture(can, 3); DmInit previous = cached(f);
            f.fault = Fault.WRONG_TYPE; f.faultOnRequest = 2;
            assertThrows(InvalidResponseException.class, () -> f.connection.verifyDmSession(previous, f.module));
            assertEquals(0, f.runtimeReads); assertEquals(0, f.polls); assertEquals(0, f.writes);
        }
    }

    @Test public void unboundDynamicChannelsCannotBypassThePollingGate() throws Exception {
        Fixture f = fixture(false, 3); DmInit previous = new DmInit(f.block);
        previous.updateRuntimeData(0, 1, new int[8], new int[8]);
        EcuQuery query = new EcuQueryImpl(previous.getEcuParams().iterator().next());
        assertThrows(InvalidResponseException.class,
                () -> f.connection.sendAddressReads(List.of(query), f.module, new PollingStateImpl()));
        assertTrue(f.requests.isEmpty()); assertEquals(0, f.polls);
    }

    @Test public void moduleWireIdentityAndBindingInputsAreFrozen() throws Exception {
        Fixture f = fixture(false, 3); DmInit previous = cached(f);
        f.module.getTester()[0] ^= 1;
        assertThrows(InvalidResponseException.class, () -> f.connection.verifyDmSession(previous, f.module));
        assertTrue(f.requests.isEmpty());
        DmInit copy = previous.metadataSnapshot();
        byte[] changed = copy.getDmInitBytes(); changed[3] ^= 1;
        assertArrayEquals(previous.getDmInitBytes(), copy.getDmInitBytes());
        assertSame(previous.getCacheBinding(), copy.getCacheBinding());
    }
}
