package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Name-based identity keeps a card stable as a live source fills in its metadata. */
final class OnlyFapCreatorResults {
    private OnlyFapCreatorResults() { }

    static String key(NativeContentItem item) {
        return CreatorNameMatcher.normalized(item.title);
    }

    static List<NativeContentItem> merge(List<NativeContentItem> local,
                                          List<NativeContentItem> live, int limit) {
        LinkedHashMap<String, NativeContentItem> map = new LinkedHashMap<>();
        if (local != null) for (NativeContentItem item : local) add(map, item);
        if (live != null) for (NativeContentItem item : live) add(map, item);
        ArrayList<NativeContentItem> result = new ArrayList<>(map.values());
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static void add(LinkedHashMap<String, NativeContentItem> map, NativeContentItem item) {
        if (item == null || !item.isCreator()) return;
        String key = key(item);
        if (key.isEmpty()) return;
        NativeContentItem previous = map.get(key);
        if (previous == null) { map.put(key, item); return; }
        // New source metadata takes priority; retain the established display name.
        NativeContentItem updated = item.merge(previous);
        String url = FapelloRepository.isModelUrl(previous.url)
                && !FapelloRepository.isModelUrl(item.url) ? previous.url : updated.url;
        map.put(key, new NativeContentItem(updated.kind, previous.title, url,
                updated.imageUrl, updated.views, updated.uploader, updated.comments,
                updated.description, updated.searchQuery));
    }
}
