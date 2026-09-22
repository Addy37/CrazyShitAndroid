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
}
