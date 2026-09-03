/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TablePresentationServiceTest {
    @Test
    public void routesRefreshSelectionAndValidationByTableIdentity() {
        Table1D first = table("Fuel Target");
        Table1D equalButSeparate = table("Fuel Target");
        final int[] events = new int[4];
        TablePresentationListener listener = new TablePresentationListener() {
            public void tableChanged(Table table) { events[0]++; }
            public void selectionAnchorChanged(Table table, int x, int y) {
                events[1] += x + y;
            }
            public void invalidScale(Table table, Scale scale) { events[2]++; }
            public void cellChanged(Table table, DataCell cell) { events[3]++; }
        };
        TablePresentationService.addListener(first, listener);
        try {
            TablePresentationService.changed(equalButSeparate);
            TablePresentationService.changed(first);
            TablePresentationService.selectionChanged(first, 2, 3);
            TablePresentationService.invalidScale(first, new Scale());
            TablePresentationService.cellChanged(first,
                    new DataCell(first, (Rom) null));
            assertEquals(1, events[0]);
            assertEquals(5, events[1]);
            assertEquals(1, events[2]);
            assertEquals(1, events[3]);
        } finally {
            TablePresentationService.removeListener(first, listener);
        }
    }

    @Test
    public void fallbackOnlyReceivesEventsWithoutAttachedPresentation() {
        Table1D table = table("Boost Target");
        final int[] fallbackEvents = {0};
        TablePresentationListener fallback = new TablePresentationListener() {
            public void invalidScale(Table target, Scale scale) {
                fallbackEvents[0]++;
            }
        };
        TablePresentationListener attached = new TablePresentationListener() { };
        TablePresentationService.addFallbackListener(fallback);
        try {
            TablePresentationService.invalidScale(table, new Scale());
            TablePresentationService.addListener(table, attached);
            TablePresentationService.invalidScale(table, new Scale());
            assertEquals(1, fallbackEvents[0]);
        } finally {
            TablePresentationService.removeListener(table, attached);
            TablePresentationService.removeListener(null, fallback);
        }
    }

    private static Table1D table(String name) {
        Table1D table = new Table1D();
        table.setName(name);
        return table;
    }
}
