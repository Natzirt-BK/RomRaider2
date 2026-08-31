/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.romraider.maps.Table;
import com.romraider.search.SearchScorer;

public final class TableSearchService {
    private TableSearchService() {
    }

    public static boolean matches(Table table, String query) {
        if (table == null) return false;
        TableSearchEntry entry = new TableSearchEntry("", table.getName(),
                table.getCategory(), table.getDescription(), null);
        return score(entry, query) > 0;
    }

    public static List<TableSearchResult> search(List<TableSearchEntry> entries,
            String query, int limit) {
        if (entries == null || limit <= 0) return Collections.emptyList();
        List<TableSearchResult> results = new ArrayList<TableSearchResult>();
        for (TableSearchEntry entry : entries) {
            int score = score(entry, query);
            if (score > 0) results.add(new TableSearchResult(entry, score));
        }
        Collections.sort(results);
        if (results.size() > limit) {
            return Collections.unmodifiableList(
                    new ArrayList<TableSearchResult>(results.subList(0, limit)));
        }
        return Collections.unmodifiableList(results);
    }

    static int score(TableSearchEntry entry, String query) {
        return SearchScorer.score(entry.getName(), entry.getCategory(),
                entry.getDescription(), entry.getAliases(), query);
    }
}
