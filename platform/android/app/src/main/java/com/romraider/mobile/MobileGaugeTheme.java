/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.graphics.Color;

import java.util.Locale;

/** RR2 mobile gauge faces; branded-theme attribution is in GAUGE_BRAND_NOTICES.md. */
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
            0xFF9AF5FF, true, false),
    RALLY_PRECISION("Rally Precision", 0xFF0D1319, 0xFFFF4F58, 0xFF78858E,
            Color.WHITE, false, false),
    CIRCUIT_STACK("Circuit Stack", 0xFF10171D, 0xFFFFC56B, 0xFF37434B,
            Color.WHITE, false, false),
    RETRO_VFD("Retro VFD", 0xFF071510, 0xFF77F7BF, 0xFF204333,
            0xFF77F7BF, false, true),
    CLUB_SPORT("Club Sport", 0xFFF4F1E6, 0xFFCB2336, 0xFF446477, 0xFF182532, false, false),
    SWEEP_RIBBON("Sweep Ribbon", 0xFF10171D, 0xFFFF4F58, 0xFF33404B, Color.WHITE, false, false),
    TWIN_ARC("Twin Arc", 0xFF10171D, 0xFF7CDFFF, 0xFFC4ACFF, Color.WHITE, false, false),
    AMBER_MATRIX("Amber Matrix", 0xFF211A0B, 0xFFFFC56B, 0xFF49391B, Color.WHITE, false, false),
    VECTOR_HUD("Vector HUD", 0xFF10171D, 0xFF7CDFFF, 0xFF436170, Color.WHITE, false, false),
    TURBO_POD("Turbo Pod", 0xFF080E14, 0xFFFF8C61, 0xFF8495A4, Color.WHITE, false, false),
    STI_NIGHT("STI Night", 0xFF080A0F, 0xFFFF3348, 0xFF732738, Color.WHITE, true, false),
    EVOLUTION_NIGHT("Evolution Night", 0xFF080A0F, 0xFFFF5B40, 0xFF666A73, Color.WHITE, true, false);

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

    com.romraider.portable.gauge.GaugeFaceRenderer.Style instrumentStyle() {
        try { return com.romraider.portable.gauge.GaugeFaceRenderer.Style.valueOf(name()); }
        catch (IllegalArgumentException exception) { return null; }
    }
}
