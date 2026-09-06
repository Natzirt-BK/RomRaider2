/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.gauge;

import java.util.Locale;

/** Shared vector instruments. Coordinates: 320 x 250; branded assets have separate notices. */
public final class GaugeFaceRenderer {
    public enum Presentation { CARD, SEAMLESS }
    public enum Style { RALLY_PRECISION, CIRCUIT_STACK, RETRO_VFD,
        CLUB_SPORT, SWEEP_RIBBON, TWIN_ARC, AMBER_MATRIX, VECTOR_HUD, TURBO_POD,
        STI_NIGHT, EVOLUTION_NIGHT, PHOSPHOR_84, ELECTRIC_BLOOM, SUNSET_GT, LASER_LED, PRISM_CASSETTE;
        public boolean usesNeedleMotion() {
            return this == STI_NIGHT || this == EVOLUTION_NIGHT || this == ELECTRIC_BLOOM || this == SUNSET_GT;
        }
    }
    public interface Surface {
        void rect(double x, double y, double width, double height, double radius, int color);
        void circle(double x, double y, double radius, int color);
        void line(double x1, double y1, double x2, double y2, double width, int color);
        void radialCircle(double x, double y, double radius, int centerColor, int edgeColor);
        /** Commands: 0 move(x,y), 1 line(x,y), 2 cubic(x1,y1,x2,y2,x,y), 3 close. */
        void path(double[] commands, int color);
        /** align: -1 left, 0 center, +1 right; text must fit maxWidth. */
        void text(String text, double x, double baseline, double size, int color, int align, double maxWidth, boolean mono);
    }
    public static final class Reading {
        public final String name, display, units, state, scaleLabel;
        public final double value, minimum, maximum, peak;
        public final double indicatorValue;
        public final boolean warning;
        public Reading(String name, String display, String units, double value,
                double minimum, double maximum, double peak, String state, String scaleLabel, boolean warning) {
            this(name, display, units, value, minimum, maximum, peak, state, scaleLabel, warning, value);
        }
        private Reading(String name, String display, String units, double value,
                double minimum, double maximum, double peak, String state, String scaleLabel, boolean warning, double indicatorValue) {
            this.name = clean(name); this.display = clean(display); this.units = clean(units);
            this.value = value; this.minimum = minimum; this.maximum = maximum; this.peak = peak;
            this.state = clean(state); this.scaleLabel = clean(scaleLabel); this.warning = warning;
            this.indicatorValue = indicatorValue;
        }
        public Reading withIndicator(double indicator) {
            return new Reading(name, display, units, value, minimum, maximum, peak, state, scaleLabel, warning, indicator);
        }
        public double indicatorProgress() {
            if (!available() || !Double.isFinite(indicatorValue)) return progress();
            return Math.max(0, Math.min(1, (indicatorValue - minimum) / (maximum - minimum)));
        }
        public boolean available() {
            return Double.isFinite(value) && Double.isFinite(minimum)
                    && Double.isFinite(maximum - minimum) && maximum > minimum;
        }
        public double progress() {
            if (!available()) return 0;
            double fraction = (value - minimum) / (maximum - minimum);
            return Math.max(0, Math.min(1, fraction));
        }
    }
    private static final int WHITE = 0xFFF0F3F4, MUTED = 0xFF9CAAB5,
            RED = 0xFFFF4F58, MINT = 0xFF77F7BF, AMBER = 0xFFFFC56B;
    private GaugeFaceRenderer() { }

    public static void draw(Surface s, Style style, Reading r) {
        draw(s, style, r, Presentation.CARD);
    }

    /** Seamless instruments omit surrounding card chrome, not dial artwork or warnings. */
    public static void draw(Surface s, Style style, Reading r, Presentation presentation) {
        if (s == null || style == null || r == null) throw new IllegalArgumentException("Gauge surface, style and reading are required");
        if (presentation == null) throw new IllegalArgumentException("Gauge presentation is required");
        boolean framed = presentation == Presentation.CARD;
        if (style == Style.STI_NIGHT || style == Style.EVOLUTION_NIGHT) {
            PremiumGaugeFaces.draw(s, style, r, framed);
            return;
        }
        if (style == Style.PHOSPHOR_84 || style == Style.ELECTRIC_BLOOM || style == Style.SUNSET_GT
                || style == Style.LASER_LED || style == Style.PRISM_CASSETTE) {
            VibrantGaugeFaces.draw(s, style, r, framed);
            return;
        }
        int accent = style == Style.RETRO_VFD ? MINT
                : style == Style.CIRCUIT_STACK || style == Style.AMBER_MATRIX ? AMBER
                : style == Style.TWIN_ARC || style == Style.VECTOR_HUD ? 0xFF7CDFFF : RED;
        if (framed) {
            s.rect(0, 0, 320, 250, 14, r.warning ? RED : 0xFF33404B);
            s.rect(1.5, 1.5, 317, 247, 13, style == Style.RETRO_VFD ? 0xFF071510 : 0xFF10171D);
        }
        s.text(r.name.toUpperCase(Locale.ROOT), 16, 24, 15, WHITE, -1, 226, false);
        s.text("RR2", 304, 24, 11, accent, 1, 38, true);
        if (framed) s.line(16, 34, 304, 34, 1, style == Style.RETRO_VFD ? 0xFF234C39 : 0xFF2B3741);
        switch (style) {
            case RALLY_PRECISION: rally(s, r); break;
            case CIRCUIT_STACK: stack(s, r); break;
            case RETRO_VFD: retro(s, r); break;
            case CLUB_SPORT: club(s, r); break;
            case SWEEP_RIBBON: ribbon(s, r); break;
            case TWIN_ARC: twin(s, r); break;
            case AMBER_MATRIX: matrix(s, r); break;
            case VECTOR_HUD: hud(s, r); break;
            case TURBO_POD: pod(s, r); break;
            default: throw new AssertionError(style);
        }
        String state = !r.available() ? (r.state.isEmpty() ? "NO VALID DATA" : r.state)
                : r.warning ? "LIMIT WARNING" : r.state;
        s.text(state, 16, 238, 10, !r.available() || r.warning ? AMBER : MUTED, -1, 174, true);
        s.text("PEAK " + compact(r.peak), 304, 238, 10, MUTED, 1, 116, true);
    }

    private static void rally(Surface s, Reading r) {
        double cx = 160, cy = 129, radius = 86;
        s.circle(cx, cy, radius + 3, 0xFF4C5761);
        s.circle(cx, cy, radius + 1.5, 0xFF080D12);
        s.circle(cx, cy, radius - 4, 0xFF18222B);
        s.circle(cx, cy, radius - 6, 0xFF0D1319);
        for (int i = 0; i <= 30; i++) {
            double angle = 150 + i * 8;
            boolean major = i % 6 == 0;
            radial(s, cx, cy, angle, major ? 67 : 74, 81, major ? 2.2 : 1, major ? WHITE : 0xFF78858E);
            if (major) {
                double a = Math.toRadians(angle);
                s.text(compact(r.minimum + (r.maximum - r.minimum) * i / 30),
                        cx + Math.cos(a) * 55, cy + Math.sin(a) * 55 + 4,
                        11, WHITE, 0, 34, false);
            }
        }
        s.text(r.units, cx, 104, 11, MUTED, 0, 80, false);
        if (r.available()) {
            double angle = 150 + r.progress() * 240;
            radial(s, cx, cy, angle, -13, 71, 4, 0xFF661E27);
            radial(s, cx, cy, angle, -10, 71, 2.4, RED);
        }
        s.circle(cx, cy, 7, 0xFF46515B); s.circle(cx, cy, 4, 0xFF0B1015);
        s.rect(110, 176, 100, 34, 5, 0xFF050A0E);
        s.text(r.available() ? r.display : "—", cx, 203, 29, WHITE, 0, 90, true);
        s.text(r.scaleLabel, cx, 222, 8, MUTED, 0, 230, true);
    }

    private static void stack(Surface s, Reading r) {
        s.rect(16, 48, 72, 166, 6, 0xFF202B34);
        for (int i = 0; i < 20; i++) {
            boolean active = r.available() && r.progress() > i / 20.0;
            s.rect(24, 201 - i * 7.5, 32 + i * .9, 4.8, .7, active ? AMBER : 0xFF37434B);
        }
        s.text(compact(r.maximum), 101, 60, 12, MUTED, -1, 73, true);
        s.text(compact(r.minimum), 101, 211, 12, MUTED, -1, 73, true);
        s.text(r.available() ? r.display : "—", 205, 128, 57, WHITE, 0, 198, true);
        s.text(r.available() ? r.units : "NO VALID DATA", 205, 152, 16,
                r.available() ? AMBER : MUTED, 0, 190, false);
        s.line(115, 174, 301, 174, 1, 0xFF38454F);
        s.text(r.scaleLabel, 301, 194, 9, MUTED, 1, 186, true);
        if (r.available() && (r.value < r.minimum || r.value > r.maximum)) {
            s.text("OUTSIDE SCALE", 301, 213, 10, AMBER, 1, 186, true);
        }
    }

    private static void retro(Surface s, Reading r) {
        s.rect(14, 49, 292, 113, 5, 0xFF0B2419);
        s.line(21, 54, 297, 54, 1, 0xFF306449);
        String display = r.available() ? r.display : "—";
        if (display.matches("-?[0-9]+(\\.[0-9]+)?") && display.length() <= 8) {
            sevenSegment(s, display, 25, 71, 270, 58, MINT);
        } else s.text(display, 160, 122, 52, MINT, 0, 263, true);
        s.text(r.available() ? r.units : "NO VALID DATA", 290, 149, 13, MINT, 1, 245, true);
        for (int i = 0; i < 32; i++) {
            s.rect(18 + i * 8.9, 179, 6.4, i % 4 == 0 ? 17 : 12, .5,
                    r.available() && r.progress() > i / 32.0 ? MINT : 0xFF204333);
        }
        s.text(compact(r.minimum), 18, 214, 11, MUTED, -1, 68, true);
        s.text(r.scaleLabel, 160, 214, 8, MUTED, 0, 155, true);
        s.text(compact(r.maximum), 302, 214, 11, MUTED, 1, 68, true);
    }

    private static void club(Surface s, Reading r) {
        int ink = 0xFF182532;
        s.circle(160, 130, 89, 0xFF75818B);
        s.circle(160, 130, 86, 0xFFDFE1DD);
        s.circle(160, 130, 82, 0xFFF4F1E6);
        s.circle(160, 130, 69, 0xFFE8E7DF);
        s.circle(160, 130, 67, 0xFFF4F1E6);
        for (int i = 0; i <= 40; i++) {
            double angle = 135 + i * 6.75;
            radial(s, 160, 130, angle, i % 8 == 0 ? 67 : 75, 80,
                    i % 8 == 0 ? 2.3 : 1, ink);
            if (i % 8 == 0) {
                double a = Math.toRadians(angle);
                s.text(compact(r.minimum + (r.maximum - r.minimum) * i / 40),
                        160 + Math.cos(a) * 54, 134 + Math.sin(a) * 54, 11, ink, 0, 34, false);
            }
        }
        s.text(r.units, 160, 105, 12, 0xFF446477, 0, 80, false);
        if (r.available()) radial(s, 160, 130, 135 + r.progress() * 270, -15, 73, 3, 0xFFCB2336);
        s.circle(160, 130, 7, 0xFF1C2C38); s.circle(160, 130, 3, 0xFF97A6AF);
        s.rect(111, 175, 98, 31, 3, 0xFFD1DAD7);
        s.text(r.available() ? r.display : "—", 160, 199, 27, ink, 0, 90, true);
        s.text(r.scaleLabel, 160, 221, 8, MUTED, 0, 235, true);
    }

    private static void ribbon(Surface s, Reading r) {
        s.text(r.available() ? r.display : "—", 20, 111, 58, WHITE, -1, 225, true);
        s.text(r.units, 300, 108, 17, RED, 1, 70, false);
        for (int i = 0; i < 40; i++) {
            double x = 20 + i * 7;
            double y = 157 - Math.max(0, i - 18) * 1.6;
            s.line(x, y, x, y + 22, 4.6,
                    r.available() && r.progress() > i / 40.0 ? WHITE : 0xFF33404B);
            if (i % 8 == 0) s.line(x, y + 27, x, y + 34, 1, MUTED);
        }
        if (r.available()) {
            double x = 20 + r.progress() * 273;
            double y = 157 - Math.max(0, r.progress() * 39 - 18) * 1.6;
            s.line(x, y - 10, x, y + 25, 3, RED);
            s.line(x - 5, y - 14, x, y - 9, 2, RED);
            s.line(x + 5, y - 14, x, y - 9, 2, RED);
        }
        s.text(compact(r.minimum), 20, 207, 11, MUTED, -1, 65, true);
        s.text(compact(r.maximum), 299, 177, 11, MUTED, 1, 65, true);
        s.text(r.scaleLabel, 300, 215, 9, MUTED, 1, 207, true);
    }

    private static void twin(Surface s, Reading r) {
        int cyan = 0xFF7CDFFF, violet = 0xFFC4ACFF;
        double peak = Double.isFinite(r.peak) && Double.isFinite(r.maximum - r.minimum)
                && r.maximum > r.minimum ? Math.max(0, Math.min(1, (r.peak - r.minimum) / (r.maximum - r.minimum))) : -1;
        for (int i = 0; i <= 60; i++) {
            double angle = 140 + i * 260.0 / 60;
            radial(s, 160, 134, angle, 81, 91, 2,
                    r.available() && r.progress() > i / 60.0 ? cyan : 0xFF263D49);
            radial(s, 160, 134, angle, 72, 75, 1.6,
                    peak >= 0 && peak > i / 60.0 ? violet : 0xFF343043);
        }
        s.text(r.available() ? r.display : "—", 160, 139, 42, WHITE, 0, 137, true);
        s.text(r.units, 160, 160, 13, cyan, 0, 110, false);
        s.text(compact(r.minimum), 48, 204, 10, MUTED, 0, 57, true);
        s.text(compact(r.maximum), 272, 204, 10, MUTED, 0, 57, true);
        s.line(103, 194, 113, 194, 3, cyan); s.text("NOW", 120, 197, 8, MUTED, -1, 29, true);
        s.line(163, 194, 173, 194, 3, violet); s.text("PEAK", 180, 197, 8, MUTED, -1, 37, true);
        s.text(r.scaleLabel, 160, 220, 8, MUTED, 0, 235, true);
    }

    private static void matrix(Surface s, Reading r) {
        s.rect(15, 52, 290, 118, 4, 0xFF211A0B);
        for (int y = 0; y < 13; y++) for (int x = 0; x < 33; x++)
            s.circle(23 + x * 8.55, 59 + y * 8.5, 1.05, 0xFF49391B);
        String display = r.available() ? r.display : "—";
        if (display.matches("-?[0-9]+(\\.[0-9]+)?") && display.length() <= 8) {
            String[] glyphs = {"01110/10001/10011/10101/11001/10001/01110",
                "00100/01100/00100/00100/00100/00100/01110", "01110/10001/00001/00010/00100/01000/11111",
                "11110/00001/00001/01110/00001/00001/11110", "00010/00110/01010/10010/11111/00010/00010",
                "11111/10000/10000/11110/00001/00001/11110", "01110/10000/10000/11110/10001/10001/01110",
                "11111/00001/00010/00100/01000/01000/01000", "01110/10001/10001/01110/10001/10001/01110",
                "01110/10001/10001/01111/00001/00001/01110"};
            int cells = 0;
            for (char c : display.toCharArray()) cells += c == '.' ? 2 : 6;
            double step = Math.min(8.8, 268.0 / cells), left = 160 - (cells - 1) * step / 2;
            for (char c : display.toCharArray()) {
                if (c == '.') { s.circle(left, 78 + 6 * step, step * .33, AMBER); left += 2 * step; continue; }
                String[] rows = c == '-' ? new String[] {"00000","00000","00000","11111","00000","00000","00000"} : glyphs[c - '0'].split("/");
                for (int y = 0; y < 7; y++) for (int x = 0; x < 5; x++) if (rows[y].charAt(x) == '1')
                    s.circle(left + x * step, 78 + y * step, step * .34, AMBER);
                left += step * 6;
            }
        } else s.text(display, 160, 133, 43, AMBER, 0, 264, true);
        s.text(r.available() ? r.units : "NO VALID DATA", 290, 158, 11, AMBER, 1, 235, true);
        for (int i = 0; i < 24; i++) s.circle(23 + i * 11.8, 188, 3,
                r.available() && r.progress() > i / 24.0 ? AMBER : 0xFF554426);
        s.text(compact(r.minimum), 20, 216, 10, MUTED, -1, 65, true);
        s.text(compact(r.maximum), 300, 216, 10, MUTED, 1, 65, true);
        s.text(r.scaleLabel, 160, 216, 8, MUTED, 0, 153, true);
    }

    private static void hud(Surface s, Reading r) {
        int cyan = 0xFF7CDFFF;
        s.line(19, 55, 57, 55, 1, cyan); s.line(19, 55, 19, 83, 1, cyan);
        s.line(19, 185, 19, 211, 1, cyan); s.line(19, 211, 57, 211, 1, cyan);
        s.text(r.available() ? r.display : "—", 34, 134, 54, WHITE, -1, 195, true);
        s.text(r.units, 37, 163, 17, cyan, -1, 165, false);
        for (int i = 0; i <= 20; i++) {
            double y = 202 - i * 7.1;
            s.line(i % 5 == 0 ? 260 : 268, y, 279, y, 1, i % 5 == 0 ? cyan : 0xFF436170);
        }
        s.text(compact(r.maximum), 300, 52, 10, MUTED, 1, 65, true);
        s.text(compact(r.minimum), 300, 218, 10, MUTED, 1, 65, true);
        if (r.available()) {
            double y = 202 - r.progress() * 142;
            s.line(247, y - 5, 255, y, 2.5, WHITE); s.line(247, y + 5, 255, y, 2.5, WHITE);
            s.line(279, y, 294, y, 2, cyan);
        }
        s.text(r.scaleLabel, 37, 192, 8, MUTED, -1, 181, true);
    }

    private static void pod(Surface s, Reading r) {
        s.circle(93, 131, 77, 0xFF576573); s.circle(93, 131, 74, 0xFF1C2833);
        s.circle(93, 131, 69, 0xFF080E14);
        for (int i = 0; i <= 32; i++) {
            double angle = 135 + i * 270.0 / 32;
            radial(s, 93, 131, angle, i % 8 == 0 ? 51 : 59, 64, i % 8 == 0 ? 2 : 1,
                    i % 8 == 0 ? WHITE : 0xFF8495A4);
        }
        if (r.available()) radial(s, 93, 131, 135 + r.progress() * 270, -10, 59, 3, 0xFFFF8C61);
        s.circle(93, 131, 5, 0xFFB7C1C9);
        s.text(compact(r.minimum), 56, 185, 9, MUTED, 0, 40, true);
        s.text(compact(r.maximum), 133, 185, 9, MUTED, 0, 40, true);
        s.rect(173, 88, 131, 77, 6, 0xFF1B2934);
        s.text(r.available() ? r.display : "—", 238, 129, 37, WHITE, 0, 119, true);
        s.text(r.units, 238, 152, 13, 0xFFFFAC87, 0, 119, false);
        s.text("CURRENT", 239, 77, 9, MUTED, 0, 119, true);
        s.text(r.scaleLabel, 300, 214, 8, MUTED, 1, 267, true);
    }

    private static void sevenSegment(Surface s, String text, double left, double top,
            double width, double height, int color) {
        double units = 0;
        for (char c : text.toCharArray()) units += c == '.' ? .27 : .72;
        double scale = Math.min(height, width / units);
        double x = left + (width - units * scale) / 2;
        int[] masks = {0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f};
        double[][] lines = {{.10,0,.50,0},{.55,.07,.55,.43},{.55,.57,.55,.93},
                {.10,1,.50,1},{.05,.57,.05,.93},{.05,.07,.05,.43},{.10,.5,.50,.5}};
        for (char c : text.toCharArray()) {
            if (c == '.') { s.circle(x + scale * .07, top + scale * .96, scale * .045, color); x += .27 * scale; continue; }
            int mask = c == '-' ? 0x40 : masks[c - '0'];
            for (int i = 0; i < 7; i++) {
                double[] line = lines[i];
                s.line(x + line[0] * scale, top + line[1] * scale,
                        x + line[2] * scale, top + line[3] * scale,
                        scale * .075, (mask & (1 << i)) != 0 ? color : 0xFF163E2B);
            }
            x += .72 * scale;
        }
    }
    private static void radial(Surface s, double cx, double cy, double angle,
            double inner, double outer, double width, int color) {
        double a = Math.toRadians(angle), x = Math.cos(a), y = Math.sin(a);
        s.line(cx + x * inner, cy + y * inner, cx + x * outer, cy + y * outer, width, color);
    }
    public static String compact(double value) {
        if (!Double.isFinite(value)) return "—";
        double magnitude = Math.abs(value);
        if (magnitude >= 1000 && magnitude < 1_000_000) return String.format(Locale.ROOT, "%.1fk", value / 1000).replace(".0k", "k");
        if (magnitude >= 1_000_000) return String.format(Locale.ROOT, "%.1e", value);
        return String.format(Locale.ROOT, magnitude >= 100 ? "%.0f" : magnitude >= 10 ? "%.1f" : "%.2f", value);
    }
    private static String clean(String value) { return value == null ? "" : value; }
}
