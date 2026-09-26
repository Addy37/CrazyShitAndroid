package com.webapp.crazyshit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tiny in-memory circuit breaker for remote content sources.
 *
 * It never performs I/O. Callers report the result of requests they were already making.
 * A failed source is temporarily deprioritized, then briefly skipped after repeated failures.
 */
final class SourceHealthManager {
    static final String CRAZYSHIT = "crazyshit";
    static final String KAOTIC = "kaotic";
    static final String EFUKT = "efukt";
    static final String BUNKR = "bunkr";
    static final String FAPELLO = "fapello";
    static final String ONLY_HAVEN = "onlyhaven";

    private static final int OPEN_AFTER_FAILURES = 2;
    private static final long DEGRADED_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(20);
    private static final long OPEN_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(60);

    private static final Map<String, State> STATES = new HashMap<>();

    private SourceHealthManager() { }

    /**
     * Returns false only while a source is inside its short circuit-breaker cooldown.
     * Expired cooldowns recover automatically on the next normal request.
     */
    static boolean tryAcquire(String source) {
        State state = state(source);
        synchronized (state) {
            long now = System.nanoTime();
            if (state.cooldownUntilNanos == 0L) return true;
            if (now < state.cooldownUntilNanos) return false;
            state.cooldownUntilNanos = 0L;
            return true;
        }
    }

    /**
     * Lower values are preferred. One recent failure moves a source behind sources that have not
     * recently failed, without blocking it. An open circuit sorts last and is skipped by tryAcquire.
     */
    static int priority(String source) {
        State state = state(source);
        synchronized (state) {
            long now = System.nanoTime();
            if (state.cooldownUntilNanos > now) return 2;
            if (state.consecutiveFailures > 0
                    && now - state.lastFailureNanos < DEGRADED_WINDOW_NANOS) {
                return 1;
            }
            return 0;
        }
    }

    static void recordSuccess(String source, long elapsedNanos) {
        State state = state(source);
        synchronized (state) {
            state.consecutiveFailures = 0;
            state.lastSuccessNanos = System.nanoTime();
            state.cooldownUntilNanos = 0L;
            if (elapsedNanos > 0L) {
                if (state.averageLatencyNanos == 0L) {
                    state.averageLatencyNanos = elapsedNanos;
                } else {
                    state.averageLatencyNanos =
                            ((state.averageLatencyNanos * 3L) + elapsedNanos) / 4L;
                }
            }
        }
    }

    static void recordFailure(String source) {
        State state = state(source);
        synchronized (state) {
            long now = System.nanoTime();
            state.consecutiveFailures++;
            state.lastFailureNanos = now;
            if (state.consecutiveFailures >= OPEN_AFTER_FAILURES) {
                state.cooldownUntilNanos = now + OPEN_WINDOW_NANOS;
            }
        }
    }

    static long averageLatencyMillis(String source) {
        State state = state(source);
        synchronized (state) {
            return TimeUnit.NANOSECONDS.toMillis(state.averageLatencyNanos);
        }
    }

    static int consecutiveFailures(String source) {
        State state = state(source);
        synchronized (state) {
            return state.consecutiveFailures;
        }
    }

    static void resetForTests() {
        synchronized (STATES) {
            STATES.clear();
        }
    }

    private static State state(String source) {
        String key = source == null ? "" : source;
        synchronized (STATES) {
            State state = STATES.get(key);
            if (state == null) {
                state = new State();
                STATES.put(key, state);
            }
            return state;
        }
    }

    private static final class State {
        int consecutiveFailures;
        long lastFailureNanos;
        long lastSuccessNanos;
        long averageLatencyNanos;
        long cooldownUntilNanos;
    }
}
