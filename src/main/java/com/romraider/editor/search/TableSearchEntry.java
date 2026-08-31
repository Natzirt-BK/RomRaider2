/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TableSearchEntry {
    private final String romId;
    private final String name;
    private final String category;
    private final String description;
    private final List<String> aliases;

    public TableSearchEntry(String romId, String name, String category,
            String description, List<String> aliases) {
        this.romId = safe(romId);
        this.name = safe(name);
        this.category = safe(category);
        this.description = safe(description);
        this.aliases = aliases == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(aliases));
    }

    public String getRomId() { return romId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public List<String> getAliases() { return aliases; }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
