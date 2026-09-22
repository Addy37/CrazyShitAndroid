package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ShitTokPreloadPolicyTest {
    @Test
    public void playerWindow_preparesOnlyCurrentAndImmediateNext() {
        int selected = 10;

        assertTrue(ChaosFeedView.shouldPreparePlayer(10, selected));
        assertTrue(ChaosFeedView.shouldPreparePlayer(11, selected));
        assertFalse(ChaosFeedView.shouldPreparePlayer(9, selected));
        assertFalse(ChaosFeedView.shouldPreparePlayer(12, selected));
        assertFalse(ChaosFeedView.shouldPreparePlayer(13, selected));
    }

    @Test
    public void startupQueue_seedsSixSwipeableItems() {
        assertEquals(6, ChaosStartupPreloader.STARTER_ITEMS);
    }

    @Test
    public void mediaCache_isCappedAtTwoHundredMiB() {
        assertEquals(200L * 1024L * 1024L, ShitTokMediaCache.MAX_CACHE_BYTES);
    }
}
