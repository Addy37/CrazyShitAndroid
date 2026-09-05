package com.webapp.crazyshit;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Versioned, allowlisted backup format. Validate the entire document before changing data. */
final class BackupDocument {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ITEMS = 5000;
    static final Set<String> BOOLEAN_SETTINGS = new HashSet<>(Arrays.asList(
            "native_player_enabled", "player_auto_pip", "remember_video_position", "oled_black_enabled",
            "ambient_feed_glow", "immersive_motion_enabled", "ad_blocking_enabled", "haptics_enabled",
            "new_video_notifications_enabled", "crazyshit_notifications_enabled", "efukt_notifications_enabled",
            "notification_show_video_titles"));
    static final Set<String> INTEGER_SETTINGS = new HashSet<>(Arrays.asList(
            "native_view_home", "native_view_collection", "notification_check_frequency_hours"));

    private BackupDocument() { }

    static JSONObject read(InputStream input) throws Exception {
        if (input == null) throw new IOException("Couldn't open the backup file.");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk)) != -1) {
            if (buffer.size() + count > MAX_BYTES) throw new IOException("This backup is too large.");
            buffer.write(chunk, 0, count);
        }
        JSONObject document = new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
        validate(document);
        return document;
    }

    static void validate(JSONObject document) throws Exception {
        if (!"crazyshit-backup".equals(document.optString("format"))
                || !(document.opt("version") instanceof Integer) || document.getInt("version") != 1)
            throw new IOException("This isn't a supported CrazyShit backup.");
        JSONArray creators = document.getJSONArray("creators");
        JSONArray later = document.getJSONArray("watchLater");
        if (creators.length() > MAX_ITEMS || later.length() > MAX_ITEMS)
            throw new IOException("This backup has too many saved items.");
        for (int i = 0; i < creators.length(); i++) {
            JSONObject creator = creators.getJSONObject(i);
            requireText(creator, "name", 250, false);
            requireText(creator, "query", 250, false);
            requireUrl(creator.getString("url"), true);
            requireUrl(creator.getString("image"), true);
            requireUrl(creator.optString("referer"), true);
        }
        for (int i = 0; i < later.length(); i++) {
            JSONObject item = later.getJSONObject(i);
            requireText(item, "title", 1000, false);
            requireUrl(item.getString("url"), false);
            if (!(item.opt("savedAt") instanceof Number) || item.getLong("savedAt") < 0)
                throw new IOException("A saved item's date is invalid.");
        }
        JSONObject settings = document.getJSONObject("settings");
        Iterator<String> keys = settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = settings.get(key);
            if (BOOLEAN_SETTINGS.contains(key) && value instanceof Boolean) continue;
            if (INTEGER_SETTINGS.contains(key) && value instanceof Integer) {
                int number = (Integer) value;
                if ("notification_check_frequency_hours".equals(key) && (number == 1 || number == 3 || number == 6)) continue;
                if (!"notification_check_frequency_hours".equals(key) && number >= 0 && number <= 3) continue;
            }
            if ("chaos_preload_mode".equals(key) && ("full".equals(value) || "unmetered".equals(value) || "minimal".equals(value))) continue;
            throw new IOException("The backup contains an unsupported setting: " + key);
        }
    }

    private static void requireText(JSONObject object, String key, int limit, boolean allowEmpty) throws Exception {
        Object raw = object.get(key);
        if (!(raw instanceof String)) throw new IOException("Invalid " + key + " in backup.");
        String value = (String) raw;
        if ((!allowEmpty && value.trim().isEmpty()) || value.length() > limit)
            throw new IOException("Invalid " + key + " in backup.");
    }

    private static void requireUrl(String value, boolean allowEmpty) throws Exception {
        if (allowEmpty && value.isEmpty()) return;
        if (value.length() > 4096) throw new IOException("A backup link is too long.");
        URI uri = new URI(value);
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IOException("A backup contains an invalid web link.");
    }
}
