package com.webapp.crazyshit;

import android.content.Context;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Home source filters use the same parsers and playback routing as the rest of the app. */
final class HomeSourceRepository {
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    List<NativeContentItem> fetch(Context context, int source, int page) throws Exception {
        if (source == 1) return new CrazyShitRepository().fetchFeed(context, CrazyShitRepository.HOME, page);
        if (source == 2) return page == 1 ? new EfuktRepository().fetchLatest(context) : Collections.emptyList();
        if (source == 3) return new FapelloRepository().fetchPopularVideos(context, page);
        List<Future<List<NativeContentItem>>> requests = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int selected = i;
            requests.add(IO.submit(() -> fetch(context, selected, page)));
        }
        List<List<NativeContentItem>> groups = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        int reached = 0;
        try {
            for (Future<List<NativeContentItem>> request : requests) {
                try {
                    List<NativeContentItem> items = request.isDone() ? request.get()
                            : request.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    if (items != null) { groups.add(items); reached++; }
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw interrupted; }
                catch (Exception unavailable) { }
            }
        } finally { for (Future<?> request : requests) request.cancel(true); }
        if (reached == 0) throw new IOException("Home sources could not be reached");
        LinkedHashMap<String, NativeContentItem> mixed = new LinkedHashMap<>();
        int count = 0;
        for (List<?> group : groups) count = Math.max(count, group.size());
        for (int row = 0; row < count; row++) for (List<NativeContentItem> group : groups) {
            if (row < group.size()) {
                NativeContentItem item = group.get(row);
                if (item != null && !item.isSection()) mixed.putIfAbsent(item.url, item);
            }
        }
        return new ArrayList<>(mixed.values());
    }
}
