package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VideoDetailProgressTest {
    @Test
    public void portraitProgressFractionClampsPlaybackBounds() {
        assertEquals(0f, VideoDetailActivity.portraitProgressFraction(0L, 10_000L), 0.0001f);
        assertEquals(0.25f, VideoDetailActivity.portraitProgressFraction(2_500L, 10_000L), 0.0001f);
        assertEquals(1f, VideoDetailActivity.portraitProgressFraction(12_000L, 10_000L), 0.0001f);
        assertEquals(0f, VideoDetailActivity.portraitProgressFraction(2_500L, 0L), 0.0001f);
    }

    @Test
    public void portraitSeekPositionMapsAndClampsScrubProgress() {
        assertEquals(25_000L, VideoDetailActivity.portraitSeekPosition(250, 1000, 100_000L));
        assertEquals(0L, VideoDetailActivity.portraitSeekPosition(-10, 1000, 100_000L));
        assertEquals(100_000L, VideoDetailActivity.portraitSeekPosition(1200, 1000, 100_000L));
        assertEquals(0L, VideoDetailActivity.portraitSeekPosition(500, 0, 100_000L));
    }
}
