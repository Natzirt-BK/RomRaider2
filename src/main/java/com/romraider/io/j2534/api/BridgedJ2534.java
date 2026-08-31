/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.romraider.io.j2534.api;

import static java.lang.System.arraycopy;
import static java.lang.System.currentTimeMillis;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import com.romraider.io.j2534.api.J2534Impl.Protocol;
import com.romraider.io.j2534.api.J2534Impl.TxFlags;

/** J2534 facade backed by an architecture-matched out-of-process helper. */
final class BridgedJ2534 implements J2534 {
    private static final int HANDLE = 1;

    private final Protocol protocol;
    private final File library;
    private final int bridgeBits;
    private final IntFunction<J2534BridgeChannel> channelFactory;
    private final Deque<byte[]> received = new ArrayDeque<byte[]>();
    private J2534BridgeChannel client;
    private boolean connected;

    BridgedJ2534(Protocol protocol, File library, int bridgeBits) {
        this(protocol, library, bridgeBits,
                bits -> new J2534BridgeClient(bits));
    }

    BridgedJ2534(Protocol protocol, File library, int bridgeBits,
            IntFunction<J2534BridgeChannel> channelFactory) {
        this.protocol = protocol;
        this.library = library;
        this.bridgeBits = bridgeBits;
        this.channelFactory = channelFactory;
    }

    @Override
    public int open() {
        if (client != null) throw new J2534Exception("J2534 bridge is already open");
        client = channelFactory.apply(bridgeBits);
        return HANDLE;
    }

    @Override
    public Version readVersion(int deviceId) {
        requireHandle(deviceId, "device");
        if (!connected) {
            return new Version("available after channel connection",
                    library.getName() + " via " + bridgeBits + "-bit bridge", "04.04");
        }
        Map<?, ?> version = object(request("ReadVersion", null), "version");
        return new Version(string(version, "firmwareVersion"),
                string(version, "dllVersion"), string(version, "apiVersion"));
    }

    @Override
    public int connect(int deviceId, int flags, int baud) {
        requireHandle(deviceId, "device");
        Map<String, Object> parameters = map(
                "dll_path", library.getAbsolutePath(),
                "protocol_id", Integer.valueOf(protocol.getValue()),
                "baud_rate", Integer.valueOf(baud),
                "connect_flags", Integer.valueOf(flags));
        request("Open", parameters);
        connected = true;
        return HANDLE;
    }

    @Override
    public void setConfig(int channelId, ConfigItem... items) {
        requireConnected(channelId);
        for (ConfigItem item : items) {
            request("SetConfig", map("parameter", Integer.valueOf(item.parameter),
                    "value", Integer.valueOf(item.value)));
        }
    }

    @Override
    public ConfigItem[] getConfig(int channelId, int... parameters) {
        requireConnected(channelId);
        ConfigItem[] result = new ConfigItem[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Number value = number(request("GetConfig",
                    map("parameter", Integer.valueOf(parameters[i]))), "configuration value");
            result[i] = new ConfigItem(parameters[i], value.intValue());
        }
        return result;
    }

    @Override
    public int startPassMsgFilter(int channelId, byte mask, byte pattern) {
        return addFilter(channelId, "pass", new byte[] {mask},
                new byte[] {pattern}, null, TxFlags.NO_FLAGS);
    }

    @Override
    public int startPassMsgFilter(int channelId, byte[] mask, byte[] pattern) {
        return addFilter(channelId, "pass", mask, pattern, null, TxFlags.NO_FLAGS);
    }

    @Override
    public int startPassMsgFilter(int channelId, byte[] mask, byte[] pattern,
            TxFlags flag) {
        return addFilter(channelId, "pass", mask, pattern, null, flag);
    }

    @Override
    public int startBlockMsgFilter(int channelId, byte[] mask, byte[] pattern) {
        return addFilter(channelId, "block", mask, pattern, null, TxFlags.NO_FLAGS);
    }

    @Override
    public int startBlockMsgFilter(int channelId, byte[] mask, byte[] pattern,
            TxFlags flag) {
        return addFilter(channelId, "block", mask, pattern, null, flag);
    }

    @Override
    public int startFlowCntrlFilter(int channelId, byte[] mask, byte[] pattern,
            byte[] flowCntrl, TxFlags flag) {
        return addFilter(channelId, "flow_control", mask, pattern, flowCntrl, flag);
    }

    @Override
    public byte[] fiveBaudInit(int channelId, byte[] input) {
        requireConnected(channelId);
        byte[] response = firstMessage(request("FiveBaudInit", map("data", bytes(input))));
        clearBuffers(channelId);
        return response;
    }

    @Override
    public byte[] fastInit(int channelId, byte[] input) {
        requireConnected(channelId);
        byte[] response = firstMessage(request("FastInit", map("data", bytes(input))));
        clearBuffers(channelId);
        return response;
    }

    @Override
    public double getVbattery(int deviceId) {
        requireHandle(deviceId, "device");
        return number(request("ReadBatteryVoltage", null), "battery voltage").doubleValue();
    }

    @Override
    public void writeMsg(int channelId, byte[] data, long timeout, TxFlags flag) {
        requireConnected(channelId);
        boolean can = isCanProtocol();
        long arbitrationId = 0L;
        byte[] payload = data;
        if (can) {
            if (data.length < 4) {
                throw new J2534Exception("CAN/J2534 messages require a four-byte arbitration ID");
            }
            arbitrationId = (long) (data[0] & 0xFF) << 24
                    | (long) (data[1] & 0xFF) << 16
                    | (long) (data[2] & 0xFF) << 8
                    | data[3] & 0xFFL;
            payload = Arrays.copyOfRange(data, 4, data.length);
        }
        boolean extended = flag == TxFlags.CAN_29BIT_ID || arbitrationId > 0x7FFL;
        request("SendMessage", map(
                "arb_id", Long.valueOf(arbitrationId),
                "data", bytes(payload),
                "extended", Boolean.valueOf(extended),
                "tx_flags", Integer.valueOf(flag.getValue()),
                "timeout_ms", Long.valueOf(timeout)));
    }

    @Override
    public byte[] readMsg(int channelId, int numMsg, long timeout) {
        requireConnected(channelId);
        if (numMsg <= 0) return new byte[0];
        List<byte[]> messages = new ArrayList<byte[]>();
        long deadline = currentTimeMillis() + timeout;
        while (messages.size() < numMsg) {
            if (!received.isEmpty()) {
                messages.add(received.removeFirst());
                continue;
            }
            long remaining = deadline - currentTimeMillis();
            if (remaining <= 0L) {
                throw new J2534Exception("readMsg error: timeout expired waiting for "
                        + (numMsg - messages.size()) + " more message(s)");
            }
            receive(Math.min(remaining, Integer.MAX_VALUE));
        }
        return concat(messages);
    }

    @Override
    public byte[] readVehicleResponse(int channelId, long timeout) {
        return readMsg(channelId, 1, timeout);
    }

    @Override
    public byte[] readMsg(int channelId, long maxWait) {
        requireConnected(channelId);
        if (received.isEmpty()) receive(maxWait);
        List<byte[]> messages = new ArrayList<byte[]>(received);
        received.clear();
        return concat(messages);
    }

    @Override
    public void readMsg(int channelId, byte[] response, long timeout) {
        requireConnected(channelId);
        int offset = 0;
        long deadline = currentTimeMillis() + timeout;
        while (offset < response.length) {
            long remaining = deadline - currentTimeMillis();
            if (remaining <= 0L) {
                throw new J2534Exception("readMsg error: timeout expired waiting for "
                        + (response.length - offset) + " more bytes");
            }
            byte[] message = readMsg(channelId, 1, remaining);
            int count = Math.min(message.length, response.length - offset);
            arraycopy(message, 0, response, offset, count);
            offset += count;
        }
    }

    @Override
    public void stopMsgFilter(int channelId, int msgId) {
        requireConnected(channelId);
        request("RemoveFilter", map("filter_id", Integer.valueOf(msgId)));
    }

    @Override
    public void clearBuffers(int channelId) {
        requireConnected(channelId);
        request("ClearBuffers", null);
        received.clear();
    }

    @Override
    public void disconnect(int channelId) {
        if (!connected) return;
        requireHandle(channelId, "channel");
        request("Close", null);
        connected = false;
        received.clear();
    }

    @Override
    public void close(int deviceId) {
        requireHandle(deviceId, "device");
        if (client == null) return;
        client.close();
        client = null;
        connected = false;
        received.clear();
    }

    private int addFilter(int channelId, String type, byte[] mask, byte[] pattern,
            byte[] flowControl, TxFlags flag) {
        requireConnected(channelId);
        Map<String, Object> parameters = map(
                "filter_type", type,
                "mask", bytes(mask),
                "pattern", bytes(pattern),
                "extended", Boolean.valueOf(flag == TxFlags.CAN_29BIT_ID),
                "tx_flags", Integer.valueOf(flag.getValue()));
        if (flowControl != null) parameters.put("flow_control", bytes(flowControl));
        int filterId = number(request("AddFilterRaw", parameters), "filter ID").intValue();
        clearBuffers(channelId);
        return filterId;
    }

    private void receive(long timeout) {
        Object value = request("ReadMessages", map(
                "timeout_ms", Long.valueOf(Math.max(0L, timeout)),
                "batch_size", Integer.valueOf(256),
                "max_drain_reads", Integer.valueOf(1)));
        List<?> messages = list(value, "messages");
        for (Object item : messages) {
            Map<?, ?> message = object(item, "message");
            byte[] payload = byteArray(message.get("data"), "message data");
            if (isCanProtocol()) {
                long arbitrationId = number(message.get("rawArbId"),
                        "arbitration ID").longValue();
                byte[] framed = new byte[payload.length + 4];
                framed[0] = (byte) (arbitrationId >>> 24);
                framed[1] = (byte) (arbitrationId >>> 16);
                framed[2] = (byte) (arbitrationId >>> 8);
                framed[3] = (byte) arbitrationId;
                arraycopy(payload, 0, framed, 4, payload.length);
                payload = framed;
            }
            received.addLast(payload);
        }
    }

    private byte[] firstMessage(Object value) {
        List<?> messages = list(value, "messages");
        if (messages.isEmpty()) return new byte[0];
        return byteArray(object(messages.get(0), "message").get("data"), "message data");
    }

    private Object request(String method, Map<String, Object> parameters) {
        if (client == null) throw new J2534Exception("J2534 bridge is not open");
        return client.request(method, parameters);
    }

    private boolean isCanProtocol() {
        return protocol == Protocol.CAN || protocol == Protocol.ISO15765;
    }

    private void requireConnected(int channelId) {
        requireHandle(channelId, "channel");
        if (!connected) throw new J2534Exception("J2534 bridge channel is not connected");
    }

    private static void requireHandle(int handle, String type) {
        if (handle != HANDLE) throw new J2534Exception("Invalid J2534 " + type + " handle");
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], entries[i + 1]);
        }
        return result;
    }

    private static List<Integer> bytes(byte[] input) {
        List<Integer> result = new ArrayList<Integer>(input.length);
        for (byte value : input) result.add(Integer.valueOf(value & 0xFF));
        return result;
    }

    private static byte[] byteArray(Object value, String description) {
        List<?> values = list(value, description);
        byte[] result = new byte[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = (byte) number(values.get(i), description).intValue();
        }
        return result;
    }

    private static Map<?, ?> object(Object value, String description) {
        if (!(value instanceof Map)) {
            throw new J2534Exception("J2534 bridge returned an invalid " + description);
        }
        return (Map<?, ?>) value;
    }

    private static List<?> list(Object value, String description) {
        if (!(value instanceof List)) {
            throw new J2534Exception("J2534 bridge returned invalid " + description);
        }
        return (List<?>) value;
    }

    private static Number number(Object value, String description) {
        if (!(value instanceof Number)) {
            throw new J2534Exception("J2534 bridge returned an invalid " + description);
        }
        return (Number) value;
    }

    private static String string(Map<?, ?> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof String)) {
            throw new J2534Exception("J2534 bridge response is missing " + name);
        }
        return (String) value;
    }

    private static byte[] concat(List<byte[]> messages) {
        int length = 0;
        for (byte[] message : messages) length += message.length;
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] message : messages) {
            arraycopy(message, 0, result, offset, message.length);
            offset += message.length;
        }
        return result;
    }
}
