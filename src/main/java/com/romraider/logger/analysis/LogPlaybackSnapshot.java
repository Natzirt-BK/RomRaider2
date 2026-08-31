/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

/** Immutable playback state published to offline UI consumers. */
public final class LogPlaybackSnapshot {
    private final PlaybackState state;
    private final double speed;
    private final int sampleIndex;

    LogPlaybackSnapshot(PlaybackState state, double speed, int sampleIndex) {
        this.state = state;
        this.speed = speed;
        this.sampleIndex = sampleIndex;
    }

    public PlaybackState getState() { return state; }
    public double getSpeed() { return speed; }
    public int getSampleIndex() { return sampleIndex; }
}
