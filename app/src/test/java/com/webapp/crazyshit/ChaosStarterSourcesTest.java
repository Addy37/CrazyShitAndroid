package com.webapp.crazyshit;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChaosStarterSourcesTest {
    @Before public void resetHealth() {
        SourceHealthManager.resetForTests();
    }

    private NativeContentItem media(String url) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video", url, "", "", "", "");
    }

    @Test public void slowCrazyShitDoesNotHoldBackKaotic() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        long started = System.nanoTime();
        try {
            List<NativeContentItem> queue = ChaosStarterSources.first(source -> {
                if (source == ChaosStarterSources.CRAZYSHIT) {
                    release.await(5, TimeUnit.SECONDS);
                    throw new IOException("offline");
                }
                if (source == ChaosStarterSources.EFUKT) throw new IOException("offline");
                return Arrays.asList(media("https://kaotic.com/one"), media("https://kaotic.com/two"));
            }, new Random(1), 800);
            assertEquals(2, queue.size());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1500);
        } finally {
            release.countDown();
        }
    }

    @Test public void efuktWorksWhenCrazyShitAndKaoticFail() throws Exception {
        List<NativeContentItem> queue = ChaosStarterSources.first(source -> {
            if (source == ChaosStarterSources.EFUKT) return Collections.singletonList(media("https://efukt.com/one"));
            throw new IOException("offline");
        }, new Random(2), 800);
        assertEquals("https://efukt.com/one", queue.get(0).url);
    }
}
