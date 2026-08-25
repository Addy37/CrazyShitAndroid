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
 * Normal site feeds are parsed with the repository. Shit Show is intentionally separate because
 * it is a JavaScript-driven swipe player, not a normal /cnt/medias/ listing.
 */
final class ChaosSourceMixer {
    private static final int MAX_SOURCE_PAGE = 8;
    private static final int SOURCES_PER_BATCH = 6;
    private static final int SHIT_SHOW_PER_BATCH = 8;
    private static final String VIDEOS = CrazyShitRepository.BASE + "videos/";
    private static final String USER_UPLOADS = CrazyShitRepository.BASE + "submissions/";

    private final CrazyShitRepository repository;
    private final Random random;
    private final ShitShowTapSource shitShow = new ShitShowTapSource();
    private final ArrayList<String> catalog = new ArrayList<>();
    private final ArrayDeque<String> sourceDeck = new ArrayDeque<>();
    private final Set<String> usedSourcePages = new HashSet<>();
    private boolean catalogLoaded;

    ChaosSourceMixer(CrazyShitRepository repository, Random random) {
        this.repository = repository;
        this.random = random;
    }

    List<NativeContentItem> loadRandomBatch(Context context) {
        // Start the rendered Shit Show page before the normal source work so it can harvest in
        // parallel while the existing broad Chaos deck keeps doing what already tested well.
        shitShow.prewarm(context);
        ensureCatalog(context);

        LinkedHashMap<String, NativeContentItem> regular = new LinkedHashMap<>();
        int sourceCount = Math.min(SOURCES_PER_BATCH, Math.max(2, catalog.size()));
        for (int i = 0; i < sourceCount; i++) {
            addRequest(context, regular, nextRequest());
        }

        ArrayList<NativeContentItem> regularItems = new ArrayList<>(regular.values());
        Collections.shuffle(regularItems, random);

        ArrayList<NativeContentItem> shitShowItems = new ArrayList<>();
        for (NativeContentItem item : shitShow.takeBatchOrWarm(context, SHIT_SHOW_PER_BATCH)) {
            if (item == null || item.url == null || item.url.isEmpty()) continue;
            if (regular.containsKey(item.url)) continue;
            shitShowItems.add(item);
        }
        Collections.shuffle(shitShowItems, random);

        return weaveShitShow(regularItems, shitShowItems);
    }

    void resetDeck() {
        sourceDeck.clear();
        usedSourcePages.clear();
        shitShow.resetDeck();
    }

    private List<NativeContentItem> weaveShitShow(
            List<NativeContentItem> regularItems,
            List<NativeContentItem> shitShowItems
    ) {
        if (shitShowItems == null || shitShowItems.isEmpty()) {
            return regularItems == null ? new ArrayList<>() : new ArrayList<>(regularItems);
        }

        ArrayList<NativeContentItem> regular = regularItems == null
                ? new ArrayList<>()
                : new ArrayList<>(regularItems);
        ArrayList<NativeContentItem> result = new ArrayList<>(regular.size() + shitShowItems.size());

        // Keep the normal Chaos order randomized, but make a harvested Shit Show clip visible soon
        // enough to be meaningful. Beta.10 shuffled eight possible clips into a potentially huge
        // regular batch, so successful harvesting could still look like a total extractor failure.
        int regularIndex = 0;
        int nextShitAfter = 2 + random.nextInt(4); // first one after 2-5 regular clips
        for (NativeContentItem shit : shitShowItems) {
            int copied = 0;
            while (regularIndex < regular.size() && copied < nextShitAfter) {
                result.add(regular.get(regularIndex++));
                copied++;
            }
            result.add(shit);
            nextShitAfter = 5 + random.nextInt(6); // then keep a loose 5-10 clip spacing
        }

        while (regularIndex < regular.size()) result.add(regular.get(regularIndex++));
        return result;
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
