package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Creator selection and ordered artwork choices for the OnlyFap hero. */
final class OnlyFapHeroPolicy {
    private OnlyFapHeroPolicy() { }

    static final class Artwork {
        final String url;
        final String referer;
        final boolean avatar;

        Artwork(String url, String referer, boolean avatar) {
            this.url = url == null ? "" : url.trim();
            this.referer = referer == null ? "" : referer.trim();
            this.avatar = avatar;
        }
    }

    static Set<String> excluded(Set<String> favorites, List<NativeContentItem> trending,
            List<NativeContentItem> hot, List<NativeContentItem> popular) {
        Set<String> keys = new HashSet<>();
        if (favorites != null) keys.addAll(favorites);
        addKeys(keys, trending);
        addKeys(keys, hot);
        addKeys(keys, popular);
        return keys;
    }

    static List<NativeContentItem> select(List<NativeContentItem> newCreators,
            List<NativeContentItem> backfill, Set<String> favorites,
            List<NativeContentItem> trending, List<NativeContentItem> hot,
            List<NativeContentItem> popular, int limit) {
        Set<String> excluded = excluded(favorites, trending, hot, popular);
        LinkedHashMap<String, NativeContentItem> selected = new LinkedHashMap<>();
        append(selected, newCreators, excluded, limit);
        append(selected, backfill, excluded, limit);
        return new ArrayList<>(selected.values());
    }

    private static void addKeys(Set<String> keys, List<NativeContentItem> items) {
        if (items == null) return;
        for (NativeContentItem item : items) {
            String key = CreatorFavoriteStore.key(item);
            if (!key.isEmpty()) keys.add(key);
        }
    }

    private static void append(LinkedHashMap<String, NativeContentItem> result,
            List<NativeContentItem> items, Set<String> excluded, int limit) {
        if (items == null) return;
        for (NativeContentItem item : items) {
            if (result.size() >= limit) break;
            if (item == null || !item.isCreator()) continue;
            String key = CreatorFavoriteStore.key(item);
            if (!key.isEmpty() && !excluded.contains(key)) result.putIfAbsent(key, item);
        }
    }

    static void addMedia(List<Artwork> artwork, List<NativeContentItem> media,
            String referer) {
        if (media == null) return;
        // Actual image files precede video posters and thumbnails. Portrait images
        // remain eligible because the full-bleed ImageView can crop them cleanly.
        for (NativeContentItem item : media) {
            if (item == null || !item.isImage() || !directImage(item.url)) continue;
            artwork.add(new Artwork(item.url, referer, false));
            if (artwork.size() >= 3) return;
        }
        for (NativeContentItem item : media) {
            if (item == null || !directImage(item.imageUrl)) continue;
            artwork.add(new Artwork(item.imageUrl, referer, false));
            if (artwork.size() >= 3) return;
        }
    }

    static List<Artwork> distinctArtwork(List<Artwork> input) {
        LinkedHashMap<String, Artwork> result = new LinkedHashMap<>();
        if (input != null) for (Artwork choice : input) {
            if (choice != null && choice.url.startsWith("https://")) {
                result.putIfAbsent(choice.url, choice);
            }
        }
        return new ArrayList<>(result.values());
    }

    private static boolean directImage(String url) {
        if (url == null) return false;
        return url.toLowerCase(Locale.US)
                .matches("^https://[^?#]+\\.(?:jpg|jpeg|png|webp|avif)(?:[?#].*)?$");
    }
}
