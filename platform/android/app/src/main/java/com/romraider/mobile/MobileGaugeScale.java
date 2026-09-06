/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import com.romraider.portable.gauge.GaugeReferenceScale;

/** Unit-aware display scale; never an engine limit or warning threshold. */
final class MobileGaugeScale {
    final double minimum;
    final double maximum;
    final boolean reference;

    MobileGaugeScale(double minimum, double maximum) {
        this(minimum, maximum, false);
    }
    private MobileGaugeScale(double minimum, double maximum, boolean reference) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.reference = reference;
    }
    float progress(double value) {
        double span = maximum - minimum;
        if (!Double.isFinite(value) || !Double.isFinite(minimum)
                || !Double.isFinite(span) || span <= 0.0) return 0f;
        return (float) Math.max(0.0, Math.min(1.0, (value - minimum) / span));
    }
    static MobileGaugeScale forChannel(String id, String name, String units,
            double measuredMinimum, double measuredMaximum) {
        GaugeReferenceScale scale = GaugeReferenceScale.forChannel(id, name, units,
                measuredMinimum, measuredMaximum);
        return new MobileGaugeScale(scale.minimum, scale.maximum, scale.reference);
    }
}
