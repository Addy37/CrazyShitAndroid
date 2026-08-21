package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class FavoriteStore {
    private static final String PREFS = "watch_later";
    private static final String KEY_ITEMS = "items";

    private FavoriteStore() {
    }

    public static List<Item> load(Context context) {
        List<Item> items = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String url = object.optString("url", "");
                if (url.isEmpty()) continue;
                items.add(new Item(
                        object.optString("title", "Saved video"),
                        url,
                        object.optLong("savedAt", 0L)
                ));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public static void add(Context context, String title, String url) {
        if (url == null || url.trim().isEmpty()) return;
        List<Item> items = load(context);
        items.removeIf(item -> item.url.equals(url));
        items.add(0, new Item(
                title == null || title.trim().isEmpty() ? "Saved video" : title.trim(),
                url,
                System.currentTimeMillis()
        ));
        save(context, items);
    }

    public static void remove(Context context, String url) {
        List<Item> items = load(context);
        items.removeIf(item -> item.url.equals(url));
        save(context, items);
    }

    public static boolean contains(Context context, String url) {
        if (url == null) return false;
        for (Item item : load(context)) {
            if (url.equals(item.url)) return true;
        }
        return false;
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ITEMS)
                .apply();
    }

    private static void save(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject object = new JSONObject();
                object.put("title", item.title);
                object.put("url", item.url);
                object.put("savedAt", item.savedAt);
                array.put(object);
            }
        } catch (Exception ignored) {
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    public static final class Item {
        public final String title;
        public final String url;
        public final long savedAt;

        public Item(String title, String url, long savedAt) {
            this.title = title;
            this.url = url;
            this.savedAt = savedAt;
        }
    }
}
