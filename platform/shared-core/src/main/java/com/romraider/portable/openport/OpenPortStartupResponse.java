/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.openport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transaction-correlated startup replies; skips framed binary vehicle data. */
public final class OpenPortStartupResponse {
    private final String operation;
    private final int requestId;
    private final boolean wake;
    private byte[] pending = new byte[0];
    private int received;
    private boolean complete;
    private byte[] keywords;

    private OpenPortStartupResponse(String operation, int requestId, boolean wake) {
        if (requestId < 1 || requestId > 65535) throw new IllegalArgumentException("Invalid request ID");
        if (operation == null || operation.isEmpty()) throw new IllegalArgumentException("Missing operation");
        this.operation = operation; this.requestId = requestId; this.wake = wake;
    }

    public static OpenPortStartupResponse ack(String operation, int requestId) {
        return new OpenPortStartupResponse(operation, requestId, false);
    }

    public static OpenPortStartupResponse wake(int requestId) {
        return new OpenPortStartupResponse("MUT-II five-baud initialization", requestId, true);
    }

    public boolean accept(byte[] chunk, int count) throws IOException {
        if (chunk == null || count < 0 || count > chunk.length) throw new IllegalArgumentException("Invalid USB response length");
        if (count > OpenPortWireProtocol.MAX_CONTROL_RESPONSE_BYTES - received)
            throw new IOException("OpenPort " + operation + ": response is too large.");
        received += count;
        int old = pending.length;
        pending = Arrays.copyOf(pending, old + count);
        System.arraycopy(chunk, 0, pending, old, count);
        int offset = 0;
        while (offset < pending.length) {
            int remaining = pending.length - offset;
            // A binary packet may contain an entire ASCII acknowledgement or error.
            // Consume its declared length before interpreting any control lines.
            if (remaining >= 3 && pending[offset] == 'a' && pending[offset+1] == 'r'
                    && pending[offset+2] >= '1' && pending[offset+2] <= '9') {
                if (remaining < 5) break;
                int payload = pending[offset+3] & 255;
                if (payload == 0) throw new IOException("OpenPort " + operation + ": invalid packet length.");
                if (remaining < payload + 4) break;
                offset += payload + 4;
                continue;
            }
            int end = offset;
            while (end + 1 < pending.length && !(pending[end] == '\r' && pending[end+1] == '\n')) end++;
            if (end + 1 >= pending.length) break;
            String line = new String(pending, offset, end-offset, StandardCharsets.US_ASCII);
            offset = end + 2;
            Matcher error = Pattern.compile("are([0-9]{1,10}) " + requestId).matcher(line);
            if (error.matches()) throw new IOException("OpenPort " + operation
                    + ": adapter error " + error.group(1) + ".");
            if (!wake && line.equals("aro " + requestId)) complete = true;
            if (wake) {
                Matcher match = Pattern.compile("arw3 ([0-9]{1,3}) ([0-9]{1,3}) " + requestId).matcher(line);
                if (match.matches()) {
                    int first = Integer.parseInt(match.group(1)), second = Integer.parseInt(match.group(2));
                    if (first <= 255 && second <= 255) {
                        keywords = new byte[]{(byte)first, (byte)second};
                        complete = true;
                    }
                }
            }
        }
        pending = Arrays.copyOfRange(pending, offset, pending.length);
        return complete;
    }

    public boolean isComplete() { return complete; }

    public byte[] keywords() {
        if (keywords == null) throw new IllegalStateException("No startup keywords received");
        return keywords.clone();
    }

    public String timeoutMessage() {
        return "OpenPort timed out during " + operation + ": " + received
                + " USB bytes received, but no matching acknowledgement."
                + (wake ? " Check ignition ON and the MUT-II connection." : "");
    }
}
