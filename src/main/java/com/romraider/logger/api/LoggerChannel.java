/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Immutable description of one selectable Logger channel. */
public final class LoggerChannel {
    private final String parameterId;
    private final String name;
    private final String units;
    private final LoggerChannelKind kind;
    private final boolean selected;

    public LoggerChannel(String parameterId, String name, String units,
            LoggerChannelKind kind, boolean selected) {
        this.parameterId = required(parameterId, "parameter id");
        this.name = required(name, "channel name");
        this.units = units == null ? "" : units.trim();
        if (kind == null) throw new IllegalArgumentException("kind is required");
        this.kind = kind;
        this.selected = selected;
    }

    public String getParameterId() { return parameterId; }
    public String getName() { return name; }
    public String getUnits() { return units; }
    public LoggerChannelKind getKind() { return kind; }
    public boolean isSelected() { return selected; }

    public LoggerChannel withSelected(boolean value) {
        if (value == selected) return this;
        return new LoggerChannel(parameterId, name, units, kind, value);
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
