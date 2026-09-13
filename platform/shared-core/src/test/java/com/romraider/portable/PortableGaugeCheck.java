/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.gauge.GaugeFaceRenderer;
import com.romraider.portable.gauge.GaugeReferenceScale;
import com.romraider.portable.gauge.MountedGaugePresentation;
import java.util.HashSet;
import java.util.Set;

public final class PortableGaugeCheck {
    public static void main(String[] args) {
        motion();
        Set<String> faces = new HashSet<>();
        Set<String> geometry = new HashSet<>();
        for (GaugeFaceRenderer.Style style : GaugeFaceRenderer.Style.values()) {
            require(style.usesNeedleMotion() == (style == GaugeFaceRenderer.Style.STI_NIGHT
                    || style == GaugeFaceRenderer.Style.EVOLUTION_NIGHT
                    || style == GaugeFaceRenderer.Style.ELECTRIC_BLOOM
                    || style == GaugeFaceRenderer.Style.SUNSET_GT
                    || style == GaugeFaceRenderer.Style.LOOP_DRIVE), "Unexpected animated instrument");
            Recording surface = new Recording();
            GaugeFaceRenderer.draw(surface, style, reading(12.7, -15, 30));
            faces.add(surface.commands.toString());
            geometry.add(surface.geometry.toString());
            Recording explicitCard = new Recording();
            GaugeFaceRenderer.draw(explicitCard, style, reading(12.7, -15, 30), GaugeFaceRenderer.Presentation.CARD);
            require(surface.commands.toString().equals(explicitCard.commands.toString()), "Default card changed");
            Recording seamless = new Recording();
            GaugeFaceRenderer.draw(seamless, style, reading(12.7, -15, 30), GaugeFaceRenderer.Presentation.SEAMLESS);
            require(surface.chrome > 0 && seamless.chrome == 0, "Seamless gauge retains card chrome: " + style);
            require(surface.labels.equals(seamless.labels), "Seamless gauge lost reading/status labels: " + style);
            mounted(style);
            for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, -100, 0, 100, Double.MAX_VALUE}) {
                for (double[] range : new double[][] {{-15, 30}, {0, 0}, {10, -10},
                        {Double.NaN, 1}, {-Double.MAX_VALUE, Double.MAX_VALUE}}) {
                    GaugeFaceRenderer.Reading reading = reading(value, range[0], range[1]);
                    require(Double.isFinite(reading.progress()) && reading.progress() >= 0
                            && reading.progress() <= 1, "Invalid gauge fraction");
                    GaugeFaceRenderer.draw(new Recording(), style, reading);
                    Recording floating = new Recording();
                    GaugeFaceRenderer.draw(floating, style, reading, GaugeFaceRenderer.Presentation.SEAMLESS);
                    require(floating.chrome == 0, "Invalid-reading state restored a gauge frame");
                }
            }
            Recording missing = new Recording();
            GaugeFaceRenderer.draw(missing, style, reading(Double.NaN, 0, 1));
            require(missing.commands.indexOf("—") >= 0, "Missing reading was not blanked");
        }
        require(faces.size() == GaugeFaceRenderer.Style.values().length, "Styles must have distinct geometry");
        require(geometry.size() == GaugeFaceRenderer.Style.values().length,
                "Styles must differ in geometry, not only colors, names or text contents");
        scale("Air/Fuel Ratio", "lambda", .6, 1.4);
        scale("MAF Sensor Voltage", "V", 0, 5);
        scale("Battery Voltage", "V", 8, 18);
        scale("Coolant Temperature", "°C", 40, 140);
        scale("Coolant Temperature", "°F", 100, 280);
        scale("Boost Pressure", "bar", -1, 2);
        require(!GaugeReferenceScale.forChannel("", "Boost", "unknown", 1, 2).reference,
                "Unknown units must not borrow psi scale");
        require(!GaugeReferenceScale.forChannel("", "Unknown", "", Double.NaN, Double.NaN).reference,
                "Unknown values must not invent a reference");
        System.out.println("Portable gauge checks passed: " + faces.size()
                + " faces, " + faces.size() * 70 + " card/seamless edge-case renders, unit-aware scales");
    }
    private static void mounted(GaugeFaceRenderer.Style style) {
        for (String state : new String[] {"LIVE", "SIMULATED", "NO RECENT DATA", "STOPPED"}) {
            for (boolean warning : new boolean[] {false, true}) {
                boolean unavailable = state.equals("NO RECENT DATA") || state.equals("STOPPED");
                var reading = new GaugeFaceRenderer.Reading("Synthetic boost", "12.7", "psi",
                        unavailable ? Double.NaN : 12.7, -15, 30, 18.4, state, "REFERENCE SCALE", warning);
                Recording compact = new Recording(MountedGaugePresentation.viewport(style));
                MountedGaugePresentation.draw(compact, style, reading);
                require(compact.chrome == 0, "Mounted card chrome: " + style);
                require(!compact.labels.contains("RR2") && !compact.labels.contains("REFERENCE SCALE")
                        && compact.labels.stream().noneMatch(label -> label.startsWith("PEAK ")),
                        "Mounted redundant labels: " + style);
                require(compact.labels.contains("SYNTHETIC BOOST") && (unavailable || compact.labels.contains("psi")),
                        "Mounted channel/units missing: " + style);
                require(compact.labels.contains(unavailable ? state : warning
                        ? state.equals("SIMULATED") ? "SIMULATED • LIMIT WARNING" : "LIMIT WARNING" : state),
                        "Mounted data state/warning missing: " + style);
                if (unavailable) require(compact.labels.contains("—"), "Mounted stale numeric reading: " + style);
            }
        }
        var bounds = MountedGaugePresentation.viewport(style);
        require(bounds.width > 0 && bounds.height > 0 && bounds.width < 320 && bounds.height < 250,
                "Mounted viewport did not tighten: " + style);
        if (style == GaugeFaceRenderer.Style.LASER_LED) {
            double oldScale = Math.min(945.0 / 320, 1024.0 / 250);
            double newScale = Math.min(945.0 / bounds.width, 1024.0 / bounds.height);
            require(newScale > oldScale * 1.4, "Screenshot's two portrait dials did not materially enlarge");
        }
    }
    private static void motion() {
        com.romraider.portable.gauge.GaugeMotion motion = new com.romraider.portable.gauge.GaugeMotion();
        motion.update(100, 0);
        require(motion.valueAt(0) == 100 && !motion.isAnimating(0), "First reading must not sweep from zero");
        motion.update(200, 1);
        require(motion.valueAt(50_000_001) == 150, "Needle interpolation midpoint");
        require(motion.valueAt(100_000_001) == 200, "Needle must settle within 100ms");
        motion.update(Double.NaN, 100_000_002);
        require(Double.isNaN(motion.valueAt(100_000_002)) && !motion.isAnimating(100_000_002), "Missing data must disappear immediately");
        motion.update(300, 100_000_003);
        require(motion.valueAt(100_000_003) == 300, "Recovery must not animate from a stale reading");
        motion.update(400, 2_000_000_000);
        require(motion.valueAt(2_000_000_000) == 400, "Long gaps must not animate from old data");
        motion.update(-Double.MAX_VALUE, 2_000_000_001);
        motion.update(Double.MAX_VALUE, 2_100_000_002);
        require(Double.isFinite(motion.valueAt(2_150_000_002L)), "Finite motion overflowed");
        GaugeFaceRenderer.Reading reading = reading(12.7, -15, 30).withIndicator(0);
        require(reading.value == 12.7 && reading.display.equals("12.7") && reading.indicatorProgress() != reading.progress(),
                "Visual interpolation changed the exact numeric reading");
    }
    private static void scale(String name, String units, double min, double max) {
        GaugeReferenceScale scale = GaugeReferenceScale.forChannel("", name, units, 0, 1);
        require(scale.minimum == min && scale.maximum == max && scale.reference, "Incorrect scale: " + name + units);
    }
    private static GaugeFaceRenderer.Reading reading(double value, double min, double max) {
        return new GaugeFaceRenderer.Reading("Synthetic boost", "12.7", "psi", value, min, max,
                18.4, "SIMULATED", "REFERENCE SCALE", false);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class Recording implements GaugeFaceRenderer.Surface {
        final MountedGaugePresentation.Viewport bounds;
        Recording() { this(null); }
        Recording(MountedGaugePresentation.Viewport bounds) { this.bounds = bounds; }
        private void point(double x, double y) {
            if (bounds != null) require(x >= bounds.left - .01 && y >= bounds.top - .01
                    && x <= bounds.left + bounds.width + .01 && y <= bounds.top + bounds.height + .01,
                    "Mounted drawing outside compact viewport: " + x + ", " + y);
        }
        final StringBuilder commands = new StringBuilder();
        final StringBuilder geometry = new StringBuilder();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        int chrome;
        private void coordinates(double... values) {
            for (double value : values) require(Double.isFinite(value), "Non-finite drawing coordinate");
            geometry.append(java.util.Arrays.toString(values));
        }
        public void rect(double x, double y, double w, double h, double r, int color) {
            point(x, y); point(x + w, y + h);
            coordinates(x,y,w,h,r); require(w >= 0 && h >= 0 && r >= 0, "Negative rectangle");
            if (x < 5 && y < 5 && w > 310 && (h > 240 || h <= 30)) chrome++;
            commands.append("rect").append(color).append(x).append(y);
        }
        public void circle(double x, double y, double r, int color) {
            point(x - r, y - r); point(x + r, y + r);
            coordinates(x,y,r); require(r >= 0, "Negative radius"); commands.append("circle").append(r);
        }
        public void line(double x, double y, double xx, double yy, double w, int color) {
            point(x - w / 2, y - w / 2); point(x + w / 2, y + w / 2);
            point(xx - w / 2, yy - w / 2); point(xx + w / 2, yy + w / 2);
            coordinates(x,y,xx,yy,w); commands.append("line").append(x).append(y);
            if (y == yy && xx - x >= 200 && (y == 31 || y == 34 || y == 227)) chrome++;
        }
        public void text(String value, double x, double y, double size, int color, int align, double width, boolean mono) {
            if (!value.isEmpty()) {
                point(align < 0 ? x : align > 0 ? x - width : x - width / 2, y - size);
                point(align < 0 ? x + width : align > 0 ? x : x + width / 2, y + size * .25);
            }
            coordinates(x,y,size,width); require(value != null && size > 0 && width > 0, "Invalid text");
            commands.append(value);
            labels.add(value);
        }
        public void radialCircle(double x, double y, double radius, int center, int edge) {
            point(x - radius, y - radius); point(x + radius, y + radius);
            coordinates(x, y, radius); require(radius > 0, "Invalid radial fill");
            commands.append("gradient").append(radius);
        }
        public void path(double[] path, int color) {
            for (int i = 0; i < path.length;) {
                int command = (int) path[i++];
                int points = command == 3 ? 0 : command == 2 ? 3 : 1;
                for (int p = 0; p < points; p++) { point(path[i], path[i + 1]); i += 2; }
            }
            coordinates(path); commands.append("path").append(path.length).append(color);
        }
    }
}
