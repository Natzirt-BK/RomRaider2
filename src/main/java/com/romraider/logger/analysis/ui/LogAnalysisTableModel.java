/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import java.util.Collections;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.romraider.logger.analysis.ChannelStatistics;

/** Read-only table projection of channel statistics. */
public final class LogAnalysisTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"Channel", "Units", "Samples",
            "Missing", "Minimum", "Maximum", "Average", "Median",
            "Std Dev", "P05", "P95"};
    private List<ChannelStatistics> statistics = Collections.emptyList();

    public void setStatistics(List<ChannelStatistics> statistics) {
        this.statistics = statistics == null
                ? Collections.<ChannelStatistics>emptyList() : statistics;
        fireTableDataChanged();
    }

    public int getRowCount() {
        return statistics.size();
    }

    public int getColumnCount() {
        return COLUMNS.length;
    }

    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    public Class<?> getColumnClass(int column) {
        if (column < 2) return String.class;
        if (column < 4) return Integer.class;
        return Double.class;
    }

    public boolean isCellEditable(int row, int column) {
        return false;
    }

    public ChannelStatistics getStatisticsAt(int row) {
        return statistics.get(row);
    }

    public Object getValueAt(int row, int column) {
        ChannelStatistics value = statistics.get(row);
        switch (column) {
            case 0: return value.getChannel().getName();
            case 1: return value.getChannel().getUnits();
            case 2: return value.getSampleCount();
            case 3: return value.getMissingCount();
            case 4: return finite(value.getMinimum());
            case 5: return finite(value.getMaximum());
            case 6: return finite(value.getMean());
            case 7: return finite(value.getMedian());
            case 8: return finite(value.getStandardDeviation());
            case 9: return finite(value.getPercentile05());
            case 10: return finite(value.getPercentile95());
            default: throw new IndexOutOfBoundsException("column " + column);
        }
    }

    private static Double finite(double value) {
        return Double.isFinite(value) ? Double.valueOf(value) : null;
    }
}
