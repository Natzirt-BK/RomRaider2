/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.gauge;

import java.util.Locale;
import com.romraider.portable.gauge.GaugeFaceRenderer.Reading;
import com.romraider.portable.gauge.GaugeFaceRenderer.Style;
import com.romraider.portable.gauge.GaugeFaceRenderer.Surface;

/** Original retro instruments. Decorative colors never define safe/warning zones. */
final class VibrantGaugeFaces {
    private static final int CYAN = 0xFF48F5EA, PINK = 0xFFFF4EA5, WHITE = 0xFFF4F2EE,
            MUTED = 0xFFA6B1C9, VIOLET = 0xFFA17BFF, ORANGE = 0xFFFFA24D, LIME = 0xFFB5FF42;
    private VibrantGaugeFaces() { }

    static void draw(Surface s, Style style, Reading r) {
        boolean light = style == Style.SUNSET_GT;
        int accent = style == Style.LASER_LED ? LIME : style == Style.SUNSET_GT ? ORANGE
                : style == Style.ELECTRIC_BLOOM ? 0xFF548CFF : CYAN;
        s.rect(0, 0, 320, 250, 15, r.warning && r.available() ? 0xFFFF554F : 0xFF566175);
        s.rect(1, 1, 318, 248, 14, light ? 0xFF35212E : 0xFF080D1A);
        s.rect(3, 3, 314, 27, 12, light ? 0xFF502D3E : 0xFF172036);
        s.text(r.name.toUpperCase(Locale.ROOT), 16, 22, 12, WHITE, -1, 250, false);
        s.text("RR2", 304, 22, 10, accent, 1, 32, true);
        s.line(16, 31, 236, 31, 1, alpha(accent, 100));
        s.line(241, 31, 276, 31, 2, PINK);
        s.line(282, 31, 304, 31, 2, ORANGE);
        switch (style) {
            case PHOSPHOR_84: phosphor(s, r); break;
            case ELECTRIC_BLOOM: electric(s, r); break;
            case SUNSET_GT: sunset(s, r); break;
            case LASER_LED: laser(s, r); break;
            case PRISM_CASSETTE: cassette(s, r); break;
            default: throw new AssertionError(style);
        }
        s.text(r.scaleLabel, 160, 221, 8, MUTED, 0, 278, true);
        s.line(16, 227, 304, 227, .6, light ? 0xFF765062 : 0xFF30394D);
        String state = !r.available() ? (r.state.isEmpty() ? "NO VALID DATA" : r.state)
                : r.warning ? "LIMIT WARNING" : r.state;
        s.text(state, 16, 241, 9, !r.available() || r.warning ? ORANGE : MUTED, -1, 170, true);
        s.text("PEAK " + GaugeFaceRenderer.compact(r.peak), 304, 241, 9, MUTED, 1, 115, true);
    }

    private static void phosphor(Surface s, Reading r) {
        s.rect(13, 43, 294, 169, 9, 0xFF203B48);
        s.rect(15, 45, 290, 165, 8, 0xFF061F29);
        // Fine stationary phosphor grille, behind the reading rather than across its digits.
        for (int i = 0; i < 15; i++) s.line(23, 53 + i * 10, 297, 53 + i * 10, .5, 0xFF16323D);
        for (int i = 0; i < 40; i++) {
            double x = 27 + i * 6.8, y = 92 - Math.sin(i * Math.PI / 39) * 36;
            boolean on = r.available() && r.progress() > i / 40.0;
            s.line(x, y, x, y + 15, 4.7, on ? CYAN : 0xFF24505A);
            if (on) s.line(x, y + 2, x, y + 12, 1.2, 0xFFE3FFFF);
            if (i % 8 == 0) s.line(x, y - 7, x, y - 3, .8, 0xFF7EC8CD);
        }
        s.text(tick(r, 0), 26, 121, 10, CYAN, -1, 65, true);
        s.text(tick(r, 1), 295, 121, 10, CYAN, 1, 65, true);
        s.rect(49, 119, 222, 76, 5, 0xFF04151E);
        digits(s, r, 53, 127, 214, 45, CYAN, 0xFF0C3942);
        s.text(r.units, 160, 190, 12, 0xFFB3FFF5, 0, 187, true);
        s.rect(24, 199, 36, 3, .5, PINK);
        s.rect(64, 199, 18, 3, .5, ORANGE);
        s.text("PHOSPHOR / 84", 294, 203, 8, 0xFF6AB2BA, 1, 150, true);
    }

    private static void electric(Surface s, Reading r) {
        s.radialCircle(160, 125, 88, 0xFF102649, 0xFF5F79B2);
        s.circle(160, 125, 85, 0xFF070B16);
        arc(s, 160, 125, 81, 135, 270, 7, 0xFF162C55);
        arc(s, 160, 125, 81, 135, 270, 1, 0xFF487EE5);
        s.radialCircle(160, 121, 76, 0xFF161838, 0xFF070C18);
        for (int i = 0; i < 54; i++) {
            double angle = 135 + i * 5;
            boolean on = r.available() && r.progress() > i / 54.0;
            radial(s, 160, 125, angle, 72, 79, 3.5, on ? 0xFF538FFF : 0xFF233858);
            if (on) radial(s, 160, 125, angle, 73, 78, 1, CYAN);
        }
        for (int i = 0; i <= 8; i++) {
            double angle = 135 + i * 270.0 / 8;
            radial(s, 160, 125, angle, 60, 67, 1.3, 0xFFDFE8FF);
            polarText(s, tick(r, i / 8.0), 160, 125, angle, 50, 10, WHITE, 34);
        }
        s.text("ELECTRIC", 160, 102, 8, VIOLET, 0, 66, true);
        s.text(r.units, 160, 151, 10, CYAN, 0, 66, false);
        if (r.available()) needle(s, 160, 125, 135 + r.indicatorProgress() * 270, 66, CYAN);
        s.radialCircle(160, 125, 10, 0xFFB16FE1, 0xFF241738);
        s.circle(160, 125, 6, 0xFF121021);
        s.rect(111, 178, 98, 33, 5, 0xFF251431);
        s.line(118, 180, 201, 180, .7, 0xFFA74683);
        s.text(r.available() ? r.display : "—", 160, 205, 28, 0xFFFF9ED4, 0, 90, true);
    }

    private static void sunset(Surface s, Reading r) {
        s.rect(14, 44, 292, 166, 12, 0xFFAC705E);
        s.rect(17, 47, 286, 160, 10, 0xFFF3DCAD);
        s.path(new double[] {0, 22, 51, 1, 80, 51, 1, 68, 64, 1, 22, 64, 3}, 0xFFFFAF43);
        s.path(new double[] {0, 240, 51, 1, 298, 51, 1, 298, 64, 1, 252, 64, 3}, 0xFFDB496F);
        arc(s, 160, 151, 96, 180, 180, 4, 0xFFDB7C50);
        arc(s, 160, 151, 91, 180, 180, 1, 0xFF975856);
        for (int i = 0; i <= 40; i++) {
            double angle = 180 + i * 4.5;
            radial(s, 160, 151, angle, i % 8 == 0 ? 78 : 85, 91, i % 8 == 0 ? 2.6 : 1, 0xFF4C3543);
            if (i % 8 == 0) polarText(s, tick(r, i / 40.0), 160, 151, angle, 65, 12, 0xFF593F48, 42);
        }
        s.text("SUNSET GT", 160, 117, 10, 0xFFAF4871, 0, 91, false);
        s.text(r.units, 160, 134, 10, 0xFF5E5361, 0, 85, true);
        if (r.available()) needle(s, 160, 151, 180 + r.indicatorProgress() * 180, 85, 0xFFCB2D70);
        s.circle(160, 151, 8, 0xFF754056); s.circle(160, 151, 4, 0xFFFFBF74);
        s.rect(77, 169, 166, 34, 4, 0xFF40283D);
        s.text(r.available() ? r.display : "—", 160, 198, 32, 0xFFFFC16C, 0, 151, true);
        for (int i = 0; i < 3; i++) {
            s.rect(28 + i * 13, 181, 8, 17, 1, i == 0 ? 0xFFED7D43 : i == 1 ? 0xFFCD4E63 : 0xFF984675);
            s.rect(258 + i * 13, 181, 8, 17, 1, i == 0 ? 0xFF984675 : i == 1 ? 0xFFCD4E63 : 0xFFED7D43);
        }
    }

    private static void laser(Surface s, Reading r) {
        s.radialCircle(160, 129, 94, 0xFF222D32, 0xFF687C80);
        s.circle(160, 129, 92, 0xFF05090B);
        arc(s, 160, 129, 89, 195, 105, 1, 0xFFB0C9C8);
        s.radialCircle(160, 123, 87, 0xFF17231D, 0xFF050A0B);
        for (int i = 0; i < 28; i++) {
            double a = 145 + i * 250.0 / 27;
            boolean on = r.available() && r.progress() > i / 28.0;
            radial(s, 160, 129, a, 77, 85, 5, on ? LIME : 0xFF26371B);
            if (on) radial(s, 160, 129, a, 78, 83, 1.2, 0xFFE4FFAB);
        }
        for (int i = 0; i <= 5; i++) polarText(s, tick(r, i / 5.0), 160, 129,
                145 + i * 50, 65, 11, 0xFFD0D9C9, 42);
        s.text("LASER / LED", 160, 99, 9, ORANGE, 0, 97, true);
        digits(s, r, 95, 112, 130, 34, 0xFFFF633F, 0xFF331810);
        s.text(r.units, 160, 167, 12, 0xFFEBF6DB, 0, 112, false);
        s.line(125, 190, 195, 190, .7, 0xFF486237);
        s.text("DIGITAL PRECISION", 160, 204, 8, 0xFF8FA285, 0, 132, true);
    }

    private static void cassette(Surface s, Reading r) {
        s.rect(14, 45, 292, 165, 8, 0xFF394459);
        s.rect(16, 47, 288, 161, 7, 0xFF0A1326);
        s.rect(21, 51, 278, 56, 4, 0xFF121A32);
        for (int i = 0; i < 36; i++) {
            double x = 28 + i * 7.35;
            double y = 88 - i * .65;
            int color = i < 12 ? CYAN : i < 24 ? VIOLET : PINK;
            boolean on = r.available() && r.progress() > i / 36.0;
            s.path(new double[] {0, x, y, 1, x + 5, y - 2.5, 1, x + 5, y + 13, 1, x, y + 15.5, 3},
                    on ? color : alpha(color, 34));
        }
        s.text(tick(r, 0), 26, 120, 10, MUTED, -1, 73, true);
        s.text(tick(r, 1), 294, 120, 10, MUTED, 1, 73, true);
        digits(s, r, 26, 137, 224, 43, 0xFFF0F3FD, 0xFF1C2842);
        s.text(r.units, 252, 196, 12, CYAN, 1, 91, true);
        for (int i = 0; i < 3; i++) {
            int color = i == 0 ? CYAN : i == 1 ? PINK : ORANGE;
            double x = 264 + i * 10;
            s.path(new double[] {0, x, 137, 1, x + 6, 131, 1, x + 6, 178, 1, x, 184, 3}, color);
        }
        s.text("PRISM / COLOR DISPLAY", 27, 198, 8, 0xFF8594B2, -1, 123, true);
    }

    private static void digits(Surface s, Reading r, double left, double top, double width, double height, int color, int unlit) {
        String display = r.available() ? r.display : "—";
        if (!display.matches("-?[0-9]+(\\.[0-9]+)?") || display.length() > 8) {
            s.text(display, left + width / 2, top + height, height, color, 0, width, true); return;
        }
        double units = 0;
        for (char c : display.toCharArray()) units += c == '.' ? .27 : .72;
        double scale = Math.min(height, width / units), x = left + (width - units * scale) / 2;
        int[] masks = {0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f};
        double[][] lines = {{.10,0,.50,0},{.55,.07,.55,.43},{.55,.57,.55,.93},
                {.10,1,.50,1},{.05,.57,.05,.93},{.05,.07,.05,.43},{.10,.5,.50,.5}};
        for (char c : display.toCharArray()) {
            if (c == '.') { s.circle(x + scale * .07, top + scale * .96, scale * .045, color); x += .27 * scale; continue; }
            int mask = c == '-' ? 0x40 : masks[c - '0'];
            for (int i = 0; i < 7; i++) {
                double[] line = lines[i]; boolean on = (mask & (1 << i)) != 0;
                double x1 = x + line[0] * scale, y1 = top + line[1] * scale;
                double x2 = x + line[2] * scale, y2 = top + line[3] * scale;
                if (on) s.line(x1, y1, x2, y2, scale * .17, alpha(color, 20));
                s.line(x1, y1, x2, y2, scale * .075, on ? color : unlit);
            }
            x += .72 * scale;
        }
    }
    private static String tick(Reading r, double fraction) {
        double value = r.minimum + (r.maximum - r.minimum) * fraction;
        return GaugeFaceRenderer.compact(value);
    }
    private static void polarText(Surface s, String text, double cx, double cy, double angle, double radius, double size, int color, double width) {
        double a = Math.toRadians(angle);
        s.text(text, cx + Math.cos(a) * radius, cy + Math.sin(a) * radius + size * .35, size, color, 0, width, false);
    }
    private static void radial(Surface s, double cx, double cy, double angle, double inner, double outer, double width, int color) {
        double a = Math.toRadians(angle), x = Math.cos(a), y = Math.sin(a);
        s.line(cx + x * inner, cy + y * inner, cx + x * outer, cy + y * outer, width, color);
    }
    private static void arc(Surface s, double cx, double cy, double radius, double start, double sweep, double width, int color) {
        int count = (int) Math.ceil(Math.abs(sweep) / 4);
        for (int i = 0; i < count; i++) {
            double a = Math.toRadians(start + sweep * i / count), b = Math.toRadians(start + sweep * (i + 1) / count);
            s.line(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius,
                    cx + Math.cos(b) * radius, cy + Math.sin(b) * radius, width, color);
        }
    }
    private static void needle(Surface s, double cx, double cy, double angle, double length, int color) {
        double a = Math.toRadians(angle), x = Math.cos(a), y = Math.sin(a);
        s.line(cx - x * 10, cy - y * 10, cx + x * length, cy + y * length, 7, alpha(color, 24));
        s.path(new double[] {0, cx + x * length, cy + y * length,
                1, cx - x * 11 - y * 2, cy - y * 11 + x * 2,
                1, cx - x * 11 + y * 2, cy - y * 11 - x * 2, 3}, color);
        s.line(cx + x * 7, cy + y * 7, cx + x * (length - 3), cy + y * (length - 3), .7, WHITE);
    }
    private static int alpha(int color, int alpha) { return (color & 0xFFFFFF) | (alpha << 24); }
}
