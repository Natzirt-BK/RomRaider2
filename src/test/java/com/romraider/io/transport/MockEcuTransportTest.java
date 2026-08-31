/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.romraider.io.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.romraider.io.transport.MockEcuTransport.FailureMode;
import com.romraider.io.transport.TransportException.Reason;

public class MockEcuTransportTest {
    private final EcuIdentity identity = new EcuIdentity("ECU-A", "ROM-A");
    private MockEcuTransport transport;

    @Before
    public void createTransport() {
        transport = new MockEcuTransport(identity);
    }

    @Test
    public void simulatesVerifiedMemoryRoundTrip() throws Exception {
        transport.connect(identity);
        byte[] written = new byte[] {0x10, 0x20, 0x30};
        transport.writeMemory(0x1000, written);
        assertArrayEquals(written, transport.readMemory(0x1000, written.length));
    }

    @Test
    public void rejectsWrongEcuIdentity() throws Exception {
        try {
            transport.connect(new EcuIdentity("OTHER", "ROM-A"));
            fail("Expected identity mismatch");
        } catch (TransportException expected) {
            assertEquals(Reason.IDENTITY_MISMATCH, expected.getReason());
        }
    }

    @Test
    public void simulatesTimeout() throws Exception {
        transport.setFailureMode(FailureMode.TIMEOUT);
        try {
            transport.connect(identity);
            fail("Expected timeout");
        } catch (TransportException expected) {
            assertEquals(Reason.TIMEOUT, expected.getReason());
        }
    }

    @Test
    public void simulatesRejectedWrite() throws Exception {
        transport.connect(identity);
        transport.setFailureMode(FailureMode.REJECT_WRITES);
        try {
            transport.writeMemory(0x1000, new byte[] {0x01});
            fail("Expected rejected write");
        } catch (TransportException expected) {
            assertEquals(Reason.REJECTED, expected.getReason());
        }
    }

    @Test
    public void simulatesReadbackMismatch() throws Exception {
        transport.connect(identity);
        transport.setFailureMode(FailureMode.READBACK_MISMATCH);
        transport.writeMemory(0x1000, new byte[] {0x22});
        assertFalse(0x22 == transport.readMemory(0x1000, 1)[0]);
    }

    @Test
    public void simulatesDisconnectDuringTransaction() throws Exception {
        transport.connect(identity);
        transport.setFailureMode(FailureMode.DISCONNECT_DURING_TRANSACTION);
        try {
            transport.writeMemory(0x1000, new byte[] {0x01});
            fail("Expected disconnect");
        } catch (TransportException expected) {
            assertEquals(Reason.DISCONNECTED, expected.getReason());
            assertFalse(transport.isConnected());
        }
    }

    @Test
    public void reportsOptionalDimeModAndRamTuneCapabilities() {
        assertTrue(transport.isDimeModAvailable());
        assertTrue(transport.isRamTuneAvailable());
        transport.setFailureMode(FailureMode.DIMEMOD_UNAVAILABLE);
        assertFalse(transport.isDimeModAvailable());
        assertFalse(transport.isRamTuneAvailable());
        transport.setFailureMode(FailureMode.RAM_TUNE_UNAVAILABLE);
        assertTrue(transport.isDimeModAvailable());
        assertFalse(transport.isRamTuneAvailable());
    }
}
