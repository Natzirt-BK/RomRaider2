/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.tab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.Color;
import java.awt.Component;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.junit.Test;

import com.romraider.logger.ecu.ui.tab.dyno.DynoChartPanel;
import com.romraider.ui.ThemeMode;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

public class LoggerSpecializedWorkspaceTest {
    @Test
    public void specializedChartsFollowTheSelectedThemeAndExplainEmptyData() {
        UiThemeService theme = UiThemeService.getInstance();
        ThemeMode original = theme.getCurrentMode();
        try {
            theme.apply(ThemeMode.DARK);
            assertChartTheme(new LoggerChartPanel("MAF (v)",
                    "Correction (%)"),
                    "Record data to populate this graph");
            assertChartTheme(new DynoChartPanel("Engine Speed (RPM)",
                    "Wheel Power", "Torque"),
                    "Record or load data to populate this graph");
        } finally {
            theme.apply(original);
        }
    }

    private static void assertChartTheme(javax.swing.JPanel owner,
            String noDataMessage) {
        ChartPanel chartPanel = null;
        for (Component component : owner.getComponents()) {
            if (component instanceof ChartPanel) {
                chartPanel = (ChartPanel) component;
                break;
            }
        }
        assertNotNull(chartPanel);
        JFreeChart chart = chartPanel.getChart();
        Color surface = UiThemeService.getInstance().color(
                ThemeToken.SURFACE);
        Color background = UiThemeService.getInstance().color(
                ThemeToken.BACKGROUND);
        assertEquals(surface, chart.getBackgroundPaint());
        assertEquals(background, chart.getXYPlot().getBackgroundPaint());
        assertEquals(noDataMessage, chart.getXYPlot().getNoDataMessage());
    }
}
