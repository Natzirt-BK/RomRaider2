/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.List;

import org.junit.Test;

public class LogStatisticsServiceTest {
    @Test
    public void calculatesSelectedRangeAndIgnoresMissingValues() throws Exception {
        LogDataset dataset = new RomRaiderCsvLogParser().parse("range.csv",
                new StringReader("Time (msec),AFR (ratio)\n"
                        + "0,10\n100,\n200,20\n300,30\n400,40\n"));

        List<ChannelStatistics> result = LogStatisticsService.analyze(dataset,
                LogRange.of(1, 5, dataset.getRowCount()));
        ChannelStatistics afr = result.get(1);

        assertEquals(3, afr.getSampleCount());
        assertEquals(1, afr.getMissingCount());
        assertEquals(20.0, afr.getMinimum(), 0.0);
        assertEquals(40.0, afr.getMaximum(), 0.0);
        assertEquals(30.0, afr.getMean(), 0.000001);
        assertEquals(30.0, afr.getMedian(), 0.0);
        assertEquals(Math.sqrt(200.0 / 3.0),
                afr.getStandardDeviation(), 0.000001);
        assertEquals(21.0, afr.getPercentile05(), 0.0);
        assertEquals(39.0, afr.getPercentile95(), 0.0);
    }

    @Test
    public void reportsAnEntirelyMissingChannelTruthfully() throws Exception {
        LogDataset dataset = new RomRaiderCsvLogParser().parse("missing.csv",
                new StringReader("Time (msec),External AFR (AFR)\n0,\n100,NaN\n"));
        ChannelStatistics external = LogStatisticsService.analyze(dataset,
                LogRange.all(dataset)).get(1);

        assertEquals(0, external.getSampleCount());
        assertEquals(2, external.getMissingCount());
        assertTrue(Double.isNaN(external.getMean()));
    }
}
