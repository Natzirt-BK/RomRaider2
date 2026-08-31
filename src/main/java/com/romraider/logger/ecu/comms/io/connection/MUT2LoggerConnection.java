/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.romraider.logger.ecu.comms.io.connection;

import com.romraider.Settings;
import com.romraider.io.connection.ConnectionManager;
import com.romraider.io.protocol.ProtocolFactory;
import com.romraider.io.protocol.mut2.iso9141.MUT2LoggerProtocol;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.manager.PollingStateImpl;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.util.SettingsManager;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.Map;

import static com.romraider.util.HexUtil.asHex;
import static com.romraider.util.ParamChecker.checkNotNull;
import static org.apache.log4j.Logger.getLogger;

/** Read-only MUT-II connection that polls each selected PID in sequence. */
public final class MUT2LoggerConnection implements LoggerConnection {
    private static final Logger LOGGER = getLogger(MUT2LoggerConnection.class);
    private final MUT2LoggerProtocol protocol;
    private final ConnectionManager manager;

    public MUT2LoggerConnection(ConnectionManager manager) {
        checkNotNull(manager, "manager");
        this.manager = manager;
        Settings settings = SettingsManager.getSettings();
        this.protocol = (MUT2LoggerProtocol) ProtocolFactory.getProtocol(
                settings.getLoggerProtocol(), settings.getTransportProtocol());
    }

    @Override
    public void open(Module module) {
        // MUT-II byte polling requires no five-baud or fast-init sequence.
    }

    @Override
    public void ecuInit(EcuInitCallback callback, Module module) {
        byte[] request = protocol.constructEcuInitRequest(module);
        byte[] response = manager.send(request);
        byte[] value = protocol.preprocessResponse(request, response, new PollingStateImpl());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(module + " MUT-II probe ---> " + asHex(request));
            LOGGER.debug(module + " MUT-II probe <--- " + asHex(response));
        }
        protocol.processEcuInitResponse(callback, value);
    }

    @Override
    public void dmInit(DmInitCallback callback, Module module) {
        // DimeMod discovery is Subaru-specific and does not apply to MUT-II.
    }

    @Override
    public void sendAddressReads(Collection<EcuQuery> queries, Module module, PollingState pollState) {
        checkNotNull(queries, "queries");
        for (EcuQuery query : queries) {
            byte[] pids = query.getBytes();
            byte[] values = new byte[pids.length];
            for (int i = 0; i < pids.length; i++) {
                byte[] request = protocol.constructPidRequest(pids[i]);
                byte[] response = manager.send(request);
                values[i] = protocol.extractPidValue(request, response);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(module + " MUT-II " + asHex(request) + " <--- " + asHex(response));
                }
            }
            query.setResponse(values);
        }
    }

    @Override
    public void clearLine() {
        // Existing ISO9141 clearLine() sends a break/zero burst.  MUT-II has no
        // stop command, so closing the channel is both safer and sufficient.
    }

    @Override
    public void close() {
        manager.close();
    }

    @Override
    public void ecuReset(Module module, int resetCode) {
        throw new UnsupportedOperationException("MUT-II ECU reset is intentionally disabled");
    }

    @Override
    public void sendAddressWrites(Map<EcuQuery, byte[]> writeQueries, Module module) {
        throw new UnsupportedOperationException("MUT-II logger support is read-only");
    }
}
