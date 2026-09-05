/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.openport.OpenPortControlResponse;
import com.romraider.portable.openport.OpenPortWireProtocol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Real adapter reply fixture, exercised without USB or ECU access. */
public final class PortableOpenPortControlCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        assertions = 0;
        // OpenPort 1.17.4955, channel 3 pass-filter reply captured September 5.
        byte[] captured = bytes("arf3 0 0\r\n");
        check(!OpenPortWireProtocol.hasCompleteResponse(captured,
                captured.length, "arf "), "Reproduce the old prefix mismatch");
        for (PortableLoggerProtocol protocol : PortableLoggerProtocol.values()) {
            check(filter(protocol).accept(captured, captured.length),
                    protocol + " accepts the captured reply");
            for (int split = 0; split < captured.length; split++) {
                OpenPortControlResponse reply = filter(protocol);
                check(!reply.accept(captured, split), "Incomplete prefix cannot succeed");
                byte[] tail = Arrays.copyOfRange(captured, split, captured.length);
                check(reply.accept(tail, tail.length), "Every fragmentation boundary completes");
                check(Arrays.equals(reply.bytes(), captured), "Accumulated response retained");
            }
            OpenPortControlResponse bytewise = filter(protocol);
            for (int index = 0; index < captured.length; index++) {
                check(bytewise.accept(new byte[] {captured[index]}, 1)
                        == (index == captured.length - 1), "Bytewise CRLF completion");
            }
            check(filter(protocol).timeoutMessage().contains(protocol.name()),
                    "Timeout identifies selected protocol");
            check(filter(protocol).timeoutMessage().contains("receive filter setup"),
                    "Timeout identifies command");
            check(filter(protocol).timeoutMessage().contains("no USB reply"),
                    "No-data timeout is explicit");
        }
        for (String valid : new String[] {"arf3 1 0\r\n", "arf3 4294967295 0\r\n",
                "\r\naro\r\narf3 0 0\r\n", "arf4 0 0\r\narf3 0 0\r\n"}) {
            check(accept(valid), "Complete matching reply after unrelated lines");
        }
        for (String invalid : new String[] {"arf ", "arf 0 0\r\n", "arf3 0\r\n",
                "arf3\r\n", "arf4 0 0\r\n", "arf30 0 0\r\n", "arf03 0 0\r\n",
                "arf3 -1 0\r\n", "arf3 +1 0\r\n", "arf3 01 0\r\n",
                "arf3 4294967296 0\r\n", "arf3 99999999999999999999 0\r\n",
                "arf3 0 1\r\n", "arf3 0 -1\r\n", "arf3 x 0\r\n",
                "arf3 0 x\r\n", "arf3 0 0 extra\r\n", "arf3 0 0 \r\n",
                "arf3 0 0\n", "arf3 0 0\r", "arf3 0 0", "arf3  0 0\r\n",
                "arf3\t0 0\r\n", "prefixarf3 0 0\r\n", "\0arf3 0 0\r\n",
                "noise\narf3 0 0\r\n", "arf3 0 0\0\r\n", ""}) {
            check(!accept(invalid), "Reject incomplete/malformed/wrong-channel reply");
        }
        for (String error : new String[] {"are 7\r\n", "are3 7\r\n",
                "are 7\r\narf3 0 0\r\n", "arf3 0 0\r\nare3 7\r\n"}) {
            expectIo(() -> accept(error), "receive filter setup");
        }
        OpenPortControlResponse error = filter(PortableLoggerProtocol.SSM);
        check(!error.accept(bytes("are3 7\r"), 7), "Incomplete error waits for LF");
        expectIo(() -> error.accept(bytes("\n"), 1), "command error");
        OpenPortControlResponse unrecognized = filter(PortableLoggerProtocol.SSM);
        byte[] privateText = bytes("unrecognized-private-payload\r\n");
        check(!unrecognized.accept(privateText, privateText.length), "Unknown response is not success");
        check(unrecognized.timeoutMessage().contains("received " + privateText.length + " USB bytes"),
                "Nonempty timeout reports byte count");
        check(!unrecognized.timeoutMessage().contains("private-payload"), "No raw bytes in diagnostics");
        OpenPortControlResponse bounded = filter(PortableLoggerProtocol.SSM);
        check(!bounded.accept(new byte[OpenPortWireProtocol.MAX_CONTROL_RESPONSE_BYTES],
                OpenPortWireProtocol.MAX_CONTROL_RESPONSE_BYTES), "Exactly bounded data is retained");
        expectIo(() -> bounded.accept(new byte[] {0}, 1), "too large");
        check(bounded.bytes().length == OpenPortWireProtocol.MAX_CONTROL_RESPONSE_BYTES,
                "Overflow never enters buffer");
        expectIo(() -> filter(PortableLoggerProtocol.SSM).accept(
                new byte[4097], 4097), "too large");
        check(!OpenPortWireProtocol.hasCompleteKLineFilterResponse(captured, captured.length + 1),
                "Invalid declared length rejected");
        check(!OpenPortWireProtocol.hasCompleteKLineFilterResponse(null, 1), "Null response rejected");
        for (String[] example : new String[][] {
                {"firmware identification", "ari ", "ari main code version : 1.17.4955\r\n"},
                {"adapter preparation", "aro\r\n", "aro\r\n"},
                {"battery measurement", "arr ", "arr 16 12330\r\n"}}) {
            OpenPortControlResponse reply = OpenPortControlResponse.forPrefix(example[0], example[1]);
            byte[] data = bytes(example[2]);
            check(!reply.accept(data, data.length - 1), "Existing acknowledgements wait for full line");
            check(reply.accept(new byte[] {'\n'}, 1), "Existing acknowledgements remain supported");
        }
        System.out.println("Portable OpenPort control checks passed: " + assertions);
    }

    private static OpenPortControlResponse filter(PortableLoggerProtocol protocol) {
        return OpenPortControlResponse.forKLineFilter(protocol);
    }
    private static boolean accept(String text) throws IOException {
        byte[] data = bytes(text);
        return filter(PortableLoggerProtocol.SSM).accept(data, data.length);
    }
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private interface Action { void run() throws IOException; }
    private static void expectIo(Action action, String message) throws IOException {
        assertions++;
        try { action.run(); } catch (IOException expected) {
            if (!expected.getMessage().contains(message)) throw expected;
            return;
        }
        throw new AssertionError("Expected IOException containing " + message);
    }
}
