/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

import java.util.Arrays;

/** One bounded RAM change with the value expected before it is applied. */
public final class LiveTuneChange {
    private final String tableName;
    private final long address;
    private final byte[] expected;
    private final byte[] replacement;

    public LiveTuneChange(String tableName, long address, byte[] expected,
            byte[] replacement) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name is required");
        }
        if (address < 0 || address > 0xFFFFFFL) {
            throw new IllegalArgumentException(
                    "Live-tune addresses must fit the 24-bit address space");
        }
        if (expected == null || replacement == null || expected.length == 0) {
            throw new IllegalArgumentException(
                    "Expected and replacement values are required");
        }
        if (expected.length != replacement.length) {
            throw new IllegalArgumentException(
                    "Expected and replacement values must have the same length");
        }
        if (expected.length > LiveTunePlan.MAX_CHANGE_BYTES) {
            throw new IllegalArgumentException("A single staged change exceeds "
                    + LiveTunePlan.MAX_CHANGE_BYTES + " bytes");
        }
        if (Arrays.equals(expected, replacement)) {
            throw new IllegalArgumentException("A staged change cannot be a no-op");
        }
        if (address + expected.length - 1 > 0xFFFFFFL) {
            throw new IllegalArgumentException(
                    "Staged change extends beyond the 24-bit address space");
        }
        this.tableName = tableName.trim();
        this.address = address;
        this.expected = expected.clone();
        this.replacement = replacement.clone();
    }

    public String getTableName() {
        return tableName;
    }

    public long getAddress() {
        return address;
    }

    public int getLength() {
        return expected.length;
    }

    public long getEndAddress() {
        return address + expected.length - 1;
    }

    public byte[] getExpected() {
        return expected.clone();
    }

    public byte[] getReplacement() {
        return replacement.clone();
    }
}
