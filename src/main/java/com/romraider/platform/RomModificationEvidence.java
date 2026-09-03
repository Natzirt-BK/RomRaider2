/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

/** Strength and source of definition-only ROM modification evidence. */
public enum RomModificationEvidence {
    NOT_DETECTED("Not detected"),
    BRANDED_TABLES("Branded tables in definition"),
    ROM_IDENTITY("ROM identity match");

    private final String displayName;

    RomModificationEvidence(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDetected() {
        return this != NOT_DETECTED;
    }
}
