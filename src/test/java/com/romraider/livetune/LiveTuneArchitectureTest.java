/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.romraider.io.transport.EcuIdentity;
import com.romraider.io.transport.MockEcuTransport;
import com.romraider.io.transport.MockEcuTransport.FailureMode;
import com.romraider.platform.DimeModState;
import com.romraider.platform.VehicleModule;
import com.romraider.platform.VehiclePlatform;

public class LiveTuneArchitectureTest {
    private static final EcuIdentity IDENTITY =
            new EcuIdentity("ECU-TEST", "ROM-TEST");

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlappingChanges() {
        new LiveTunePlan(IDENTITY, Arrays.asList(
                change("Fuel A", 0x1000, 1, 2),
                change("Fuel B", 0x1000, 3, 4)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNoOpChanges() {
        change("Fuel", 0x1000, 1, 1);
    }

    @Test
    public void draftReportsItsOfflineScopeBeforeIdentityBinding() {
        LiveTuneDraft draft = new LiveTuneDraft(Arrays.asList(
                change("Fuel", 0x1000, 1, 2),
                change("Timing", 0x1010, 3, 4)));

        assertEquals(2, draft.getTableCount());
        assertEquals(2, draft.getTotalBytes());
        assertEquals(0x1000, draft.getStartAddress());
        assertEquals(0x1010, draft.getEndAddress());
        assertEquals(IDENTITY, draft.bindTo(IDENTITY).getExpectedIdentity());
    }

    @Test
    public void preflightRequiresEveryRuntimeAndIdentityGate() {
        LiveTunePlan plan = plan(change("Fuel", 0x1000, 1, 2));
        LiveTunePreflight ready = LiveTunePreflightEvaluator.evaluate(
                plan, readyContext());
        assertTrue(ready.isReady());
        assertEquals(6, ready.getChecks().size());

        LiveTuneSafetyContext unavailable = new LiveTuneSafetyContext(
                VehiclePlatform.SUBARU, VehicleModule.ENGINE_ECU,
                DimeModState.PRESENT, true, false,
                new EcuIdentity("OTHER", "ROM-TEST"));
        LiveTunePreflight blocked = LiveTunePreflightEvaluator.evaluate(
                plan, unavailable);
        assertFalse(blocked.isReady());
        assertEquals(3, blocked.getChecks().stream()
                .filter(check -> check.getStatus()
                        == LiveTuneCheckStatus.FAIL).count());
    }

    @Test
    public void appliesAndVerifiesAPlanOnlyOnMockTransport() throws Exception {
        MockEcuTransport transport = connectedTransport();
        transport.writeMemory(0x1000, new byte[] {1, 2});
        LiveTuneSession session = new LiveTuneSession(plan(
                new LiveTuneChange("Fuel", 0x1000,
                        new byte[] {1, 2}, new byte[] {3, 4})));
        session.preflight(readyContext());

        LiveTuneSimulationResult result =
                LiveTuneSimulator.apply(session, transport);

        assertTrue(result.isVerified());
        assertEquals(1, result.getAppliedChanges());
        assertEquals(LiveTuneSessionState.VERIFIED, session.getState());
        assertArrayEquals(new byte[] {3, 4},
                transport.readMemory(0x1000, 2));
    }

    @Test
    public void staleValueStopsBeforeAnyMockWrite() throws Exception {
        MockEcuTransport transport = connectedTransport();
        transport.writeMemory(0x1000, new byte[] {9});
        LiveTuneSession session = readySession(
                change("Fuel", 0x1000, 1, 2));

        LiveTuneSimulationResult result =
                LiveTuneSimulator.apply(session, transport);

        assertFalse(result.isVerified());
        assertEquals(0, result.getAppliedChanges());
        assertEquals(LiveTuneSessionState.FAILED, session.getState());
        assertArrayEquals(new byte[] {9}, transport.readMemory(0x1000, 1));
    }

    @Test
    public void readbackMismatchFailsTheMockTransaction() throws Exception {
        MockEcuTransport transport = connectedTransport();
        LiveTuneSession session = readySession(
                change("Fuel", 0x1000, 0, 2));
        transport.setFailureMode(FailureMode.READBACK_MISMATCH);

        LiveTuneSimulationResult result =
                LiveTuneSimulator.apply(session, transport);

        assertFalse(result.isVerified());
        assertEquals(1, result.getAppliedChanges());
        assertEquals(LiveTuneSessionState.FAILED, session.getState());
    }

    @Test(expected = IllegalStateException.class)
    public void simulationCannotBypassPreflight() throws Exception {
        LiveTuneSimulator.apply(new LiveTuneSession(
                plan(change("Fuel", 0x1000, 0, 2))), connectedTransport());
    }

    private static LiveTuneSession readySession(LiveTuneChange change) {
        LiveTuneSession session = new LiveTuneSession(plan(change));
        session.preflight(readyContext());
        return session;
    }

    private static LiveTunePlan plan(LiveTuneChange change) {
        return new LiveTunePlan(IDENTITY, Collections.singletonList(change));
    }

    private static LiveTuneChange change(String table, long address,
            int expected, int replacement) {
        return new LiveTuneChange(table, address,
                new byte[] {(byte) expected},
                new byte[] {(byte) replacement});
    }

    private static LiveTuneSafetyContext readyContext() {
        return new LiveTuneSafetyContext(VehiclePlatform.SUBARU,
                VehicleModule.ENGINE_ECU, DimeModState.ACTIVE,
                true, true, IDENTITY);
    }

    private static MockEcuTransport connectedTransport() throws Exception {
        MockEcuTransport transport = new MockEcuTransport(IDENTITY);
        transport.connect(IDENTITY);
        return transport;
    }
}
