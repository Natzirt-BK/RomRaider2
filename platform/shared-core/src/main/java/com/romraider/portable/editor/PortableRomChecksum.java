/* RomRaider2 ECU Studio - GPL 2.0 or later.
 * Subaru sum algorithm derived from com.romraider.maps.RomChecksum.
 */
package com.romraider.portable.editor;

import java.util.ArrayList;
import java.util.List;

/** Definition-backed Subaru checksums. Never changes caller-owned ROM bytes. */
public final class PortableRomChecksum {
    private static final int TOTAL = 0x5AA5A55A;
    public enum Status { VALID, NEEDS_CORRECTION, DISABLED, UNSUPPORTED, INVALID }

    private final List<int[]> blocks;
    private final byte[] original;
    private final String unsupported;

    PortableRomChecksum(List<int[]> blocks, byte[] original, String unsupported) {
        this.blocks = new ArrayList<>();
        for (int[] block : blocks) this.blocks.add(block.clone());
        this.original = original.clone();
        this.unsupported = unsupported;
    }

    public Status validate(byte[] bytes) {
        if (unsupported != null || blocks.isEmpty()) return Status.UNSUPPORTED;
        try {
            checkStructure(bytes);
            // Desktop treats the first disabled entry as disabling its block.
            // Preserve that state; never silently re-enable checksum protection.
            for (int[] block : blocks) {
                int p = block[0];
                if (word(bytes, p) == 0 && word(bytes, p + 4) == 0
                        && word(bytes, p + 8) == TOTAL) return Status.DISABLED;
            }
            for (int[] block : blocks) {
                for (int p = block[0]; p < block[0] + block[1]; p += 12) {
                    if (word(bytes, p + 8) != sum(bytes, p)) return Status.NEEDS_CORRECTION;
                }
            }
            return Status.VALID;
        } catch (IllegalArgumentException ex) {
            return Status.INVALID;
        }
    }

    public String description(byte[] bytes) {
        switch (validate(bytes)) {
            case VALID: return "Subaru checksums valid. Save Copy rechecks and corrects after edits.";
            case NEEDS_CORRECTION: return "Subaru checksums need correction. Save Copy will correct them.";
            case DISABLED: return "Checksum protection is disabled in this ROM. Save Copy preserves it; desktop validation required.";
            case INVALID: return "Checksum layout is invalid or has changed. Saving is blocked; reload the ROM and definition.";
            default: return "Checksum correction unavailable" + (unsupported == null ? "." : ": " + unsupported)
                    + " Review copies require desktop checksum validation.";
        }
    }

    /** Corrects supported checksums on a copy; unsupported/disabled remain unchanged. */
    public byte[] prepareCopy(byte[] bytes) {
        Status status = validate(bytes);
        if (status == Status.INVALID) throw new IllegalArgumentException(
                "Invalid checksum layout; no ROM copy was written");
        byte[] result = bytes.clone();
        if (status != Status.NEEDS_CORRECTION) return result;
        for (int[] block : blocks) {
            for (int p = block[0]; p < block[0] + block[1]; p += 12) {
                int value = sum(result, p);
                for (int i = 0; i < 4; i++) result[p + 8 + i] = (byte) (value >>> (24 - 8 * i));
            }
        }
        if (validate(result) != Status.VALID) throw new IllegalArgumentException(
                "Corrected checksum verification failed; no ROM copy was written");
        return result;
    }

    private void checkStructure(byte[] bytes) {
        if (bytes == null || bytes.length != original.length) throw new IllegalArgumentException();
        if (blocks.size() > 64) throw new IllegalArgumentException();
        long work = 0;
        long entries = 0;
        for (int[] block : blocks) {
            int address = block[0], size = block[1];
            if (address < 0 || size <= 0 || size % 12 != 0 || (address & 3) != 0
                    || address > bytes.length - size) throw new IllegalArgumentException();
            entries += size / 12;
            if (entries > 4096) throw new IllegalArgumentException();
            for (int[] other : blocks) {
                if (other != block && overlaps(address, address + size, other[0], (long) other[0] + other[1]))
                    throw new IllegalArgumentException();
            }
            for (int p = address; p < address + size; p += 12) {
                int start = word(bytes, p), end = word(bytes, p + 4);
                // The desktop loop reads a full word while its start is below
                // end. Stock Subaru ROMs can use an unaligned end marker.
                long readEnd = ((long) end + 3) & ~3L;
                if (start != word(original, p) || end != word(original, p + 4)
                        || start < 0 || end < start || end > bytes.length
                        || (start & 3) != 0 || readEnd > bytes.length) throw new IllegalArgumentException();
                for (int[] target : blocks) {
                    if (overlaps(start, readEnd, target[0], (long) target[0] + target[1]))
                        throw new IllegalArgumentException();
                }
                work += readEnd - start;
                if (work > 64L * 1024 * 1024) throw new IllegalArgumentException();
            }
        }
    }

    private static boolean overlaps(long a, long b, long c, long d) {
        return a < b && c < d && a < d && c < b;
    }

    private static int sum(byte[] bytes, int entry) {
        int sum = 0;
        for (int p = word(bytes, entry); p < word(bytes, entry + 4); p += 4) sum += word(bytes, p);
        return TOTAL - sum; // Deliberate 32-bit wrap, matching the desktop algorithm.
    }

    private static int word(byte[] bytes, int p) {
        return (bytes[p] & 255) << 24 | (bytes[p + 1] & 255) << 16
                | (bytes[p + 2] & 255) << 8 | bytes[p + 3] & 255;
    }
}
