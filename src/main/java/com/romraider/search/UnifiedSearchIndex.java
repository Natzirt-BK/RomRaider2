/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe catalog shared by editor, logger, diagnostics, and commands. */
public final class UnifiedSearchIndex {
    private static final UnifiedSearchIndex INSTANCE = new UnifiedSearchIndex();
    private final Map<String, List<SearchEntry>> entriesBySource =
            new LinkedHashMap<String, List<SearchEntry>>();

    public static UnifiedSearchIndex getInstance() {
        return INSTANCE;
    }

    public synchronized void replaceSource(String sourceId,
            List<SearchEntry> entries) {
        String source = sourceId == null ? "" : sourceId.trim();
        if (source.isEmpty()) throw new IllegalArgumentException("Source ID is required");
        List<SearchEntry> copy = entries == null
                ? Collections.<SearchEntry>emptyList()
                : new ArrayList<SearchEntry>(entries);
        entriesBySource.put(source, Collections.unmodifiableList(copy));
    }

    public synchronized void removeSource(String sourceId) {
        entriesBySource.remove(sourceId);
    }

    public synchronized List<SearchEntry> entries() {
        List<SearchEntry> all = new ArrayList<SearchEntry>();
        for (List<SearchEntry> entries : entriesBySource.values()) all.addAll(entries);
        return Collections.unmodifiableList(all);
    }

    public synchronized List<SearchResult> search(String query, int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<SearchResult> results = new ArrayList<SearchResult>();
        for (List<SearchEntry> entries : entriesBySource.values()) {
            for (SearchEntry entry : entries) {
                int score = SearchScorer.score(entry.getTitle(), entry.getContext(),
                        entry.getDescription(), entry.getAliases(), query);
                if (score > 0) results.add(new SearchResult(entry, score));
            }
        }
        Collections.sort(results);
        if (results.size() > limit) {
            results = new ArrayList<SearchResult>(results.subList(0, limit));
        }
        return Collections.unmodifiableList(results);
    }
}
