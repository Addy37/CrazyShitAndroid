package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creator metadata learned from real listings and search responses, plus existing favorites. */
final class CreatorCatalog {
    static final String PREFS = "creator_catalog_v1";
    private static final int MAX_CACHED = 600;
    private static final String[] SHELVES = {"popular_creator_feed_v3", "popular_creator_feed_v2",
            "fapzone_creator_feed_v1_1", "fapzone_creator_feed_v1_2", "fapzone_creator_feed_v1_3"};

    private CreatorCatalog() { }

    static String key(NativeContentItem item) {
        // Search can ignore accents and punctuation; stored creator identities must not.
        return CreatorFavoriteStore.key(item);
    }

    static List<NativeContentItem> all(Context context) {
        LinkedHashMap<String, NativeContentItem> result = read(context);
        // Older versions saved favorite names only. Keep every one usable after upgrading.
        for (String name : CreatorFavoriteStore.names(context)) {
            NativeContentItem legacy = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    name, "", "", "", "", "", "", name);
            result.putIfAbsent(key(legacy), legacy);
        }
        return new ArrayList<>(result.values());
    }

    static List<NativeContentItem> matching(Context context, String query, boolean favoritesOnly, int limit) {
        Set<String> favorites = CreatorFavoriteStore.names(context);
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (NativeContentItem item : all(context)) {
            if (favoritesOnly && !favorites.contains(CreatorFavoriteStore.key(item))) continue;
            if (CreatorNameMatcher.rank(item.title, query) != Integer.MAX_VALUE) result.add(item);
        }
        Comparator<NativeContentItem> names = Comparator.comparing(
                item -> CreatorNameMatcher.normalized(item.title));
        if (favoritesOnly || query.trim().isEmpty()) result.sort(names);
        else result.sort(Comparator.<NativeContentItem>comparingInt(
                        item -> CreatorNameMatcher.rank(item.title, query))
                .thenComparingInt(item -> favorites.contains(CreatorFavoriteStore.key(item)) ? 0 : 1)
                .thenComparing(names));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    static synchronized void remember(Context context, List<NativeContentItem> items) {
        LinkedHashMap<String, NativeContentItem> records = read(context);
        for (NativeContentItem item : items) {
            if (item == null || !item.isCreator() || item.title.trim().isEmpty()) continue;
            String key = key(item);
            NativeContentItem old = records.remove(key);
            records.put(key, old == null ? item : item.merge(old));
        }
        Set<String> favorites = CreatorFavoriteStore.names(context);
        java.util.Iterator<Map.Entry<String, NativeContentItem>> iterator = records.entrySet().iterator();
        while (records.size() > MAX_CACHED && iterator.hasNext()) {
            if (!favorites.contains(CreatorFavoriteStore.key(iterator.next().getValue()))) iterator.remove();
        }
        try {
            context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("items", ContentItemCodec.encodeList(new ArrayList<>(records.values()), 5000).toString())
                    .apply();
        } catch (Exception ignored) { }
    }

    private static LinkedHashMap<String, NativeContentItem> read(Context context) {
        LinkedHashMap<String, NativeContentItem> out = new LinkedHashMap<>();
        for (String shelf : SHELVES) readInto(context, shelf, out);
        readInto(context, PREFS, out);
        return out;
    }

    private static void readInto(Context context, String prefs, Map<String, NativeContentItem> out) {
        try {
            JSONArray values = new JSONArray(context.getApplicationContext()
                    .getSharedPreferences(prefs, Context.MODE_PRIVATE).getString("items", "[]"));
            for (NativeContentItem item : ContentItemCodec.decodeList(values, 5000)) {
                if (!item.title.trim().isEmpty() && item.isCreator()) out.put(key(item), item);
            }
        } catch (Exception ignored) { }
    }

    static NativeContentItem fromModel(FapelloRepository.Model model) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, model.name, model.url,
                model.imageUrl, "", model.url, "", "Fapzone", model.name);
    }
}
