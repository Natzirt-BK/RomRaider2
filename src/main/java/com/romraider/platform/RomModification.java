/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

/** Subaru ROM modification families that can be identified from definitions. */
public enum RomModification {
    DIME_MOD("DimeMod", "dimemod", "dime mod"),
    CARBERRY("CarBerry", "carberry"),
    MERP_MOD("MerpMod", "merpmod", "merp mod");

    private final String displayName;
    private final String[] identifiers;

    RomModification(String displayName, String... identifiers) {
        this.displayName = displayName;
        this.identifiers = identifiers;
    }

    public String getDisplayName() {
        return displayName;
    }

    String[] getIdentifiers() {
        return identifiers.clone();
    }
}
