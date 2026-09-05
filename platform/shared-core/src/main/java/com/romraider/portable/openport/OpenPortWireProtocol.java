/*
 * RomRaider2 ECU Studio - GPL 2.0 or later.
 * OpenPort command framing is based on NikolaKozina/j2534, BSD-3-Clause.
 * See licenses/NikolaKozina-j2534-BSD-3-Clause.txt.
 */
package com.romraider.portable.openport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import com.romraider.portable.logger.PortableLoggerProtocol;

/** Platform-neutral command and response framing for a Tactrix OpenPort 2.0. */
public final class OpenPortWireProtocol {
    public static final int VENDOR_ID = 0x0403;
    public static final int PRODUCT_ID = 0xCC4D;
    public static final int MAX_CONTROL_RESPONSE_BYTES = 4096;
    public static final int ISO9141_CHANNEL = 3;
    public static final int ISO9141_NO_CHECKSUM = 0x0200;
    public static final int SSM_BAUD = 4800;
    public static final int MAX_VEHICLE_MESSAGE_BYTES = 4096;

    private OpenPortWireProtocol() { }

    public static byte[] identifyRequest() {
        return ascii("\r\n\r\nati\r\n");
    }

    public static byte[] openRequest() {
        return ascii("ata\r\n");
    }

    public static byte[] closeRequest() {
        return ascii("atz\r\n");
    }

    public static byte[] batteryVoltageRequest() {
        return ascii("atr 16\r\n");
    }

    /** Opens the OpenPort's ISO9141 channel without adapter checksumming. */
    public static byte[] openSsmKLineRequest() {
        return openKLineRequest(PortableLoggerProtocol.SSM);
    }

    public static byte[] openKLineRequest(PortableLoggerProtocol protocol) {
        if (protocol == null) throw new IllegalArgumentException("Protocol required");
        return ascii("ato" + ISO9141_CHANNEL + " "
                + ISO9141_NO_CHECKSUM + " " + protocol.baud() + " 0\r\n");
    }

    /** Fixed read-only K-line timing and 8N1; no pin voltage or init commands. */
    public static byte[][] kLineConfigurationRequests() {
        return new byte[][] {ascii("ats3 7 1\r\n"), ascii("ats3 10 1\r\n"),
                ascii("ats3 12 0\r\n"), ascii("ats3 3 0\r\n"),
                ascii("ats3 32 0\r\n"), ascii("ats3 22 0\r\n")};
    }

    public static byte[] kLinePassFilterRequest() {
        byte[] header = ascii("atf3 1 0 1\r\n");
        return java.util.Arrays.copyOf(header, header.length + 2);
    }

    /** A command prefix alone is not a complete USB response. */
    public static boolean hasCompleteResponse(byte[] response, int length,
            String prefix) {
        if (response == null || length <= 0 || prefix == null) return false;
        String text = new String(response, 0, Math.min(length, response.length),
                StandardCharsets.ISO_8859_1);
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            String line = lines[index];
            // Only CRLF-terminated lines can acknowledge a command.
            if (line.endsWith("\r") && (line + "\n").startsWith(prefix)) return true;
        }
        return false;
    }

    public static byte[] closeSsmKLineRequest() {
        return ascii("atc" + ISO9141_CHANNEL + "\r\n");
    }

    /** Wraps one already-checksummed SSM frame for an OpenPort channel write. */
    public static byte[] transmitSsmKLineRequest(byte[] frame) {
        if (frame == null || frame.length == 0
                || frame.length > MAX_VEHICLE_MESSAGE_BYTES) {
            throw new IllegalArgumentException(
                    "OpenPort vehicle message length is invalid");
        }
        byte[] header = ascii("att" + ISO9141_CHANNEL + " "
                + frame.length + " 0\r\n");
        ByteArrayOutputStream request = new ByteArrayOutputStream(
                header.length + frame.length);
        request.write(header, 0, header.length);
        request.write(frame, 0, frame.length);
        return request.toByteArray();
    }

    public static boolean contains(byte[] response, int length,
            String expected) {
        if (response == null || length <= 0 || expected == null
                || expected.isEmpty()) return false;
        byte[] needle = ascii(expected);
        int limit = Math.min(length, response.length) - needle.length;
        for (int start = 0; start <= limit; start++) {
            int index = 0;
            while (index < needle.length
                    && response[start + index] == needle[index]) index++;
            if (index == needle.length) return true;
        }
        return false;
    }

    public static String parseFirmwareVersion(byte[] response, int length) {
        String line = responseLine(response, length, "ari ");
        String version = line.substring(4).trim();
        if (version.isEmpty()) {
            throw new IllegalArgumentException(
                    "OpenPort firmware response is incomplete");
        }
        return version;
    }

    public static int parseBatteryMillivolts(byte[] response, int length) {
        String[] fields = responseLine(response, length, "arr ")
                .trim().split("\\s+");
        if (fields.length < 3 || !"arr".equals(fields[0])
                || !"16".equals(fields[1])) {
            throw new IllegalArgumentException(
                    "OpenPort battery response is invalid");
        }
        try {
            int millivolts = Integer.parseInt(fields[2]);
            if (millivolts < 0 || millivolts > 100_000) {
                throw new NumberFormatException();
            }
            return millivolts;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "OpenPort battery voltage is invalid", ex);
        }
    }

    private static String responseLine(byte[] response, int length,
            String prefix) {
        if (response == null || length <= 0) {
            throw new IllegalArgumentException("OpenPort response is empty");
        }
        String text = new String(response, 0, Math.min(length, response.length),
                StandardCharsets.ISO_8859_1);
        int start = text.indexOf(prefix);
        if (start < 0) {
            throw new IllegalArgumentException(
                    "OpenPort response did not contain " + prefix.trim());
        }
        int end = text.indexOf('\n', start);
        if (end < 0) throw new IllegalArgumentException("OpenPort response is incomplete");
        return text.substring(start, end).replace("\r", "");
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
