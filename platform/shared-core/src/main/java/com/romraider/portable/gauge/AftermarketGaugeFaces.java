/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.gauge;

import java.util.Locale;
import com.romraider.portable.gauge.GaugeFaceRenderer.Reading;
import com.romraider.portable.gauge.GaugeFaceRenderer.Style;
import com.romraider.portable.gauge.GaugeFaceRenderer.Surface;

/** Original instrument artwork; manufacturer references are documented in GAUGE_DESIGN.md. */
final class AftermarketGaugeFaces {
    private static final int WHITE = 0xFFF2F6F8, MUTED = 0xFF9AAEBC,
            AMBER = 0xFFFFBE5C, BLUE = 0xFF53ABFF, RED = 0xFFFF564E;
    private AftermarketGaugeFaces() { }

    static void draw(Surface s, Style style, Reading r, boolean framed) {
        int accent = style == Style.LOOP_DRIVE ? BLUE : style == Style.ION_OLED ? WHITE : AMBER;
        if (framed) {
            s.rect(0, 0, 320, 250, 14, r.available() && r.warning ? RED : 0xFF425463);
            s.rect(1.5, 1.5, 317, 247, 13, 0xFF0A1118);
        }
        s.text(r.name.toUpperCase(Locale.ROOT), 16, 23, 13, WHITE, -1, 244, false);
        s.text("RR2", 304, 23, 10, accent, 1, 32, true);
        switch (style) {
            case APEX_24: apex(s, r); break;
            case ION_OLED: ion(s, r); break;
            case LOOP_DRIVE: loop(s, r); break;
            case CHRONO_ROLL: chrono(s, r); break;
            default: throw new AssertionError(style);
        }
        s.text(r.scaleLabel, 160, 223, 8, MUTED, 0, 270, true);
        String state = !r.available() ? (r.state.isEmpty() ? "NO VALID DATA" : r.state)
                : r.warning ? "LIMIT WARNING" : r.state;
        s.text(state, 16, 241, 9, !r.available() || r.warning ? AMBER : MUTED, -1, 172, true);
        s.text("PEAK " + GaugeFaceRenderer.compact(r.peak), 304, 241, 9, MUTED, 1, 112, true);
    }

    /** A wide 24-block crown over a slanted numeric glass panel, without an analog needle. */
    private static void apex(Surface s, Reading r) {
        s.radialCircle(160, 130, 89, 0xFF1D2831, 0xFF63727A);
        s.circle(160, 130, 86, 0xFF060B0F);
        arc(s, 160, 130, 81, 158, 224, 1, 0xFF647985);
        for (int i = 0; i < 24; i++) {
            double angle = 158 + i * 224.0 / 23;
            boolean active = r.available() && r.progress() > i / 24.0;
            radial(s, 160, 130, angle, 69, 79, 6.4, active ? AMBER : 0xFF30291E);
            if (active) radial(s, 160, 130, angle, 71, 77, 2, 0xFFFFE1AD);
        }
        s.text("APEX / 24", 160, 92, 10, AMBER, 0, 100, true);
        // This wide display protrudes beyond the dial, unlike a small centered LED pod.
        s.path(new double[]{0, 51, 109, 1, 282, 109, 1, 269, 163, 1, 38, 163, 3}, 0xFF62717C);
        s.path(new double[]{0, 54, 112, 1, 277, 112, 1, 266, 160, 1, 43, 160, 3}, 0xFF04080C);
        s.line(57, 114, 271, 114, 1, 0xFF9CB0BD);
        s.text(value(r), 160, 153, 44, WHITE, 0, 213, true);
        s.text(r.units, 160, 181, 13, AMBER, 0, 150, false);
        s.text(tick(r, 0), 95, 203, 10, MUTED, 0, 64, true);
        s.text(tick(r, 1), 225, 203, 10, MUTED, 0, 64, true);
    }

    /** Quiet OLED window: no needle or LED ring competing with the exact readout. */
    private static void ion(Surface s, Reading r) {
        s.radialCircle(160, 128, 89, 0xFF1B252D, 0xFFB7C3C8);
        s.circle(160, 128, 86, 0xFF030608);
        s.circle(160, 128, 79, 0xFF101820);
        s.circle(160, 128, 77, 0xFF05090C);
        s.text("ION OLED", 160, 75, 10, WHITE, 0, 100, false);
        s.rect(59, 85, 202, 91, 5, 0xFF3C4B55);
        s.rect(61, 87, 198, 87, 4, 0xFF010406);
        s.text(value(r), 160, 137, 48, WHITE, 0, 182, false);
        s.text(r.units, 245, 164, 13, 0xFFB6E8EE, 1, 170, false);
        s.line(89, 190, 231, 190, 2, 0xFF33454F);
        if (r.available()) s.line(89, 190, 89 + r.progress() * 142, 190, 2, WHITE);
        s.text(tick(r, 0), 89, 205, 9, MUTED, -1, 67, true);
        s.text(tick(r, 1), 231, 205, 9, MUTED, 1, 67, true);
    }

    /** Blue loop, analog pointer and two explicitly labeled windows: reading and historical peak. */
    private static void loop(Surface s, Reading r) {
        s.radialCircle(160, 126, 88, 0xFF1A2E40, 0xFF8D9FAF);
        s.circle(160, 126, 85, 0xFF060B11);
        arc(s, 160, 126, 80, 145, 250, 1, 0xFF5C85AB);
        for (int i = 0; i < 36; i++) {
            double a = 145 + i * 250.0 / 35;
            radial(s, 160, 126, a, 73, 79, 3.2,
                    r.available() && r.progress() > i / 36.0 ? BLUE : 0xFF18314D);
        }
        for (int i = 0; i <= 5; i++) {
            double a = 145 + i * 50;
            radial(s, 160, 126, a, 61, 68, 1.8, WHITE);
            double angle = Math.toRadians(a);
            s.text(tick(r, i / 5.0), 160 + Math.cos(angle) * 50, 130 + Math.sin(angle) * 50,
                    10, WHITE, 0, 37, false);
        }
        s.text("LOOP DRIVE", 160, 105, 8, BLUE, 0, 77, false);
        if (r.available()) {
            double angle = 145 + r.indicatorProgress() * 250;
            radial(s, 160, 126, angle, -12, 68, 5, 0xFF64262C);
            radial(s, 160, 126, angle, -10, 68, 2.8, RED);
        }
        s.circle(160, 126, 8, 0xFF84929F); s.circle(160, 126, 5, 0xFF101924);
        s.rect(37, 165, 246, 46, 6, 0xFF4A5864);
        s.rect(39, 167, 151, 42, 4, 0xFF070E17);
        s.rect(192, 167, 89, 42, 4, 0xFF0C1624);
        s.text(value(r), 114, 196, 28, 0xFFFFB467, 0, 139, true);
        s.text(r.units, 114, 206, 8, MUTED, 0, 135, true);
        s.text("PEAK", 236, 179, 8, BLUE, 0, 79, true);
        s.text(GaugeFaceRenderer.compact(r.peak), 236, 200, 19, 0xFFAACDEC, 0, 79, true);
    }

    /** Mechanical counter drums and a linear pointer. Exact digits only; no invented roll animation. */
    private static void chrono(Surface s, Reading r) {
        s.rect(14, 45, 292, 167, 11, 0xFF74818C);
        s.rect(17, 48, 286, 161, 9, 0xFF243039);
        for (int i = 0; i < 20; i++) s.line(24, 53 + i * 7.7, 296, 53 + i * 7.7, .5, 0xFF35414A);
        s.text("CHRONO ROLL", 28, 67, 9, 0xFFEACCA1, -1, 145, true);
        s.text(r.units, 290, 67, 11, WHITE, 1, 100, true);
        s.rect(25, 78, 270, 83, 5, 0xFF03070A);
        String display = value(r);
        if (r.available() && display.matches("-?[0-9]+(\\.[0-9]+)?") && display.length() <= 8) {
            double cell = Math.min(47, 254.0 / display.length()), left = 160 - cell * display.length() / 2;
            for (int i = 0; i < display.length(); i++) {
                double x = left + i * cell;
                boolean decimal = display.charAt(i) == '.';
                if (!decimal) {
                    s.rect(x + 1, 83, cell - 2, 73, 3, 0xFFE8DCC2);
                    s.rect(x + 1, 83, cell - 2, 11, 2, 0xFF9C927C);
                    s.rect(x + 1, 147, cell - 2, 9, 2, 0xFFB6A88D);
                    s.line(x + 1, 120, x + cell - 1, 120, .7, 0xFFBFB299);
                }
                s.text(display.substring(i, i + 1), x + cell / 2, 140, 49,
                        decimal ? WHITE : 0xFF14232B, 0, cell - 4, true);
            }
        } else s.text(display, 160, 143, 49, WHITE, 0, 254, true);
        for (int i = 0; i <= 24; i++) s.line(30 + i * 260.0 / 24, 168,
                30 + i * 260.0 / 24, i % 6 == 0 ? 181 : 176, i % 6 == 0 ? 1.5 : .8, 0xFFD9DEE0);
        if (r.available()) {
            double x = 30 + r.progress() * 260;
            s.path(new double[]{0, x - 5, 185, 1, x + 5, 185, 1, x, 178, 3}, RED);
        }
        s.text(tick(r, 0), 29, 202, 10, WHITE, -1, 95, true);
        s.text(tick(r, 1), 291, 202, 10, WHITE, 1, 95, true);
    }

    private static String value(Reading r) { return r.available() ? r.display : "—"; }
    private static String tick(Reading r, double p) {
        return GaugeFaceRenderer.compact(r.minimum + (r.maximum - r.minimum) * p);
    }
    private static void radial(Surface s, double x, double y, double degrees, double inner, double outer, double width, int color) {
        double a = Math.toRadians(degrees), dx = Math.cos(a), dy = Math.sin(a);
        s.line(x + dx * inner, y + dy * inner, x + dx * outer, y + dy * outer, width, color);
    }
    private static void arc(Surface s, double x, double y, double r, double start, double sweep, double width, int color) {
        for (int i = 0; i < 80; i++) {
            double a = Math.toRadians(start + sweep * i / 80), b = Math.toRadians(start + sweep * (i + 1) / 80);
            s.line(x + Math.cos(a) * r, y + Math.sin(a) * r, x + Math.cos(b) * r, y + Math.sin(b) * r, width, color);
        }
    }
}
