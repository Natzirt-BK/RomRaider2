/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

import java.util.Arrays;

import com.romraider.io.transport.MockEcuTransport;
import com.romraider.io.transport.TransportException;

/**
 * Exercises the staged-change transaction against the mock transport only.
 * There is deliberately no overload accepting a production ECU transport.
 */
public final class LiveTuneSimulator {
    private LiveTuneSimulator() {
    }

    public static LiveTuneSimulationResult apply(LiveTuneSession session,
            MockEcuTransport transport) {
        if (session == null || transport == null) {
            throw new IllegalArgumentException(
                    "Live-tune session and mock transport are required");
        }
        session.beginSimulation();
        int applied = 0;
        try {
            if (!transport.isConnected() || !session.getPlan()
                    .getExpectedIdentity().equals(
                            transport.getConnectedIdentity())) {
                return failed(session, applied,
                        "Mock ECU identity changed after preflight");
            }
            for (LiveTuneChange change : session.getPlan().getChanges()) {
                byte[] current = transport.readMemory(
                        change.getAddress(), change.getLength());
                if (!Arrays.equals(current, change.getExpected())) {
                    return failed(session, applied,
                            "Staged value is stale for "
                                    + change.getTableName());
                }
            }
            for (LiveTuneChange change : session.getPlan().getChanges()) {
                transport.writeMemory(change.getAddress(),
                        change.getReplacement());
                applied++;
                byte[] readback = transport.readMemory(
                        change.getAddress(), change.getLength());
                if (!Arrays.equals(readback, change.getReplacement())) {
                    return failed(session, applied,
                            "Readback verification failed for "
                                    + change.getTableName());
                }
            }
            session.finishSimulation(true);
            return new LiveTuneSimulationResult(true, applied,
                    "All mock RAM changes were verified");
        } catch (TransportException exception) {
            return failed(session, applied,
                    "Mock transport failed: " + exception.getMessage());
        }
    }

    private static LiveTuneSimulationResult failed(LiveTuneSession session,
            int applied, String message) {
        session.finishSimulation(false);
        return new LiveTuneSimulationResult(false, applied, message);
    }
}
