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

import com.romraider.io.protocol.Protocol;
import com.romraider.logger.ecu.comms.io.protocol.LoggerProtocol;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.definition.Module;

import java.util.Collection;

import static com.romraider.util.ParamChecker.checkNotNull;
import static com.romraider.util.ParamChecker.checkNotNullOrEmpty;

/** RomRaider logger adapter for Mitsubishi MUT-II. */
public final class MUT2LoggerProtocol implements LoggerProtocol {
    private final MUT2Protocol protocol = new MUT2Protocol();

    @Override
    public byte[] constructEcuInitRequest(Module module) {
        return protocol.constructEcuInitRequest(module);
    }

    public byte[] constructPidRequest(byte pid) {
        return new byte[]{pid};
    }

    public byte extractPidValue(byte[] request, byte[] response) {
        return MUT2ResponseProcessor.extractValue(request, response);
    }

    @Override
    public byte[] constructReadAddressRequest(Module module, Collection<EcuQuery> queries) {
        checkNotNullOrEmpty(queries, "queries");
        EcuQuery query = queries.iterator().next();
        byte[] bytes = query.getBytes();
        if (queries.size() != 1 || bytes.length != 1) {
            throw new IllegalArgumentException("MUT-II constructs one PID request at a time");
        }
        return constructPidRequest(bytes[0]);
    }

    @Override
    public byte[] constructReadAddressResponse(Collection<EcuQuery> queries, PollingState pollState) {
        checkNotNullOrEmpty(queries, "queries");
        return new byte[queries.size()];
    }

    @Override
    public byte[] preprocessResponse(byte[] request, byte[] response, PollingState pollState) {
        return protocol.preprocessResponse(request, response, pollState);
    }

    @Override
    public void processEcuInitResponse(EcuInitCallback callback, byte[] response) {
        checkNotNull(callback, "callback");
        protocol.checkValidEcuInitResponse(response);
        EcuInit ecuInit = protocol.parseEcuInitResponse(response);
        callback.callback(ecuInit);
    }

    @Override
    public void processReadAddressResponses(Collection<EcuQuery> queries,
            byte[] response, PollingState pollState) {
        checkNotNullOrEmpty(queries, "queries");
        checkNotNullOrEmpty(response, "response");
        if (queries.size() != response.length) {
            throw new IllegalArgumentException("MUT-II query and response counts differ");
        }
        int index = 0;
        for (EcuQuery query : queries) {
            query.setResponse(new byte[]{response[index++]});
        }
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public byte[] constructEcuResetRequest(Module module, int resetCode) {
        return protocol.constructEcuResetRequest(module, resetCode);
    }

    @Override
    public void processEcuResetResponse(byte[] response) {
        protocol.checkValidEcuResetResponse(response);
    }

    @Override
    public byte[] constructWriteAddressRequest(Module module, byte[] writeAddress, byte value) {
        return protocol.constructWriteAddressRequest(module, writeAddress, value);
    }

    @Override
    public void processWriteResponse(byte[] data, byte[] processedResponse) {
        protocol.checkValidWriteResponse(data, processedResponse);
    }
}
