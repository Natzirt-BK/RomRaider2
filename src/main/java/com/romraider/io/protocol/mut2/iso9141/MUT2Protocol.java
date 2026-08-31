/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.romraider.io.protocol.mut2.iso9141;

import com.romraider.io.connection.ConnectionProperties;
import com.romraider.io.connection.SerialConnectionProperties;
import com.romraider.io.protocol.Protocol;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.MUT2EcuInit;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.InvalidResponseException;

import static com.romraider.io.protocol.mut2.iso9141.MUT2ResponseProcessor.extractValue;
import static com.romraider.util.ByteUtil.asUnsignedInt;
import static com.romraider.util.HexUtil.asHex;
import static com.romraider.util.ParamChecker.checkNotNullOrEmpty;

/** Mitsubishi MUT-II byte polling over the ISO9141 K-line transport. */
public final class MUT2Protocol implements Protocol {
    /** MUT 0x14 is battery voltage (raw * 0.0733 V). */
    public static final byte ECU_PROBE_PID = (byte) 0x14;
    public static final String DEFAULT_ECU_ID = "MUT2_GENERIC";
    private static final int MIN_PROBE_VALUE = 80;
    private static final int MAX_PROBE_VALUE = 250;

    @Override
    public byte[] constructEcuInitRequest(Module module) {
        return new byte[]{ECU_PROBE_PID};
    }

    @Override
    public byte[] constructReadAddressRequest(Module module, byte[][] addresses) {
        if (addresses == null || addresses.length != 1 || addresses[0].length != 1) {
            throw new IllegalArgumentException("MUT-II reads exactly one one-byte PID per request");
        }
        return new byte[]{addresses[0][0]};
    }

    @Override
    public byte[] preprocessResponse(byte[] request, byte[] response, PollingState pollState) {
        return new byte[]{extractValue(request, response)};
    }

    @Override
    public byte[] parseResponseData(byte[] processedResponse) {
        checkNotNullOrEmpty(processedResponse, "processedResponse");
        return processedResponse.clone();
    }

    @Override
    public void checkValidEcuInitResponse(byte[] processedResponse) {
        checkNotNullOrEmpty(processedResponse, "processedResponse");
        int rawBattery = asUnsignedInt(processedResponse);
        if (processedResponse.length != 1 || rawBattery < MIN_PROBE_VALUE || rawBattery > MAX_PROBE_VALUE) {
            throw new InvalidResponseException(
                    "Unexpected MUT-II battery probe response: " + asHex(processedResponse));
        }
    }

    @Override
    public EcuInit parseEcuInitResponse(byte[] processedResponse) {
        return new MUT2EcuInit(processedResponse, DEFAULT_ECU_ID);
    }

    @Override
    public ConnectionProperties getDefaultConnectionProperties() {
        return new SerialConnectionProperties(15625, 8, 1, 0, 250, 100);
    }

    @Override
    public byte[] constructWriteMemoryRequest(Module module, byte[] address, byte[] values) {
        throw unsupportedWrite();
    }

    @Override
    public byte[] constructWriteAddressRequest(Module module, byte[] address, byte value) {
        throw unsupportedWrite();
    }

    @Override
    public byte[] constructReadMemoryRequest(Module module, byte[] address, int numBytes) {
        throw new UnsupportedOperationException("MUT-II memory reads are not implemented");
    }

    @Override
    public byte[] constructEcuResetRequest(Module module, int resetCode) {
        throw new UnsupportedOperationException("MUT-II ECU reset is intentionally disabled");
    }

    @Override
    public void checkValidEcuResetResponse(byte[] processedResponse) {
        throw new UnsupportedOperationException("MUT-II ECU reset is intentionally disabled");
    }

    @Override
    public void checkValidWriteResponse(byte[] data, byte[] processedResponse) {
        throw unsupportedWrite();
    }

    private UnsupportedOperationException unsupportedWrite() {
        return new UnsupportedOperationException("MUT-II logger support is read-only");
    }
}
