/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.Objects;

/** Optional user-authored scale and warning behavior for one logger channel. */
public final class LoggerGaugeConfiguration {
    public enum AlertState { NORMAL, LOW, HIGH, UNAVAILABLE }

    private final Double scaleMinimum;
    private final Double scaleMaximum;
    private final Double lowWarning;
    private final Double highWarning;
    private final double hysteresis;
    private final String conversionIdentity;

    public LoggerGaugeConfiguration(Double scaleMinimum, Double scaleMaximum,
            Double lowWarning, Double highWarning, double hysteresis) {
        this(scaleMinimum, scaleMaximum, lowWarning, highWarning, hysteresis, "");
    }

    public LoggerGaugeConfiguration(Double scaleMinimum, Double scaleMaximum,
            Double lowWarning, Double highWarning, double hysteresis, String conversionIdentity) {
        finiteOrNull(scaleMinimum, "Scale minimum");
        finiteOrNull(scaleMaximum, "Scale maximum");
        finiteOrNull(lowWarning, "Low warning");
        finiteOrNull(highWarning, "High warning");
        if ((scaleMinimum == null) != (scaleMaximum == null)) {
            throw new IllegalArgumentException(
                    "Both custom scale values are required");
        }
        if (scaleMinimum != null && scaleMinimum >= scaleMaximum) {
            throw new IllegalArgumentException(
                    "Scale minimum must be below its maximum");
        }
        if (lowWarning != null && highWarning != null
                && lowWarning >= highWarning) {
            throw new IllegalArgumentException(
                    "Low warning must be below high warning");
        }
        if (!Double.isFinite(hysteresis) || hysteresis < 0.0) {
            throw new IllegalArgumentException(
                    "Warning hysteresis cannot be negative");
        }
        this.scaleMinimum = scaleMinimum;
        this.scaleMaximum = scaleMaximum;
        this.lowWarning = lowWarning;
        this.highWarning = highWarning;
        this.hysteresis = hysteresis;
        this.conversionIdentity = conversionIdentity == null ? "" : conversionIdentity.trim();
    }

    public Double getScaleMinimum() { return scaleMinimum; }
    public Double getScaleMaximum() { return scaleMaximum; }
    public Double getLowWarning() { return lowWarning; }
    public Double getHighWarning() { return highWarning; }
    public double getHysteresis() { return hysteresis; }
    public String getConversionIdentity() { return conversionIdentity; }

    public boolean matchesConversion(String identity) {
        return !conversionIdentity.isEmpty() && conversionIdentity.equals(identity);
    }

    public LoggerGaugeConfiguration forConversion(String identity) {
        if (identity == null || identity.trim().isEmpty()) {
            throw new IllegalArgumentException("A known channel conversion is required");
        }
        return new LoggerGaugeConfiguration(scaleMinimum, scaleMaximum, lowWarning,
                highWarning, hysteresis, identity);
    }

    public boolean hasCustomScale() {
        return scaleMinimum != null;
    }

    public boolean hasWarnings() {
        return lowWarning != null || highWarning != null;
    }

    public AlertState alertState(double value, AlertState previous) {
        if (!Double.isFinite(value)) return AlertState.UNAVAILABLE;
        AlertState prior = previous == null ? AlertState.NORMAL : previous;
        // Crossing the opposite warning must override even a wide hysteresis band.
        if (highWarning != null && value >= highWarning) return AlertState.HIGH;
        if (lowWarning != null && value <= lowWarning) return AlertState.LOW;
        if (prior == AlertState.HIGH && highWarning != null
                && value > highWarning - hysteresis) return AlertState.HIGH;
        if (prior == AlertState.LOW && lowWarning != null
                && value < lowWarning + hysteresis) return AlertState.LOW;
        return AlertState.NORMAL;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LoggerGaugeConfiguration)) return false;
        LoggerGaugeConfiguration that = (LoggerGaugeConfiguration) other;
        return Double.compare(hysteresis, that.hysteresis) == 0
                && Objects.equals(scaleMinimum, that.scaleMinimum)
                && Objects.equals(scaleMaximum, that.scaleMaximum)
                && Objects.equals(lowWarning, that.lowWarning)
                && Objects.equals(highWarning, that.highWarning)
                && conversionIdentity.equals(that.conversionIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scaleMinimum, scaleMaximum, lowWarning,
                highWarning, hysteresis, conversionIdentity);
    }

    private static void finiteOrNull(Double value, String label) {
        if (value != null && !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
