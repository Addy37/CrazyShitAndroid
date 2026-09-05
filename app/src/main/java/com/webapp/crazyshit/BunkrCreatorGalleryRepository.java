package com.webapp.crazyshit;

import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Incrementally combines Bunkr, Fapello, WikiFeet and WikiFeet X media for Fapzone. */
final class BunkrCreatorGalleryRepository {
    private static final int MAX_SESSIONS = 4;
    private static final int MAX_SEARCH_PAGES = 5;
    private static final int ALBUMS_PER_BATCH = 4;
    private static final int FAPELLO_MODELS_PER_BATCH = 2;
    private static final int FAPELLO_MODEL_LIMIT = 4;
    private static final int WIKIFEET_CREATORS_PER_BATCH = 2;
    private static final int WIKIFEET_CREATOR_LIMIT = 4;
    private static final int WIKIFEET_PAGE_SIZE = 24;
    private static final int MAX_FAPELLO_PAGES = 250;
    private static final int BATCH_TARGET = 48;
    private static final int MAX_MEDIA_ITEMS = 10_000;
    private static final long ALBUM_BATCH_BUDGET_MS = 16_000L;
    private static final ExecutorService ALBUM_IO = Executors.newFixedThreadPool(6);
    private static final LinkedHashMap<String, State> STATES =
            new LinkedHashMap<>(8, 0.75f, true);

    static final class Batch {
        final ArrayList<NativeContentItem> items;
        final boolean endReached;

        Batch(ArrayList<NativeContentItem> items, boolean endReached) {
            this.items = items;
            this.endReached = endReached;
        }
    }

    void reset(String sessionId, String query) {
        if (sessionId == null || sessionId.trim().isEmpty()) return;
        synchronized (STATES) {
            STATES.put(sessionId, new State(query));
            trimLocked();
        }
    }

    Batch fetchNext(Context context, String sessionId, String query) throws IOException {
        State state = state(context, sessionId, query);
        synchronized (state) {
            Batch batch = fetchNextLocked(context.getApplicationContext(), state);
            saveCursor(context, sessionId, state, batch);
            return batch;
        }
    }

    private Batch fetchNextLocked(Context context, State state) throws IOException {
        if (state.finished()) return new Batch(new ArrayList<>(), true);

        IOException bunkrCatalogError = null;
        try {
            loadAlbumsIfNeeded(context, state);
            state.bunkrSearchFailures = 0;
        } catch (IOException error) {
            bunkrCatalogError = error;
            state.bunkrSearchFailures++;
            if (state.bunkrSearchFailures >= 2) state.searchFinished = true;
        }

        IOException fapelloCatalogError = null;
        try {
            loadFapelloModelsIfNeeded(context, state);
            state.fapelloSearchFailures = 0;
        } catch (IOException error) {
            fapelloCatalogError = error;
            state.fapelloSearchFailures++;
            if (state.fapelloSearchFailures >= 2) state.fapelloCatalogLoaded = true;
        }

        IOException wikiFeetCatalogError = loadWikiFeetCatalog(context, state);

        ArrayList<AlbumCursor> selected = new ArrayList<>();
        while (!state.pending.isEmpty() && selected.size() < ALBUMS_PER_BATCH) {
            selected.add(state.pending.removeFirst());
        }

        ArrayList<FapelloCursor> selectedFapello = new ArrayList<>();
        while (!state.fapelloPending.isEmpty() &&
                selectedFapello.size() < FAPELLO_MODELS_PER_BATCH) {
            selectedFapello.add(state.fapelloPending.removeFirst());
        }

        ArrayList<WikiFeetCursor> selectedWikiFeet = new ArrayList<>();
        while (!state.wikiFeetPending.isEmpty() &&
                selectedWikiFeet.size() < WIKIFEET_CREATORS_PER_BATCH) {
            selectedWikiFeet.add(state.wikiFeetPending.removeFirst());
        }

        ExecutorCompletionService<AlbumPage> completed =
                new ExecutorCompletionService<>(ALBUM_IO);
        LinkedHashMap<Future<AlbumPage>, AlbumCursor> requests = new LinkedHashMap<>();
        BunkrRepository repository = new BunkrRepository();
        for (AlbumCursor cursor : selected) {
            Future<AlbumPage> request = completed.submit(() -> {
                try {
                    return new AlbumPage(
                            cursor,
                            repository.fetchAlbum(context, cursor.albumUrl, cursor.nextPage),
                            null
                    );
                } catch (Exception error) {
                    return new AlbumPage(cursor, new ArrayList<>(), error);
                }
            });
            requests.put(request, cursor);
        }

        LinkedHashMap<Future<FapelloPage>, FapelloCursor> fapelloRequests =
                new LinkedHashMap<>();
        for (FapelloCursor cursor : selectedFapello) {
            Future<FapelloPage> request = ALBUM_IO.submit(() -> {
                try {
                    return new FapelloPage(
                            cursor,
                            new FapelloRepository().fetchModelMedia(
                                    context,
                                    cursor.model,
                                    cursor.nextPage
                            ),
                            null
                    );
                } catch (Exception error) {
                    return new FapelloPage(cursor, new ArrayList<>(), error);
                }
            });
            fapelloRequests.put(request, cursor);
        }

        LinkedHashMap<Future<WikiFeetPage>, WikiFeetCursor> wikiFeetRequests =
                new LinkedHashMap<>();
        for (WikiFeetCursor cursor : selectedWikiFeet) {
            Future<WikiFeetPage> request = ALBUM_IO.submit(() -> {
                try {
                    return new WikiFeetPage(
                            cursor,
                            new WikiFeetRepository().fetchCreatorMedia(
                                    context, cursor.creator, cursor.nextPage, WIKIFEET_PAGE_SIZE),
                            null
                    );
                } catch (Exception error) {
                    return new WikiFeetPage(cursor, new ArrayList<>(), error);
                }
            });
            wikiFeetRequests.put(request, cursor);
        }

        ArrayList<NativeContentItem> bunkrResult = new ArrayList<>();
        Set<Future<AlbumPage>> finishedRequests = new HashSet<>();
        long deadline = SystemClock.elapsedRealtime() + ALBUM_BATCH_BUDGET_MS;
        for (int i = 0; i < requests.size(); i++) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) break;
            Future<AlbumPage> future;
            try {
                future = completed.poll(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            if (future == null) break;
            finishedRequests.add(future);
            try {
                applyPage(state, bunkrResult, future.get());
                if (bunkrResult.size() >= BATCH_TARGET) break;
            } catch (Exception ignored) {
                retry(state, requests.get(future));
            }
        }

        for (Map.Entry<Future<AlbumPage>, AlbumCursor> request : requests.entrySet()) {
            if (finishedRequests.contains(request.getKey())) continue;
            request.getKey().cancel(true);
            retry(state, request.getValue());
        }

        ArrayList<NativeContentItem> fapelloResult = new ArrayList<>();
        for (Map.Entry<Future<FapelloPage>, FapelloCursor> request :
                fapelloRequests.entrySet()) {
            Future<FapelloPage> future = request.getKey();
            try {
                long remaining = deadline - SystemClock.elapsedRealtime();
                FapelloPage page;
                if (future.isDone()) page = future.get();
                else if (remaining > 0L) {
                    page = future.get(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
                } else {
                    future.cancel(true);
                    retryFapello(state, request.getValue());
                    continue;
                }
                applyFapelloPage(state, fapelloResult, page);
            } catch (Exception ignored) {
                future.cancel(true);
                retryFapello(state, request.getValue());
            }
        }

        ArrayList<NativeContentItem> wikiFeetResult = new ArrayList<>();
        for (Map.Entry<Future<WikiFeetPage>, WikiFeetCursor> request :
                wikiFeetRequests.entrySet()) {
            Future<WikiFeetPage> future = request.getKey();
            try {
                long remaining = deadline - SystemClock.elapsedRealtime();
                WikiFeetPage page;
                if (future.isDone()) page = future.get();
                else if (remaining > 0L) {
                    page = future.get(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
                } else {
                    future.cancel(true);
                    retryWikiFeet(state, request.getValue());
                    continue;
                }
                applyWikiFeetPage(state, wikiFeetResult, page);
            } catch (Exception ignored) {
                future.cancel(true);
                retryWikiFeet(state, request.getValue());
            }
        }

        ArrayList<NativeContentItem> result = interleave(
                bunkrResult, fapelloResult, wikiFeetResult);

        if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) {
            state.pending.clear();
            state.fapelloPending.clear();
            state.wikiFeetPending.clear();
            state.searchFinished = true;
            state.fapelloCatalogLoaded = true;
            state.wikiFeetCatalogLoaded = true;
            state.wikiFeetXCatalogLoaded = true;
        }
        if (result.isEmpty() && bunkrCatalogError != null && fapelloCatalogError != null &&
                wikiFeetCatalogError != null &&
                state.loadedMediaUrls.isEmpty()) {
            throw new IOException("Fapzone sources could not be reached", fapelloCatalogError);
        }
        return new Batch(result, state.finished());
    }

    private void loadAlbumsIfNeeded(Context context, State state) throws IOException {
        BunkrRepository repository = new BunkrRepository();
        int searchAttempts = 0;
        while (state.pending.isEmpty() && !state.searchFinished && searchAttempts < 2) {
            int page = state.nextSearchPage;
            searchAttempts++;
            List<NativeContentItem> albums = repository.searchAlbums(context, state.query, page);
            state.nextSearchPage = page + 1;
            if (albums == null || albums.isEmpty()) {
                state.searchFinished = true;
                break;
            }
            int added = 0;
            for (NativeContentItem album : albums) {
                if (album == null || !BunkrRepository.isAlbumUrl(album.url) ||
                        !state.albumUrls.add(album.url)) continue;
                state.pending.addLast(new AlbumCursor(album.url));
                added++;
            }
            if (page >= MAX_SEARCH_PAGES) state.searchFinished = true;
            if (added == 0 && page >= MAX_SEARCH_PAGES) break;
        }
    }

    private void loadFapelloModelsIfNeeded(Context context, State state) throws IOException {
        if (state.fapelloCatalogLoaded) return;
        List<FapelloRepository.Model> models = new FapelloRepository().searchModels(
                context,
                state.query,
                FAPELLO_MODEL_LIMIT
        );
        if (models != null) {
            for (FapelloRepository.Model model : models) {
                if (model == null || !FapelloRepository.isModelUrl(model.url) ||
                        !state.fapelloModelUrls.add(model.url)) continue;
                state.fapelloPending.addLast(new FapelloCursor(model));
            }
        }
        state.fapelloCatalogLoaded = true;
    }

    private IOException loadWikiFeetCatalog(Context context, State state) {
        IOException firstError = null;
        if (!state.wikiFeetCatalogLoaded) {
            try {
                loadWikiFeetSite(context, state, WikiFeetRepository.Site.WIKIFEET);
                state.wikiFeetCatalogLoaded = true;
                state.wikiFeetSearchFailures = 0;
            } catch (IOException error) {
                firstError = error;
                if (++state.wikiFeetSearchFailures >= 2) state.wikiFeetCatalogLoaded = true;
            }
        }
        if (!state.wikiFeetXCatalogLoaded) {
            try {
                loadWikiFeetSite(context, state, WikiFeetRepository.Site.WIKIFEET_X);
                state.wikiFeetXCatalogLoaded = true;
                state.wikiFeetXSearchFailures = 0;
            } catch (IOException error) {
                if (firstError == null) firstError = error;
                if (++state.wikiFeetXSearchFailures >= 2) state.wikiFeetXCatalogLoaded = true;
            }
        }
        return firstError;
    }

    private void loadWikiFeetSite(
            Context context,
            State state,
            WikiFeetRepository.Site site
    ) throws IOException {
        for (WikiFeetRepository.Creator creator : new WikiFeetRepository().searchCreators(
                context, site, state.query, WIKIFEET_CREATOR_LIMIT)) {
            if (creator == null || !WikiFeetRepository.isProfileUrl(creator.url, site) ||
                    !state.wikiFeetProfileUrls.add(creator.url)) continue;
            state.wikiFeetPending.addLast(new WikiFeetCursor(creator));
        }
    }

    private void applyPage(
            State state,
            ArrayList<NativeContentItem> destination,
            AlbumPage page
    ) {
        if (page == null || page.cursor == null) return;
        if (page.error != null) {
            retry(state, page.cursor);
            return;
        }
        if (page.items == null || page.items.isEmpty()) {
            if (page.cursor.nextPage == 1) retry(state, page.cursor);
            return;
        }

        int added = 0;
        for (NativeContentItem item : page.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            added++;
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        if (added > 0 && state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.pending.addLast(page.cursor);
        }
    }

    private void retry(State state, AlbumCursor cursor) {
        if (cursor == null) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.pending.addLast(cursor);
    }

    private void applyFapelloPage(
            State state,
            ArrayList<NativeContentItem> destination,
            FapelloPage page
    ) {
        if (page == null || page.cursor == null) return;
        if (page.error != null) {
            retryFapello(state, page.cursor);
            return;
        }
        if (page.items == null || page.items.isEmpty()) return;

        for (NativeContentItem item : page.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        if (state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS &&
                page.cursor.nextPage < MAX_FAPELLO_PAGES) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.fapelloPending.addLast(page.cursor);
        }
    }

    private void retryFapello(State state, FapelloCursor cursor) {
        if (cursor == null) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.fapelloPending.addLast(cursor);
    }

    private void applyWikiFeetPage(
            State state,
            ArrayList<NativeContentItem> destination,
            WikiFeetPage page
    ) {
        if (page == null || page.cursor == null) return;
        if (page.error != null) {
            retryWikiFeet(state, page.cursor);
            return;
        }
        if (page.items == null || page.items.isEmpty()) return;
        for (NativeContentItem item : page.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        if (page.items.size() >= WIKIFEET_PAGE_SIZE &&
                state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.wikiFeetPending.addLast(page.cursor);
        }
    }

    private void retryWikiFeet(State state, WikiFeetCursor cursor) {
        if (cursor == null) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.wikiFeetPending.addLast(cursor);
    }

    private ArrayList<NativeContentItem> interleave(
            List<NativeContentItem>... sources
    ) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        int count = 0;
        for (List<NativeContentItem> source : sources) {
            count = Math.max(count, source == null ? 0 : source.size());
        }
        for (int i = 0; i < count; i++) {
            for (List<NativeContentItem> source : sources) {
                if (source != null && i < source.size()) result.add(source.get(i));
            }
        }
        return result;
    }

    private State state(Context context, String sessionId, String query) throws IOException {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IOException("Creator gallery session was missing");
        }
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) throw new IOException("Creator name was missing");
        synchronized (STATES) {
            State current = STATES.get(sessionId);
            if (current == null || !cleanQuery.equalsIgnoreCase(current.query)) {
                current = restoreCursor(context, sessionId, cleanQuery);
                STATES.put(sessionId, current);
                trimLocked();
            }
            return current;
        }
    }

    private void saveCursor(Context context, String id, State state, Batch batch) {
        try {
            org.json.JSONObject json = new org.json.JSONObject().put("query", state.query)
                    .put("next", state.nextSearchPage).put("searchFinished", state.searchFinished)
                    .put("fapelloLoaded", state.fapelloCatalogLoaded)
                    .put("wikiFeetLoaded", state.wikiFeetCatalogLoaded)
                    .put("wikiFeetXLoaded", state.wikiFeetXCatalogLoaded)
                    .put("albums", new org.json.JSONArray(state.albumUrls))
                    .put("models", new org.json.JSONArray(state.fapelloModelUrls))
                    .put("wikiFeetProfiles", new org.json.JSONArray(state.wikiFeetProfileUrls))
                    .put("media", new org.json.JSONArray(state.loadedMediaUrls));
            org.json.JSONArray pending = new org.json.JSONArray();
            for (AlbumCursor cursor : state.pending) pending.put(new org.json.JSONObject()
                    .put("url", cursor.albumUrl).put("next", cursor.nextPage));
            json.put("pending", pending);
            org.json.JSONArray models = new org.json.JSONArray();
            for (FapelloCursor cursor : state.fapelloPending) models.put(new org.json.JSONObject()
                    .put("name", cursor.model.name).put("url", cursor.model.url)
                    .put("image", cursor.model.imageUrl).put("next", cursor.nextPage));
            json.put("pendingModels", models);
            org.json.JSONArray wikiFeet = new org.json.JSONArray();
            for (WikiFeetCursor cursor : state.wikiFeetPending) {
                WikiFeetRepository.Creator creator = cursor.creator;
                wikiFeet.put(new org.json.JSONObject()
                        .put("site", creator.site.id).put("name", creator.name)
                        .put("url", creator.url).put("image", creator.imageUrl)
                        .put("photos", creator.photoCount).put("next", cursor.nextPage));
            }
            json.put("pendingWikiFeet", wikiFeet);
            BunkrGallerySessionStore.recordCreatorBatch(context, id, batch.items, batch.endReached, json);
        } catch (Exception ignored) { }
    }

    private State restoreCursor(Context context, String id, String query) {
        State state = new State(query);
        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.restore(context, id);
        org.json.JSONObject json = null;
        try { if (snapshot != null && !snapshot.cursor.isEmpty()) json = new org.json.JSONObject(snapshot.cursor); }
        catch (org.json.JSONException ignored) { }
        if (json == null || !query.equalsIgnoreCase(json.optString("query"))) return state;
        state.nextSearchPage = Math.max(1, json.optInt("next", 1));
        state.searchFinished = json.optBoolean("searchFinished");
        state.fapelloCatalogLoaded = json.optBoolean("fapelloLoaded");
        state.wikiFeetCatalogLoaded = json.optBoolean("wikiFeetLoaded");
        state.wikiFeetXCatalogLoaded = json.optBoolean("wikiFeetXLoaded");
        restoreSet(json.optJSONArray("albums"), state.albumUrls);
        restoreSet(json.optJSONArray("models"), state.fapelloModelUrls);
        restoreSet(json.optJSONArray("wikiFeetProfiles"), state.wikiFeetProfileUrls);
        restoreSet(json.optJSONArray("media"), state.loadedMediaUrls);
        org.json.JSONArray pending = json.optJSONArray("pending");
        if (pending != null) for (int i = 0; i < pending.length(); i++) {
            org.json.JSONObject item = pending.optJSONObject(i);
            if (item == null || !BunkrRepository.isAlbumUrl(item.optString("url"))) continue;
            AlbumCursor cursor = new AlbumCursor(item.optString("url"));
            cursor.nextPage = Math.max(1, item.optInt("next", 1));
            state.pending.add(cursor);
        }
        org.json.JSONArray models = json.optJSONArray("pendingModels");
        if (models != null) for (int i = 0; i < models.length(); i++) {
            org.json.JSONObject item = models.optJSONObject(i);
            if (item == null || !FapelloRepository.isModelUrl(item.optString("url"))) continue;
            FapelloCursor cursor = new FapelloCursor(new FapelloRepository.Model(
                    item.optString("name"), item.optString("url"), item.optString("image")));
            cursor.nextPage = Math.max(1, item.optInt("next", 1));
            state.fapelloPending.add(cursor);
        }
        org.json.JSONArray wikiFeet = json.optJSONArray("pendingWikiFeet");
        if (wikiFeet != null) for (int i = 0; i < wikiFeet.length(); i++) {
            org.json.JSONObject item = wikiFeet.optJSONObject(i);
            if (item == null) continue;
            WikiFeetRepository.Site site = "wikifeetx".equals(item.optString("site"))
                    ? WikiFeetRepository.Site.WIKIFEET_X
                    : WikiFeetRepository.Site.WIKIFEET;
            if (!WikiFeetRepository.isProfileUrl(item.optString("url"), site)) continue;
            WikiFeetCursor cursor = new WikiFeetCursor(new WikiFeetRepository.Creator(
                    site, item.optString("name"), item.optString("url"),
                    item.optString("image"), item.optInt("photos")));
            cursor.nextPage = Math.max(1, item.optInt("next", 1));
            state.wikiFeetPending.add(cursor);
        }
        return state;
    }

    private void restoreSet(org.json.JSONArray values, Set<String> output) {
        if (values != null) for (int i = 0; i < Math.min(10000, values.length()); i++) output.add(values.optString(i));
    }

    private void trimLocked() {
        while (STATES.size() > MAX_SESSIONS) {
            String oldest = STATES.keySet().iterator().next();
            STATES.remove(oldest);
        }
    }

    private static final class State {
        final String query;
        final ArrayDeque<AlbumCursor> pending = new ArrayDeque<>();
        final ArrayDeque<FapelloCursor> fapelloPending = new ArrayDeque<>();
        final ArrayDeque<WikiFeetCursor> wikiFeetPending = new ArrayDeque<>();
        final Set<String> albumUrls = new HashSet<>();
        final Set<String> fapelloModelUrls = new HashSet<>();
        final Set<String> wikiFeetProfileUrls = new HashSet<>();
        final Set<String> loadedMediaUrls = new HashSet<>();
        int nextSearchPage = 1;
        int bunkrSearchFailures;
        int fapelloSearchFailures;
        int wikiFeetSearchFailures;
        int wikiFeetXSearchFailures;
        boolean searchFinished;
        boolean fapelloCatalogLoaded;
        boolean wikiFeetCatalogLoaded;
        boolean wikiFeetXCatalogLoaded;

        State(String query) {
            this.query = query == null ? "" : query.trim();
        }

        boolean finished() {
            return searchFinished && pending.isEmpty() &&
                    fapelloCatalogLoaded && fapelloPending.isEmpty() &&
                    wikiFeetCatalogLoaded && wikiFeetXCatalogLoaded &&
                    wikiFeetPending.isEmpty();
        }
    }

    private static final class AlbumCursor {
        final String albumUrl;
        int nextPage = 1;
        int failures;

        AlbumCursor(String albumUrl) {
            this.albumUrl = albumUrl;
        }
    }

    private static final class AlbumPage {
        final AlbumCursor cursor;
        final List<NativeContentItem> items;
        final Exception error;

        AlbumPage(AlbumCursor cursor, List<NativeContentItem> items, Exception error) {
            this.cursor = cursor;
            this.items = items;
            this.error = error;
        }
    }

    private static final class FapelloCursor {
        final FapelloRepository.Model model;
        int nextPage = 1;
        int failures;

        FapelloCursor(FapelloRepository.Model model) {
            this.model = model;
        }
    }

    private static final class FapelloPage {
        final FapelloCursor cursor;
        final List<NativeContentItem> items;
        final Exception error;

        FapelloPage(FapelloCursor cursor, List<NativeContentItem> items, Exception error) {
            this.cursor = cursor;
            this.items = items;
            this.error = error;
        }
    }

    private static final class WikiFeetCursor {
        final WikiFeetRepository.Creator creator;
        int nextPage = 1;
        int failures;

        WikiFeetCursor(WikiFeetRepository.Creator creator) {
            this.creator = creator;
        }
    }

    private static final class WikiFeetPage {
        final WikiFeetCursor cursor;
        final List<NativeContentItem> items;
        final Exception error;

        WikiFeetPage(WikiFeetCursor cursor, List<NativeContentItem> items, Exception error) {
            this.cursor = cursor;
            this.items = items;
            this.error = error;
        }
    }
}
