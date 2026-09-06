/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileGaugeSnapshotTest {
    private MobileGaugeSnapshot snapshot(double value) {
        return new MobileGaugeSnapshot("fixture", "Fixture", "V", "0.0", value);
    }

    @Test public void invalidFirstReadingDoesNotPoisonPeaks() {
        MobileGaugeSnapshot sample = snapshot(Double.NaN);
        assertEquals("—", sample.displayValue());
        assertTrue(Double.isNaN(sample.minimum));
        sample.accept(12);
        sample.accept(13);
        assertEquals(12, sample.minimum, 0);
        assertEquals(13, sample.maximum, 0);
        assertEquals("13.0", sample.displayValue());
    }

    @Test public void invalidCurrentPreservesFinitePeaksAndRecovers() {
        MobileGaugeSnapshot sample = snapshot(12);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            sample.accept(invalid);
            assertEquals("—", sample.displayValue());
            assertTrue(Double.isNaN(sample.value));
            assertEquals(12, sample.minimum, 0);
            assertEquals(12, sample.maximum, 0);
            assertEquals("NO VALID DATA", MobileGaugeSnapshot.summaryValue(invalid));
        }
        sample.accept(0);
        assertEquals("0.0", sample.displayValue());
        assertEquals("0.000", MobileGaugeSnapshot.summaryValue(0));
        assertEquals(0, sample.minimum, 0);
        assertEquals(12, sample.maximum, 0);
    }

    @Test public void resetDuringGapWaitsForNextFiniteReading() {
        MobileGaugeSnapshot sample = snapshot(12);
        sample.accept(Double.NaN);
        sample.resetPeaks();
        assertTrue(Double.isNaN(sample.maximum));
        sample.accept(14);
        assertEquals(14, sample.minimum, 0);
        assertEquals(14, sample.maximum, 0);
    }

    @Test public void missingAndExtremeValuesNeverProduceInvalidDrawingCoordinates() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.MAX_VALUE, -Double.MAX_VALUE}) {
            MobileGaugeScale scale = MobileGaugeScale.forChannel("unknown", "Unknown", "unit", value, value);
            assertTrue(Double.isFinite(scale.minimum));
            assertTrue(Double.isFinite(scale.maximum - scale.minimum));
            assertTrue(Float.isFinite(scale.progress(value)));
            assertTrue(scale.progress(value) >= 0 && scale.progress(value) <= 1);
        }
        assertEquals(0f, new MobileGaugeScale(0, 100).progress(Double.NaN), 0);
    }
}
