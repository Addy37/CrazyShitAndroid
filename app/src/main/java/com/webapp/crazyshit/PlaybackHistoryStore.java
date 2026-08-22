package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Local-only playback history and Continue Watching data. */
public final class PlaybackHistoryStore {
    private static final String PREFS = "playback_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 200;
    private static final long MIN_WATCH_MS = 30_000L;
    private static final float COMPLETE_FRACTION = 0.95f;

    private PlaybackHistoryStore() {
    }

    public static void record(
            Context context,
            String title,
            String pageUrl,
            long positionMs,
            long durationMs,
            boolean ended
    ) {
        if (context == null || pageUrl == null || pageUrl.trim().isEmpty()) return;
        if (!ended && positionMs < MIN_WATCH_MS) return;

        List<Item> items = load(context);
        items.removeIf(item -> pageUrl.equals(item.pageUrl));

        boolean complete = ended || isComplete(positionMs, durationMs);
        long safePosition = Math.max(0L, positionMs);
        long safeDuration = Math.max(0L, durationMs);
        String safeTitle = title == null || title.trim().isEmpty() ? "Video" : title.trim();

        items.add(0, new Item(
                safeTitle,
                pageUrl.trim(),
                safePosition,
                safeDuration,
                System.currentTimeMillis(),
                complete
        ));

        if (items.size() > MAX_ITEMS) {
            items = new ArrayList<>(items.subList(0, MAX_ITEMS));
        }
        save(context, items);
    }

    public static List<Item> load(Context context) {
        ArrayList<Item> items = new ArrayList<>();
        if (context == null) return items;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String pageUrl = object.optString("pageUrl", "").trim();
                if (pageUrl.isEmpty()) continue;
                items.add(new Item(
                        object.optString("title", "Video"),
                        pageUrl,
                        Math.max(0L, object.optLong("positionMs", 0L)),
                        Math.max(0L, object.optLong("durationMs", 0L)),
                        Math.max(0L, object.optLong("lastWatched", 0L)),
                        object.optBoolean("complete", false)
                ));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(items, (a, b) -> Long.compare(b.lastWatched, a.lastWatched));
        return items;
    }

    public static List<Item> continueWatching(Context context) {
        ArrayList<Item> out = new ArrayList<>();
        for (Item item : load(context)) {
            if (item.complete) continue;
            if (item.positionMs < MIN_WATCH_MS) continue;
            if (item.durationMs > 0L && isComplete(item.positionMs, item.durationMs)) continue;
            out.add(item);
        }
        return out;
    }

    public static void remove(Context context, String pageUrl) {
        if (context == null || pageUrl == null) return;
        List<Item> items = load(context);
        items.removeIf(item -> pageUrl.equals(item.pageUrl));
        save(context, items);
    }

    public static void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ITEMS)
                .apply();
    }

    private static boolean isComplete(long positionMs, long durationMs) {
        return durationMs > 0L && positionMs >= (long) (durationMs * COMPLETE_FRACTION);
    }

    private static void save(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject object = new JSONObject();
                object.put("title", item.title);
                object.put("pageUrl", item.pageUrl);
                object.put("positionMs", item.positionMs);
                object.put("durationMs", item.durationMs);
                object.put("lastWatched", item.lastWatched);
                object.put("complete", item.complete);
                array.put(object);
            }
        } catch (Exception ignored) {
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    public static final class Item {
        public final String title;
        public final String pageUrl;
        public final long positionMs;
        public final long durationMs;
        public final long lastWatched;
        public final boolean complete;

        Item(
                String title,
                String pageUrl,
                long positionMs,
                long durationMs,
                long lastWatched,
                boolean complete
        ) {
            this.title = title == null || title.trim().isEmpty() ? "Video" : title;
            this.pageUrl = pageUrl == null ? "" : pageUrl;
            this.positionMs = Math.max(0L, positionMs);
            this.durationMs = Math.max(0L, durationMs);
            this.lastWatched = Math.max(0L, lastWatched);
            this.complete = complete;
        }

        public int progressPercent() {
            if (durationMs <= 0L) return 0;
            return (int) Math.max(0L, Math.min(100L, positionMs * 100L / durationMs));
        }
    }
}
