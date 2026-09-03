/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Toolkit-neutral choice for one Logger channel conversion. */
public final class LoggerChannelUnitOption {
    private final String id;
    private final String label;
    private final boolean selected;

    public LoggerChannelUnitOption(String id, String label, boolean selected) {
        this.id = required(id, "unit option id");
        this.label = required(label, "unit option label");
        this.selected = selected;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public boolean isSelected() { return selected; }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
