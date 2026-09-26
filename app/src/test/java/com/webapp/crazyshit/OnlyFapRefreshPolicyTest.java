package com.webapp.crazyshit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OnlyFapRefreshPolicyTest {
    @Test
    public void freshLoadDoesNotRefreshBeforeWindow() {
        long loadedAt = 10_000L;
        long now = loadedAt + OnlyFapRefreshPolicy.REFRESH_AFTER_MS - 1L;

        assertFalse(OnlyFapRefreshPolicy.shouldRefresh(loadedAt, now, false));
    }

    @Test
    public void completedLoadRefreshesAtWindow() {
        long loadedAt = 10_000L;
        long now = loadedAt + OnlyFapRefreshPolicy.REFRESH_AFTER_MS;

        assertTrue(OnlyFapRefreshPolicy.shouldRefresh(loadedAt, now, false));
    }

    @Test
    public void activeLoadNeverStartsSecondRefresh() {
        long loadedAt = 10_000L;
        long now = loadedAt + OnlyFapRefreshPolicy.REFRESH_AFTER_MS + 1L;

        assertFalse(OnlyFapRefreshPolicy.shouldRefresh(loadedAt, now, true));
    }

    @Test
    public void initialStateUsesNormalFirstLoadPath() {
        assertFalse(OnlyFapRefreshPolicy.shouldRefresh(
                0L,
                OnlyFapRefreshPolicy.REFRESH_AFTER_MS * 2L,
                false
        ));
    }
}
