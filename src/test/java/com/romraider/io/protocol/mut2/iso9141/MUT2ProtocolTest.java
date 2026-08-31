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

import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class MUT2ProtocolTest {
    private final MUT2Protocol protocol = new MUT2Protocol();

    @Test
    public void constructsSingleBytePidRequest() {
        assertArrayEquals(new byte[]{(byte) 0x21},
                protocol.constructReadAddressRequest(null,
                        new byte[][]{new byte[]{(byte) 0x21}}));
    }

    @Test
    public void extractsResponseWithTransportLoopback() {
        assertEquals(0x80, MUT2ResponseProcessor.extractValue(
                new byte[]{(byte) 0x21},
                new byte[]{(byte) 0x21, (byte) 0x80}) & 0xff);
    }

    @Test
    public void extractsResponseWhenTransportSuppressesLoopback() {
        assertEquals(0x80, MUT2ResponseProcessor.extractValue(
                new byte[]{(byte) 0x21},
                new byte[]{(byte) 0x80}) & 0xff);
    }

    @Test
    public void acceptsPlausibleBatteryProbeAndAssignsDefinitionId() {
        byte[] response = new byte[]{(byte) 180};
        protocol.checkValidEcuInitResponse(response);
        EcuInit init = protocol.parseEcuInitResponse(response);
        assertEquals("MUT2_GENERIC", init.getEcuId());
        assertArrayEquals(response, init.getEcuInitBytes());
    }

    @Test(expected = InvalidResponseException.class)
    public void rejectsEchoOnlyBatteryProbe() {
        protocol.checkValidEcuInitResponse(new byte[]{MUT2Protocol.ECU_PROBE_PID});
    }

    @Test(expected = UnsupportedOperationException.class)
    public void keepsLoggerReadOnly() {
        protocol.constructWriteAddressRequest(null, new byte[]{0x01}, (byte) 0x02);
    }
}
