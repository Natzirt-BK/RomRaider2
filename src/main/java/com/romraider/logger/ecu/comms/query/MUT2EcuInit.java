/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.romraider.logger.ecu.comms.query;

import static com.romraider.util.ParamChecker.checkNotNullOrEmpty;

/**
 * Identification returned after a successful Mitsubishi MUT-II probe.
 *
 * MUT-II byte polling does not expose an SSM-style ECU identification packet.
 * The definition-selected identifier is therefore supplied by the protocol
 * implementation after a live response has proved that an ECU is present.
 */
public final class MUT2EcuInit implements EcuInit {
    private final byte[] ecuInitBytes;
    private final String ecuId;

    public MUT2EcuInit(byte[] ecuInitBytes, String ecuId) {
        checkNotNullOrEmpty(ecuInitBytes, "ecuInitBytes");
        checkNotNullOrEmpty(ecuId, "ecuId");
        this.ecuInitBytes = ecuInitBytes.clone();
        this.ecuId = ecuId;
    }

    @Override
    public String getEcuId() {
        return ecuId;
    }

    @Override
    public byte[] getEcuInitBytes() {
        return ecuInitBytes.clone();
    }
}
