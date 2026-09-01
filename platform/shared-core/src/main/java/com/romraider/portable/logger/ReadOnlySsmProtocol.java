/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.util.Arrays;

/** Bounded Subaru SSM/ISO9141 framing with no ECU write commands. */
public final class ReadOnlySsmProtocol {
    private static final int ECU = 0x10;
    private static final int TESTER = 0xF0;
    private static final int INIT_REQUEST = 0xBF;
    private static final int INIT_RESPONSE = 0xFF;
    private static final int READ_ADDRESS_REQUEST = 0xA8;
    private static final int READ_ADDRESS_RESPONSE = 0xE8;
    private static final int MAX_ADDRESSES = 64;

    private ReadOnlySsmProtocol() { }

    public static byte[] ecuInitRequest() {
        return frame(INIT_REQUEST, new byte[0]);
    }

    public static byte[] readAddressesRequest(int... addresses) {
        if (addresses == null || addresses.length == 0
                || addresses.length > MAX_ADDRESSES) {
            throw new IllegalArgumentException(
                    "Between 1 and 64 SSM addresses are required");
        }
        byte[] payload = new byte[1 + addresses.length * 3];
        payload[0] = 0; // One-shot read; continuous polling is managed by the app.
        int offset = 1;
        for (int address : addresses) {
            if (address < 0 || address > 0xFFFFFF) {
                throw new IllegalArgumentException(
                        "SSM addresses must fit in 24 bits");
            }
            payload[offset++] = (byte) (address >>> 16);
            payload[offset++] = (byte) (address >>> 8);
            payload[offset++] = (byte) address;
        }
        return frame(READ_ADDRESS_REQUEST, payload);
    }

    public static void validateEcuInitResponse(byte[] response) {
        validateResponse(response, INIT_RESPONSE);
    }

    /** Returns the five-byte ECU identifier carried by an SSM init response. */
    public static String ecuId(byte[] response) {
        validateEcuInitResponse(response);
        if (response.length < 14) {
            throw new IllegalArgumentException(
                    "SSM init response does not contain an ECU ID");
        }
        StringBuilder id = new StringBuilder(10);
        for (int index = 8; index < 13; index++) {
            id.append(String.format(java.util.Locale.ROOT, "%02X",
                    response[index] & 0xFF));
        }
        return id.toString();
    }

    public static byte[] readAddressValues(byte[] response,
            int expectedValues) {
        if (expectedValues < 1 || expectedValues > MAX_ADDRESSES) {
            throw new IllegalArgumentException(
                    "Expected SSM value count is invalid");
        }
        validateResponse(response, READ_ADDRESS_RESPONSE);
        int values = response.length - 6;
        if (values != expectedValues) {
            throw new IllegalArgumentException("SSM response contained "
                    + values + " values; expected " + expectedValues);
        }
        return Arrays.copyOfRange(response, 5, response.length - 1);
    }

    private static byte[] frame(int command, byte[] payload) {
        int dataLength = 1 + payload.length;
        byte[] request = new byte[dataLength + 5];
        request[0] = (byte) 0x80;
        request[1] = (byte) ECU;
        request[2] = (byte) TESTER;
        request[3] = (byte) dataLength;
        request[4] = (byte) command;
        System.arraycopy(payload, 0, request, 5, payload.length);
        request[request.length - 1] = checksum(request);
        return request;
    }

    private static void validateResponse(byte[] response, int command) {
        if (response == null || response.length < 7) {
            throw new IllegalArgumentException("SSM response is too short");
        }
        if ((response[0] & 0xFF) != 0x80
                || (response[1] & 0xFF) != TESTER
                || ((response[2] & 0xFF) != ECU
                    && (response[2] & 0xFF) != 0x18)) {
            throw new IllegalArgumentException("SSM response header is invalid");
        }
        if ((response[3] & 0xFF) != response.length - 5) {
            throw new IllegalArgumentException("SSM response length is invalid");
        }
        if ((response[4] & 0xFF) != command) {
            throw new IllegalArgumentException("Unexpected SSM response command");
        }
        if (checksum(response) != response[response.length - 1]) {
            throw new IllegalArgumentException("SSM response checksum is invalid");
        }
    }

    private static byte checksum(byte[] frame) {
        int total = 0;
        for (int index = 0; index < frame.length - 1; index++) {
            total += frame[index] & 0xFF;
        }
        return (byte) total;
    }
}
