package com.webapp.crazyshit;

/**
 * Tiny process-local bridge between SplashActivity and the first Chaos video frame.
 * It lets the branded splash remain visually continuous while NativeMainActivity starts underneath.
 */
final class ChaosStartupHandoff {
    private static final Object LOCK = new Object();

    private static boolean waiting;
    private static boolean firstFrameReady;

    private ChaosStartupHandoff() {
    }

    static void begin() {
        synchronized (LOCK) {
            waiting = true;
            firstFrameReady = false;
        }
    }

    static boolean isWaiting() {
        synchronized (LOCK) {
            return waiting;
        }
    }

    static boolean isFirstFrameReady() {
        synchronized (LOCK) {
            return waiting && firstFrameReady;
        }
    }

    static void markFirstFrameReady() {
        synchronized (LOCK) {
            if (waiting) firstFrameReady = true;
        }
    }

    static void finish() {
        synchronized (LOCK) {
            waiting = false;
            firstFrameReady = false;
        }
    }
}
