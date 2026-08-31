/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class LogCursorModelTest {
    @Test
    public void sharesOneClampedCursorAcrossRangeChanges() throws Exception {
        LogDataset dataset = dataset();
        LogCursorModel cursor = new LogCursorModel();
        AtomicInteger changes = new AtomicInteger();
        cursor.addListener((value, range, sample) -> changes.incrementAndGet());

        cursor.configure(dataset, LogRange.of(1, 4, dataset.getRowCount()));
        assertEquals(1, cursor.getSampleIndex());
        cursor.seek(99);
        assertEquals(3, cursor.getSampleIndex());
        cursor.step(-1);
        assertEquals(2, cursor.getSampleIndex());
        cursor.setRange(LogRange.of(0, 2, dataset.getRowCount()));
        assertEquals(1, cursor.getSampleIndex());
        assertEquals(4, changes.get());
    }

    static LogDataset dataset() throws Exception {
        return new RomRaiderCsvLogParser().parse("play.csv", new StringReader(
                "Time (msec),RPM (rpm),AFR (ratio)\n"
                + "0,1000,14.7\n100,1100,14.5\n300,1300,13.0\n"
                + "500,1500,12.0\n"));
    }
}
