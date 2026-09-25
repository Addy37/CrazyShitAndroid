package com.webapp.crazyshit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SourceHealthManagerTest {
    @Before public void resetHealth() {
        SourceHealthManager.resetForTests();
    }

    @Test public void firstFailureDeprioritizesWithoutBlocking() {
        SourceHealthManager.recordFailure(SourceHealthManager.CRAZYSHIT);

        assertEquals(1, SourceHealthManager.priority(SourceHealthManager.CRAZYSHIT));
        assertTrue(SourceHealthManager.tryAcquire(SourceHealthManager.CRAZYSHIT));
        assertEquals(1, SourceHealthManager.consecutiveFailures(SourceHealthManager.CRAZYSHIT));
    }

    @Test public void repeatedFailuresOpenShortCircuit() {
        SourceHealthManager.recordFailure(SourceHealthManager.CRAZYSHIT);
        SourceHealthManager.recordFailure(SourceHealthManager.CRAZYSHIT);

        assertEquals(2, SourceHealthManager.priority(SourceHealthManager.CRAZYSHIT));
        assertFalse(SourceHealthManager.tryAcquire(SourceHealthManager.CRAZYSHIT));
    }

    @Test public void successRestoresSourceImmediately() {
        SourceHealthManager.recordFailure(SourceHealthManager.KAOTIC);
        SourceHealthManager.recordFailure(SourceHealthManager.KAOTIC);
        assertFalse(SourceHealthManager.tryAcquire(SourceHealthManager.KAOTIC));

        SourceHealthManager.recordSuccess(
                SourceHealthManager.KAOTIC, java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(450));

        assertEquals(0, SourceHealthManager.priority(SourceHealthManager.KAOTIC));
        assertTrue(SourceHealthManager.tryAcquire(SourceHealthManager.KAOTIC));
        assertEquals(0, SourceHealthManager.consecutiveFailures(SourceHealthManager.KAOTIC));
        assertEquals(450, SourceHealthManager.averageLatencyMillis(SourceHealthManager.KAOTIC));
    }

    @Test public void homeUsesHealthyFallbackFirstAfterFailure() throws Exception {
        List<Integer> calls = new ArrayList<>();
        HomeSourceRepository repository = new HomeSourceRepository((context, source, page) -> {
            calls.add(source);
            if (source == 1) throw new IOException("offline");
            if (source == 3) {
                return Collections.singletonList(media("https://kaotic.com/working"));
            }
            return Collections.emptyList();
        }, 500);

        HomeSourceRepository.FeedResult first = repository.fetchWithFallback(null, 1, 1);
        assertEquals(3, first.source);
        assertEquals(java.util.Arrays.asList(1, 3), calls);

        calls.clear();
        HomeSourceRepository.FeedResult second = repository.fetchWithFallback(null, 1, 1);
        assertEquals(3, second.source);
        assertEquals(Collections.singletonList(3), calls);
    }

    private NativeContentItem media(String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Video", url, "", "", "", "");
    }
}
