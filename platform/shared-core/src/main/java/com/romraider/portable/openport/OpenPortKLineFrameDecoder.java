/*
 * RomRaider2 ECU Studio - GPL 2.0 or later.
 * OpenPort packet framing is based on NikolaKozina/j2534, BSD-3-Clause.
 * See licenses/NikolaKozina-j2534-BSD-3-Clause.txt.
 */
package com.romraider.portable.openport;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Incrementally turns bounded OpenPort K-line USB packets into ECU frames. */
public final class OpenPortKLineFrameDecoder {
    private static final int CHANNEL_ISO9141 = 0x33;
    private static final int NORMAL_DATA = 0x00;
    private static final int RX_END = 0x40;
    private static final int EXTENDED_RX_END = 0x44;
    private static final int RX_START = 0x80;
    private static final int MAX_BUFFER_BYTES = 16 * 1024;
    private static final int MAX_FRAME_BYTES = 4096;

    private byte[] pending = new byte[0];
    private final ByteArrayOutputStream frame = new ByteArrayOutputStream();

    public List<byte[]> accept(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return Collections.emptyList();
        if (pending.length + bytes.length > MAX_BUFFER_BYTES) {
            reset();
            throw new IllegalArgumentException(
                    "OpenPort receive buffer limit reached");
        }
        byte[] combined = Arrays.copyOf(pending, pending.length + bytes.length);
        System.arraycopy(bytes, 0, combined, pending.length, bytes.length);
        List<byte[]> completed = new ArrayList<byte[]>();
        int offset = 0;
        while (combined.length - offset >= 5) {
            if (!packetHeaderAt(combined, offset)) {
                offset++;
                continue;
            }
            int payloadLength = combined[offset + 3] & 0xFF;
            if (payloadLength < 1) {
                reset();
                throw new IllegalArgumentException(
                        "OpenPort packet length is invalid");
            }
            int packetLength = payloadLength + 4;
            if (combined.length - offset < packetLength) break;
            int type = combined[offset + 4] & 0xFF;
            if (type == RX_START) {
                frame.reset();
            } else if (type == NORMAL_DATA) {
                int dataLength = payloadLength - 1;
                if (frame.size() + dataLength > MAX_FRAME_BYTES) {
                    reset();
                    throw new IllegalArgumentException(
                            "OpenPort ECU frame limit reached");
                }
                frame.write(combined, offset + 5, dataLength);
            } else if (type == RX_END || type == EXTENDED_RX_END) {
                if (frame.size() > 0) completed.add(frame.toByteArray());
                frame.reset();
            }
            offset += packetLength;
        }
        pending = Arrays.copyOfRange(combined, offset, combined.length);
        return completed;
    }

    public void reset() {
        pending = new byte[0];
        frame.reset();
    }

    private static boolean packetHeaderAt(byte[] bytes, int offset) {
        return bytes[offset] == 0x61 && bytes[offset + 1] == 0x72
                && (bytes[offset + 2] & 0xFF) == CHANNEL_ISO9141;
    }
}
