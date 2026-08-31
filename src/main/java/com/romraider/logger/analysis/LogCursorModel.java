/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shared sample cursor for linked offline analysis views. */
public final class LogCursorModel {
    public interface Listener {
        void cursorChanged(LogDataset dataset, LogRange range, int sampleIndex);
    }

    private final List<Listener> listeners =
            new CopyOnWriteArrayList<Listener>();
    private LogDataset dataset;
    private LogRange range;
    private int sampleIndex = -1;

    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void configure(LogDataset dataset, LogRange range) {
        validate(dataset, range);
        this.dataset = dataset;
        this.range = range;
        sampleIndex = range.getStartInclusive();
        notifyListeners();
    }

    public synchronized void setRange(LogRange range) {
        validate(dataset, range);
        this.range = range;
        sampleIndex = clamp(sampleIndex, range);
        notifyListeners();
    }

    public synchronized void seek(int sampleIndex) {
        if (range == null) return;
        int next = clamp(sampleIndex, range);
        if (this.sampleIndex == next) return;
        this.sampleIndex = next;
        notifyListeners();
    }

    public synchronized void step(int offset) {
        if (range == null || offset == 0) return;
        seek(sampleIndex + offset);
    }

    public synchronized LogDataset getDataset() { return dataset; }
    public synchronized LogRange getRange() { return range; }
    public synchronized int getSampleIndex() { return sampleIndex; }
    public synchronized boolean isConfigured() { return dataset != null; }

    private static void validate(LogDataset dataset, LogRange range) {
        if (dataset == null || range == null
                || range.getEndExclusive() > dataset.getRowCount()) {
            throw new IllegalArgumentException("dataset and valid range are required");
        }
    }

    private static int clamp(int sampleIndex, LogRange range) {
        return Math.max(range.getStartInclusive(), Math.min(sampleIndex,
                range.getEndExclusive() - 1));
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.cursorChanged(dataset, range, sampleIndex);
        }
    }
}
