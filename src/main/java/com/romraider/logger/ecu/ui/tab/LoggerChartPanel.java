/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2012 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.logger.ecu.ui.tab;

import com.romraider.logger.ecu.ui.handler.graph.SpringUtilities;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import static com.romraider.util.ParamChecker.checkNotNull;
import jamlab.Polyfit;
import static org.jfree.chart.ChartFactory.createScatterPlot;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import static org.jfree.chart.plot.PlotOrientation.VERTICAL;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYDotRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import java.awt.Color;
import java.awt.Dimension;

public final class LoggerChartPanel extends JPanel {
    private static final long serialVersionUID = -6579979878171615665L;
    private final XYSeries data = new XYSeries("Data");
    private final XYTrendline trendline = new XYTrendline(data);
    private final XYSeries hilite = new XYSeries("Hilite");
    private final String labelX;
    private final String labelY;

    public LoggerChartPanel(String labelX, String labelY) {
        super(new SpringLayout());
        checkNotNull(labelX, labelY);
        this.labelX = labelX;
        this.labelY = labelY;
        addChart();
    }

    public synchronized void addData(double x, double y) {
        if (hilite.getItemCount() == 1) {
            XYDataItem item = hilite.remove(0);
            data.add(item);
        }
        hilite.add(x, y);
    }

    public void clear() {
        trendline.clear();
        hilite.clear();
        data.clear();
    }

    public void interpolate(int order) {
        trendline.update(order);
    }

    public double[] calculate(double[] x) {
        return trendline.calculate(x);
    }

    public double[] getPolynomialCoefficients() {
        Polyfit fit = trendline.getPolyFit();
        return fit.getPolynomialCoefficients();
    }

    private void addChart() {
        ChartPanel chartPanel = new ChartPanel(createChart(), false, true, true, true, true);
        chartPanel.setMinimumSize(new Dimension(400, 300));
        chartPanel.setPreferredSize(new Dimension(500, 400));
        add(chartPanel);
        SpringUtilities.makeCompactGrid(this, 1, 1, 2, 2, 2, 2);
    }

    private JFreeChart createChart() {
        JFreeChart chart = createScatterPlot(null, labelX, labelY, null, VERTICAL, false, true, false);
        UiThemeService theme = UiThemeService.getInstance();
        chart.setBackgroundPaint(theme.color(ThemeToken.SURFACE));
        configurePlot(chart);
        addSeries(chart, 0, hilite, 4, theme.color(ThemeToken.SUCCESS));
        addTrendLine(chart, 1, trendline,
                theme.color(ThemeToken.ACCENT));
        addSeries(chart, 2, data, 2, theme.color(ThemeToken.DANGER));
        return chart;
    }

    private void configurePlot(JFreeChart chart) {
        UiThemeService theme = UiThemeService.getInstance();
        Color text = theme.color(ThemeToken.PRIMARY_TEXT);
        Color secondary = theme.color(ThemeToken.SECONDARY_TEXT);
        Color grid = theme.color(ThemeToken.RAISED_SURFACE);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(theme.color(ThemeToken.BACKGROUND));
        plot.getDomainAxis().setLabelPaint(text);
        plot.getRangeAxis().setLabelPaint(text);
        plot.getDomainAxis().setTickLabelPaint(secondary);
        plot.getRangeAxis().setTickLabelPaint(secondary);
        plot.setDomainGridlinePaint(grid);
        plot.setRangeGridlinePaint(grid);
        plot.setOutlinePaint(grid);
        plot.setNoDataMessage("Record data to populate this graph");
        plot.setNoDataMessagePaint(secondary);
        plot.setRenderer(buildScatterRenderer(2,
                theme.color(ThemeToken.DANGER)));
    }

    private XYDotRenderer buildScatterRenderer(int size, Color color) {
        XYDotRenderer renderer = new XYDotRenderer();
        renderer.setDotHeight(size);
        renderer.setDotWidth(size);
        renderer.setSeriesPaint(0, color);
        return renderer;
    }

    private void addTrendLine(JFreeChart chart, int index, XYTrendline trendline, Color color) {
        XYPlot plot = chart.getXYPlot();
        plot.setDataset(index, trendline);
        plot.setRenderer(index, buildTrendLineRenderer(color));
    }

    private void addSeries(JFreeChart chart, int index, XYSeries series, int size, Color color) {
        XYDataset dataset = new XYSeriesCollection(series);
        XYPlot plot = chart.getXYPlot();
        plot.setDataset(index, dataset);
        plot.setRenderer(index, buildScatterRenderer(size, color));
    }

    private StandardXYItemRenderer buildTrendLineRenderer(Color color) {
        StandardXYItemRenderer renderer = new StandardXYItemRenderer();
        renderer.setSeriesPaint(0, color);
        return renderer;
    }
}
