/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Locale;
import com.romraider.logger.api.LiveDataSample;
import org.junit.jupiter.api.Test;

class FxLoggerStatisticsTest {
    @Test void computesFiniteCurrentUnitStatisticsOnly() {
        FxLoggerStatistics stats = FxLoggerStatistics.from(List.of(
                sample(1, "V"), sample(3, "V"), sample(8, "V"),
                sample(1000, "mV"), sample(Double.NaN, "V"),
                sample(Double.POSITIVE_INFINITY, "V")), "V");
        assertEquals(3, stats.count());
        assertEquals(1, stats.minimum());
        assertEquals(8, stats.maximum());
        assertEquals(4, stats.average(), 1e-12);
    }

    @Test void emptyOrInvalidHistoryIsNotShownAsZero() {
        FxLoggerStatistics empty = FxLoggerStatistics.from(List.of(), "V");
        assertEquals(0, empty.count());
        assertEquals("—", empty.display(empty.average()));
        assertEquals(0, FxLoggerStatistics.from(List.of(sample(Double.NaN, "V")), "V").count());
    }

    @Test void largeOppositeValuesDoNotOverflowMean() {
        FxLoggerStatistics stats = FxLoggerStatistics.from(List.of(
                sample(Double.MAX_VALUE, "V"), sample(-Double.MAX_VALUE, "V")), "V");
        assertEquals(0, stats.average());
    }

    @Test void formattingDoesNotFollowDecimalCommaLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            FxLoggerStatistics stats = FxLoggerStatistics.from(List.of(sample(1.5, "V")), "V");
            assertEquals("1.50000", stats.display(stats.average()));
        } finally { Locale.setDefault(previous); }
    }

    private static LiveDataSample sample(double value, String units) {
        return new LiveDataSample("test", "Test", value, Double.toString(value), units, 1);
    }
}
