/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

/** Raw DimeMod RAM Tune discovery metadata from the current ECU session. */
public final class RamTuneRuntimeMetadata {
    private static final long MAX_SSM_ADDRESS = 0xFFFFFFL;

    private final String runtimeVersion;
    private final long signatureAddress;
    private final long lookupTableSize;

    public RamTuneRuntimeMetadata(String runtimeVersion,
            long signatureAddress, long lookupTableSize) {
        this.runtimeVersion = runtimeVersion == null ? ""
                : runtimeVersion.trim();
        this.signatureAddress = signatureAddress;
        this.lookupTableSize = lookupTableSize;
    }

    public String getRuntimeVersion() { return runtimeVersion; }
    public long getSignatureAddress() { return signatureAddress; }
    public long getLookupTableSize() { return lookupTableSize; }

    public boolean isStructurallyValid() {
        return signatureAddress > 0 && signatureAddress <= MAX_SSM_ADDRESS
                && lookupTableSize > 0;
    }

    public String getDisplaySummary() {
        if (!isStructurallyValid()) return "Invalid runtime metadata";
        return String.format("signature %06X • LUT %,d",
                signatureAddress, lookupTableSize);
    }
}
