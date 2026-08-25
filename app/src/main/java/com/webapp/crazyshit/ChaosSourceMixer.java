package com.webapp.crazyshit;

import android.content.Context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int REGULAR_ITEMS_PER_SOURCE = 4;
    private static final int SHIT_SHOW_PER_BATCH = 24;
    private static final int STARTER_ITEMS = 6;
    private static final String VIDEOS = CrazyShitRepository.BASE + "videos/";
    private static final String USER_UPLOADS = CrazyShitRepository.BASE + "submissions/";

    private final CrazyShitRepository repository;
    private final Random random;
    private final ShitShowTapSource shitShow = new ShitShowTapSource();
    private final ArrayList<String> catalog = new ArrayList<>();
    private final ArrayDeque<String> sourceDeck = new ArrayDeque<>();
    private final Set<String> usedSourcePages = new HashSet<>();
    private boolean catalogLoaded;
    private boolean starterPending = true;

    ChaosSourceMixer(CrazyShitRepository repository, Random random) {
        this.repository = repository;
        this.random = random;
    }

    List<NativeContentItem> loadRandomBatch(Context context) {
        // Cold-start optimization: warm Shit Show immediately, but let the first Chaos request
        // return from one known regular source instead of waiting for the full six-source catalog
        // and rendered Shit Show cache. The splash now preloads this same tiny pool while its intro
        // is visible, so the mixer can usually hand the first items to Chaos without another fetch.
        shitShow.prewarm(context);
        if (starterPending) {
            starterPending = false;

            List<NativeContentItem> preloaded = ChaosStartupPreloader.takeStarter();
            if (!preloaded.isEmpty()) return preloaded;

            List<NativeContentItem> starter = loadStarterBatch(context);
            if (!starter.isEmpty()) return starter;
        }

        // Keep all six regular source slots so Home/Trending/Videos/User Uploads/categories remain
        // broad, but cap each source at four clips. Pair that with up to 24 Shit Show stories so
        // the finished Chaos batch is intentionally close to a 50/50 mix.
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
        starterPending = true;
        shitShow.resetDeck();
    }

    private List<NativeContentItem> loadStarterBatch(Context context) {
        ArrayList<String> starterSources = new ArrayList<>(Arrays.asList(
                CrazyShitRepository.HOME,
                CrazyShitRepository.TRENDING,
                VIDEOS,
                USER_UPLOADS
        ));
        Collections.shuffle(starterSources, random);

        // Usually the first source succeeds. Fall through only when a source is temporarily empty
        // or unavailable so startup still has a reliable escape hatch without loading the catalog.
        for (String url : starterSources) {
            try {
                ArrayList<NativeContentItem> candidates = new ArrayList<>();
                for (NativeContentItem item : repository.fetchFeed(context, url, 1)) {
                    if (item == null || item.url == null || item.url.isEmpty()) continue;
                    if (!NativeContentItem.KIND_MEDIA.equals(item.kind)) continue;
                    candidates.add(item);
                }
                if (candidates.isEmpty()) continue;
                Collections.shuffle(candidates, random);
                int take = Math.min(STARTER_ITEMS, candidates.size());
                return new ArrayList<>(candidates.subList(0, take));
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
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
        ArrayList<NativeContentItem> shit = new ArrayList<>(shitShowItems);
        ArrayList<NativeContentItem> result = new ArrayList<>(regular.size() + shit.size());

        int regularIndex = 0;
        int shitIndex = 0;
        boolean shitNext = random.nextBoolean();

        // Alternate sources while both are available. Randomizing which side starts keeps refreshes
        // from feeling scripted while still making Shit Show appear roughly every other swipe.
        while (regularIndex < regular.size() && shitIndex < shit.size()) {
            if (shitNext) {
                result.add(shit.get(shitIndex++));
            } else {
                result.add(regular.get(regularIndex++));
            }
            shitNext = !shitNext;
        }

        while (shitIndex < shit.size()) result.add(shit.get(shitIndex++));
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
            ArrayList<NativeContentItem> candidates = new ArrayList<>();
            for (NativeContentItem item : repository.fetchFeed(context, request.url, request.page)) {
                if (item == null || item.url == null || item.url.isEmpty()) continue;
                if (!NativeContentItem.KIND_MEDIA.equals(item.kind)) continue;
                candidates.add(item);
            }
            Collections.shuffle(candidates, random);
            int take = Math.min(REGULAR_ITEMS_PER_SOURCE, candidates.size());
            for (int i = 0; i < take; i++) {
                NativeContentItem item = candidates.get(i);
                combined.putIfAbsent(item.url, item);
            }
        } catch (Exception ignored) {
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
