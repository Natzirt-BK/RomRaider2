/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** Positive capability declarations for one protocol, target, and device. */
public final class FlashCapabilities {
    public static final FlashCapabilities NONE =
            new FlashCapabilities(false, false, false, false);

    private final boolean canRead;
    private final boolean canWrite;
    private final boolean canVerify;
    private final boolean canRecover;

    public FlashCapabilities(boolean canRead, boolean canWrite,
            boolean canVerify, boolean canRecover) {
        this.canRead = canRead;
        this.canWrite = canWrite;
        this.canVerify = canVerify;
        this.canRecover = canRecover;
    }

    public boolean canRead() { return canRead; }
    public boolean canWrite() { return canWrite; }
    public boolean canVerify() { return canVerify; }
    public boolean canRecover() { return canRecover; }

    public boolean supports(FlashOperation operation) {
        if (operation == null) return false;
        switch (operation) {
            case READ: return canRead;
            case WRITE: return canWrite;
            case VERIFY: return canVerify;
            case RECOVER: return canRecover;
            default: return false;
        }
    }
}
