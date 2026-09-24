package com.webapp.crazyshit;

import android.content.Context;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Warms the existing unified creator gallery while a creator-backed ShitTok clip is nearby. */
final class ShitTokCreatorGalleryPreloader {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<String> WARMING = ConcurrentHashMap.newKeySet();

    private ShitTokCreatorGalleryPreloader() {
    }

    static void warm(Context context, NativeContentItem item) {
        String creator = ShitTokCreatorMetadata.creatorName(item);
        if (creator.isEmpty() || context == null) return;

        String key = key(creator);
        String recent = BunkrGallerySessionStore.recentCreator(creator);
        if (recent != null) {
            SESSIONS.put(key, recent);
            return;
        }
        if (!WARMING.add(key)) return;

        Context app = context.getApplicationContext();
        Context safeContext = app != null ? app : context;
        String sessionId = BunkrGallerySessionStore.createCreator(
                creator,
                BunkrRepository.searchUrl(creator),
                creator
        );
        SESSIONS.put(key, sessionId);

        IO.execute(() -> {
            BunkrCreatorGalleryRepository repository = new BunkrCreatorGalleryRepository();
            repository.reset(sessionId, creator, "", creator);
            try {
                repository.fetchNext(
                        safeContext,
                        sessionId,
                        creator,
                        "",
                        creator,
                        items -> BunkrGallerySessionStore.appendPreview(sessionId, items)
                );
            } catch (Exception ignored) {
            } finally {
                WARMING.remove(key);
            }
        });
    }

    static String sessionId(String creator) {
        if (creator == null || creator.trim().isEmpty()) return "";
        String recent = BunkrGallerySessionStore.recentCreator(creator.trim());
        if (recent != null) return recent;
        String session = SESSIONS.get(key(creator));
        return session == null ? "" : session;
    }

    private static String key(String creator) {
        return creator == null ? "" : creator.trim().toLowerCase(Locale.US);
    }
}
