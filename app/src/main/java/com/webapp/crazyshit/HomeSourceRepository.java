package com.webapp.crazyshit;

import android.content.Context;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Independent Home requests publish usable results as soon as each source completes. */
final class HomeSourceRepository {
    interface SourceLoader {
        List<NativeContentItem> fetch(Context context, int source, int page) throws Exception;
    }
    private static final ExecutorService IO = Executors.newFixedThreadPool(6);
    private final SourceLoader loader;
    private final long timeoutMillis;
    private static final long INITIAL_SOURCE_MILLIS = 2500;
    static final class FeedResult {
        final int source;
        final List<NativeContentItem> items;
        FeedResult(int source, List<NativeContentItem> items) {
            this.source = source;
            this.items = items;
        }
    }
    HomeSourceRepository() {
        this((context, source, page) -> {
            if (source == 1) return new CrazyShitRepository().fetchFeed(context, CrazyShitRepository.HOME, page);
            if (source == 2) return page == 1 ? new EfuktRepository().fetchLatest(context) : Collections.emptyList();
            WebVideoSourceRepository web = new WebVideoSourceRepository();
            if (source == 3) return web.fetchFeed(context, WebVideoSourceRepository.Source.KAOTIC, page);
            throw new IOException("Unknown Home source");
        }, 25_000L);
    }
    HomeSourceRepository(SourceLoader loader, long timeoutMillis) {
        this.loader = loader;
        this.timeoutMillis = timeoutMillis;
    }
    FeedResult fetchWithFallback(Context context, int selected, int page) throws Exception {
        if (selected != 1 || page != 1) {
            return new FeedResult(selected, fetch(context, selected, page));
        }

        Integer[] candidates = {1, 3, 2};
        Arrays.sort(candidates, Comparator.comparingInt(
                source -> SourceHealthManager.priority(sourceKey(source))));

        for (int source : candidates) {
            String healthKey = sourceKey(source);
            if (!SourceHealthManager.tryAcquire(healthKey)) continue;

            long started = System.nanoTime();
            try {
                List<NativeContentItem> items = fetchOne(context, source, 1,
                        Math.min(timeoutMillis, INITIAL_SOURCE_MILLIS));
                if (hasMedia(items)) {
                    SourceHealthManager.recordSuccess(
                            healthKey, System.nanoTime() - started);
                    return new FeedResult(source, items);
                }
                SourceHealthManager.recordFailure(healthKey);
            } catch (IOException ignored) {
                SourceHealthManager.recordFailure(healthKey);
                // Try the next source for this request only; do not change the saved selection.
            }
        }
        throw new IOException("No Home source returned usable media.");
    }
    private List<NativeContentItem> fetchOne(Context context, int source, int page,
                                               long timeout) throws Exception {
        Future<List<NativeContentItem>> request = IO.submit(() -> loader.fetch(context, source, page));
        try {
            return request.get(timeout, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException failed) {
            throw new IOException("Home source unavailable", failed);
        } finally {
            request.cancel(true);
        }
    }
    private static boolean hasMedia(List<NativeContentItem> items) {
        if (items == null) return false;
        for (NativeContentItem item : items) {
            if (item != null && NativeContentItem.KIND_MEDIA.equals(item.kind)
                    && item.url != null && !item.url.isEmpty()) return true;
        }
        return false;
    }
    List<NativeContentItem> fetch(Context context, int source, int page) throws Exception {
        return fetch(context, source, page, null);
    }
    List<NativeContentItem> fetch(Context context, int source, int page,
                                  Consumer<List<NativeContentItem>> progress) throws Exception {
        CompletionService<SourceResponse> completed = new ExecutorCompletionService<>(IO);
        List<Future<SourceResponse>> requests = new ArrayList<>();
        int first = source == 0 ? 1 : source;
        int last = source == 0 ? 3 : source;
        for (int i = first; i <= last; i++) {
            final int selected = i;
            final String healthKey = sourceKey(selected);
            // Automatic multi-source loading may skip an open circuit. An explicit source
            // selection still gets one normal attempt because the user deliberately chose it.
            if (source == 0 && !SourceHealthManager.tryAcquire(healthKey)) continue;
            requests.add(completed.submit(() -> {
                long started = System.nanoTime();
                try {
                    List<NativeContentItem> items = loader.fetch(context, selected, page);
                    if (!Thread.currentThread().isInterrupted()) {
                        if (page > 1 || hasMedia(items)) {
                            SourceHealthManager.recordSuccess(
                                    healthKey, System.nanoTime() - started);
                        } else {
                            SourceHealthManager.recordFailure(healthKey);
                        }
                    }
                    return new SourceResponse(selected, items);
                } catch (Exception failed) {
                    if (!Thread.currentThread().isInterrupted()
                            && !(failed instanceof InterruptedException)) {
                        SourceHealthManager.recordFailure(healthKey);
                    }
                    throw failed;
                }
            }));
        }
        LinkedHashMap<String, NativeContentItem> visible = new LinkedHashMap<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        int reached = 0;
        try {
            for (int i = 0; i < requests.size(); i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                Future<SourceResponse> request = completed.poll(
                        remaining, TimeUnit.NANOSECONDS);
                if (request == null) break;
                try {
                    SourceResponse response = request.get();
                    List<NativeContentItem> items = response.items;
                    if (items == null) continue;
                    reached++;
                    for (NativeContentItem item : items) {
                        if (item != null && (source != 0 || !item.isSection())) {
                            visible.putIfAbsent(item.url, item);
                        }
                    }
                    if (progress != null && !visible.isEmpty()) {
                        progress.accept(new ArrayList<>(visible.values()));
                    }
                } catch (ExecutionException unavailable) {
                    // Keep the other sources usable when one host fails.
                }
            }
        } finally {
            for (Future<?> request : requests) request.cancel(true);
        }
        if (reached == 0 || (page == 1 && visible.isEmpty())) {
            throw new IOException("No Home source returned media. Retry or select another source.");
        }
        return new ArrayList<>(visible.values());
    }

    private static String sourceKey(int source) {
        if (source == 1) return SourceHealthManager.CRAZYSHIT;
        if (source == 2) return SourceHealthManager.EFUKT;
        if (source == 3) return SourceHealthManager.KAOTIC;
        return "home-" + source;
    }

    private static final class SourceResponse {
        final int source;
        final List<NativeContentItem> items;

        SourceResponse(int source, List<NativeContentItem> items) {
            this.source = source;
            this.items = items;
        }
    }
}
