/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.gauge.GaugeFaceRenderer;
import com.romraider.portable.gauge.GaugeReferenceScale;
import java.util.HashSet;
import java.util.Set;

public final class PortableGaugeCheck {
    public static void main(String[] args) {
        Set<String> faces = new HashSet<>();
        for (GaugeFaceRenderer.Style style : GaugeFaceRenderer.Style.values()) {
            Recording surface = new Recording();
            GaugeFaceRenderer.draw(surface, style, reading(12.7, -15, 30));
            faces.add(surface.commands.toString());
            for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, -100, 0, 100, Double.MAX_VALUE}) {
                for (double[] range : new double[][] {{-15, 30}, {0, 0}, {10, -10},
                        {Double.NaN, 1}, {-Double.MAX_VALUE, Double.MAX_VALUE}}) {
                    GaugeFaceRenderer.Reading reading = reading(value, range[0], range[1]);
                    require(Double.isFinite(reading.progress()) && reading.progress() >= 0
                            && reading.progress() <= 1, "Invalid gauge fraction");
                    GaugeFaceRenderer.draw(new Recording(), style, reading);
                }
            }
            Recording missing = new Recording();
            GaugeFaceRenderer.draw(missing, style, reading(Double.NaN, 0, 1));
            require(missing.commands.indexOf("—") >= 0, "Missing reading was not blanked");
        }
        require(faces.size() == GaugeFaceRenderer.Style.values().length, "Styles must have distinct geometry");
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
                + " faces, " + faces.size() * 35 + " edge-case renders, unit-aware scales");
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
        final StringBuilder commands = new StringBuilder();
        private void coordinates(double... values) {
            for (double value : values) require(Double.isFinite(value), "Non-finite drawing coordinate");
        }
        public void rect(double x, double y, double w, double h, double r, int color) {
            coordinates(x,y,w,h,r); require(w >= 0 && h >= 0 && r >= 0, "Negative rectangle");
            commands.append("rect").append(color).append(x).append(y);
        }
        public void circle(double x, double y, double r, int color) {
            coordinates(x,y,r); require(r >= 0, "Negative radius"); commands.append("circle").append(r);
        }
        public void line(double x, double y, double xx, double yy, double w, int color) {
            coordinates(x,y,xx,yy,w); commands.append("line").append(x).append(y);
        }
        public void text(String value, double x, double y, double size, int color, int align, double width, boolean mono) {
            coordinates(x,y,size,width); require(value != null && size > 0 && width > 0, "Invalid text");
            commands.append(value);
        }
    }
}
