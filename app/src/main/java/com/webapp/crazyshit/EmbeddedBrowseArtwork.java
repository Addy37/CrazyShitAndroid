package com.webapp.crazyshit;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Local Series/Categories artwork captured once at build time and shipped inside the APK.
 * No thumbnail request is made to CrazyShit when these cards are displayed.
 */
final class EmbeddedBrowseArtwork {
    private static final String ASSET = "browse_artwork.json";
    private static volatile Map<String, byte[]> artwork;

    private EmbeddedBrowseArtwork() {
    }

    static byte[] get(Context context, String collectionUrl) {
        if (context == null || collectionUrl == null || collectionUrl.trim().isEmpty()) return null;
        ensureLoaded(context.getApplicationContext());
        Map<String, byte[]> local = artwork;
        return local == null ? null : local.get(normalize(collectionUrl));
    }

    static boolean has(Context context, String collectionUrl) {
        byte[] value = get(context, collectionUrl);
        return value != null && value.length > 512;
    }

    private static void ensureLoaded(Context context) {
        if (artwork != null) return;
        synchronized (EmbeddedBrowseArtwork.class) {
            if (artwork != null) return;
            HashMap<String, byte[]> loaded = new HashMap<>();
            try (InputStream input = context.getAssets().open(ASSET);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) output.write(buffer, 0, read);
                }
                String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
                JSONObject object = new JSONObject(json);
                Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String encoded = object.optString(key, "");
                    if (encoded.isEmpty()) continue;
                    try {
                        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                        if (bytes != null && bytes.length > 512) loaded.put(normalize(key), bytes);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            artwork = loaded.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(loaded);
        }
    }

    private static String normalize(String value) {
        String url = value == null ? "" : value.trim();
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        while (url.endsWith("/") && url.length() > "https://a.b/".length()) {
            url = url.substring(0, url.length() - 1);
        }
        return url.toLowerCase(Locale.US);
    }
}
