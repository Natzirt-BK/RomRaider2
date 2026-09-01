/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

/** One contiguous difference between a loaded ROM and its current bytes. */
public final class PortableByteChange {
    private final int offset;
    private final byte[] original;
    private final byte[] current;

    PortableByteChange(int offset, byte[] original, byte[] current) {
        this.offset = offset;
        this.original = original.clone();
        this.current = current.clone();
    }

    public int getOffset() { return offset; }
    public int getLength() { return original.length; }
    public byte[] getOriginal() { return original.clone(); }
    public byte[] getCurrent() { return current.clone(); }
}
