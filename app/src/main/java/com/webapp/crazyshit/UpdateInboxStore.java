package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local, bounded inbox for source activity. Content updates stay in-app instead of spamming Android. */
final class UpdateInboxStore {
    static final String CATEGORY_ONLYFAP = "onlyfap";
    static final String CATEGORY_VIDEOS = "videos";
    static final String CATEGORY_APP = "app";

    private static final String PREFS = "zerochill_update_inbox_v1";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 200;
    private static final Object LOCK = new Object();

    private UpdateInboxStore() {
    }

    static void record(Context context, List<NotificationCoordinator.SourceAlert> alerts) {
        if (context == null || alerts == null || alerts.isEmpty()) return;
        synchronized (LOCK) {
            ArrayList<Entry> incoming = buildEntries(context.getApplicationContext(), alerts);
            if (incoming.isEmpty()) return;

            ArrayList<Entry> combined = new ArrayList<>(incoming);
            combined.addAll(readLocked(context));
            dedupeAndTrim(combined);
            writeLocked(context, combined);
        }
    }

    static void recordAppUpdate(
            Context context,
            String version,
            String title,
            boolean beta
    ) {
        if (context == null || version == null || version.trim().isEmpty()) return;
        synchronized (LOCK) {
            Entry entry = new Entry();
            entry.timestamp = System.currentTimeMillis();
            entry.category = CATEGORY_APP;
            entry.sourceKey = "app";
            entry.sourceLabel = beta ? "Beta" : "Stable";
            entry.title = title == null || title.trim().isEmpty()
                    ? "ZeroChill " + version.trim()
                    : title.trim();
            entry.subtitle = "App update " + version.trim() + " is ready";
            entry.appVersion = version.trim();
            entry.count = 1;
            entry.fingerprint = fingerprint(CATEGORY_APP, entry.appVersion, Collections.emptyList());
            entry.id = entry.fingerprint + ":" + entry.timestamp;

            ArrayList<Entry> existing = readLocked(context);
            for (Entry current : existing) {
                if (entry.fingerprint.equals(current.fingerprint)) return;
            }
            ArrayList<Entry> combined = new ArrayList<>();
            combined.add(entry);
            combined.addAll(existing);
            dedupeAndTrim(combined);
            writeLocked(context, combined);
        }
    }

    static List<Entry> all(Context context) {
        synchronized (LOCK) {
            ArrayList<Entry> entries = readLocked(context);
            if (pruneNonFavoriteContent(context, entries)) writeLocked(context, entries);
            return new ArrayList<>(entries);
        }
    }

    static List<Entry> filtered(Context context, String category) {
        ArrayList<Entry> result = new ArrayList<>();
        for (Entry entry : all(context)) {
            if (category == null || category.isEmpty() || category.equals(entry.category)) {
                result.add(entry);
            }
        }
        return result;
    }

    static int unreadCount(Context context) {
        int count = 0;
        for (Entry entry : all(context)) if (!entry.read) count++;
        return count;
    }

    static void markRead(Context context, String id) {
        if (id == null || id.trim().isEmpty()) return;
        synchronized (LOCK) {
            ArrayList<Entry> entries = readLocked(context);
            boolean changed = false;
            for (Entry entry : entries) {
                if (id.equals(entry.id) && !entry.read) {
                    entry.read = true;
                    changed = true;
                    break;
                }
            }
            if (changed) writeLocked(context, entries);
        }
    }

    static void markAllRead(Context context) {
        synchronized (LOCK) {
            ArrayList<Entry> entries = readLocked(context);
            boolean changed = false;
            for (Entry entry : entries) {
                if (!entry.read) {
                    entry.read = true;
                    changed = true;
                }
            }
            if (changed) writeLocked(context, entries);
        }
    }

    private static ArrayList<Entry> buildEntries(
            Context context,
            List<NotificationCoordinator.SourceAlert> alerts
    ) {
        long now = System.currentTimeMillis();
        ArrayList<Entry> result = new ArrayList<>();
        LinkedHashMap<String, CreatorAccumulator> creators = new LinkedHashMap<>();
        int sequence = 0;

        for (NotificationCoordinator.SourceAlert alert : alerts) {
            if (alert == null || alert.items == null || alert.items.isEmpty()) continue;
            String sourceKey = clean(alert.key).toLowerCase(Locale.US);
            if (!isOnlyFapSource(sourceKey)) continue;

            for (NativeContentItem item : alert.items) {
                if (item == null || item.url == null || item.url.trim().isEmpty()) continue;
                NotificationCoordinator.ExperienceItem wrapped =
                        new NotificationCoordinator.ExperienceItem(sourceKey, item);
                String creator = NotificationCoordinator.onlyFapCreatorName(wrapped);
                if (creator.isEmpty() || !isFavoriteCreator(context, creator)) continue;

                String key = CreatorNameMatcher.normalized(creator);
                CreatorAccumulator acc = creators.get(key);
                if (acc == null) {
                    acc = new CreatorAccumulator(creator);
                    creators.put(key, acc);
                }
                acc.add(sourceKey, item);
            }
        }

        for (CreatorAccumulator acc : creators.values()) {
            result.add(creatorEntry(context, now + sequence++, acc));
        }

        Collections.sort(result, (left, right) -> Long.compare(right.timestamp, left.timestamp));
        return result;
    }

    private static Entry creatorEntry(Context context, long timestamp, CreatorAccumulator acc) {
        Entry entry = new Entry();
        entry.timestamp = timestamp;
        entry.category = CATEGORY_ONLYFAP;
        entry.sourceKey = "onlyfap";
        entry.sourceLabel = "OnlyFap";
        entry.creatorName = acc.name;
        entry.title = acc.name;
        entry.count = acc.items.size();
        entry.videoCount = acc.videoCount;
        entry.subtitle = acc.videoCount == acc.items.size() && entry.count > 0
                ? entry.count + (entry.count == 1 ? " new video" : " new videos")
                : entry.count == 1 ? "New content" : entry.count + " new items";
        entry.freshUrls.addAll(acc.urls);
        entry.items.addAll(acc.items);
        applyCreatorMetadata(context, entry, acc);
        entry.fingerprint = fingerprint(entry.category, entry.creatorName, entry.freshUrls);
        entry.id = entry.fingerprint + ":" + timestamp;
        return entry;
    }

    private static void applyCreatorMetadata(
            Context context,
            Entry entry,
            CreatorAccumulator acc
    ) {
        String wanted = CreatorNameMatcher.normalized(entry.creatorName);
        for (NativeContentItem candidate : CreatorCatalog.all(context)) {
            if (!wanted.equals(CreatorNameMatcher.normalized(candidate.title)) &&
                    !wanted.equals(CreatorNameMatcher.normalized(candidate.searchQuery))) continue;
            if (entry.avatarUrl.isEmpty() && candidate.imageUrl != null &&
                    !candidate.imageUrl.trim().isEmpty()) {
                entry.avatarUrl = candidate.imageUrl.trim();
                entry.avatarReferer = candidate.uploader == null || candidate.uploader.trim().isEmpty()
                        ? clean(candidate.url)
                        : candidate.uploader.trim();
            }
            if (entry.fapelloProfileUrl.isEmpty() &&
                    FapelloRepository.isModelUrl(candidate.url)) {
                entry.fapelloProfileUrl = candidate.url.trim();
            }
        }

        if (entry.avatarUrl.isEmpty()) {
            for (SourceItem sourceItem : acc.sourceItems) {
                if (!"onlyhaven".equals(sourceItem.sourceKey)) continue;
                NativeContentItem item = sourceItem.item;
                if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
                    entry.avatarUrl = item.imageUrl.trim();
                    entry.avatarReferer = clean(item.url);
                    break;
                }
            }
        }
    }

    private static String fingerprint(String category, String subject, List<String> urls) {
        ArrayList<String> stable = new ArrayList<>();
        if (urls != null) stable.addAll(urls);
        Collections.sort(stable);
        return Integer.toHexString(
                (clean(category) + "|" + clean(subject) + "|" + stable.toString()).hashCode()
        );
    }

    private static void dedupeAndTrim(ArrayList<Entry> entries) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        java.util.Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry == null || entry.fingerprint.isEmpty() || !seen.add(entry.fingerprint)) {
                iterator.remove();
            }
        }
        Collections.sort(entries, (left, right) -> Long.compare(right.timestamp, left.timestamp));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
    }

    private static ArrayList<Entry> readLocked(Context context) {
        ArrayList<Entry> result = new ArrayList<>();
        try {
            String encoded = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_ENTRIES, "[]");
            JSONArray values = new JSONArray(encoded);
            for (int i = 0; i < Math.min(values.length(), MAX_ENTRIES); i++) {
                JSONObject value = values.optJSONObject(i);
                Entry entry = Entry.decode(value);
                if (entry != null) result.add(entry);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void writeLocked(Context context, List<Entry> entries) {
        JSONArray values = new JSONArray();
        for (Entry entry : entries) {
            if (entry == null) continue;
            try {
                values.put(entry.encode());
            } catch (Exception ignored) {
            }
            if (values.length() >= MAX_ENTRIES) break;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, values.toString())
                .apply();
    }

    private static boolean isOnlyFapSource(String key) {
        return "fapello".equals(key) || "bunkr".equals(key) || "onlyhaven".equals(key);
    }

    static boolean isFavoriteCreator(Context context, String creatorName) {
        if (context == null || creatorName == null || creatorName.trim().isEmpty()) return false;
        String wanted = CreatorNameMatcher.normalized(creatorName);
        if (wanted.isEmpty()) return false;
        for (String favorite : CreatorFavoriteStore.names(context)) {
            if (wanted.equals(CreatorNameMatcher.normalized(favorite))) return true;
        }
        return false;
    }

    private static boolean pruneNonFavoriteContent(Context context, ArrayList<Entry> entries) {
        boolean changed = false;
        java.util.Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry == null) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (CATEGORY_APP.equals(entry.category)) continue;
            boolean keepFavoriteCreator = CATEGORY_ONLYFAP.equals(entry.category)
                    && !entry.creatorName.isEmpty()
                    && isFavoriteCreator(context, entry.creatorName);
            if (!keepFavoriteCreator) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Entry {
        String id = "";
        String fingerprint = "";
        String category = "";
        String sourceKey = "";
        String sourceLabel = "";
        String title = "";
        String subtitle = "";
        String creatorName = "";
        String avatarUrl = "";
        String avatarReferer = "";
        String fapelloProfileUrl = "";
        String appVersion = "";
        long timestamp;
        int count;
        int videoCount;
        boolean read;
        final ArrayList<String> freshUrls = new ArrayList<>();
        final ArrayList<NativeContentItem> items = new ArrayList<>();

        JSONObject encode() throws Exception {
            JSONObject value = new JSONObject()
                    .put("id", id)
                    .put("fingerprint", fingerprint)
                    .put("category", category)
                    .put("sourceKey", sourceKey)
                    .put("sourceLabel", sourceLabel)
                    .put("title", title)
                    .put("subtitle", subtitle)
                    .put("creatorName", creatorName)
                    .put("avatarUrl", avatarUrl)
                    .put("avatarReferer", avatarReferer)
                    .put("fapelloProfileUrl", fapelloProfileUrl)
                    .put("appVersion", appVersion)
                    .put("timestamp", timestamp)
                    .put("count", count)
                    .put("videoCount", videoCount)
                    .put("read", read)
                    .put("freshUrls", new JSONArray(freshUrls))
                    .put("items", ContentItemCodec.encodeList(items, 1));
            return value;
        }

        static Entry decode(JSONObject value) {
            if (value == null) return null;
            Entry entry = new Entry();
            entry.id = value.optString("id", "");
            entry.fingerprint = value.optString("fingerprint", "");
            entry.category = value.optString("category", "");
            entry.sourceKey = value.optString("sourceKey", "");
            entry.sourceLabel = value.optString("sourceLabel", "");
            entry.title = value.optString("title", "");
            entry.subtitle = value.optString("subtitle", "");
            entry.creatorName = value.optString("creatorName", "");
            entry.avatarUrl = value.optString("avatarUrl", "");
            entry.avatarReferer = value.optString("avatarReferer", "");
            entry.fapelloProfileUrl = value.optString("fapelloProfileUrl", "");
            entry.appVersion = value.optString("appVersion", "");
            entry.timestamp = value.optLong("timestamp", 0L);
            entry.count = value.optInt("count", 0);
            entry.videoCount = value.optInt("videoCount", 0);
            entry.read = value.optBoolean("read", false);
            JSONArray urls = value.optJSONArray("freshUrls");
            if (urls != null) {
                for (int i = 0; i < urls.length(); i++) {
                    String url = urls.optString(i, "").trim();
                    if (!url.isEmpty()) entry.freshUrls.add(url);
                }
            }
            entry.items.addAll(ContentItemCodec.decodeList(value.optJSONArray("items"), 1));
            if (entry.fingerprint.isEmpty()) {
                entry.fingerprint = fingerprint(entry.category, entry.title, entry.freshUrls);
            }
            if (entry.id.isEmpty()) entry.id = entry.fingerprint + ":" + entry.timestamp;
            return entry;
        }

        NativeContentItem firstItem() {
            return items.isEmpty() ? null : items.get(0);
        }
    }

    private static final class CreatorAccumulator {
        final String name;
        final ArrayList<NativeContentItem> items = new ArrayList<>();
        final ArrayList<String> urls = new ArrayList<>();
        final ArrayList<SourceItem> sourceItems = new ArrayList<>();
        int videoCount;

        CreatorAccumulator(String name) {
            this.name = clean(name);
        }

        void add(String sourceKey, NativeContentItem item) {
            items.add(item);
            sourceItems.add(new SourceItem(sourceKey, item));
            if (!urls.contains(item.url.trim())) urls.add(item.url.trim());
            if (item.isVideo()) videoCount++;
        }
    }

    private static final class SourceItem {
        final String sourceKey;
        final NativeContentItem item;

        SourceItem(String sourceKey, NativeContentItem item) {
            this.sourceKey = sourceKey;
            this.item = item;
        }
    }
}
