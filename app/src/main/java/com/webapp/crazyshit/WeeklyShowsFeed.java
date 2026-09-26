package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

final class WeeklyShowsFeed {
    static final int WINDOW_DAYS = 7;
    static final int MAX_ITEMS = 30;

    private WeeklyShowsFeed() {
    }

    static List<NativeContentItem> build(List<NativeContentItem> source, long nowMillis) {
        LinkedHashMap<String, NativeContentItem> unique = new LinkedHashMap<>();
        if (source != null) {
            for (NativeContentItem item : source) {
                if (item == null || !item.isVideo() || item.url == null || item.url.isEmpty()) continue;
                if (!SourcePublishedDate.isWithinLastDays(
                        item.publishedAtMillis,
                        nowMillis,
                        WINDOW_DAYS
                )) continue;
                NativeContentItem old = unique.get(item.url);
                unique.put(item.url, old == null ? item : old.merge(item));
            }
        }

        ArrayList<NativeContentItem> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong(
                (NativeContentItem item) -> item.publishedAtMillis
        ).reversed());
        if (result.size() > MAX_ITEMS) {
            return new ArrayList<>(result.subList(0, MAX_ITEMS));
        }
        return result;
    }

    static String sourceLabel(NativeContentItem item) {
        if (item == null || item.url == null) return "";
        if (EfuktRepository.isEfuktUrl(item.url)) return "EFUKT";
        if (WebVideoSourceRepository.isKaoticUrl(item.url)) return "KAOTIC";
        return "CRAZYSHIT";
    }
}
