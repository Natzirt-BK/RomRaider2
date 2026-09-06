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

    @Test
    public void oppositeFiniteExtremesKeepFiniteStatistics() throws Exception {
        ChannelStatistics stats = statistics(-Double.MAX_VALUE, Double.MAX_VALUE);
        assertEquals(0.0, stats.getMean(), 0.0);
        assertEquals(0.0, stats.getMedian(), 0.0);
        assertEquals(Double.MAX_VALUE, stats.getStandardDeviation(), 0.0);
        assertEquals(-0.9, stats.getPercentile05() / Double.MAX_VALUE, 1e-15);
        assertEquals(0.9, stats.getPercentile95() / Double.MAX_VALUE, 1e-15);
    }

    @Test
    public void squaredDeviationsDoNotOverflowOrUnderflow() throws Exception {
        assertEquals(1e200, statistics(-1e200, 1e200).getStandardDeviation(), 1e185);
        assertEquals(1e-200, statistics(-1e-200, 1e-200).getStandardDeviation(), 1e-215);
    }

    @Test
    public void hugeOffsetRetainsSmallRepresentableSpread() throws Exception {
        double low = 1e308, high = Math.nextUp(low);
        assertEquals((high - low) / 2, statistics(low, high).getStandardDeviation(), 0.0);
        ChannelStatistics constant = statistics(Double.MAX_VALUE, Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, constant.getMean(), 0.0);
        assertEquals(0.0, constant.getStandardDeviation(), 0.0);
    }

    @Test
    public void cancellationRetainsSmallMeanEvenIfIntermediateSumOverflows() throws Exception {
        assertEquals(0.2, statistics(-Double.MAX_VALUE, -Double.MAX_VALUE,
                1.0, Double.MAX_VALUE, Double.MAX_VALUE).getMean(), 0.0);
        assertEquals(1.0 / 3.0, statistics(-1e100, 1.0, 1e100).getMean(), 0.0);
    }

    @Test
    public void subnormalSamplesAreNotLostByDividingEachSampleFirst() throws Exception {
        ChannelStatistics constant = statistics(Double.MIN_VALUE, Double.MIN_VALUE);
        assertEquals(Double.MIN_VALUE, constant.getMean(), 0.0);
        assertEquals(0.0, constant.getStandardDeviation(), 0.0);
        assertEquals(0.0, statistics(0.0, Double.MIN_VALUE).getStandardDeviation(), 0.0);
    }

    @Test
    public void distributionScalesAcrossFiveHundredDecimalOrders() throws Exception {
        for (int exponent = -250; exponent <= 250; exponent += 25) {
            double scale = Math.pow(10, exponent);
            ChannelStatistics stats = statistics(4 * scale, scale, 3 * scale, 2 * scale);
            assertEquals(2.5, stats.getMean() / scale, 1e-14);
            assertEquals(Math.sqrt(1.25), stats.getStandardDeviation() / scale, 1e-14);
            assertEquals(2.5, stats.getMedian() / scale, 1e-14);
            assertEquals(1.15, stats.getPercentile05() / scale, 1e-14);
            assertEquals(3.85, stats.getPercentile95() / scale, 1e-14);
        }
    }

    @Test
    public void extremesDoNotChangeMissingValueOrSingletonRules() throws Exception {
        ChannelStatistics stats = statistics(Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.MAX_VALUE);
        assertEquals(1, stats.getSampleCount());
        assertEquals(3, stats.getMissingCount());
        assertEquals(Double.MAX_VALUE, stats.getMean(), 0.0);
        assertEquals(Double.MAX_VALUE, stats.getMedian(), 0.0);
        assertEquals(Double.MAX_VALUE, stats.getPercentile05(), 0.0);
        assertEquals(Double.MAX_VALUE, stats.getPercentile95(), 0.0);
        assertEquals(0.0, stats.getStandardDeviation(), 0.0);
    }

    private static ChannelStatistics statistics(double... values) throws Exception {
        StringBuilder csv = new StringBuilder("Time (msec),Value\n");
        for (int row = 0; row < values.length; row++)
            csv.append(row).append(',').append(values[row]).append('\n');
        LogDataset data = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(csv.toString()));
        return LogStatisticsService.analyze(data, LogRange.all(data)).get(1);
    }
}
