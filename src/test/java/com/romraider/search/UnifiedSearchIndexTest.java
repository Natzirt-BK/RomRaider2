/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;

public class UnifiedSearchIndexTest {
    private static final String SOURCE = "test:unified-search";
    private final UnifiedSearchIndex index = UnifiedSearchIndex.getInstance();

    @After
    public void cleanUp() {
        index.removeSource(SOURCE);
    }

    @Test
    public void searchesAcrossKindsWithSharedDeterministicRanking() {
        SearchEntry map = entry(SearchKind.TABLE, "Boost Target",
                "Boost", "Main target map", "boost map");
        SearchEntry logger = entry(SearchKind.LOGGER_PARAMETER,
                "Manifold Relative Pressure", "Logger", "Live pressure",
                "boost");
        SearchEntry command = entry(SearchKind.COMMAND, "Open Logger",
                "Command", "Launch datalogging", "datalog");
        index.replaceSource(SOURCE, Arrays.asList(logger, command, map));

        List<SearchResult> boost = sourceResults("boost target");
        assertEquals(map, boost.get(0).getEntry());
        assertEquals(SearchKind.TABLE, boost.get(0).getEntry().getKind());
        assertEquals(command, sourceResults("datalog").get(0).getEntry());
    }

    @Test
    public void replacingAndRemovingSourcesNeverLeavesStaleEntries() {
        index.replaceSource(SOURCE, Arrays.asList(
                entry(SearchKind.TABLE, "Fuel A", "Fuel", "", ""),
                entry(SearchKind.TABLE, "Fuel B", "Fuel", "", "")));
        assertEquals(2, sourceResults("fuel").size());

        index.replaceSource(SOURCE, Collections.singletonList(
                entry(SearchKind.DTC, "P0300 Random Misfire", "DTC", "", "misfire")));
        assertTrue(sourceResults("fuel").isEmpty());
        assertEquals(1, sourceResults("misfire").size());

        index.removeSource(SOURCE);
        assertTrue(sourceResults("misfire").isEmpty());
    }

    private List<SearchResult> sourceResults(String query) {
        List<SearchResult> matches = new ArrayList<SearchResult>();
        for (SearchResult result : index.search(query, 1000)) {
            if (SOURCE.equals(result.getEntry().getSourceId())) {
                matches.add(result);
            }
        }
        return matches;
    }

    private static SearchEntry entry(SearchKind kind, String title,
            String context, String description, String alias) {
        return new SearchEntry(kind, SOURCE, title, title, context, description,
                alias.isEmpty() ? Collections.<String>emptyList()
                        : Collections.singletonList(alias));
    }
}
