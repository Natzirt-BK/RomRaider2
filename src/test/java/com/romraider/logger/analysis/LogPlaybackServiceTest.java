/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LogPlaybackServiceTest {
    @Test
    public void advancesByCapturedTimeAndCompletesAtRangeEnd() throws Exception {
        LogDataset dataset = LogCursorModelTest.dataset();
        LogCursorModel cursor = new LogCursorModel();
        LogPlaybackService playback = new LogPlaybackService(cursor);
        playback.load(dataset, LogRange.of(1, 4, dataset.getRowCount()));

        playback.play();
        playback.advance(150.0);
        assertEquals(1, cursor.getSampleIndex());
        playback.advance(50.0);
        assertEquals(2, cursor.getSampleIndex());
        playback.setSpeed(2.0);
        playback.advance(100.0);
        assertEquals(3, cursor.getSampleIndex());
        assertEquals(PlaybackState.COMPLETE, playback.snapshot().getState());
    }

    @Test
    public void seekStepStopAndReplayRemainBounded() throws Exception {
        LogDataset dataset = LogCursorModelTest.dataset();
        LogCursorModel cursor = new LogCursorModel();
        LogPlaybackService playback = new LogPlaybackService(cursor);
        playback.load(dataset, LogRange.all(dataset));

        playback.seek(2);
        playback.step(10);
        assertEquals(3, cursor.getSampleIndex());
        playback.play();
        assertEquals(0, cursor.getSampleIndex());
        playback.pause();
        assertEquals(PlaybackState.PAUSED, playback.snapshot().getState());
        playback.seek(2);
        playback.stop();
        assertEquals(0, cursor.getSampleIndex());
    }

    @Test
    public void preservesSubsampleTimeWhenTheCursorCrossesAFrame() throws Exception {
        LogDataset dataset = LogCursorModelTest.dataset();
        LogCursorModel cursor = new LogCursorModel();
        LogPlaybackService playback = new LogPlaybackService(cursor);
        playback.load(dataset, LogRange.all(dataset));

        playback.play();
        playback.advance(60.0);
        playback.advance(60.0);
        assertEquals(1, cursor.getSampleIndex());
        playback.advance(180.0);
        assertEquals(2, cursor.getSampleIndex());
    }
}
