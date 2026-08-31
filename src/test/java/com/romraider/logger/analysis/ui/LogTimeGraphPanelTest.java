/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import static org.junit.Assert.assertEquals;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.util.Arrays;

import org.junit.Test;

import com.romraider.logger.analysis.LogCursorModel;
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.LogRange;
import com.romraider.logger.analysis.RomRaiderCsvLogParser;

public class LogTimeGraphPanelTest {
    @Test
    public void paintsSelectedChannelsAndSeeksTheSharedCursor() throws Exception {
        LogDataset dataset = new RomRaiderCsvLogParser().parse("graph.csv",
                new StringReader("Time (msec),RPM (rpm),AFR (ratio)\n"
                        + "0,1000,14.7\n100,1100,14.5\n"
                        + "200,1200,13.5\n300,1300,12.5\n"));
        LogCursorModel cursor = new LogCursorModel();
        LogTimeGraphPanel graph = new LogTimeGraphPanel(cursor);
        graph.setSize(800, 300);
        cursor.configure(dataset, LogRange.all(dataset));
        graph.setChannels(Arrays.asList(dataset.getChannels().get(1),
                dataset.getChannels().get(2)));

        BufferedImage image = new BufferedImage(800, 300,
                BufferedImage.TYPE_INT_ARGB);
        graph.paint(image.getGraphics());
        graph.dispatchEvent(new MouseEvent(graph, MouseEvent.MOUSE_PRESSED,
                0L, 0, 570, 100, 1, false));

        assertEquals(3, cursor.getSampleIndex());
        assertEquals(2, graph.getChannels().size());
    }
}
