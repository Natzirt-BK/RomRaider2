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

import java.util.HashMap;
import java.util.Map;

import com.romraider.io.transport.TransportException.Reason;

public final class MockEcuTransport implements EcuTransport {
    public enum FailureMode {
        NONE,
        TIMEOUT,
        REJECT_WRITES,
        WRONG_ECU,
        READBACK_MISMATCH,
        DISCONNECT_DURING_TRANSACTION,
        DIMEMOD_UNAVAILABLE,
        RAM_TUNE_UNAVAILABLE
    }

    private final EcuIdentity simulatedIdentity;
    private final Map<Long, Byte> memory = new HashMap<Long, Byte>();
    private FailureMode failureMode = FailureMode.NONE;
    private boolean connected;
    private boolean mismatchPending;

    public MockEcuTransport(EcuIdentity simulatedIdentity) {
        if (simulatedIdentity == null) {
            throw new IllegalArgumentException("Simulated ECU identity is required");
        }
        this.simulatedIdentity = simulatedIdentity;
    }

    public void setFailureMode(FailureMode failureMode) {
        if (failureMode == null) {
            throw new IllegalArgumentException("Failure mode is required");
        }
        this.failureMode = failureMode;
        this.mismatchPending = false;
    }

    public FailureMode getFailureMode() {
        return failureMode;
    }

    public void connect(EcuIdentity expectedIdentity) throws TransportException {
        if (failureMode == FailureMode.TIMEOUT) {
            throw new TransportException(Reason.TIMEOUT, "Simulated connection timeout");
        }
        if (failureMode == FailureMode.WRONG_ECU ||
                !simulatedIdentity.equals(expectedIdentity)) {
            throw new TransportException(Reason.IDENTITY_MISMATCH,
                    "Expected " + expectedIdentity + " but found " + simulatedIdentity);
        }
        connected = true;
    }

    public void disconnect() {
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public EcuIdentity getConnectedIdentity() {
        return connected ? simulatedIdentity : null;
    }

    public boolean isDimeModAvailable() {
        return failureMode != FailureMode.DIMEMOD_UNAVAILABLE;
    }

    public boolean isRamTuneAvailable() {
        return isDimeModAvailable() &&
                failureMode != FailureMode.RAM_TUNE_UNAVAILABLE;
    }

    public byte[] readMemory(long address, int length) throws TransportException {
        validateRequest(address, length);
        failIfUnavailable();
        byte[] result = new byte[length];
        for (int index = 0; index < length; index++) {
            Byte value = memory.get(Long.valueOf(address + index));
            result[index] = value == null ? 0 : value.byteValue();
        }
        if (mismatchPending && result.length > 0) {
            result[0] = (byte) (result[0] ^ 0x01);
            mismatchPending = false;
        }
        return result;
    }

    public void writeMemory(long address, byte[] data) throws TransportException {
        if (data == null) {
            throw new TransportException(Reason.INVALID_REQUEST,
                    "Write data is required");
        }
        validateRequest(address, data.length);
        failIfUnavailable();
        if (failureMode == FailureMode.REJECT_WRITES) {
            throw new TransportException(Reason.REJECTED, "Simulated rejected write");
        }
        if (failureMode == FailureMode.DISCONNECT_DURING_TRANSACTION) {
            connected = false;
            throw new TransportException(Reason.DISCONNECTED,
                    "Simulated disconnect during write");
        }
        for (int index = 0; index < data.length; index++) {
            memory.put(Long.valueOf(address + index), Byte.valueOf(data[index]));
        }
        mismatchPending = failureMode == FailureMode.READBACK_MISMATCH;
    }

    private void validateRequest(long address, int length) throws TransportException {
        if (address < 0 || length <= 0) {
            throw new TransportException(Reason.INVALID_REQUEST,
                    "Memory requests require a non-negative address and positive length");
        }
    }

    private void failIfUnavailable() throws TransportException {
        if (!connected) {
            throw new TransportException(Reason.DISCONNECTED,
                    "Mock ECU transport is disconnected");
        }
        if (failureMode == FailureMode.TIMEOUT) {
            throw new TransportException(Reason.TIMEOUT, "Simulated transport timeout");
        }
    }
}
