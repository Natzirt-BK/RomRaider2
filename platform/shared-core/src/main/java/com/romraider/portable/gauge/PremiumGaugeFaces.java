/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.gauge;

import java.util.Locale;
import com.romraider.portable.gauge.GaugeFaceRenderer.Reading;
import com.romraider.portable.gauge.GaugeFaceRenderer.Style;
import com.romraider.portable.gauge.GaugeFaceRenderer.Surface;

/** Night-cluster interpretations. Illumination is not a configured warning band. */
final class PremiumGaugeFaces {
    private PremiumGaugeFaces() { }
    static void draw(Surface s, Style style, Reading r) {
        boolean sti = style == Style.STI_NIGHT;
        int red = sti ? 0xFFFF3348 : 0xFFFF5B40;
        int numerals = sti ? 0xFFFF493F : 0xFFE8E3D9;
        s.rect(0, 0, 320, 250, 16, r.warning && r.available() ? 0xFFFF5D51 : 0xFF41434A);
        s.rect(1, 1, 318, 248, 15, 0xFF080A0F);
        s.rect(2, 2, 316, 28, 14, 0xFF12151B);
        s.text(r.name.toUpperCase(Locale.ROOT), 16, 21, 12, 0xFFCCD0D5, -1, 243, false);
        s.text("RR2", 304, 21, 10, 0xFF737982, 1, 35, true);

        // Concentric machined bezel, dark glass, and a restrained inner light well.
        s.circle(160, 130, 98, 0xFF020305);
        s.radialCircle(160, 127, 96, 0xFF0B0D13, 0xFF666A73);
        s.circle(160, 130, 93.5, 0xFF151820);
        arc(s, 160, 130, 95, 195, 125, 1, 0xFFBDC1C8);
        arc(s, 160, 130, 94.5, 15, 105, 1, 0xFF3D414C);
        s.radialCircle(160, 122, 91, sti ? 0xFF1E1219 : 0xFF232329, 0xFF050609);
        arc(s, 160, 130, 87, 135, 270, 8, alpha(red, 13));
        arc(s, 160, 130, 86, 135, 270, 4, alpha(red, 25));
        arc(s, 160, 130, 85.5, 135, 270, 1, alpha(red, 120));

        boolean rpm = r.units.trim().equalsIgnoreCase("rpm");
        int divisions = divisions(r, rpm);
        for (int i = 0; i <= divisions * 5; i++) {
            boolean major = i % 5 == 0;
            double angle = 135 + i * 270.0 / (divisions * 5);
            radial(s, angle, major ? 74 : 79, 85, major ? 4 : 2.2, alpha(red, major ? 37 : 20));
            radial(s, angle, major ? 74 : 79, 85, major ? 1.8 : .7,
                    major ? numerals : alpha(numerals, sti ? 190 : 120));
            if (major) {
                double number = r.minimum + (r.maximum - r.minimum) * i / (divisions * 5);
                if (rpm) number /= 1000;
                String label = tick(number);
                double a = Math.toRadians(angle);
                double x = 160 + Math.cos(a) * 64, y = 134 + Math.sin(a) * 64;
                if (sti) s.text(label, x, y, 13.4, alpha(red, 35), 0, 34, false);
                s.text(label, x, y, 13, numerals, 0, 34, false);
            }
        }

        if (sti) {
            GaugeArtwork.sti(s, 137.5, 87, 45, 0xFFFF2D63);
        } else {
            s.path(new double[] {0, 137, 84, 1, 145, 84, 1, 140, 94, 1, 132, 94, 3}, 0xFFFF4B45);
            s.path(new double[] {0, 148, 84, 1, 156, 84, 1, 151, 94, 1, 143, 94, 3}, 0xFFFF9E4B);
            s.text("MR", 165, 94, 13, 0xFFE8E3D9, -1, 30, false);
        }
        s.text(rpm ? "×1000 r/min" : r.units, 160, 111, 9,
                sti ? 0xFFD85759 : 0xFF969AA4, 0, 80, false);

        if (r.available()) needle(s, 135 + r.indicatorProgress() * 270, sti);
        s.radialCircle(160, 130, 12, 0xFF42414A, 0xFF08090D);
        s.circle(160, 130, 9.5, 0xFF191B22);
        arc(s, 160, 130, 10, 205, 115, .7, 0xFF686773);

        // The numeric reading stays separate and exact; no fabricated odometer or ACD state.
        s.rect(111, 180, 98, 34, 5, 0xFF020305);
        s.rect(112, 181, 96, 32, 4, sti ? 0xFF200C13 : 0xFF211E15);
        s.line(117, 183, 202, 183, .6, sti ? 0xFF71303D : 0xFF5C533E);
        s.text(r.available() ? r.display : "—", 160, 208, 28,
                sti ? 0xFFFF797C : 0xFFEDE1B2, 0, 89, true);
        s.text(r.scaleLabel, 160, 223, 7, 0xFF8D8993, 0, 164, true);
        String state = !r.available() ? (r.state.isEmpty() ? "NO VALID DATA" : r.state)
                : r.warning ? "LIMIT WARNING" : r.state;
        s.text(state, 16, 239, 9, !r.available() || r.warning ? 0xFFFFC27B : 0xFF9FA2AA, -1, 168, true);
        s.text("PEAK " + GaugeFaceRenderer.compact(r.peak), 304, 239, 9, 0xFF9FA2AA, 1, 116, true);
    }
    private static int divisions(Reading r, boolean rpm) {
        if (rpm && r.minimum == 0 && r.maximum == 9000) return 9;
        double span = r.maximum - r.minimum;
        if (!Double.isFinite(span) || span <= 0) return 8;
        double base = Math.pow(10, Math.floor(Math.log10(span / 8)));
        double step = span / 8 / base;
        step = (step <= 1.5 ? 1 : step <= 3.5 ? 2 : step <= 7.5 ? 5 : 10) * base;
        return Math.max(5, Math.min(10, (int) Math.round(span / step)));
    }
    private static String tick(double value) {
        if (!Double.isFinite(value)) return "—";
        if (value == Math.rint(value)) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, Math.abs(value) < 2 ? "%.2f" : "%.1f", value);
    }
    private static void needle(Surface s, double angle, boolean sti) {
        double a = Math.toRadians(angle), dx = Math.cos(a), dy = Math.sin(a);
        int light = sti ? 0xFFFFECF1 : 0xFFFF674F;
        for (int i = 3; i >= 1; i--) s.line(160 - dx * 13, 130 - dy * 13,
                160 + dx * 77, 130 + dy * 77, i * 3, alpha(light, 13));
        s.path(new double[] {0, 160 + dx * 79, 130 + dy * 79,
                1, 160 - dx * 14 - dy * 2.2, 130 - dy * 14 + dx * 2.2,
                1, 160 - dx * 14 + dy * 2.2, 130 - dy * 14 - dx * 2.2, 3}, light);
        s.line(160 + dx * 10, 130 + dy * 10, 160 + dx * 76, 130 + dy * 76,
                .7, sti ? 0xFFFFFFFF : 0xFFFFCAA1);
    }
    private static void radial(Surface s, double angle, double inner, double outer, double width, int color) {
        double a = Math.toRadians(angle);
        s.line(160 + Math.cos(a) * inner, 130 + Math.sin(a) * inner,
                160 + Math.cos(a) * outer, 130 + Math.sin(a) * outer, width, color);
    }
    private static void arc(Surface s, double cx, double cy, double radius, double start, double sweep, double width, int color) {
        int segments = Math.max(1, (int) Math.ceil(Math.abs(sweep) / 3));
        for (int i = 0; i < segments; i++) {
            double a = Math.toRadians(start + sweep * i / segments), b = Math.toRadians(start + sweep * (i + 1) / segments);
            s.line(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius,
                    cx + Math.cos(b) * radius, cy + Math.sin(b) * radius, width, color);
        }
    }
    private static int alpha(int color, int alpha) { return (color & 0xFFFFFF) | (alpha << 24); }
}
