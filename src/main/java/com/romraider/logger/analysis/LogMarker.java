/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

public final class LogMarker implements Comparable<LogMarker> {
    private final int sampleIndex;
    private final LogMarkerType type;
    private final String label;

    public LogMarker(int sampleIndex, LogMarkerType type, String label) {
        if (sampleIndex < 0 || type == null) {
            throw new IllegalArgumentException("sample and type are required");
        }
        this.sampleIndex = sampleIndex;
        this.type = type;
        this.label = label == null ? "" : label.trim();
    }

    public int getSampleIndex() { return sampleIndex; }
    public LogMarkerType getType() { return type; }
    public String getLabel() { return label; }
    public String getDisplayName() {
        return label.isEmpty() ? type.getDisplayName() : label;
    }

    public int compareTo(LogMarker other) {
        int sample = Integer.compare(sampleIndex, other.sampleIndex);
        return sample != 0 ? sample
                : getDisplayName().compareToIgnoreCase(other.getDisplayName());
    }
}
