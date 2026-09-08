/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.logger.Elm327Transcript;
import com.romraider.portable.logger.Elm327Transcript.Status;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Synthetic transcripts only. Does not discover or open any adapter. */
public final class PortableElm327Check {
    public static void main(String[] args) {
        require(Arrays.equals(new byte[] {'0', '1', '0', 'C', 13}, Elm327Transcript.mode01Command(12)), "CR framing");
        require(new String(Elm327Transcript.command("AT E0"), StandardCharsets.US_ASCII).equals("AT E0\r"), "AT framing");
        for (String bad : new String[] {"", " ", "010C\r04", "010C\n", "é", ">", "A".repeat(129)}) {
            rejects(() -> Elm327Transcript.command(bad));
        }
        rejects(() -> Elm327Transcript.mode01Command(-1));
        rejects(() -> Elm327Transcript.mode01Command(256));
        for (String valid : new String[] {"41 0C 1A F8\r>", "010c\r\n410c1af8\r\n>\r\n",
                "SEARCHING...\r41\t0C 1A F8\r>", "SEARCHING...410C1AF8\r>"}) {
            for (int split = 0; split <= valid.length(); split++) {
                Elm327Transcript transcript = new Elm327Transcript();
                transcript.accept(valid.substring(0, split));
                if (!valid.substring(0, split).contains(">")) {
                    require(transcript.mode01(12, 2).getStatus() == Status.INCOMPLETE, "premature parse");
                }
                transcript.accept(valid.substring(split));
                Elm327Transcript.Result result = transcript.mode01(12, 2);
                require(result.getStatus() == Status.DATA, "positive response status");
                byte[] data = result.getData();
                require(transcript.isComplete(), "prompt completion");
                require(Arrays.equals(new byte[] {0x1a, (byte) 0xf8}, data), "split response " + split);
                data[0] = 0;
                require(result.getData()[0] == 0x1a, "result isolation");
            }
        }
        check("NO DATA\r>", Status.NO_DATA);
        check("STOPPED\r>", Status.STOPPED);
        check("BUS INIT: ERROR\r>", Status.BUS_ERROR);
        check("CAN ERROR\r>", Status.BUS_ERROR);
        check("7F 01 12\r>", Status.NEGATIVE_RESPONSE);
        check("41 0C 1A F8\r7F0112\r>", Status.NEGATIVE_RESPONSE);
        check("410C1AF8\r410C1AF8\r>", Status.AMBIGUOUS);
        check("410C1AF8\r410C1AF9\r>", Status.AMBIGUOUS);
        check("410C1AF8\rNO DATA\r>", Status.NO_DATA);
        for (String bad : new String[] {">", "?\r>", "010C\r>", "410C1A\r>", "410C1AF800\r>",
                "410D1AF8\r>", "420C1AF8\r>", "000C1AF8\r>", "410C1AFX\r>",
                "7E8 04 41 0C 1A F8\r>", "410C1AF8\r>410C1AF8\r>", "410C1AF8\r>>",
                "410C1AF8\u0000\r>", "A".repeat(Elm327Transcript.MAX_RESPONSE_CHARS) + ">"}) {
            check(bad, Status.MALFORMED);
        }
        check("410C1AF8\r", Status.INCOMPLETE);
        Elm327Transcript isolated = new Elm327Transcript();
        isolated.accept("410C1A");
        Elm327Transcript next = new Elm327Transcript();
        next.accept("F8\r>");
        require(next.mode01(12, 2).getStatus() == Status.MALFORMED, "cross-transaction data");
        rejects(() -> next.mode01(12, 0));
        rejects(() -> next.mode01(12, 33));
        Elm327Transcript pids = new Elm327Transcript();
        pids.accept("0100\r4100BE3FB813\r>");
        require(pids.mode01(0, 4).getStatus() == Status.DATA, "supported PID reply");
        Elm327Transcript boundary = new Elm327Transcript();
        boundary.accept(" ".repeat(Elm327Transcript.MAX_RESPONSE_CHARS - 9) + "410C1AF8>");
        require(boundary.mode01(12, 2).getStatus() == Status.DATA, "exact buffer boundary");
        boundary.accept(" ");
        require(boundary.mode01(12, 2).getStatus() == Status.MALFORMED, "overflow after prompt");
        require(Elm327Transcript.command("A".repeat(128)).length == 129, "exact command boundary");
        System.out.println("Portable ELM327 transcript checks passed (offline only).");
    }

    private static void check(String wire, Status expected) {
        Elm327Transcript transcript = new Elm327Transcript();
        // Exercise every byte as its own read as well as coalesced input above.
        for (int i = 0; i < wire.length(); i++) transcript.accept(wire.substring(i, i + 1));
        Elm327Transcript.Result result = transcript.mode01(12, 2);
        require(result.getStatus() == expected, "expected " + expected + ", got " + result.getStatus());
        require(result.getData().length == 0, "failure exposed data");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid command accepted");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
