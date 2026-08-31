/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2015 RomRaider.com
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

package com.romraider.io.protocol.ssm.iso15765;

import static com.romraider.io.protocol.ssm.iso15765.SSMProtocol.ADDRESS_SIZE;
import static com.romraider.io.protocol.ssm.iso15765.SSMProtocol.DATA_SIZE;
import static com.romraider.io.protocol.ssm.iso15765.SSMProtocol.RESPONSE_NON_DATA_BYTES;
import static com.romraider.io.protocol.ssm.iso15765.SSMResponseProcessor.extractResponseData;
import static com.romraider.io.protocol.ssm.iso15765.SSMResponseProcessor.filterRequestFromResponse;
import static com.romraider.util.ParamChecker.checkNotNull;
import static com.romraider.util.ParamChecker.checkNotNullOrEmpty;
import static java.lang.System.arraycopy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.romraider.io.protocol.Protocol;
import com.romraider.logger.ecu.comms.io.protocol.LoggerProtocol;
import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.EcuQueryImpl;
import com.romraider.logger.ecu.definition.EcuAddressCANA8PatchedImpl;
import com.romraider.logger.ecu.definition.EcuData;
import com.romraider.logger.ecu.definition.Module;

public final class SSMLoggerProtocol implements LoggerProtocol {
    private final Protocol protocol = new SSMProtocol();

    public byte[] constructEcuInitRequest(Module module) {
        return protocol.constructEcuInitRequest(module);
    }

    public byte[] constructEcuResetRequest(Module module, int resetCode) {
        return protocol.constructEcuResetRequest(module, resetCode);
    }

    public byte[] constructReadAddressRequest(Module module, Collection<EcuQuery> queries) {
        Collection<EcuQuery> filteredQueries = filterDuplicatesAndOptimize(queries);
        return protocol.constructReadAddressRequest(module, convertToByteAddresses(filteredQueries));
    }

    public byte[] constructReadAddressResponse(Collection<EcuQuery> queries, PollingState pollState) {
        checkNotNullOrEmpty(queries, "queries");
        // CAN_ID 0xE8 value1 value2 ... valueN
        Collection<EcuQuery> filteredQueries = filterDuplicatesAndOptimize(queries);
        int numAddresses = 0;
        for (EcuQuery ecuQuery : filteredQueries) {
            int responseLength = DATA_SIZE * (ecuQuery.getBytes().length / ADDRESS_SIZE);
            if (ecuQuery.getBytes()[0] == (byte) 0xF2) {
                responseLength *=2;
            } else if (ecuQuery.getBytes()[0] == (byte) 0xF4) {
                responseLength *=4;
            }
            numAddresses += responseLength;
        }
        return new byte[(numAddresses * DATA_SIZE + RESPONSE_NON_DATA_BYTES)];
    }

    public byte[] preprocessResponse(byte[] request, byte[] response, PollingState pollState) {
        return filterRequestFromResponse(request, response, pollState);
    }

    public void processEcuInitResponse(EcuInitCallback callback, byte[] response) {
        checkNotNull(callback, "callback");
        checkNotNullOrEmpty(response, "response");
        protocol.checkValidEcuInitResponse(response);
        EcuInit ecuInit = protocol.parseEcuInitResponse(response);
        callback.callback(ecuInit);
    }

    public void processEcuResetResponse(byte[] response) {
        checkNotNullOrEmpty(response, "response");
        protocol.checkValidEcuResetResponse(response);
    }

    // processes the response bytes and sets individual responses on corresponding query objects
    public void processReadAddressResponses(Collection<EcuQuery> queries, byte[] response, PollingState pollState) {
        checkNotNullOrEmpty(queries, "queries");
        checkNotNullOrEmpty(response, "response");
        byte[] responseData = extractResponseData(response);
        Collection<EcuQuery> filteredQueries = filterDuplicatesAndOptimize(queries);
        Map<String, byte[]> addressResults = new HashMap<String, byte[]>();
        int i = 0;
        for (EcuQuery filteredQuery : filteredQueries) {
            int responseLength = DATA_SIZE * (filteredQuery.getBytes().length / ADDRESS_SIZE);
            if (filteredQuery.getBytes()[0] == (byte) 0xF2) {
                responseLength *=2;
            } else if (filteredQuery.getBytes()[0] == (byte) 0xF4) {
                responseLength *=4;
            }
            byte[] bytes = new byte[responseLength];
            arraycopy(responseData, i, bytes, 0, bytes.length);
            addressResults.put(filteredQuery.getHex(), bytes);
            i += bytes.length;
        }
        for (EcuQuery query : queries) {
            query.setResponse(addressResults.get(query.getHex()));
        }
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public byte[] constructWriteAddressRequest(
            Module module, byte[] writeAddress, byte value) {

        return protocol.constructWriteAddressRequest(module, writeAddress, value);
    }

    public void processWriteResponse(byte[] data, byte[] response) {
        checkNotNullOrEmpty(data, "data");
        checkNotNullOrEmpty(response, "response");
        protocol.checkValidWriteResponse(data, response);
    }

    private Collection<EcuQuery> filterDuplicatesAndOptimize(Collection<EcuQuery> queries) {
        ArrayList<EcuQuery> filteredQueries = new ArrayList<>();
        for (EcuQuery query : queries) {
            if (!filteredQueries.contains(query)) {
                filteredQueries.add(query);
            }
        }
        for (int i = 0; i < filteredQueries.size(); i++) {
            EcuQuery query = filteredQueries.get(i);
            if (query.getLoggerData() instanceof EcuData) {
                if (query.getBytes().length / ADDRESS_SIZE == 2 || query.getBytes().length / ADDRESS_SIZE == 4) {
                    query = new EcuQueryImpl((EcuData) query.getLoggerData());
                    ((EcuData) query.getLoggerData()).setAddress(new EcuAddressCANA8PatchedImpl(((EcuData) query.getLoggerData()).getAddress()));
                }
            }
        }
        return filteredQueries;
    }

    private byte[][] convertToByteAddresses(Collection<EcuQuery> queries) {
        int byteCount = 0;
        for (EcuQuery query : queries) {
            byteCount += query.getBytes().length / ADDRESS_SIZE;
        }
        byte[][] addresses = new byte[byteCount][ADDRESS_SIZE];
        int i = 0;
        for (EcuQuery query : queries) {
            byte[] bytes = query.getBytes();
            for (int j = 0; j < bytes.length / ADDRESS_SIZE; j++) {
                arraycopy(bytes, j * ADDRESS_SIZE, addresses[i++], 0, ADDRESS_SIZE);
            }
        }
        return addresses;
    }

}
