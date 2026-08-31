/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

/** Describes one numeric channel in a captured logger file. */
public final class LogChannel {
    private final int index;
    private final String label;
    private final String name;
    private final String units;

    LogChannel(int index, String label) {
        if (index < 0) throw new IllegalArgumentException("index must be non-negative");
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("channel label must not be empty");
        }
        this.index = index;
        this.label = label.trim();

        int unitsStart = findUnitsStart(this.label);
        if (unitsStart >= 0) {
            this.name = this.label.substring(0, unitsStart).trim();
            this.units = this.label.substring(unitsStart + 1,
                    this.label.length() - 1).trim();
        } else {
            this.name = this.label;
            this.units = "";
        }
    }

    private static int findUnitsStart(String label) {
        if (!label.endsWith(")")) return -1;
        int start = label.lastIndexOf(" (");
        return start < 0 ? -1 : start + 1;
    }

    public int getIndex() {
        return index;
    }

    public String getLabel() {
        return label;
    }

    public String getName() {
        return name;
    }

    public String getUnits() {
        return units;
    }

    public boolean isTimeChannel() {
        return "time".equalsIgnoreCase(name)
                || name.toLowerCase(java.util.Locale.ROOT).startsWith("time ");
    }

    @Override public String toString() { return label; }
}
