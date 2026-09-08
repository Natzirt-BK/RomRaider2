package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.api.LiveDataSample;
import java.util.List;
import org.junit.jupiter.api.Test;

class FxLiveGraphTest {
    private LiveDataSample sample(double value, long time, String units) {
        return new LiveDataSample("x", "Synthetic", value, "", units, time, units);
    }
    @Test void timeScaleReflectsUnequalPollingIntervalsAndDoesNotBridgeGaps() {
        assertEquals(.1, FxLiveGraph.fraction(100, 0, 1000));
        assertEquals(.9, FxLiveGraph.fraction(900, 0, 1000));
        assertFalse(FxLiveGraph.connected(sample(1, 0, "V"), sample(2, 3000, "V")));
        assertFalse(FxLiveGraph.connected(sample(Double.NaN, 0, "V"), sample(2, 100, "V")));
        assertTrue(FxLiveGraph.connected(sample(1, 0, "V"), sample(2, 100, "V")));
    }
    @Test void rangesExcludeIncompatibleUnitsAndInvalidValues() {
        var series = FxLiveGraph.series(sample(2, 100, "V"), List.of(sample(1000, 0, "mV"), sample(2, 100, "V"), sample(4, 200, "V"), sample(Double.NaN, 300, "V")));
        assertEquals(2, series.min()); assertEquals(4, series.max()); assertEquals(3, series.samples().size());
    }
}
