/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.io.j2534.api;

import static com.romraider.io.protocol.ssm.iso9141.SSMChecksumCalculator.calculateChecksum;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.Test;

import com.romraider.logger.ecu.comms.manager.PollingState;
import com.romraider.logger.ecu.comms.manager.PollingStateImpl;

public class J2534ConnectionISO9141Test {
    @Test
    public void validatesOnlyOneCompleteExpectedSsmFrame() {
        byte[] frame = frame((byte) 0x11, (byte) 0x22);
        assertTrue(J2534ConnectionISO9141.isValidResponse(frame, frame.length));
        assertFalse(J2534ConnectionISO9141.isValidResponse(frame, frame.length + 1));

        byte[] corrupt = frame.clone();
        corrupt[corrupt.length - 1]++;
        assertFalse(J2534ConnectionISO9141.isValidResponse(corrupt, corrupt.length));
    }

    @Test
    public void synthesizesRequestEchoForInitialPollingResponse() {
        StubJ2534 stub = new StubJ2534(frame((byte) 0x11, (byte) 0x22));
        J2534ConnectionISO9141 connection = new J2534ConnectionISO9141(
                stub.proxy(), 7, 100L);
        PollingState state = new PollingStateImpl();
        byte[] request = new byte[] {(byte) 0x80, 0x10, (byte) 0xF0, 0x01, (byte) 0xA8, 0x09};
        byte[] expectedFrame = frame((byte) 0x11, (byte) 0x22);
        byte[] response = new byte[request.length + expectedFrame.length];

        connection.send(request, response, state);

        byte[] expected = new byte[response.length];
        System.arraycopy(request, 0, expected, 0, request.length);
        System.arraycopy(expectedFrame, 0, expected, request.length, expectedFrame.length);
        assertArrayEquals(expected, response);
        assertEquals(1, stub.requestWrites);
    }

    @Test
    public void resynchronizesWhenFastPollStillReturnsPreviousQueryShape() {
        byte[] oldFrame = frame((byte) 0x01);
        byte[] currentFrame = frame((byte) 0x11, (byte) 0x22);
        StubJ2534 stub = new StubJ2534(oldFrame, currentFrame);
        J2534ConnectionISO9141 connection = new J2534ConnectionISO9141(
                stub.proxy(), 7, 100L);
        PollingState state = new PollingStateImpl();
        state.setCurrentState(PollingState.State.STATE_1);
        state.setLastState(PollingState.State.STATE_1);
        byte[] request = new byte[] {(byte) 0x80, 0x10, (byte) 0xF0, 0x01, (byte) 0xA8, 0x09};
        byte[] response = new byte[currentFrame.length];

        connection.send(request, response, state);

        assertArrayEquals(currentFrame, response);
        assertFalse(state.isNewQuery());
        assertEquals(1, stub.breakWrites);
        assertEquals(1, stub.requestWrites);
    }

    private static byte[] frame(byte... data) {
        byte[] frame = new byte[data.length + 6];
        frame[0] = (byte) 0x80;
        frame[1] = (byte) 0xF0;
        frame[2] = 0x10;
        frame[3] = (byte) (data.length + 1);
        frame[4] = (byte) 0xE8;
        System.arraycopy(data, 0, frame, 5, data.length);
        frame[frame.length - 1] = calculateChecksum(frame);
        return frame;
    }

    private static final class StubJ2534 implements InvocationHandler {
        private final Queue<byte[]> responses = new ArrayDeque<byte[]>();
        private int requestWrites;
        private int breakWrites;

        private StubJ2534(byte[]... frames) {
            for (byte[] frame : frames) responses.add(frame);
        }

        private J2534 proxy() {
            return (J2534) Proxy.newProxyInstance(
                    J2534.class.getClassLoader(), new Class<?>[] {J2534.class}, this);
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("readVehicleResponse")) return responses.remove();
            if (method.getName().equals("readMsg") && method.getParameterTypes().length == 2)
                return new byte[0];
            if (method.getName().equals("writeMsg")) {
                byte[] bytes = (byte[]) args[1];
                if (bytes.length == 20) breakWrites++;
                else requestWrites++;
                return null;
            }
            if (method.getReturnType() == Integer.TYPE) return 0;
            if (method.getReturnType() == Double.TYPE) return 0.0;
            return null;
        }
    }
}
