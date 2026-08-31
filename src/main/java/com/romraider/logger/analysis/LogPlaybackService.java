/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Deterministic offline playback state machine. It owns no thread; a UI or test
 * supplies elapsed wall-clock milliseconds through {@link #advance(double)}.
 */
public final class LogPlaybackService {
    public interface Listener {
        void playbackChanged(LogPlaybackSnapshot snapshot);
    }

    private final LogCursorModel cursor;
    private final List<Listener> listeners =
            new CopyOnWriteArrayList<Listener>();
    private LogTimeline timeline;
    private PlaybackState state = PlaybackState.EMPTY;
    private double speed = 1.0;
    private double positionMillis;
    private boolean controllingCursor;

    public LogPlaybackService(LogCursorModel cursor) {
        if (cursor == null) throw new IllegalArgumentException("cursor");
        this.cursor = cursor;
        cursor.addListener((dataset, range, sampleIndex) -> {
            synchronized (LogPlaybackService.this) {
                if (!controllingCursor && timeline != null && sampleIndex >= 0) {
                    positionMillis = timeline.getElapsedMillis(sampleIndex);
                    if (state == PlaybackState.COMPLETE) {
                        state = PlaybackState.PAUSED;
                    }
                    notifyListeners();
                }
            }
        });
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void load(LogDataset dataset, LogRange range) {
        timeline = new LogTimeline(dataset);
        state = PlaybackState.PAUSED;
        controllingCursor = true;
        try {
            cursor.configure(dataset, range);
        } finally {
            controllingCursor = false;
        }
        positionMillis = timeline.getElapsedMillis(range.getStartInclusive());
        notifyListeners();
    }

    public synchronized void setRange(LogRange range) {
        if (timeline == null) return;
        state = PlaybackState.PAUSED;
        controllingCursor = true;
        try {
            cursor.setRange(range);
        } finally {
            controllingCursor = false;
        }
        positionMillis = timeline.getElapsedMillis(cursor.getSampleIndex());
        notifyListeners();
    }

    public synchronized void play() {
        if (timeline == null) return;
        LogRange range = cursor.getRange();
        if (state == PlaybackState.COMPLETE
                || cursor.getSampleIndex() >= range.getEndExclusive() - 1) {
            controlledSeek(range.getStartInclusive());
            positionMillis = timeline.getElapsedMillis(range.getStartInclusive());
        }
        state = PlaybackState.PLAYING;
        notifyListeners();
    }

    public synchronized void pause() {
        if (state != PlaybackState.PLAYING) return;
        state = PlaybackState.PAUSED;
        notifyListeners();
    }

    public synchronized void stop() {
        if (timeline == null) return;
        state = PlaybackState.PAUSED;
        LogRange range = cursor.getRange();
        controlledSeek(range.getStartInclusive());
        positionMillis = timeline.getElapsedMillis(range.getStartInclusive());
        notifyListeners();
    }

    public synchronized void step(int offset) {
        if (timeline == null || offset == 0) return;
        state = PlaybackState.PAUSED;
        controllingCursor = true;
        try {
            cursor.step(offset);
        } finally {
            controllingCursor = false;
        }
        positionMillis = timeline.getElapsedMillis(cursor.getSampleIndex());
        notifyListeners();
    }

    public synchronized void seek(int sampleIndex) {
        if (timeline == null) return;
        controlledSeek(sampleIndex);
        positionMillis = timeline.getElapsedMillis(cursor.getSampleIndex());
        if (state == PlaybackState.COMPLETE) state = PlaybackState.PAUSED;
        notifyListeners();
    }

    public synchronized void setSpeed(double speed) {
        if (!Double.isFinite(speed) || speed < 0.25 || speed > 8.0) {
            throw new IllegalArgumentException("speed must be between 0.25x and 8x");
        }
        this.speed = speed;
        notifyListeners();
    }

    public synchronized void advance(double elapsedWallMillis) {
        if (state != PlaybackState.PLAYING || elapsedWallMillis <= 0.0
                || !Double.isFinite(elapsedWallMillis)) return;
        LogRange range = cursor.getRange();
        double end = timeline.getElapsedMillis(range.getEndExclusive() - 1);
        positionMillis += elapsedWallMillis * speed;
        if (positionMillis >= end) {
            positionMillis = end;
            controlledSeek(range.getEndExclusive() - 1);
            state = PlaybackState.COMPLETE;
            notifyListeners();
            return;
        }
        int sample = timeline.sampleAtOrBefore(positionMillis, range);
        if (sample != cursor.getSampleIndex()) controlledSeek(sample);
    }

    public synchronized LogPlaybackSnapshot snapshot() {
        return new LogPlaybackSnapshot(state, speed, cursor.getSampleIndex());
    }

    private void notifyListeners() {
        LogPlaybackSnapshot snapshot = snapshot();
        for (Listener listener : listeners) listener.playbackChanged(snapshot);
    }

    private void controlledSeek(int sampleIndex) {
        controllingCursor = true;
        try {
            cursor.seek(sampleIndex);
        } finally {
            controllingCursor = false;
        }
    }
}
