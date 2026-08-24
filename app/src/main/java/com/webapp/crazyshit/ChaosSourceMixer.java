package com.webapp.crazyshit;

import android.content.Context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds randomized Chaos batches from the site's broad video catalog.
 *
 * Shit Show gets one dedicated randomized source request per batch so it cannot disappear inside
 * a large category deck. The rest of each batch still rotates through Home, Trending, Videos,
 * User Uploads and every category currently exposed by the site.
 */
final class ChaosSourceMixer {
    private static final int MAX_SOURCE_PAGE = 8;
    private static final int SOURCES_PER_BATCH = 6;
    private static final String VIDEOS = CrazyShitRepository.BASE + "videos/";
    private static final String SHIT_SHOW = CrazyShitRepository.BASE + "shitshow/";
    private static final String USER_UPLOADS = CrazyShitRepository.BASE + "submissions/";

    private final CrazyShitRepository repository;
    private final Random random;
    private final ArrayList<String> catalog = new ArrayList<>();
    private final ArrayDeque<String> sourceDeck = new ArrayDeque<>();
    private final Set<String> usedSourcePages = new HashSet<>();
    private boolean catalogLoaded;

    ChaosSourceMixer(CrazyShitRepository repository, Random random) {
        this.repository = repository;
        this.random = random;
    }

    List<NativeContentItem> loadRandomBatch(Context context) {
        ensureCatalog(context);
        LinkedHashMap<String, NativeContentItem> combined = new LinkedHashMap<>();

        // Always sample Shit Show once per batch. It used to be just one entry in a large source
        // deck, which made it possible to swipe for a long time without ever seeing it.
        addRequest(context, combined, nextRequestFor(SHIT_SHOW));

        int sourceCount = Math.min(SOURCES_PER_BATCH, Math.max(2, catalog.size()));
        for (int i = 0; i < sourceCount; i++) {
            addRequest(context, combined, nextRequest());
        }

        ArrayList<NativeContentItem> result = new ArrayList<>(combined.values());
        Collections.shuffle(result, random);
        return result;
    }

    void resetDeck() {
        sourceDeck.clear();
        usedSourcePages.clear();
    }

    private void ensureCatalog(Context context) {
        if (catalogLoaded) return;

        LinkedHashSet<String> urls = new LinkedHashSet<>();
        urls.add(CrazyShitRepository.HOME);
        urls.add(CrazyShitRepository.TRENDING);
        urls.add(VIDEOS);
        urls.add(USER_UPLOADS);

        try {
            for (NativeContentItem category : repository.fetchCategories(context)) {
                if (category == null || category.url == null || category.url.trim().isEmpty()) continue;
                urls.add(category.url.trim());
            }
        } catch (Exception ignored) {
            // Core sources above keep Chaos working if the categories index is temporarily down.
        }

        catalog.clear();
        catalog.addAll(urls);
        catalogLoaded = true;
        refillDeck();
    }

    private void addRequest(Context context,
                            LinkedHashMap<String, NativeContentItem> combined,
                            SourceRequest request) {
        if (request == null) return;
        try {
            for (NativeContentItem item : repository.fetchFeed(context, request.url, request.page)) {
                if (item == null || item.url == null || item.url.isEmpty()) continue;
                if (!NativeContentItem.KIND_MEDIA.equals(item.kind)) continue;
                combined.putIfAbsent(item.url, item);
            }
        } catch (Exception ignored) {
            // A randomly selected page may not exist for a smaller category. Other sources in the
            // batch still contribute and the next draw will choose a different source/page pair.
        }
    }

    private SourceRequest nextRequestFor(String url) {
        if (url == null || url.isEmpty()) return null;
        for (int i = 0; i < MAX_SOURCE_PAGE * 2; i++) {
            int page = 1 + random.nextInt(MAX_SOURCE_PAGE);
            String key = url + "#" + page;
            if (usedSourcePages.add(key)) return new SourceRequest(url, page);
        }

        // All recent pages for this dedicated source were already sampled. Allow a new randomized
        // cycle without clearing the unrelated source history.
        for (int page = 1; page <= MAX_SOURCE_PAGE; page++) {
            usedSourcePages.remove(url + "#" + page);
        }
        int page = 1 + random.nextInt(MAX_SOURCE_PAGE);
        usedSourcePages.add(url + "#" + page);
        return new SourceRequest(url, page);
    }

    private SourceRequest nextRequest() {
        if (catalog.isEmpty()) return null;

        int attempts = Math.max(16, catalog.size() * 3);
        for (int i = 0; i < attempts; i++) {
            if (sourceDeck.isEmpty()) refillDeck();
            String url = sourceDeck.pollFirst();
            if (url == null || url.isEmpty()) continue;

            int page = 1 + random.nextInt(MAX_SOURCE_PAGE);
            String key = url + "#" + page;
            if (usedSourcePages.add(key)) return new SourceRequest(url, page);
        }

        // Once most source/page combinations have been sampled, start a fresh randomized cycle.
        usedSourcePages.clear();
        if (sourceDeck.isEmpty()) refillDeck();
        String url = sourceDeck.pollFirst();
        if (url == null || url.isEmpty()) return null;
        int page = 1 + random.nextInt(MAX_SOURCE_PAGE);
        usedSourcePages.add(url + "#" + page);
        return new SourceRequest(url, page);
    }

    private void refillDeck() {
        if (catalog.isEmpty()) return;
        ArrayList<String> shuffled = new ArrayList<>(catalog);
        Collections.shuffle(shuffled, random);
        sourceDeck.addAll(shuffled);
    }

    private static final class SourceRequest {
        final String url;
        final int page;

        SourceRequest(String url, int page) {
            this.url = url;
            this.page = page;
        }
    }
}
