/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.gauge;

import java.util.Locale;

/** Display references only: never engine limits or warning thresholds. */
public final class GaugeReferenceScale {
    public final double minimum, maximum;
    public final boolean reference;
    private GaugeReferenceScale(double minimum, double maximum, boolean reference) {
        this.minimum = minimum; this.maximum = maximum; this.reference = reference;
    }
    public static GaugeReferenceScale forChannel(String id, String name, String units, double low, double high) {
        String identity = clean(id) + " " + clean(name), unit = clean(units);
        if (unit.equals("lambda")) return fixed(.6, 1.4);
        if (unit.equals("rpm")) return fixed(0, 9000);
        if (unit.equals("afr") || (identity.contains("air/fuel") && unit.equals("ratio"))) return fixed(8, 22);
        if (identity.contains("boost") || identity.contains("manifold relative")) {
            if (unit.equals("psi")) return fixed(-15, 30);
            if (unit.equals("kpa")) return fixed(-100, 200);
            if (unit.equals("bar")) return fixed(-1, 2);
        }
        if (identity.contains("pressure")) {
            if (unit.equals("psi")) return fixed(0, 120);
            if (unit.equals("kpa")) return fixed(0, 800);
            if (unit.equals("bar")) return fixed(0, 10);
        }
        if (identity.contains("temperature") || identity.contains("temp")) {
            if (unit.equals("c") || unit.equals("°c") || unit.equals("celsius")) return fixed(40, 140);
            if (unit.equals("f") || unit.equals("°f") || unit.equals("fahrenheit")) return fixed(100, 280);
        }
        if (unit.equals("v")) return identity.contains("maf") || identity.contains("mass air")
                ? fixed(0, 5) : identity.contains("battery") || identity.contains("system voltage")
                ? fixed(8, 18) : recent(low, high);
        if (unit.equals("%")) return fixed(0, identity.contains("load") ? 300 : 100);
        if (unit.equals("°") || unit.equals("deg") || unit.equals("degrees")) {
            if (identity.contains("knock")) return fixed(-12, 12);
            if (identity.contains("ignition") || identity.contains("timing")) return fixed(-20, 60);
        }
        return recent(low, high);
    }
    private static GaugeReferenceScale fixed(double low, double high) { return new GaugeReferenceScale(low, high, true); }
    private static GaugeReferenceScale recent(double low, double high) {
        if (!Double.isFinite(low) || !Double.isFinite(high) || high < low) return new GaugeReferenceScale(0, 1, false);
        double padding = high > low ? (high - low) * .12 : Math.max(Math.abs(high) * .2, 1);
        double min = low - padding, max = high + padding;
        if (!Double.isFinite(min) || !Double.isFinite(max - min) || max <= min) return new GaugeReferenceScale(0, 1, false);
        return new GaugeReferenceScale(min, max, false);
    }
    private static String clean(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
