package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OnlyFapSpeedPolicyTest {
    @Test public void nearestGalleryVideosGetMorePreloadBytes() {
        assertEquals(1024L * 1024L, GalleryVideoCache.preloadBytesForDistance(1));
        assertEquals(512L * 1024L, GalleryVideoCache.preloadBytesForDistance(2));
        assertEquals(256L * 1024L, GalleryVideoCache.preloadBytesForDistance(3));
        assertEquals(256L * 1024L, GalleryVideoCache.preloadBytesForDistance(5));
    }
}
