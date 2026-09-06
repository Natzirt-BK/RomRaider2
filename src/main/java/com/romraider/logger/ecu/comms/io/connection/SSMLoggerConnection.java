/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2022 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.logger.ecu.comms.io.connection;

import static com.romraider.util.HexUtil.asHex;
import static com.romraider.util.ParamChecker.checkNotNull;
import static org.apache.log4j.Logger.getLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.romraider.io.protocol.ssm.iso9141.SSMProtocol;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.UnsupportedProtocolException;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.io.connection.ConnectionManager;
import com.romraider.io.protocol.ProtocolFactory;
import com.romraider.logger.ecu.comms.io.protocol.LoggerProtocol;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.manager.PollingStateImpl;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.util.SettingsManager;

public final class SSMLoggerConnection implements LoggerConnection {
    private static final Logger LOGGER = getLogger(SSMLoggerConnection.class);
    private final LoggerProtocol protocol;
    private final ConnectionManager manager;
    private List<EcuQuery> tcuQueries = new ArrayList<EcuQuery>();
    private final Collection<EcuQuery> tcuSubQuery = new ArrayList<EcuQuery>();
    Settings settings = SettingsManager.getSettings();

    public SSMLoggerConnection(ConnectionManager manager) {
        checkNotNull(manager, "manager");
        this.manager = manager;

        this.protocol = ProtocolFactory.getProtocol(
                settings.getLoggerProtocol(),
                settings.getTransportProtocol());
    }

    /** Package-local seam for synthetic transport qualification; never opens a device. */
    SSMLoggerConnection(ConnectionManager manager, LoggerProtocol protocol) {
        checkNotNull(manager, "manager");
        checkNotNull(protocol, "protocol");
        this.manager = manager;
        this.protocol = protocol;
    }

    @Override
    public void open(Module module) {
    }

    @Override
    public void ecuReset(Module module, int resetCode) {
        byte[] request = protocol.constructEcuResetRequest(module, resetCode);
        if (LOGGER.isDebugEnabled())
            LOGGER.debug(module + " Reset Request  ---> " + asHex(request));
        byte[] response = manager.send(request);
        byte[] processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
        if (LOGGER.isDebugEnabled())
            LOGGER.debug(module + " Reset Response <--- " + asHex(processedResponse));
        protocol.processEcuResetResponse(processedResponse);
    }

    @Override
    public void ecuInit(EcuInitCallback callback, Module module) {
        byte[] request = protocol.constructEcuInitRequest(module);
        if (LOGGER.isDebugEnabled())
            LOGGER.debug(module + " Init Request  ---> " + asHex(request));
        byte[] response = manager.send(request);
        byte[] processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
        if (LOGGER.isDebugEnabled())
            LOGGER.debug(module + " Init Response <--- " + asHex(processedResponse));
        protocol.processEcuInitResponse(callback, processedResponse);
    }

    public void dmInit(DmInitCallback callback, Module module) throws InterruptedException {
        if (!(protocol.getProtocol() instanceof SSMProtocol) &&
                !(protocol.getProtocol() instanceof com.romraider.io.protocol.ssm.iso15765.SSMProtocol)) {
            return;
        }
        DmInit dmInit = callback.getDmInit();
        byte resetState;
        if (dmInit == null) {
            byte[] request = protocol.getProtocol().constructReadAddressRequest(module, new byte[][]{new byte[]{0x00, 0x00, 0x60}});
            byte[] response = manager.send(request);
            byte[] processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
            byte responseType = dmResponseType(processedResponse);
            if (responseType != SSMProtocol.READ_ADDRESS_RESPONSE) {
                return;
            }
            resetState = dmPayload(processedResponse, SSMProtocol.READ_ADDRESS_RESPONSE, 1, 1)[0];
            request = protocol.constructWriteAddressRequest(module, new byte[]{0x00, 0x00, 0x60}, (byte) 0xDE);
            if (LOGGER.isDebugEnabled())
                LOGGER.debug(module + " Init DM Request  ---> " + asHex(request));
            Thread.sleep(100);
            response = manager.send(request);
            processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
            if (LOGGER.isDebugEnabled())
                LOGGER.debug(module + " Init DM Response <--- " + asHex(processedResponse));
            responseType = dmResponseType(processedResponse);
            if (responseType != SSMProtocol.WRITE_ADDRESS_RESPONSE) {
                // error
                return;
            }
            if (dmPayload(processedResponse, SSMProtocol.WRITE_ADDRESS_RESPONSE, 1, 1)[0] == (byte) 0xAD) {
                request = protocol.getProtocol().constructReadAddressRequest(module, new byte[][]{new byte[]{0x00, 0x00, 0x60}});
                Thread.sleep(100);
                response = manager.send(request);
                processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
                responseType = dmResponseType(processedResponse);
                if (responseType != SSMProtocol.READ_ADDRESS_RESPONSE) {
                    return;
                }
                int length = (dmPayload(processedResponse, SSMProtocol.READ_ADDRESS_RESPONSE, 1, 1)[0] & 0xFF) << 8;
                Thread.sleep(100);
                response = manager.send(request);
                processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
                responseType = dmResponseType(processedResponse);
                if (responseType != SSMProtocol.READ_ADDRESS_RESPONSE) {
                    return;
                }
                length |= dmPayload(processedResponse, SSMProtocol.READ_ADDRESS_RESPONSE, 1, 1)[0] & 0xFF;
                int startAddress = 0x00;
                Thread.sleep(100);
                response = manager.send(request);
                processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
                responseType = dmResponseType(processedResponse);
                if (responseType != SSMProtocol.READ_ADDRESS_RESPONSE) {
                    return;
                }
                int highAddrByte = dmPayload(processedResponse, SSMProtocol.READ_ADDRESS_RESPONSE, 1, 1)[0] & 0xFF;
                if (highAddrByte > 0x0F && highAddrByte != 0xFF) {
                    // error reading address
                    return;
                }
                startAddress |= highAddrByte << 16;
                Thread.sleep(100);
                response = manager.send(request);
                processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
                responseType = dmResponseType(processedResponse);
                if (responseType != SSMProtocol.READ_ADDRESS_RESPONSE) {
                    return;
                }
                startAddress |= (dmPayload(processedResponse, SSMProtocol.READ_ADDRESS_RESPONSE, 1, 1)[0] & 0xFF) << 8;
                Thread.sleep(100);
                response = manager.send(request);
                processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
                responseType = dmResponseType(processedResponse);
                if (responseType != SSMProtocol.READ_ADDRESS_RESPONSE) {
                    return;
                }
                startAddress |= dmPayload(processedResponse, SSMProtocol.READ_ADDRESS_RESPONSE, 1, 1)[0] & 0xFF;

                request = protocol.constructWriteAddressRequest(module, new byte[]{0x00, 0x00, 0x00}, (byte) 0x00);
                Thread.sleep(100);
                response = manager.send(request);
                processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
                responseType = dmResponseType(processedResponse);
                if (responseType != SSMProtocol.WRITE_ADDRESS_RESPONSE) {
                    // error, skipping for CAN...
                    if (!(protocol.getProtocol() instanceof com.romraider.io.protocol.ssm.iso15765.SSMProtocol)) {
                        return;
                    }
                }

                if (responseType == SSMProtocol.WRITE_ADDRESS_RESPONSE)
                    dmPayload(processedResponse, SSMProtocol.WRITE_ADDRESS_RESPONSE, 1, 1);
                dmInit = new DmInit(readDmDiscovery(module, startAddress, length));
            } else {
                // restoring Reset state
                request = protocol.constructWriteAddressRequest(module, new byte[]{0x00, 0x00, 0x60}, resetState);
                Thread.sleep(100);
                manager.send(request);
            }
        }

        boolean forceUpdate = false;
        // read runtime params
        if (dmInit != null && dmInit.getMajorVer() == 2) {
            int aiAddr = dmInit.getActiveInputsAddress();
            int afAddr = dmInit.getActiveFeaturesAddress();
            int cerrAddr = dmInit.getCurrentErrorCodesAddress();
            int merrAddr = dmInit.getMemorizedErrorCodesAddress();

            if (dmInit.getMinorVer() < 1) {
                byte[] request = protocol.getProtocol().constructReadAddressRequest(module, new byte[][]{
                        getThreeByteAddr(afAddr),
                        getThreeByteAddr(afAddr + 1),
                        getThreeByteAddr(afAddr + 2),
                        getThreeByteAddr(afAddr + 3),
                        getThreeByteAddr(aiAddr),
                        getThreeByteAddr(aiAddr + 1),
                        getThreeByteAddr(cerrAddr),
                        getThreeByteAddr(cerrAddr + 1),
                        getThreeByteAddr(cerrAddr + 2),
                        getThreeByteAddr(cerrAddr + 3),
                        getThreeByteAddr(merrAddr),
                        getThreeByteAddr(merrAddr + 1),
                        getThreeByteAddr(merrAddr + 2),
                        getThreeByteAddr(merrAddr + 3),
                });
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug(module + " Init DM Runtime Params Request  ---> " + asHex(request));
                Thread.sleep(100);
                byte[] response = manager.send(request);
                byte[] processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug(module + " Init DM Runtime Params Response <--- " + asHex(processedResponse));
                byte responseType = dmResponseType(processedResponse);
                if (responseType != SSMProtocol.READ_ADDRESS_RESPONSE) {
                    // error
                    return;
                }
                dmPayload(processedResponse, SSMProtocol.READ_ADDRESS_RESPONSE, 14, 14);
                forceUpdate = dmInit.updateRuntimeData(getIntFromResponse(processedResponse, 5),
                        getShortFromResponse(processedResponse, 9),
                        new int[]{getIntFromResponse(processedResponse, 11)},
                        new int[]{getIntFromResponse(processedResponse, 15)}
                );

            } else {
                byte[] request = protocol.getProtocol().constructReadAddressRequest(module, new byte[][]{
                        getThreeByteAddr(afAddr),
                        getThreeByteAddr(afAddr + 1),
                        getThreeByteAddr(afAddr + 2),
                        getThreeByteAddr(afAddr + 3),
                        getThreeByteAddr(aiAddr),
                        getThreeByteAddr(aiAddr + 1),
                        getThreeByteAddr(cerrAddr),
                        getThreeByteAddr(cerrAddr + 1),
                        getThreeByteAddr(cerrAddr + 2),
                        getThreeByteAddr(cerrAddr + 3),
                        getThreeByteAddr(cerrAddr + 4),
                        getThreeByteAddr(cerrAddr + 5),
                        getThreeByteAddr(cerrAddr + 6),
                        getThreeByteAddr(cerrAddr + 7),
                        getThreeByteAddr(cerrAddr + 8),
                        getThreeByteAddr(cerrAddr + 9),
                        getThreeByteAddr(cerrAddr + 10),
                        getThreeByteAddr(cerrAddr + 11),
                        getThreeByteAddr(cerrAddr + 12),
                        getThreeByteAddr(cerrAddr + 13),
                        getThreeByteAddr(cerrAddr + 14),
                        getThreeByteAddr(cerrAddr + 15),
                        getThreeByteAddr(merrAddr),
                        getThreeByteAddr(merrAddr + 1),
                        getThreeByteAddr(merrAddr + 2),
                        getThreeByteAddr(merrAddr + 3),
                        getThreeByteAddr(merrAddr + 4),
                        getThreeByteAddr(merrAddr + 5),
                        getThreeByteAddr(merrAddr + 6),
                        getThreeByteAddr(merrAddr + 7),
                        getThreeByteAddr(merrAddr + 8),
                        getThreeByteAddr(merrAddr + 9),
                        getThreeByteAddr(merrAddr + 10),
                        getThreeByteAddr(merrAddr + 11),
                        getThreeByteAddr(merrAddr + 12),
                        getThreeByteAddr(merrAddr + 13),
                        getThreeByteAddr(merrAddr + 14),
                        getThreeByteAddr(merrAddr + 15),
                });
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug(module + " Init DM Runtime Params Request  ---> " + asHex(request));
                Thread.sleep(100);
                byte[] response = manager.send(request);
                byte[] processedResponse = protocol.preprocessResponse(request, response, new PollingStateImpl());
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug(module + " Init DM Runtime Params Response <--- " + asHex(processedResponse));
                byte responseType = dmResponseType(processedResponse);
                if (responseType != SSMProtocol.READ_ADDRESS_RESPONSE) {
                    // error
                    return;
                }
                dmPayload(processedResponse, SSMProtocol.READ_ADDRESS_RESPONSE, 38, 38);
                forceUpdate = dmInit.updateRuntimeData(getIntFromResponse(processedResponse, 5),
                        getShortFromResponse(processedResponse, 9),
                        new int[]{
                                getShortFromResponse(processedResponse, 11),
                                getShortFromResponse(processedResponse, 13),
                                getShortFromResponse(processedResponse, 15),
                                getShortFromResponse(processedResponse, 17),
                                getShortFromResponse(processedResponse, 19),
                                getShortFromResponse(processedResponse, 21),
                                getShortFromResponse(processedResponse, 23),
                                getShortFromResponse(processedResponse, 25)
                        },
                        new int[]{
                                getShortFromResponse(processedResponse, 27),
                                getShortFromResponse(processedResponse, 29),
                                getShortFromResponse(processedResponse, 31),
                                getShortFromResponse(processedResponse, 33),
                                getShortFromResponse(processedResponse, 35),
                                getShortFromResponse(processedResponse, 37),
                                getShortFromResponse(processedResponse, 39),
                                getShortFromResponse(processedResponse, 41)
                        }
                );
            }
        }
        callback.callback(dmInit, forceUpdate);
    }

    /** Read the advertised block without wrapping addresses or accepting empty progress. */
    byte[] readDmDiscovery(Module module, int startAddress, int length) throws InterruptedException {
        if (length < 4 || length > 0xffff || startAddress < 0
                || (long) startAddress + length > 0x1000000L)
            throw new InvalidResponseException("Invalid DimeMod discovery address/length");
        byte[] discovery = new byte[length];
        int offset = 0;
        boolean addressReads = false;
        while (offset < length) {
            int requested = Math.min(length - offset, addressReads ? 32 : 96);
            byte[] request;
            if (!addressReads) {
                try {
                    request = protocol.getProtocol().constructReadMemoryRequest(module,
                            getThreeByteAddr(startAddress + offset), requested);
                } catch (UnsupportedProtocolException unsupported) {
                    // Only CAN's unsupported memory command selects the address-read path.
                    if (!(protocol.getProtocol() instanceof com.romraider.io.protocol.ssm.iso15765.SSMProtocol))
                        throw unsupported;
                    addressReads = true;
                    continue;
                }
            } else {
                byte[][] addresses = new byte[requested][];
                for (int i = 0; i < requested; i++) addresses[i] = getThreeByteAddr(startAddress + offset + i);
                request = protocol.getProtocol().constructReadAddressRequest(module, addresses);
            }
            Thread.sleep(100);
            byte[] processed = protocol.preprocessResponse(request, manager.send(request), new PollingStateImpl());
            byte[] data = dmPayload(processed, addressReads ? SSMProtocol.READ_ADDRESS_RESPONSE
                    : SSMProtocol.READ_MEMORY_RESPONSE, addressReads ? requested : 1, requested);
            System.arraycopy(data, 0, discovery, offset, data.length);
            offset += data.length;
        }
        return discovery;
    }

    private byte[] dmPayload(byte[] processed, byte expectedType, int minimum, int maximum) {
        if (dmResponseType(processed) != expectedType)
            throw new InvalidResponseException("Unexpected DimeMod response type or truncated header");
        // Native validators check IDs, frame lengths and the K-line checksum.
        byte[] data = protocol.getProtocol().parseResponseData(processed);
        if (data.length < minimum || data.length > maximum)
            throw new InvalidResponseException("Unexpected DimeMod payload length: " + data.length);
        return data;
    }

    private static byte dmResponseType(byte[] processed) {
        if (processed == null || processed.length < 5)
            throw new InvalidResponseException("Truncated DimeMod response header");
        return processed[4];
    }

    private static int getShortFromResponse(byte[] processedResponse, int offset) {
        return ((processedResponse[offset] & 0xFF) << 8) +
                (processedResponse[offset + 1] & 0xFF);
    }

    private static int getIntFromResponse(byte[] processedResponse, int offset) {
        return ((processedResponse[offset] & 0xFF) << 24) +
                ((processedResponse[offset + 1] & 0xFF) << 16) +
                ((processedResponse[offset + 2] & 0xFF) << 8) +
                (processedResponse[offset + 3] & 0xFF);
    }

    private static byte[] getThreeByteAddr(int afAddr) {
        return new byte[]{(byte) (afAddr >> 16), (byte) (afAddr >> 8), (byte) afAddr};
    }

    @Override
    public final void sendAddressReads(
            Collection<EcuQuery> queries,
            Module module,
            PollingState pollState) {

        // Determine if ISO15765 is selected and then if TCU is selected.  If
        // both are true then proceed to split queries so max CAN data packet
        // contains 8 or less bytes, otherwise don't split up the queries.
        if (settings.isCanBus() && module.getName().equalsIgnoreCase("TCU")) {
            tcuQueries = (ArrayList<EcuQuery>) queries;
            final int tcuQueryListLength = tcuQueries.size();
            for (int i = 0; i < tcuQueryListLength; i++) {
                tcuSubQuery.clear();
                tcuSubQuery.add(tcuQueries.get(i));
                final int addrLength = tcuQueries.get(i).getAddresses().length;
                final byte[] request = protocol.constructReadAddressRequest(
                        module, tcuSubQuery);
                byte[] response = new byte[0];
                if (addrLength == 1) {
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug(module + " CAN Request  ---> " + asHex(request));
                    response = protocol.constructReadAddressResponse(
                            tcuSubQuery, pollState);
                    manager.send(request, response, pollState);
                }
                if (addrLength > 1) {
                    response = SSMLoggerCANSubQuery.doSubQuery(
                            (ArrayList<EcuQuery>) tcuSubQuery, manager,
                            protocol, module, pollState);
                }
                final byte[] processedResponse = protocol.preprocessResponse(
                        request, response, pollState);
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug(module + " CAN Response <--- " + asHex(processedResponse));
                protocol.processReadAddressResponses(
                        tcuSubQuery, processedResponse, pollState);
            }
        } else {
            final byte[] request = protocol.constructReadAddressRequest(
                    module, queries);
            if (pollState.getCurrentState() == PollingState.State.STATE_0) {
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("Mode:" + pollState.getCurrentState() + " " +
                            module + " Request  ---> " + asHex(request));
            }
            final byte[] response = protocol.constructReadAddressResponse(
                    queries, pollState);
            manager.send(request, response, pollState);
            final byte[] processedResponse = protocol.preprocessResponse(
                    request, response, pollState);
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Mode:" + pollState.getCurrentState() + " " +
                        module + " Response <--- " + asHex(processedResponse));
            protocol.processReadAddressResponses(
                    queries, processedResponse, pollState);
        }
    }

    @Override
    public void clearLine() {
        manager.clearLine();
    }

    @Override
    public void close() {
        manager.close();
    }

    @Override
    public final void sendAddressWrites(
            Map<EcuQuery, byte[]> writeQueries, Module module) {

        for (EcuQuery writeKey : writeQueries.keySet()) {
            if (writeKey.getBytes().length == 3) {
                final byte[] request =
                        protocol.constructWriteAddressRequest(
                                module,
                                writeKey.getBytes(),
                                writeQueries.get(writeKey)[0]);

                if (LOGGER.isDebugEnabled())
                    LOGGER.debug(module + " Write Request  ---> " + asHex(request));
                final byte[] response = manager.send(request);
                byte[] processedResponse =
                        protocol.preprocessResponse(
                                request,
                                response,
                                new PollingStateImpl());
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug(module + " Write Response <--- " + asHex(processedResponse));
                protocol.processWriteResponse(
                        writeQueries.get(writeKey), processedResponse);
            }
        }
    }
}
