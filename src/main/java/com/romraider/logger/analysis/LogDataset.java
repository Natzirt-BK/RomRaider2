/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, numeric representation of one captured logger file. */
public final class LogDataset {
    private final String sourceName;
    private final List<LogChannel> channels;
    private final double[][] rows;

    LogDataset(String sourceName, List<LogChannel> channels,
            List<double[]> rows) {
        if (sourceName == null) throw new IllegalArgumentException("sourceName");
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("channels must not be empty");
        }
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("rows must not be empty");
        }
        this.sourceName = sourceName;
        this.channels = Collections.unmodifiableList(
                new ArrayList<LogChannel>(channels));
        this.rows = new double[rows.size()][];
        for (int row = 0; row < rows.size(); row++) {
            double[] values = rows.get(row);
            if (values.length != channels.size()) {
                throw new IllegalArgumentException("row width does not match channels");
            }
            this.rows[row] = values.clone();
        }
    }

    public String getSourceName() {
        return sourceName;
    }

    public List<LogChannel> getChannels() {
        return channels;
    }

    public int getChannelCount() {
        return channels.size();
    }

    public int getRowCount() {
        return rows.length;
    }

    public double getValue(int row, int channel) {
        return rows[row][channel];
    }

    public LogChannel getTimeChannel() {
        for (LogChannel channel : channels) {
            if (channel.isTimeChannel()) return channel;
        }
        return null;
    }
}
