package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small in-memory handoff between an album grid and its full-screen media viewer. */
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

        Snapshot(Session source) {
            title = source.title;
            albumUrl = source.albumUrl;
            creatorQuery = source.creatorQuery;
            items = new ArrayList<>(source.items);
            currentPage = source.currentPage;
            endReached = source.endReached;
            resolvedUrls = new LinkedHashMap<>(source.resolvedUrls);
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

        Session(String title, String albumUrl, String creatorQuery) {
            this.title = title == null ? "Fapzone gallery" : title;
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

    private static void addUnique(
            ArrayList<NativeContentItem> destination,
            List<NativeContentItem> incoming
    ) {
        if (incoming == null) return;
        for (NativeContentItem candidate : incoming) {
            if (candidate == null || candidate.url == null || candidate.url.isEmpty()) continue;
            boolean duplicate = false;
            for (NativeContentItem existing : destination) {
                if (candidate.url.equals(existing.url)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) destination.add(candidate);
        }
    }

    private static void trim() {
        while (SESSIONS.size() > MAX_SESSIONS) {
            String oldest = SESSIONS.keySet().iterator().next();
            SESSIONS.remove(oldest);
        }
    }
}
