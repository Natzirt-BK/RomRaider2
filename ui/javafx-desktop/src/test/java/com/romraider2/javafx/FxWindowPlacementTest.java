/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

class FxWindowPlacementTest {
    @Test
    void fitsDecoratedEditorAndLoggerAboveWindowsTaskbar() {
        Rectangle2D work = new Rectangle2D(0, 0, 1280, 752);
        assertEquals(work, FxWindowPlacement.fit(work, 1376, 899));
        assertEquals(work, FxWindowPlacement.fit(work, 1396, 899));
    }

    @Test
    void centersPreferredSizeOnLargeScreen() {
        assertEquals(new Rectangle2D(280, 90, 1360, 860),
                FxWindowPlacement.fit(new Rectangle2D(0, 0, 1920, 1040), 1360, 860));
    }

    @Test
    void respectsNegativeMonitorOriginsAndTopPanels() {
        assertEquals(new Rectangle2D(-1600, 32, 1600, 868),
                FxWindowPlacement.fit(new Rectangle2D(-1600, 32, 1600, 868), 1800, 1000));
    }

    @Test
    void fitsHighDpiLogicalWorkAreaBelowUsualMinimum() {
        Rectangle2D work = new Rectangle2D(0, 0, 853, 485);
        assertEquals(work, FxWindowPlacement.fit(work, 1380, 860));
    }

    @Test
    void onlyShrinksTheDimensionThatExceedsTheWorkArea() {
        assertEquals(new Rectangle2D(1900, 0, 1000, 752),
                FxWindowPlacement.fit(new Rectangle2D(1760, 0, 1280, 752), 1000, 900));
    }
}
