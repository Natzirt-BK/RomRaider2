package com.romraider.io.j2534.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.romraider.io.j2534.api.J2534Impl.Config;
import com.romraider.io.j2534.api.J2534Impl.Protocol;
import com.romraider.io.j2534.api.J2534Impl.TxFlags;

public final class BridgedJ2534Test {
    @Test
    public void translatesKlineOperationsWithoutChangingVendorBytes() {
        FakeChannel channel = new FakeChannel();
        BridgedJ2534 api = new BridgedJ2534(Protocol.ISO9141,
                new File("C:/Tactrix/op20pt32.dll"), 32, bits -> channel);

        int device = api.open();
        int connection = api.connect(device, 0x200, 15625);
        Version version = api.readVersion(device);
        assertEquals("firmware", version.firmware);
        api.setConfig(connection, new ConfigItem(Config.DATA_BITS.getValue(), 0));
        int filter = api.startPassMsgFilter(connection, (byte) 0, (byte) 0);
        api.writeMsg(connection, new byte[] {(byte) 0x80, 0x10, (byte) 0xF0, 0x01},
                1000L, TxFlags.NO_FLAGS);
        channel.nextMessages = messages(0L,
                new byte[] {(byte) 0x80, (byte) 0xF0, 0x10, 0x01});

        assertArrayEquals(new byte[] {(byte) 0x80, (byte) 0xF0, 0x10, 0x01},
                api.readVehicleResponse(connection, 1000L));
        api.stopMsgFilter(connection, filter);
        api.disconnect(connection);
        api.close(device);

        Call open = channel.first("Open");
        assertEquals(Long.valueOf(3), asLong(open.parameters.get("protocol_id")));
        assertEquals(Long.valueOf(15625), asLong(open.parameters.get("baud_rate")));
        Call send = channel.first("SendMessage");
        assertEquals(Long.valueOf(0), asLong(send.parameters.get("arb_id")));
        assertEquals(Arrays.asList(128, 16, 240, 1), send.parameters.get("data"));
        assertTrue(channel.closed);
    }

    @Test
    public void preservesCanArbitrationIdFlagsAndFlowControlFilterData() {
        FakeChannel channel = new FakeChannel();
        BridgedJ2534 api = new BridgedJ2534(Protocol.ISO15765,
                new File("C:/Vendor/passthru64.dll"), 64, bits -> channel);
        int device = api.open();
        int connection = api.connect(device, 0, 500000);

        api.writeMsg(connection,
                new byte[] {0, 0, 0x07, (byte) 0xE0, 0x22, (byte) 0xF1, (byte) 0x90},
                2000L, TxFlags.ISO15765_FRAME_PAD);
        api.startFlowCntrlFilter(connection,
                new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                new byte[] {0, 0, 0x07, (byte) 0xE8},
                new byte[] {0, 0, 0x07, (byte) 0xE0},
                TxFlags.ISO15765_FRAME_PAD);
        channel.nextMessages = messages(0x7E8L,
                new byte[] {0x62, (byte) 0xF1, (byte) 0x90});

        assertArrayEquals(new byte[] {0, 0, 0x07, (byte) 0xE8,
                0x62, (byte) 0xF1, (byte) 0x90},
                api.readMsg(connection, 1, 1000L));

        Call send = channel.first("SendMessage");
        assertEquals(Long.valueOf(0x7E0), asLong(send.parameters.get("arb_id")));
        assertEquals(Arrays.asList(34, 241, 144), send.parameters.get("data"));
        assertEquals(Long.valueOf(TxFlags.ISO15765_FRAME_PAD.getValue()),
                asLong(send.parameters.get("tx_flags")));
        Call filter = channel.first("AddFilterRaw");
        assertEquals(Arrays.asList(0, 0, 7, 224),
                filter.parameters.get("flow_control"));
        api.disconnect(connection);
        api.close(device);
    }

    private static List<Object> messages(long arbitrationId, byte[] data) {
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("rawArbId", Long.valueOf(arbitrationId));
        List<Integer> bytes = new ArrayList<Integer>();
        for (byte value : data) bytes.add(Integer.valueOf(value & 0xFF));
        message.put("data", bytes);
        return Arrays.<Object>asList(message);
    }

    private static Long asLong(Object number) {
        return Long.valueOf(((Number) number).longValue());
    }

    private static final class Call {
        private final String method;
        private final Map<String, Object> parameters;

        private Call(String method, Map<String, Object> parameters) {
            this.method = method;
            this.parameters = parameters;
        }
    }

    private static final class FakeChannel implements J2534BridgeChannel {
        private final List<Call> calls = new ArrayList<Call>();
        private List<Object> nextMessages = new ArrayList<Object>();
        private boolean closed;

        @Override
        public Object request(String method, Map<String, Object> parameters) {
            calls.add(new Call(method, parameters));
            if ("ReadVersion".equals(method)) {
                Map<String, Object> version = new LinkedHashMap<String, Object>();
                version.put("firmwareVersion", "firmware");
                version.put("dllVersion", "dll");
                version.put("apiVersion", "04.04");
                return version;
            }
            if ("AddFilterRaw".equals(method)) return Long.valueOf(9);
            if ("GetConfig".equals(method)) return Long.valueOf(0);
            if ("ReadMessages".equals(method)) {
                List<Object> messages = nextMessages;
                nextMessages = new ArrayList<Object>();
                return messages;
            }
            return null;
        }

        @Override
        public void close() {
            closed = true;
        }

        private Call first(String method) {
            for (Call call : calls) {
                if (method.equals(call.method)) return call;
            }
            throw new AssertionError("No bridge call for " + method);
        }
    }
}
