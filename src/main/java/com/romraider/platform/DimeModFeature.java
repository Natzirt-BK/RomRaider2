/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

/** DimeMod capabilities that can be inferred safely from an active definition. */
public enum DimeModFeature {
    SPEED_DENSITY("Speed Density", "speed density"),
    FLEX_FUEL("Flex Fuel", "flex fuel", "flexfuel", "ethanol content"),
    ALS("Anti-Lag / Rotational Idle", "anti-lag", "anti lag", "als",
            "rotational idle"),
    MAP_SWITCHING("Map Switching", "map switch", "mapswitch"),
    PER_CYLINDER_KNOCK("Per-Cylinder Knock", "per cylinder knock",
            "per-cylinder knock"),
    FAILSAFE("Failsafe", "failsafe"),
    RAM_TUNE("RAM Tune", "ram tune", "ramtune"),
    PWM("PWM", "pwm"),
    EXTERNAL_INPUTS("External Inputs", "external input", "custom sensor"),
    FUEL_PRESSURE("Fuel Pressure", "fuel pressure"),
    OIL_PRESSURE("Oil Pressure", "oil pressure", "oil press"),
    OIL_TEMPERATURE("Oil Temperature", "oil temperature", "oil temp"),
    VALET_MODE("Valet Mode", "valet");

    private final String displayName;
    private final String[] searchTerms;

    DimeModFeature(String displayName, String... searchTerms) {
        this.displayName = displayName;
        this.searchTerms = searchTerms;
    }

    public String getDisplayName() { return displayName; }
    String[] getSearchTerms() { return searchTerms.clone(); }
}
