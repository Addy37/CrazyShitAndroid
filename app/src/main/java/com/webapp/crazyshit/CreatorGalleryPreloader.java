package com.webapp.crazyshit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Warms small creator-gallery sessions for creator cards that are visible or close to visible.
 *
 * Two workers protect source traffic. The queue is bounded by reservation count so fast scrolling
 * cannot turn creator-card binding into an unbounded network backlog.
 */
final class CreatorGalleryPreloader {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final int MAX_RESERVED = 8;
    private static final int IMAGE_WARM_LIMIT = 8;
    private static final int IMAGE_CACHE_KEYS = 160;

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<String> WARMING = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger RESERVED = new AtomicInteger();

    private static final LinkedHashMap<String, Boolean> WARMED_IMAGES =
            new LinkedHashMap<String, Boolean>(IMAGE_CACHE_KEYS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > IMAGE_CACHE_KEYS;
                }
            };

    private CreatorGalleryPreloader() {
    }

    static void warm(Context context, NativeContentItem creator) {
        if (creator == null || !creator.isCreator()) return;
        String query = creator.searchQuery == null || creator.searchQuery.trim().isEmpty()
                ? creator.title
                : creator.searchQuery.trim();
        String fapelloProfile = FapelloRepository.isModelUrl(creator.url) ? creator.url : "";
        warm(context, creator.title, query, fapelloProfile);
    }

    static void warm(Context context, String creatorName, String query, String fapelloProfileUrl) {
        if (context == null) return;
        String cleanName = clean(creatorName);
        String cleanQuery = clean(query);
        if (cleanQuery.isEmpty()) cleanQuery = cleanName;
        if (cleanQuery.isEmpty()) return;

        String key = key(cleanQuery);
        String recent = BunkrGallerySessionStore.recentCreator(cleanQuery);
        if (recent != null) {
            SESSIONS.put(key, recent);
            BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(recent);
            if (snapshot != null) warmImages(context, snapshot.items);
            return;
        }
        if (SESSIONS.containsKey(key) || WARMING.contains(key)) return;
        if (!reserve()) return;
        if (!WARMING.add(key)) {
            RESERVED.decrementAndGet();
            return;
        }

        Context app = context.getApplicationContext();
        Context safeContext = app == null ? context : app;
        String finalName = cleanName.isEmpty() ? cleanQuery : cleanName;
        String finalQuery = cleanQuery;
        String finalFapelloProfile = clean(fapelloProfileUrl);

        IO.execute(() -> {
            String sessionId = "";
            try {
                String nowRecent = BunkrGallerySessionStore.recentCreator(finalQuery);
                if (nowRecent != null) {
                    SESSIONS.put(key, nowRecent);
                    BunkrGallerySessionStore.Snapshot snapshot =
                            BunkrGallerySessionStore.snapshot(nowRecent);
                    if (snapshot != null) warmImages(safeContext, snapshot.items);
                    return;
                }

                sessionId = BunkrGallerySessionStore.createCreator(
                        finalName,
                        BunkrRepository.searchUrl(finalQuery),
                        finalQuery
                );
                SESSIONS.put(key, sessionId);

                BunkrCreatorGalleryRepository repository = new BunkrCreatorGalleryRepository();
                repository.reset(sessionId, finalQuery, finalFapelloProfile, finalName);
                String activeSession = sessionId;
                repository.fetchNext(
                        safeContext,
                        activeSession,
                        finalQuery,
                        finalFapelloProfile,
                        finalName,
                        items -> {
                            if (items == null || items.isEmpty()) return;
                            BunkrGallerySessionStore.appendPreview(activeSession, items);
                            warmImages(safeContext, items);
                        }
                );

                BunkrGallerySessionStore.Snapshot snapshot =
                        BunkrGallerySessionStore.snapshot(activeSession);
                if (snapshot != null) warmImages(safeContext, snapshot.items);
            } catch (Exception ignored) {
            } finally {
                WARMING.remove(key);
                RESERVED.decrementAndGet();
                if (!sessionId.isEmpty()) {
                    BunkrGallerySessionStore.Snapshot snapshot =
                            BunkrGallerySessionStore.snapshot(sessionId);
                    if (snapshot == null || snapshot.items.isEmpty()) {
                        SESSIONS.remove(key, sessionId);
                    }
                }
            }
        });
    }

    static String sessionId(NativeContentItem creator) {
        if (creator == null) return "";
        String query = creator.searchQuery == null || creator.searchQuery.trim().isEmpty()
                ? creator.title
                : creator.searchQuery.trim();
        return sessionId(query);
    }

    static String sessionId(String query) {
        String cleanQuery = clean(query);
        if (cleanQuery.isEmpty()) return "";
        String recent = BunkrGallerySessionStore.recentCreator(cleanQuery);
        if (recent != null) return recent;
        String session = SESSIONS.get(key(cleanQuery));
        return session == null ? "" : session;
    }

    private static boolean reserve() {
        while (true) {
            int current = RESERVED.get();
            if (current >= MAX_RESERVED) return false;
            if (RESERVED.compareAndSet(current, current + 1)) return true;
        }
    }

    private static void warmImages(Context context, List<NativeContentItem> items) {
        if (context == null || items == null || items.isEmpty()) return;
        Context app = context.getApplicationContext();
        Context safeContext = app == null ? context : app;
        ArrayList<NativeContentItem> targets = new ArrayList<>();
        synchronized (WARMED_IMAGES) {
            for (NativeContentItem item : items) {
                if (item == null || item.imageUrl == null || item.imageUrl.trim().isEmpty()) continue;
                String image = item.imageUrl.trim();
                if (WARMED_IMAGES.containsKey(image)) continue;
                WARMED_IMAGES.put(image, Boolean.TRUE);
                targets.add(item);
                if (targets.size() >= IMAGE_WARM_LIMIT) break;
            }
        }
        if (targets.isEmpty()) return;

        MAIN.post(() -> {
            for (NativeContentItem item : targets) {
                try {
                    Glide.with(safeContext)
                            .load(withHeaders(item.imageUrl, imageReferer(item)))
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .dontTransform()
                            .preload(384, 384);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static GlideUrl withHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader(
                        "Referer",
                        pageUrl == null || pageUrl.trim().isEmpty()
                                ? BunkrRepository.DEFAULT_PAGE_ORIGIN + "/"
                                : pageUrl
                )
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private static String imageReferer(NativeContentItem item) {
        if (item != null && WikiFeetRepository.isWikiFeetUrl(item.url) &&
                WikiFeetRepository.isWikiFeetUrl(item.uploader)) return item.uploader;
        if (item != null && !FapelloRepository.isPostUrl(item.url) &&
                FapelloRepository.isModelUrl(item.uploader)) return item.uploader;
        if (item != null && OnlyHavenRepository.isOnlyHavenUrl(item.uploader)) {
            return item.uploader;
        }
        return item == null ? null : item.url;
    }

    private static String key(String creator) {
        String normalized = CreatorNameMatcher.normalized(creator);
        return normalized.isEmpty() ? clean(creator).toLowerCase(Locale.US) : normalized;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
