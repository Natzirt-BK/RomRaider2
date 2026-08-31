/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.StringReader;

import org.junit.Test;

import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.LogRange;
import com.romraider.logger.analysis.LogStatisticsService;
import com.romraider.logger.analysis.RomRaiderCsvLogParser;

public class LogAnalysisTableModelTest {
    @Test
    public void exposesTypedReadOnlyStatisticsAndBlankMissingResults()
            throws Exception {
        LogDataset dataset = new RomRaiderCsvLogParser().parse("table.csv",
                new StringReader("Time (msec),AFR (ratio)\n0,\n100,NaN\n"));
        LogAnalysisTableModel model = new LogAnalysisTableModel();
        model.setStatistics(LogStatisticsService.analyze(dataset,
                LogRange.all(dataset)));

        assertEquals(2, model.getRowCount());
        assertEquals(Integer.class, model.getColumnClass(2));
        assertEquals(Double.class, model.getColumnClass(4));
        assertEquals("AFR", model.getValueAt(1, 0));
        assertEquals(2, model.getValueAt(1, 3));
        assertNull(model.getValueAt(1, 4));
        assertFalse(model.isCellEditable(0, 0));
    }
}
