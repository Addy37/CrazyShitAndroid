package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Persistent creator favorites used by Fapzone cards. */
final class CreatorFavoriteStore {
    private static final String PREFS = "creator_favorites";
    private static final String KEY_CREATORS = "creators";

    private CreatorFavoriteStore() {
    }

    static boolean contains(Context context, NativeContentItem creator) {
        String key = key(creator);
        if (key.isEmpty()) return false;
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_CREATORS, new HashSet<>())
                .contains(key);
    }

    static synchronized boolean toggle(Context context, NativeContentItem creator) {
        String key = key(creator);
        if (key.isEmpty()) return false;

        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(
                preferences.getStringSet(KEY_CREATORS, new HashSet<>())
        );
        boolean favorite;
        if (favorites.contains(key)) {
            favorites.remove(key);
            favorite = false;
        } else {
            favorites.add(key);
            favorite = true;
        }
        preferences.edit().putStringSet(KEY_CREATORS, favorites).apply();
        return favorite;
    }

    private static String key(NativeContentItem creator) {
        if (creator == null) return "";
        String value = clean(creator.searchQuery);
        if (value.isEmpty()) value = clean(creator.title);
        return value.toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
