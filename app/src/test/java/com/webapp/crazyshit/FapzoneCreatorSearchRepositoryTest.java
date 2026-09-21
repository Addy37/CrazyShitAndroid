package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class FapzoneCreatorSearchRepositoryTest {
    @Test public void publishesFastSourceBeforeSlowSourceCompletes() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstPublished = new CountDownLatch(1);

        List<FapzoneCreatorSearchRepository.SourceSearch> sources = Arrays.asList(
                (ignoredContext, query, limit) ->
                        Collections.singletonList(creator("Fast Creator", "Fapello")),
                (ignoredContext, query, limit) -> {
                    try {
                        assertTrue(firstPublished.await(2, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException(interrupted);
                    }
                    return Collections.singletonList(creator("Slow Creator", "OnlyHaven"));
                }
        );

        FapzoneCreatorSearchRepository repository =
                new FapzoneCreatorSearchRepository(executor, sources);
        List<Integer> publishedSizes = new ArrayList<>();
        List<Boolean> completionStates = new ArrayList<>();

        try {
            List<NativeContentItem> finalItems = repository.search(
                    context,
                    "creator",
                    10,
                    (items, complete) -> {
                        publishedSizes.add(items.size());
                        completionStates.add(complete);
                        if (!complete && items.size() == 1) firstPublished.countDown();
                    }
            );

            assertEquals(Arrays.asList(1, 2), publishedSizes);
            assertEquals(Arrays.asList(false, true), completionStates);
            assertEquals(2, finalItems.size());
            assertEquals("Fast Creator", finalItems.get(0).title);
            assertEquals("Slow Creator", finalItems.get(1).title);
        } finally {
            executor.shutdownNow();
        }
    }

    private NativeContentItem creator(String name, String source) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                name,
                "https://example.com/" + name.replace(" ", "-").toLowerCase(),
                "",
                "",
                "",
                "",
                source,
                name
        );
    }
}
