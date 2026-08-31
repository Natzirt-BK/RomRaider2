/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import java.util.Arrays;

/** Immutable request passed from application services to the flash backend. */
public final class FlashRequest {
    private final FlashOperation operation;
    private final FlashTarget target;
    private final FlashDevice device;
    private final byte[] romImage;

    public FlashRequest(FlashOperation operation, FlashTarget target,
            FlashDevice device, byte[] romImage) {
        if (operation == null) throw new IllegalArgumentException("operation is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (device == null) throw new IllegalArgumentException("device is required");
        if ((operation == FlashOperation.WRITE || operation == FlashOperation.VERIFY)
                && (romImage == null || romImage.length == 0)) {
            throw new IllegalArgumentException("write and verify require a ROM image");
        }
        this.operation = operation;
        this.target = target;
        this.device = device;
        this.romImage = romImage == null ? null
                : Arrays.copyOf(romImage, romImage.length);
    }

    public FlashOperation getOperation() { return operation; }
    public FlashTarget getTarget() { return target; }
    public FlashDevice getDevice() { return device; }
    public boolean hasRomImage() { return romImage != null; }
    public int getRomImageSize() { return romImage == null ? 0 : romImage.length; }
    public byte[] copyRomImage() {
        return romImage == null ? null : Arrays.copyOf(romImage, romImage.length);
    }
}
