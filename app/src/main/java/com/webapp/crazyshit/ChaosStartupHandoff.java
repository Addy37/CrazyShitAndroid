package com.webapp.crazyshit;

/**
 * Tiny process-local bridge between SplashActivity and the first selected Chaos player.
 * It lets the branded splash remain visually continuous while NativeMainActivity starts underneath.
 */
final class ChaosStartupHandoff {
    private static final Object LOCK = new Object();

    private static boolean waiting;
    private static boolean firstChaosPlayerReady;

    private ChaosStartupHandoff() {
    }

    static void begin() {
        synchronized (LOCK) {
            waiting = true;
            firstChaosPlayerReady = false;
        }
    }

    static boolean isWaiting() {
        synchronized (LOCK) {
            return waiting;
        }
    }

    static boolean isFirstChaosPlayerReady() {
        synchronized (LOCK) {
            return waiting && firstChaosPlayerReady;
        }
    }

    static void markFirstChaosPlayerReady() {
        synchronized (LOCK) {
            if (waiting) firstChaosPlayerReady = true;
        }
    }

    static void finish() {
        synchronized (LOCK) {
            waiting = false;
            firstChaosPlayerReady = false;
        }
    }
}
