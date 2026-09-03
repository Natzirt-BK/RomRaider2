/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import java.util.Locale;

/** Stable display scale for a mobile logger value. */
final class MobileGaugeScale {
    final double minimum;
    final double maximum;

    MobileGaugeScale(double minimum, double maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    float progress(double value) {
        double span = maximum - minimum;
        if (span <= 0.0) return 0f;
        return (float) Math.max(0.0, Math.min(1.0,
                (value - minimum) / span));
    }

    static MobileGaugeScale forChannel(String id, String name, String units,
            double measuredMinimum, double measuredMaximum) {
        String identity = clean(id) + " " + clean(name);
        String unit = clean(units);
        if (identity.contains("rpm") || identity.contains("engine speed")) {
            return new MobileGaugeScale(0, 9000);
        }
        if (identity.contains("air/fuel") || identity.contains("afr")) {
            return new MobileGaugeScale(8, 22);
        }
        if (unit.contains("lambda") || identity.contains("lambda")) {
            return new MobileGaugeScale(.6, 1.4);
        }
        if (identity.contains("boost")
                || identity.contains("manifold relative")) {
            if (unit.contains("kpa")) return new MobileGaugeScale(-100, 200);
            if (unit.equals("bar")) return new MobileGaugeScale(-1, 2);
            return new MobileGaugeScale(-15, 30);
        }
        if (identity.contains("oil pressure")
                || identity.contains("fuel pressure")) {
            if (unit.contains("kpa")) return new MobileGaugeScale(0, 800);
            if (unit.equals("bar")) return new MobileGaugeScale(0, 10);
            return new MobileGaugeScale(0, 120);
        }
        if (identity.contains("temperature") || identity.contains("temp")) {
            return unit.contains("c") ? new MobileGaugeScale(40, 140)
                    : new MobileGaugeScale(100, 280);
        }
        if (identity.contains("voltage") || unit.equals("v")) {
            return new MobileGaugeScale(8, 18);
        }
        if (identity.contains("load") && unit.equals("%")) {
            return new MobileGaugeScale(0, 300);
        }
        if (identity.contains("throttle") || identity.contains("duty")
                || unit.equals("%")) return new MobileGaugeScale(0, 100);
        if (identity.contains("ignition") || identity.contains("timing")) {
            return new MobileGaugeScale(-20, 60);
        }
        if (identity.contains("knock")) return new MobileGaugeScale(-12, 12);
        double span = measuredMaximum - measuredMinimum;
        double padding = span > 0 ? span * .12
                : Math.max(Math.abs(measuredMaximum) * .20, 1.0);
        return new MobileGaugeScale(measuredMinimum - padding,
                measuredMaximum + padding);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
