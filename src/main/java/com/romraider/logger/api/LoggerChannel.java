/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Immutable description of one selectable Logger channel. */
public final class LoggerChannel {
    private final String parameterId;
    private final String name;
    private final String units;
    private final LoggerChannelKind kind;
    private final boolean selected;
    private final List<LoggerChannelUnitOption> unitOptions;

    public LoggerChannel(String parameterId, String name, String units,
            LoggerChannelKind kind, boolean selected) {
        this(parameterId, name, units, kind, selected,
                units == null || units.trim().isEmpty()
                        ? Collections.<LoggerChannelUnitOption>emptyList()
                        : Collections.singletonList(
                                new LoggerChannelUnitOption("0", units, true)));
    }

    public LoggerChannel(String parameterId, String name, String units,
            LoggerChannelKind kind, boolean selected,
            Collection<LoggerChannelUnitOption> unitOptions) {
        this.parameterId = required(parameterId, "parameter id");
        this.name = required(name, "channel name");
        this.units = units == null ? "" : units.trim();
        if (kind == null) throw new IllegalArgumentException("kind is required");
        if (unitOptions == null) {
            throw new IllegalArgumentException("unit options are required");
        }
        this.kind = kind;
        this.selected = selected;
        this.unitOptions = Collections.unmodifiableList(
                new ArrayList<LoggerChannelUnitOption>(unitOptions));
    }

    public String getParameterId() { return parameterId; }
    public String getName() { return name; }
    public String getUnits() { return units; }
    public LoggerChannelKind getKind() { return kind; }
    public boolean isSelected() { return selected; }
    public List<LoggerChannelUnitOption> getUnitOptions() { return unitOptions; }

    public LoggerChannel withSelected(boolean value) {
        if (value == selected) return this;
        return new LoggerChannel(parameterId, name, units, kind, value,
                unitOptions);
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
