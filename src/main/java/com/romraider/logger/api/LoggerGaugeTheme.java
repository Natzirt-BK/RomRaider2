/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.Locale;

/** RR2 dashboard styles; branded-theme attribution is in GAUGE_BRAND_NOTICES.md. */
public enum LoggerGaugeTheme {
    RR2_CLASSIC("RR2 Classic"),
    RALLY_HERITAGE("Rally Heritage"),
    AMBER_GT("Amber GT"),
    CENTRAL_TACH("Central Tach"),
    NEON_CIRCUIT("Neon Circuit"),
    HANDHELD("Handheld"),
    RALLY_PRECISION("Rally Precision"),
    CIRCUIT_STACK("Circuit Stack"),
    RETRO_VFD("Retro VFD"),
    CLUB_SPORT("Club Sport"),
    SWEEP_RIBBON("Sweep Ribbon"),
    TWIN_ARC("Twin Arc"),
    AMBER_MATRIX("Amber Matrix"),
    VECTOR_HUD("Vector HUD"),
    TURBO_POD("Turbo Pod"),
    STI_NIGHT("STI Night"),
    EVOLUTION_NIGHT("Evolution Night"),
    PHOSPHOR_84("Phosphor 84"),
    ELECTRIC_BLOOM("Electric Bloom"),
    SUNSET_GT("Sunset GT"),
    LASER_LED("Laser LED"),
    PRISM_CASSETTE("Prism Cassette"),
    APEX_24("Apex 24"),
    ION_OLED("Ion OLED"),
    LOOP_DRIVE("Loop Drive"),
    CHRONO_ROLL("Chrono Roll");

    private final String displayName;

    LoggerGaugeTheme(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Current 25-face collection. Retain HANDHELD only for existing preference compatibility. */
    public static LoggerGaugeTheme[] selectableValues() {
        return java.util.Arrays.stream(values()).filter(theme -> theme != HANDHELD).toArray(LoggerGaugeTheme[]::new);
    }

    public static LoggerGaugeTheme fromName(String value) {
        if (value == null) return RR2_CLASSIC;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return RR2_CLASSIC;
        }
    }

    /** Missing or unknown overrides inherit the default; never silently pin Classic. */
    public static LoggerGaugeTheme optionalFromName(String value) {
        if (value == null) return null;
        try { return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
