/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Current reading and finite-only session peaks, independent of Android views. */
final class MobileGaugeSnapshot {
    final String id;
    final String name;
    final String units;
    private final String format;
    double value = Double.NaN;
    double minimum = Double.NaN;
    double maximum = Double.NaN;

    MobileGaugeSnapshot(String id, String name, String units, String format, double value) {
        this.id = id;
        this.name = name;
        this.units = units;
        this.format = format;
        accept(value);
    }

    void accept(double next) {
        value = Double.isFinite(next) ? next : Double.NaN;
        if (!Double.isFinite(value)) return;
        minimum = Double.isFinite(minimum) ? Math.min(minimum, value) : value;
        maximum = Double.isFinite(maximum) ? Math.max(maximum, value) : value;
    }

    void resetPeaks() {
        minimum = value;
        maximum = value;
    }

    String displayValue() {
        if (!Double.isFinite(value)) return "—";
        try {
            DecimalFormat formatter = new DecimalFormat(format,
                    DecimalFormatSymbols.getInstance(Locale.ROOT));
            formatter.setGroupingUsed(false);
            return formatter.format(value);
        } catch (IllegalArgumentException exception) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
    }

    static String summaryValue(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value)
                : "NO VALID DATA";
    }
}
