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
    HomeSourceRepository() {
        this((context, source, page) -> {
            if (source == 1) return new CrazyShitRepository().fetchFeed(context, CrazyShitRepository.HOME, page);
            if (source == 2) return page == 1 ? new EfuktRepository().fetchLatest(context) : Collections.emptyList();
            WebVideoSourceRepository web = new WebVideoSourceRepository();
            if (source == 3) return web.fetchFeed(context, WebVideoSourceRepository.Source.KAOTIC, page);
            if (source == 4) return web.fetchFeed(context, WebVideoSourceRepository.Source.THEYNC, page);
            if (source == 5) return web.fetchFeed(context, WebVideoSourceRepository.Source.ITEMFIX, page);
            throw new IOException("Unknown Home source");
        }, 25_000L);
    }
    HomeSourceRepository(SourceLoader loader, long timeoutMillis) {
        this.loader = loader;
        this.timeoutMillis = timeoutMillis;
    }
    List<NativeContentItem> fetch(Context context, int source, int page) throws Exception {
        return fetch(context, source, page, null);
    }
    List<NativeContentItem> fetch(Context context, int source, int page,
                                  Consumer<List<NativeContentItem>> progress) throws Exception {
        CompletionService<List<NativeContentItem>> completed = new ExecutorCompletionService<>(IO);
        List<Future<List<NativeContentItem>>> requests = new ArrayList<>();
        int first = source == 0 ? 1 : source;
        int last = source == 0 ? 5 : source;
        for (int i = first; i <= last; i++) {
            final int selected = i;
            requests.add(completed.submit(() -> loader.fetch(context, selected, page)));
        }
        LinkedHashMap<String, NativeContentItem> visible = new LinkedHashMap<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        int reached = 0;
        try {
            for (int i = 0; i < requests.size(); i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                Future<List<NativeContentItem>> request = completed.poll(remaining, TimeUnit.NANOSECONDS);
                if (request == null) break;
                try {
                    List<NativeContentItem> items = request.get();
                    if (items == null) continue;
                    reached++;
                    for (NativeContentItem item : items) {
                        if (item != null && (source != 0 || !item.isSection())) {
                            visible.putIfAbsent(item.url, item);
                        }
                    }
                    if (progress != null && !visible.isEmpty()) progress.accept(new ArrayList<>(visible.values()));
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
}
