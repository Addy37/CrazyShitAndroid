package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared gallery state, with media and paging cursors saved in one atomic snapshot. */
final class BunkrGallerySessionStore {
    private static final int MAX_SESSIONS = 4;
    private static final LinkedHashMap<String, Session> SESSIONS = new LinkedHashMap<>();

    static final class Snapshot {
        final String title;
        final String albumUrl;
        final String creatorQuery;
        final ArrayList<NativeContentItem> items;
        final int currentPage;
        final boolean endReached;
        final Map<String, String> resolvedUrls;
        final String cursor;

        Snapshot(Session source) {
            title = source.title;
            albumUrl = source.albumUrl;
            creatorQuery = source.creatorQuery;
            items = new ArrayList<>(source.items);
            currentPage = source.currentPage;
            endReached = source.endReached;
            resolvedUrls = new LinkedHashMap<>(source.resolvedUrls);
            cursor = source.cursor;
        }
    }

    private static final class Session {
        final String title;
        final String albumUrl;
        final String creatorQuery;
        final ArrayList<NativeContentItem> items = new ArrayList<>();
        final LinkedHashMap<String, String> resolvedUrls = new LinkedHashMap<>();
        int currentPage;
        boolean endReached;
        String cursor = "";

        Session(String title, String albumUrl, String creatorQuery) {
            this.title = title == null ? "OnlyFap gallery" : title;
            this.albumUrl = albumUrl == null ? "" : albumUrl;
            this.creatorQuery = creatorQuery == null ? "" : creatorQuery.trim();
        }
    }

    private BunkrGallerySessionStore() {
    }

    static synchronized String create(String title, String albumUrl) {
        return createInternal(title, albumUrl, "");
    }

    static synchronized String createCreator(String title, String albumUrl, String creatorQuery) {
        return createInternal(title, albumUrl, creatorQuery);
    }

    /** Reuse a warm, completed snapshot for a repeat visit within this process. */
    static synchronized String recentCreator(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String[] ids = SESSIONS.keySet().toArray(new String[0]);
        for (int i = ids.length - 1; i >= 0; i--) {
            Session session = SESSIONS.get(ids[i]);
            if (session != null && query.equalsIgnoreCase(session.creatorQuery)
                    && !session.items.isEmpty() && !session.cursor.isEmpty()) return ids[i];
        }
        return null;
    }

    private static String createInternal(String title, String albumUrl, String creatorQuery) {
        String id = UUID.randomUUID().toString();
        SESSIONS.put(id, new Session(title, albumUrl, creatorQuery));
        trim();
        return id;
    }

    static synchronized void replace(
            String id,
            List<NativeContentItem> items,
            int currentPage,
            boolean endReached
    ) {
        Session session = SESSIONS.get(id);
        if (session == null) return;
        session.items.clear();
        addUnique(session.items, items);
        session.currentPage = Math.max(0, currentPage);
        session.endReached = endReached;
    }

    static synchronized int append(
            String id,
            List<NativeContentItem> items,
            int currentPage,
            boolean endReached
    ) {
        Session session = SESSIONS.get(id);
        if (session == null) return 0;
        int before = session.items.size();
        addUnique(session.items, items);
        session.currentPage = Math.max(session.currentPage, currentPage);
        session.endReached = endReached;
        return session.items.size() - before;
    }

    static synchronized void updatePaging(String id, int currentPage, boolean endReached) {
        Session session = SESSIONS.get(id);
        if (session == null) return;
        session.currentPage = Math.max(session.currentPage, currentPage);
        session.endReached = endReached;
    }

    /** Make early media available to the fullscreen viewer before the batch cursor is saved. */
    static synchronized void appendPreview(String id, List<NativeContentItem> items) {
        Session session = SESSIONS.get(id);
        if (session != null) addUnique(session.items, items);
    }

    static synchronized void setResolvedUrl(String id, String pageUrl, String mediaUrl) {
        Session session = SESSIONS.get(id);
        if (session == null || pageUrl == null || pageUrl.isEmpty() ||
                mediaUrl == null || mediaUrl.isEmpty()) return;
        session.resolvedUrls.put(pageUrl, mediaUrl);
    }

    static synchronized void clearResolvedUrl(String id, String pageUrl) {
        Session session = SESSIONS.get(id);
        if (session == null || pageUrl == null || pageUrl.isEmpty()) return;
        session.resolvedUrls.remove(pageUrl);
    }

    static synchronized Snapshot snapshot(String id) {
        Session session = SESSIONS.get(id);
        return session == null ? null : new Snapshot(session);
    }

    static synchronized void recordCreatorBatch(android.content.Context context, String id,
            List<NativeContentItem> items, boolean endReached, org.json.JSONObject cursor) {
        Session session = SESSIONS.get(id);
        if (session == null) {
            restore(context, id);
            session = SESSIONS.get(id);
        }
        if (session == null) {
            String query = cursor.optString("query");
            session = new Session(query, BunkrRepository.searchUrl(query), query);
            SESSIONS.put(id, session);
            trim();
        }
        addUnique(session.items, items);
        session.currentPage++;
        session.endReached = endReached;
        session.cursor = cursor.toString();
        persist(context, id);
    }

    static synchronized void persist(android.content.Context context, String id) {
        Snapshot snapshot = snapshot(id);
        if (snapshot == null) return;
        android.content.Context app = context.getApplicationContext();
        SNAPSHOT_IO.execute(() -> {
            try {
                org.json.JSONObject value = new org.json.JSONObject()
                        .put("title", snapshot.title).put("album", snapshot.albumUrl)
                        .put("query", snapshot.creatorQuery).put("page", snapshot.currentPage)
                        .put("end", snapshot.endReached)
                        .put("items", ContentItemCodec.encodeList(snapshot.items, 10000));
                if (!snapshot.cursor.isEmpty()) value.put("cursor", new org.json.JSONObject(snapshot.cursor));
                ScreenSnapshotStore.save(app, id, value);
            } catch (Exception ignored) { }
        });
    }

    static Snapshot restore(android.content.Context context, String id) {
        Snapshot warm = snapshot(id);
        if (warm != null) return warm;
        org.json.JSONObject value = ScreenSnapshotStore.read(context, id);
        if (value == null) return null;
        synchronized (BunkrGallerySessionStore.class) {
            Snapshot newer = snapshot(id);
            if (newer != null) return newer;
            Session restored = new Session(value.optString("title"), value.optString("album"), value.optString("query"));
            restored.items.addAll(ContentItemCodec.decodeList(value.optJSONArray("items"), 10000));
            restored.currentPage = value.optInt("page");
            restored.endReached = value.optBoolean("end");
            org.json.JSONObject cursor = value.optJSONObject("cursor");
            restored.cursor = cursor == null ? "" : cursor.toString();
            SESSIONS.put(id, restored);
            trim();
            return new Snapshot(restored);
        }
    }

    private static final java.util.concurrent.ExecutorService SNAPSHOT_IO = java.util.concurrent.Executors.newSingleThreadExecutor();

    private static void addUnique(
            ArrayList<NativeContentItem> destination,
            List<NativeContentItem> incoming
    ) {
        if (incoming == null) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (NativeContentItem existing : destination) seen.add(existing.url);
        for (NativeContentItem candidate : incoming) {
            if (candidate != null && !candidate.url.isEmpty() && seen.add(candidate.url)) destination.add(candidate);
        }
    }

    private static void trim() {
        while (SESSIONS.size() > MAX_SESSIONS) {
            String oldest = SESSIONS.keySet().iterator().next();
            SESSIONS.remove(oldest);
        }
    }
}
