/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.gauge;

import java.util.Locale;
import com.romraider.portable.gauge.GaugeFaceRenderer.Reading;
import com.romraider.portable.gauge.GaugeFaceRenderer.Style;
import com.romraider.portable.gauge.GaugeFaceRenderer.Surface;

/** Compact mounted presentation. Artwork coordinates stay unchanged; card chrome is omitted. */
public final class MountedGaugePresentation {
    public static final class Viewport {
        public final double left, top, width, height;
        private Viewport(double left, double top, double width, double height) {
            this.left = left; this.top = top; this.width = width; this.height = height;
        }
        public double aspect() { return width / height; }
    }
    private static final Viewport ROUND = new Viewport(56, 10, 208, 234);
    private static final Viewport WIDE = new Viewport(8, 10, 304, 234);
    private static final Viewport OVAL = new Viewport(32, 10, 256, 234);
    public static final Viewport CLASSIC = new Viewport(78, 6, 164, 199);
    private MountedGaugePresentation() { }

    public static Viewport viewport(Style style) {
        if (style == null) throw new IllegalArgumentException("Gauge style is required");
        switch (style) {
            case RALLY_PRECISION: case CLUB_SPORT: case ELECTRIC_BLOOM: case LASER_LED:
            case STI_NIGHT: case EVOLUTION_NIGHT: case ION_OLED:
                return ROUND;
            case APEX_24: case LOOP_DRIVE:
                return OVAL; // These styles deliberately extend their display outside the round bezel.
            default: return WIDE;
        }
    }

    public static void draw(Surface surface, Style style, Reading reading) {
        if (surface == null || reading == null) throw new IllegalArgumentException("Gauge surface and reading are required");
        Viewport bounds = viewport(style);
        // All shared faces reserve y<35 for the card title/badge and y>227 for
        // their footer. Dial numerals, internal peak windows and warnings remain.
        GaugeFaceRenderer.draw(new DialSurface(surface, reading.scaleLabel), style, reading,
                GaugeFaceRenderer.Presentation.SEAMLESS);
        surface.text(reading.name.toUpperCase(Locale.ROOT), 160, 25, 10.5, 0xFFF0F3F4,
                0, bounds.width - 12, false);
        String state = !reading.available() ? (reading.state.isEmpty() ? "NO VALID DATA" : reading.state)
                : reading.warning ? "LIMIT WARNING" : reading.state;
        if (reading.warning && reading.available() && reading.state.equals("SIMULATED")) state = "SIMULATED • LIMIT WARNING";
        // Simulation/stale/warning identity must not disappear with decorative metadata.
        surface.text(state, 160, 239, 9, !reading.available() || reading.warning ? 0xFFFFC56B : 0xFF9CAAB5,
                0, bounds.width - 12, true);
    }

    private static final class DialSurface implements Surface {
        private final Surface delegate;
        private final String scaleLabel;
        DialSurface(Surface delegate, String scaleLabel) { this.delegate = delegate; this.scaleLabel = scaleLabel; }
        public void rect(double x, double y, double w, double h, double r, int color) { delegate.rect(x, y, w, h, r, color); }
        public void circle(double x, double y, double r, int color) { delegate.circle(x, y, r, color); }
        public void line(double x1, double y1, double x2, double y2, double w, int color) { delegate.line(x1, y1, x2, y2, w, color); }
        public void radialCircle(double x, double y, double r, int center, int edge) { delegate.radialCircle(x, y, r, center, edge); }
        public void path(double[] commands, int color) { delegate.path(commands, color); }
        public void text(String text, double x, double y, double size, int color, int align, double maxWidth, boolean mono) {
            if (y < 35 || y > 227 || (!scaleLabel.isEmpty() && scaleLabel.equals(text))) return;
            delegate.text(text, x, y, size, color, align, maxWidth, mono);
        }
    }
}
