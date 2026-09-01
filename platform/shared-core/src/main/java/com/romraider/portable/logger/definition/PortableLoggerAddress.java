/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

/** A bounded contiguous SSM address range. */
public final class PortableLoggerAddress {
    private final int address;
    private final int length;

    public PortableLoggerAddress(int address, int length) {
        if (address < 0 || address > 0xFFFFFF || length < 1 || length > 8
                || address + length - 1 > 0xFFFFFF) {
            throw new IllegalArgumentException("Logger address range is invalid");
        }
        this.address = address;
        this.length = length;
    }

    public int getAddress() { return address; }
    public int getLength() { return length; }

    public int[] expand() {
        int[] addresses = new int[length];
        for (int index = 0; index < length; index++) {
            addresses[index] = address + index;
        }
        return addresses;
    }
}
