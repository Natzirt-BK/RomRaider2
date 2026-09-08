/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Offline command framing and strict, headerless Mode 01 response decoding.
 * Requires headers off and CAN auto-formatting on. This is not a transport:
 * callers must enforce transaction deadlines/cancellation and synchronize the
 * prompt before sending. Construct one instance per transaction; never reuse
 * incomplete responses. Header-bearing/multiframe replies are not supported.
 */
public final class Elm327Transcript {
    public static final int MAX_RESPONSE_CHARS = 4096;
    public enum Status {
        DATA, INCOMPLETE, NO_DATA, STOPPED, BUS_ERROR, NEGATIVE_RESPONSE,
        AMBIGUOUS, MALFORMED
    }

    public static final class Result {
        private final Status status;
        private final byte[] data;
        private Result(Status status, byte[] data) {
            this.status = status;
            this.data = data.clone();
        }
        public Status getStatus() { return status; }
        public byte[] getData() { return data.clone(); }
    }

    private final StringBuilder response = new StringBuilder();
    private boolean complete;
    private boolean malformed;
    private int received;

    /** ELM commands end in CR on every host OS. Embedded commands are rejected. */
    public static byte[] command(String command) {
        Objects.requireNonNull(command, "command");
        if (command.trim().isEmpty() || command.length() > 128) {
            throw new IllegalArgumentException("Expected one non-empty ELM command");
        }
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c < 32 || c > 126 || c == '>') {
                throw new IllegalArgumentException("Expected printable ASCII without framing characters");
            }
        }
        return (command + "\r").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] mode01Command(int pid) {
        checkPid(pid);
        return command(String.format(Locale.ROOT, "01%02X", pid));
    }

    /** Accept arbitrary serial chunks, but never silently merge two prompts. */
    public void accept(CharSequence chunk) {
        Objects.requireNonNull(chunk, "chunk");
        for (int i = 0; i < chunk.length() && !malformed; i++) {
            if (++received > MAX_RESPONSE_CHARS) { malformed = true; break; }
            char c = chunk.charAt(i);
            if (complete) {
                if (c != '\r' && c != '\n' && c != ' ' && c != '\t') malformed = true;
            } else if (c == '>') {
                complete = true;
            } else if ((c >= 32 && c <= 126) || c == '\r' || c == '\n' || c == '\t') {
                response.append(c);
            } else {
                malformed = true;
            }
        }
    }

    public boolean isComplete() { return complete && !malformed; }

    public boolean isMalformed() { return malformed; }

    /** Complete adapter reply, excluding the prompt. Never exposes a partial frame. */
    public String responseText() {
        if (!isComplete()) throw new IllegalStateException("ELM response is not complete");
        return response.toString();
    }

    /**
     * Never infer a value from a PID occurring inside arbitrary bytes. A single
     * complete positive service/PID/length match is required. Multiple replies
     * are ambiguous even if identical: with headers hidden their owners cannot
     * be established. No partial value is returned alongside an error.
     */
    public Result mode01(int pid, int dataLength) {
        checkPid(pid);
        if (dataLength < 1 || dataLength > 32) throw new IllegalArgumentException("Invalid PID width");
        if (malformed) return result(Status.MALFORMED);
        if (!complete) return result(Status.INCOMPLETE);
        byte[] found = null;
        Status failure = null;
        String echo = String.format(Locale.ROOT, "01%02X", pid);
        for (String line : response.toString().toUpperCase(Locale.ROOT).split("[\r\n]+")) {
            String value = line.replace(" ", "").replace("\t", "");
            if (value.isEmpty() || value.equals(echo)) continue;
            // Progress can precede the reply on the same line.
            if (value.startsWith("SEARCHING...")) value = value.substring(12);
            if (value.startsWith("BUSINIT...OK")) value = value.substring(12);
            if (value.isEmpty()) continue;
            Status lineFailure = null;
            if (value.equals("NODATA")) lineFailure = Status.NO_DATA;
            else if (value.equals("STOPPED")) lineFailure = Status.STOPPED;
            else if (value.equals("UNABLETOCONNECT") || value.equals("CANERROR")
                    || value.equals("BUSERROR") || value.equals("BUFFERFULL")
                    || value.startsWith("BUSINIT")) lineFailure = Status.BUS_ERROR;
            if (lineFailure != null) { failure = lineFailure; continue; }
            if (!value.matches("(?:[0-9A-F]{2})+")) return result(Status.MALFORMED);
            byte[] bytes = new byte[value.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
            }
            if (bytes.length == 3 && bytes[0] == 0x7f && bytes[1] == 0x01) {
                failure = Status.NEGATIVE_RESPONSE;
                continue;
            }
            if (bytes.length != dataLength + 2 || bytes[0] != 0x41 || (bytes[1] & 255) != pid) {
                return result(Status.MALFORMED);
            }
            if (found != null) failure = Status.AMBIGUOUS;
            found = Arrays.copyOfRange(bytes, 2, bytes.length);
        }
        if (failure != null) return result(failure);
        return found == null ? result(Status.MALFORMED) : new Result(Status.DATA, found);
    }

    private static Result result(Status status) { return new Result(status, new byte[0]); }
    private static void checkPid(int pid) {
        if (pid < 0 || pid > 255) throw new IllegalArgumentException("PID must fit one byte");
    }
}
