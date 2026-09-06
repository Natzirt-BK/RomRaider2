/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.util.List;
import java.util.Locale;
import com.romraider.logger.api.LiveDataSample;

/** Finite values in the current unit only; no placeholder zero readings. */
record FxLoggerStatistics(long count, double minimum, double maximum, double average) {
    static FxLoggerStatistics from(List<LiveDataSample> history, String units) {
        long count = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double mean = 0;
        for (LiveDataSample sample : history) {
            double value = sample.getRawValue();
            if (!sample.getUnits().equals(units) || !Double.isFinite(value)) continue;
            count++;
            min = Math.min(min, value);
            max = Math.max(max, value);
            // Weighted form avoids overflowing a sum or opposite-sign delta.
            mean = mean * ((count - 1.0) / count) + value / count;
        }
        return new FxLoggerStatistics(count, min, max, mean);
    }

    String display(double value) {
        return count == 0 ? "—" : String.format(Locale.ROOT, "%.6g", value);
    }
}
