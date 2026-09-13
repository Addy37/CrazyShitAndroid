package com.webapp.crazyshit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RatingFeedbackPromptTest {
    private static final long NOW = 2_000_000_000L;

    @Test
    public void becomesEligibleAtAllThreeUsageThresholds() {
        assertTrue(eligible(
                RatingFeedbackPrompt.REQUIRED_SESSIONS,
                RatingFeedbackPrompt.REQUIRED_ACTIVE_MS,
                RatingFeedbackPrompt.REQUIRED_PLAYBACKS,
                0,
                false,
                0L,
                0L));
    }

    @Test
    public void requiresSessionsTimeAndSuccessfulPlayback() {
        assertFalse(eligible(2, RatingFeedbackPrompt.REQUIRED_ACTIVE_MS,
                RatingFeedbackPrompt.REQUIRED_PLAYBACKS, 0, false, 0L, 0L));
        assertFalse(eligible(RatingFeedbackPrompt.REQUIRED_SESSIONS,
                RatingFeedbackPrompt.REQUIRED_ACTIVE_MS - 1,
                RatingFeedbackPrompt.REQUIRED_PLAYBACKS, 0, false, 0L, 0L));
        assertFalse(eligible(RatingFeedbackPrompt.REQUIRED_SESSIONS,
                RatingFeedbackPrompt.REQUIRED_ACTIVE_MS,
                RatingFeedbackPrompt.REQUIRED_PLAYBACKS - 1, 0, false, 0L, 0L));
    }

    @Test
    public void respectsSnoozeDismissalCompletionAndErrorCooldown() {
        assertFalse(eligible(3, RatingFeedbackPrompt.REQUIRED_ACTIVE_MS, 5,
                0, false, NOW + 1, 0L));
        assertFalse(eligible(3, RatingFeedbackPrompt.REQUIRED_ACTIVE_MS, 5,
                2, false, 0L, 0L));
        assertFalse(eligible(3, RatingFeedbackPrompt.REQUIRED_ACTIVE_MS, 5,
                0, true, 0L, 0L));
        assertFalse(eligible(3, RatingFeedbackPrompt.REQUIRED_ACTIVE_MS, 5,
                0, false, 0L, NOW - RatingFeedbackPrompt.ERROR_COOLDOWN_MS + 1));
        assertTrue(eligible(3, RatingFeedbackPrompt.REQUIRED_ACTIVE_MS, 5,
                0, false, 0L, NOW - RatingFeedbackPrompt.ERROR_COOLDOWN_MS));
    }

    private boolean eligible(
            int sessions,
            long activeMs,
            int playbacks,
            int dismissals,
            boolean completed,
            long snoozeUntil,
            long lastErrorAt
    ) {
        return RatingFeedbackPrompt.isEligibleState(
                sessions, activeMs, playbacks, dismissals, completed,
                snoozeUntil, lastErrorAt, NOW);
    }
}
