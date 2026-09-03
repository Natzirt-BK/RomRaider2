/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.graphics.Color;

import java.util.Locale;

/** Original RR2 mobile gauge faces with no manufacturer artwork or logos. */
enum MobileGaugeTheme {
    RR2_CLASSIC("RR2 Classic", 0xFF141B22, 0xFFD92632, 0xFF718397,
            Color.WHITE, false, false),
    RALLY_HERITAGE("Rally Heritage", 0xFF101318, 0xFFFF3447, 0xFFE7EDF2,
            Color.WHITE, true, false),
    AMBER_GT("Amber GT", 0xFF090A0C, 0xFFFFA31A, 0xFF6C4514,
            0xFFFFD68A, true, true),
    CENTRAL_TACH("Central Tach", 0xFFF2EEE3, 0xFFD71920, 0xFF252525,
            0xFF171717, false, false),
    NEON_CIRCUIT("Neon Circuit", 0xFF06131D, 0xFF22E8FF, 0xFF8A2BE2,
            0xFF9AF5FF, true, false);

    final String displayName;
    final int face;
    final int primary;
    final int secondary;
    final int ink;
    final boolean glow;
    final boolean segmented;

    MobileGaugeTheme(String displayName, int face, int primary, int secondary,
            int ink, boolean glow, boolean segmented) {
        this.displayName = displayName;
        this.face = face;
        this.primary = primary;
        this.secondary = secondary;
        this.ink = ink;
        this.glow = glow;
        this.segmented = segmented;
    }

    static MobileGaugeTheme fromName(String value) {
        if (value == null) return RR2_CLASSIC;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return RR2_CLASSIC;
        }
    }
}
