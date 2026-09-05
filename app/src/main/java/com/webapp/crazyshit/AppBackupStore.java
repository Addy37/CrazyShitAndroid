package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class AppBackupStore {
    private AppBackupStore() { }

    static JSONObject export(Context context) throws Exception {
        JSONObject document = new JSONObject().put("format", "crazyshit-backup").put("version", 1)
                .put("createdAt", System.currentTimeMillis()).put("appVersion", BuildConfig.VERSION_NAME);
        document.put("creators", ContentItemCodec.encodeList(
                CreatorCatalog.matching(context, "", true, BackupDocument.MAX_ITEMS + 1), BackupDocument.MAX_ITEMS + 1));
        JSONArray later = new JSONArray();
        for (FavoriteStore.Item item : FavoriteStore.load(context)) later.put(new JSONObject()
                .put("title", item.title).put("url", item.url).put("savedAt", item.savedAt));
        document.put("watchLater", later);
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, ?> entry : context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getAll().entrySet()) {
            if (BackupDocument.BOOLEAN_SETTINGS.contains(entry.getKey())
                    || BackupDocument.INTEGER_SETTINGS.contains(entry.getKey()) || "chaos_preload_mode".equals(entry.getKey()))
                settings.put(entry.getKey(), entry.getValue());
        }
        document.put("settings", settings);
        BackupDocument.validate(document);
        if (document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > BackupDocument.MAX_BYTES)
            throw new IOException("Your backup is too large to export.");
        return document;
    }

    static synchronized void restore(Context context, JSONObject document) throws Exception {
        BackupDocument.validate(document);
        Set<String> favorites = CreatorFavoriteStore.names(context);
        LinkedHashMap<String, NativeContentItem> creators = new LinkedHashMap<>();
        for (NativeContentItem item : CreatorCatalog.all(context)) creators.put(CreatorCatalog.key(item), item);
        for (NativeContentItem incoming : ContentItemCodec.decodeList(document.getJSONArray("creators"), BackupDocument.MAX_ITEMS)) {
            NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_CREATOR, incoming.title,
                    incoming.url, incoming.imageUrl, "", incoming.uploader, "", "", incoming.searchQuery);
            favorites.add(CreatorFavoriteStore.key(item));
            creators.put(CreatorCatalog.key(item), item);
        }
        LinkedHashMap<String, JSONObject> watchLater = new LinkedHashMap<>();
        JSONArray incoming = document.getJSONArray("watchLater");
        for (int i = 0; i < incoming.length(); i++) {
            JSONObject item = incoming.getJSONObject(i);
            watchLater.put(item.getString("url"), item);
        }
        for (FavoriteStore.Item item : FavoriteStore.load(context)) watchLater.put(item.url, new JSONObject()
                .put("title", item.title).put("url", item.url).put("savedAt", item.savedAt));
        if (favorites.size() > BackupDocument.MAX_ITEMS || watchLater.size() > BackupDocument.MAX_ITEMS)
            throw new IOException("The combined saved lists exceed the backup limit.");
        ArrayList<JSONObject> ordered = new ArrayList<>(watchLater.values());
        ordered.sort((a, b) -> Long.compare(b.optLong("savedAt"), a.optLong("savedAt")));
        LinkedHashMap<String, Map<String, Object>> changes = new LinkedHashMap<>();
        changes.put("creator_favorites", single("creators", favorites));
        changes.put(CreatorCatalog.PREFS, single("items", ContentItemCodec.encodeList(new ArrayList<>(creators.values()), 10000).toString()));
        changes.put("watch_later", single("items", new JSONArray(ordered).toString()));
        Map<String, Object> settings = new LinkedHashMap<>();
        JSONObject values = document.getJSONObject("settings");
        java.util.Iterator<String> keys = values.keys();
        while (keys.hasNext()) { String key = keys.next(); settings.put(key, values.get(key)); }
        changes.put("app_prefs", settings);

        Map<String, Map<String, ?>> originals = new LinkedHashMap<>();
        for (String name : changes.keySet()) originals.put(name, context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll());
        try {
            for (Map.Entry<String, Map<String, Object>> change : changes.entrySet()) {
                SharedPreferences.Editor editor = context.getSharedPreferences(change.getKey(), Context.MODE_PRIVATE).edit();
                write(editor, change.getValue());
                if (!editor.commit()) throw new IOException("Couldn't save the restored data.");
            }
        } catch (Exception failure) {
            for (Map.Entry<String, Map<String, ?>> old : originals.entrySet()) {
                SharedPreferences.Editor editor = context.getSharedPreferences(old.getKey(), Context.MODE_PRIVATE).edit().clear();
                write(editor, old.getValue()); editor.commit();
            }
            throw failure;
        }
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>(); map.put(key, value); return map;
    }

    @SuppressWarnings("unchecked")
    private static void write(SharedPreferences.Editor editor, Map<String, ?> values) {
        for (Map.Entry<String, ?> value : values.entrySet()) {
            Object item = value.getValue(); String key = value.getKey();
            if (item instanceof Boolean) editor.putBoolean(key, (Boolean) item);
            else if (item instanceof Integer) editor.putInt(key, (Integer) item);
            else if (item instanceof Long) editor.putLong(key, (Long) item);
            else if (item instanceof Float) editor.putFloat(key, (Float) item);
            else if (item instanceof String) editor.putString(key, (String) item);
            else if (item instanceof Set) editor.putStringSet(key, new HashSet<>((Set<String>) item));
        }
    }
}
