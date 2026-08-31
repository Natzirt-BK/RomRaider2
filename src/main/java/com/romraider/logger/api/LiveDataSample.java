/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Immutable converted logger value safe to hand to other application layers. */
public final class LiveDataSample {
    private final String parameterId;
    private final String name;
    private final double rawValue;
    private final String displayValue;
    private final String units;
    private final long timestampMillis;

    public LiveDataSample(String parameterId, String name, double rawValue,
            String displayValue, String units, long timestampMillis) {
        this.parameterId = required(parameterId, "parameter id");
        this.name = required(name, "parameter name");
        this.rawValue = rawValue;
        this.displayValue = normalize(displayValue);
        this.units = normalize(units);
        this.timestampMillis = timestampMillis;
    }

    public String getParameterId() { return parameterId; }
    public String getName() { return name; }
    public double getRawValue() { return rawValue; }
    public String getDisplayValue() { return displayValue; }
    public String getUnits() { return units; }
    public long getTimestampMillis() { return timestampMillis; }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
