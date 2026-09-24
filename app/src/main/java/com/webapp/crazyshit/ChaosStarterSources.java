package com.webapp.crazyshit;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** A bounded race for the first playable ShitTok feed. */
final class ChaosStarterSources {
    interface Loader {
        List<NativeContentItem> load(int source) throws Exception;
    }

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    static final long STARTUP_MILLIS = 2500;
    static final int CRAZYSHIT = 1, KAOTIC = 2, EFUKT = 3;

    private ChaosStarterSources() { }

    static Loader live(Context context, CrazyShitRepository crazyShit, Random random) {
        String[] urls = {CrazyShitRepository.HOME, CrazyShitRepository.TRENDING,
                CrazyShitRepository.BASE + "videos/", CrazyShitRepository.BASE + "submissions/"};
        String url = urls[random.nextInt(urls.length)];
        return source -> {
            if (source == CRAZYSHIT) return crazyShit.fetchFeed(context, url, 1);
            if (source == KAOTIC) return new WebVideoSourceRepository().fetchFeed(
                    context, WebVideoSourceRepository.Source.KAOTIC, 1);
            return new EfuktRepository().fetchLatest(context);
        };
    }

    static List<NativeContentItem> first(Loader loader, Random random, long budgetMillis)
            throws InterruptedException {
        CompletionService<List<NativeContentItem>> completions = new ExecutorCompletionService<>(IO);
        ArrayList<Future<?>> requests = new ArrayList<>();
        for (int source : new int[]{CRAZYSHIT, KAOTIC, EFUKT}) {
            final int selected = source;
            requests.add(completions.submit(() -> loader.load(selected)));
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
        LinkedHashMap<String, NativeContentItem> playable = new LinkedHashMap<>();
        try {
            for (int pending = requests.size(); pending > 0; pending--) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                Future<List<NativeContentItem>> done = completions.poll(remaining, TimeUnit.NANOSECONDS);
                if (done == null) break;
                try {
                    List<NativeContentItem> results = done.get();
                    if (results == null) continue;
                    for (NativeContentItem item : results) {
                        if (item != null && NativeContentItem.KIND_MEDIA.equals(item.kind)
                                && item.url != null && !item.url.isEmpty()) {
                            playable.putIfAbsent(item.url, item);
                        }
                    }
                    if (playable.size() >= ChaosStartupPreloader.STARTER_ITEMS) break;
                } catch (java.util.concurrent.ExecutionException ignored) {
                    // The other two feeds may still be usable.
                }
            }
            ArrayList<NativeContentItem> queue = new ArrayList<>(playable.values());
            Collections.shuffle(queue, random);
            return new ArrayList<>(queue.subList(0,
                    Math.min(ChaosStartupPreloader.STARTER_ITEMS, queue.size())));
        } finally {
            for (Future<?> request : requests) request.cancel(true);
        }
    }
}
