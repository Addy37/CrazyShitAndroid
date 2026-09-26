package com.webapp.crazyshit;

/** Time-based refresh policy for the OnlyFap discovery hub. */
final class OnlyFapRefreshPolicy {
    static final long REFRESH_AFTER_MS = 45L * 60L * 1000L;

    private OnlyFapRefreshPolicy() { }

    static boolean shouldRefresh(long lastCompletedElapsedMs, long nowElapsedMs, boolean loading) {
        if (loading || lastCompletedElapsedMs <= 0L) return false;
        return nowElapsedMs - lastCompletedElapsedMs >= REFRESH_AFTER_MS;
    }
}
