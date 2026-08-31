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

import com.romraider.logger.ecu.exception.InvalidResponseException;

import static com.romraider.util.HexUtil.asHex;
import static com.romraider.util.ParamChecker.checkNotNullOrEmpty;

/** Utilities for MUT-II's one-byte request/one-byte value exchange. */
public final class MUT2ResponseProcessor {
    private MUT2ResponseProcessor() {
    }

    /**
     * Return the ECU value byte from a transport response.
     *
     * ISO9141 adapters commonly return the transmitted PID as loopback before
     * the ECU value.  Serial adapters may suppress that echo.  In either case
     * the ECU value is the final byte delivered for the exchange.
     */
    public static byte extractValue(byte[] request, byte[] response) {
        checkNotNullOrEmpty(request, "request");
        checkNotNullOrEmpty(response, "response");
        if (request.length != 1) {
            throw new InvalidResponseException(
                    "MUT-II request must contain exactly one PID: " + asHex(request));
        }
        return response[response.length - 1];
    }
}
