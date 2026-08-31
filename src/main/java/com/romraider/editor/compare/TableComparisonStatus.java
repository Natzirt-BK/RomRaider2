/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.compare;

public enum TableComparisonStatus {
    DIFFERENT("Modified"),
    ONLY_LEFT("Only in left ROM"),
    ONLY_RIGHT("Only in right ROM"),
    EQUAL("Unchanged");

    private final String displayName;

    TableComparisonStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
