/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.openport;

import com.romraider.portable.logger.PortableLoggerProtocol;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bounded, fragmented adapter replies; never used to scan binary ECU frames. */
public final class OpenPortControlResponse {
    private final String operation;
    private final String expectedPrefix;
    private final ByteArrayOutputStream received = new ByteArrayOutputStream();

    private OpenPortControlResponse(String operation, String expectedPrefix) {
        if (operation == null || operation.isEmpty()) {
            throw new IllegalArgumentException("Adapter operation is required");
        }
        this.operation = operation;
        this.expectedPrefix = expectedPrefix;
    }

    public static OpenPortControlResponse forPrefix(String operation,
            String expectedPrefix) {
        if (expectedPrefix == null || expectedPrefix.isEmpty()) {
            throw new IllegalArgumentException("Acknowledgement prefix is required");
        }
        return new OpenPortControlResponse(operation, expectedPrefix);
    }

    public static OpenPortControlResponse forKLineFilter(
            PortableLoggerProtocol protocol) {
        if (protocol == null) throw new IllegalArgumentException("Protocol required");
        return new OpenPortControlResponse(protocol.name() + " receive filter setup", null);
    }

    public boolean accept(byte[] chunk, int count) throws IOException {
        if (chunk == null || count < 0 || count > chunk.length) {
            throw new IllegalArgumentException("Invalid USB response length");
        }
        if (count > OpenPortWireProtocol.MAX_CONTROL_RESPONSE_BYTES - received.size()) {
            throw new IOException("OpenPort " + operation + ": control response is too large.");
        }
        received.write(chunk, 0, count);
        byte[] response = received.toByteArray();
        String text = new String(response, StandardCharsets.ISO_8859_1);
        for (String line : text.split("(?<=\\r\\n)", -1)) {
            if (line.matches("are[0-9]*(?: [^\\r\\n]*)?\\r\\n")) {
                throw new IOException("OpenPort " + operation + ": adapter reported a command error.");
            }
        }
        return expectedPrefix == null
                ? OpenPortWireProtocol.hasCompleteKLineFilterResponse(response, response.length)
                : OpenPortWireProtocol.hasCompleteResponse(response, response.length, expectedPrefix);
    }

    public byte[] bytes() {
        return received.toByteArray();
    }

    public String timeoutMessage() {
        return "OpenPort timed out during " + operation + ": "
                + (received.size() == 0 ? "no USB reply received."
                : "received " + received.size() + " USB bytes, but no valid acknowledgement.");
    }
}
