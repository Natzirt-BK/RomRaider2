/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TableSearchServiceTest {
    @Test
    public void exactNameOutranksDescriptionAndResultsAreDeterministic() {
        TableSearchEntry exact = entry("EVO8", "Boost Limit", "Boost", "Maximum pressure", null);
        TableSearchEntry description = entry("EVO9", "Safety Limits", "Engine",
                "Contains boost limit protection", null);
        List<TableSearchResult> results = TableSearchService.search(
                Arrays.asList(description, exact), "boost limit", 10);
        assertEquals(exact, results.get(0).getEntry());
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
    }

    @Test
    public void fuzzyWordsCategoryAndAliasesAreSearchable() {
        TableSearchEntry timing = entry("EVO8", "Ignition Timing Advance", "Timing",
                "Main high octane map", Arrays.asList("spark map", "high octane ignition"));
        assertEquals(timing, TableSearchService.search(
                Collections.singletonList(timing), "ign tim", 5).get(0).getEntry());
        assertEquals(timing, TableSearchService.search(
                Collections.singletonList(timing), "spark map", 5).get(0).getEntry());
        assertEquals(timing, TableSearchService.search(
                Collections.singletonList(timing), "timing", 5).get(0).getEntry());
    }

    @Test
    public void honorsResultLimit() {
        List<TableSearchEntry> entries = Arrays.asList(
                entry("1", "Fuel A", "Fuel", "", null),
                entry("2", "Fuel B", "Fuel", "", null),
                entry("3", "Fuel C", "Fuel", "", null));
        assertEquals(2, TableSearchService.search(entries, "fuel", 2).size());
    }

    private static TableSearchEntry entry(String rom, String name,
            String category, String description, List<String> aliases) {
        return new TableSearchEntry(rom, name, category, description, aliases);
    }
}
