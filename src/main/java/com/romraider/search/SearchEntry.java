/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, UI-independent item stored in the application search index. */
public final class SearchEntry {
    private final SearchKind kind;
    private final String sourceId;
    private final String targetId;
    private final String title;
    private final String context;
    private final String description;
    private final List<String> aliases;

    public SearchEntry(SearchKind kind, String sourceId, String targetId,
            String title, String context, String description,
            List<String> aliases) {
        if (kind == null) throw new IllegalArgumentException("Search kind is required");
        this.kind = kind;
        this.sourceId = safe(sourceId);
        this.targetId = safe(targetId);
        this.title = safe(title);
        this.context = safe(context);
        this.description = safe(description);
        this.aliases = aliases == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(aliases));
    }

    public SearchKind getKind() { return kind; }
    public String getSourceId() { return sourceId; }
    public String getTargetId() { return targetId; }
    public String getTitle() { return title; }
    public String getContext() { return context; }
    public String getDescription() { return description; }
    public List<String> getAliases() { return aliases; }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
