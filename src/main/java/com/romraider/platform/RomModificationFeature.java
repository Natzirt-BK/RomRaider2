/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

/** Features that can be identified from explicitly branded ROM definitions. */
public enum RomModificationFeature {
    CARBERRY_SPEED_DENSITY(RomModification.CARBERRY, "Speed Density",
            "speed density", "maf/speed density"),
    CARBERRY_ANTI_LAG(RomModification.CARBERRY, "Anti-Lag",
            "anti-lag", "anti lag"),
    CARBERRY_LAUNCH_CONTROL(RomModification.CARBERRY, "Launch Control",
            "launch control"),
    CARBERRY_NO_LIFT_SHIFT(RomModification.CARBERRY, "No-Lift-To-Shift",
            "no-lift-to-shift", "nlts"),
    CARBERRY_FLEX_FUEL(RomModification.CARBERRY, "Flex Fuel",
            "flex fuel", "ethanol content"),
    CARBERRY_MAP_SWITCHING(RomModification.CARBERRY, "Map Switching",
            "map switching", "map switch"),
    CARBERRY_KNOCK_CEL(RomModification.CARBERRY, "Knock-CEL",
            "knock-cel", "knock cel"),
    CARBERRY_ALCOHOL_INJECTION(RomModification.CARBERRY,
            "Alcohol Injection", "alcohol injection"),

    MERPMOD_SPEED_DENSITY(RomModification.MERP_MOD, "Speed Density",
            "speed density", "sd mode"),
    MERPMOD_MAP_SWITCHING(RomModification.MERP_MOD, "Map Switching",
            "map switching", "map switch"),
    MERPMOD_LAUNCH_CONTROL(RomModification.MERP_MOD, "Launch Control",
            "launch control"),
    MERPMOD_FLEX_FUEL(RomModification.MERP_MOD, "Flex Fuel",
            "flex fuel", "ethanol content");

    private final RomModification modification;
    private final String displayName;
    private final String[] searchTerms;

    RomModificationFeature(RomModification modification, String displayName,
            String... searchTerms) {
        this.modification = modification;
        this.displayName = displayName;
        this.searchTerms = searchTerms;
    }

    public RomModification getModification() { return modification; }
    public String getDisplayName() { return displayName; }
    String[] getSearchTerms() { return searchTerms.clone(); }
}
