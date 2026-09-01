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

/** Incrementally combines matching Bunkr albums and Fapello model media for Fapzone. */
final class BunkrCreatorGalleryRepository {
    private static final int MAX_SESSIONS = 4;
    private static final int MAX_SEARCH_PAGES = 5;
    private static final int ALBUMS_PER_BATCH = 4;
    private static final int FAPELLO_MODELS_PER_BATCH = 2;
    private static final int FAPELLO_MODEL_LIMIT = 4;
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
        State state = state(sessionId, query);
        synchronized (state) {
            return fetchNextLocked(context.getApplicationContext(), state);
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

        ArrayList<AlbumCursor> selected = new ArrayList<>();
        while (!state.pending.isEmpty() && selected.size() < ALBUMS_PER_BATCH) {
            selected.add(state.pending.removeFirst());
        }

        ArrayList<FapelloCursor> selectedFapello = new ArrayList<>();
        while (!state.fapelloPending.isEmpty() &&
                selectedFapello.size() < FAPELLO_MODELS_PER_BATCH) {
            selectedFapello.add(state.fapelloPending.removeFirst());
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

        ArrayList<NativeContentItem> result = interleave(bunkrResult, fapelloResult);

        if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) {
            state.pending.clear();
            state.fapelloPending.clear();
            state.searchFinished = true;
            state.fapelloCatalogLoaded = true;
        }
        if (result.isEmpty() && bunkrCatalogError != null && fapelloCatalogError != null &&
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

    private ArrayList<NativeContentItem> interleave(
            List<NativeContentItem> bunkr,
            List<NativeContentItem> fapello
    ) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        int bunkrSize = bunkr == null ? 0 : bunkr.size();
        int fapelloSize = fapello == null ? 0 : fapello.size();
        int count = Math.max(bunkrSize, fapelloSize);
        for (int i = 0; i < count; i++) {
            if (i < bunkrSize) result.add(bunkr.get(i));
            if (i < fapelloSize) result.add(fapello.get(i));
        }
        return result;
    }

    private State state(String sessionId, String query) throws IOException {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IOException("Creator gallery session was missing");
        }
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) throw new IOException("Creator name was missing");
        synchronized (STATES) {
            State current = STATES.get(sessionId);
            if (current == null || !cleanQuery.equalsIgnoreCase(current.query)) {
                current = new State(cleanQuery);
                STATES.put(sessionId, current);
                trimLocked();
            }
            return current;
        }
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
        final Set<String> albumUrls = new HashSet<>();
        final Set<String> fapelloModelUrls = new HashSet<>();
        final Set<String> loadedMediaUrls = new HashSet<>();
        int nextSearchPage = 1;
        int bunkrSearchFailures;
        int fapelloSearchFailures;
        boolean searchFinished;
        boolean fapelloCatalogLoaded;

        State(String query) {
            this.query = query == null ? "" : query.trim();
        }

        boolean finished() {
            return searchFinished && pending.isEmpty() &&
                    fapelloCatalogLoaded && fapelloPending.isEmpty();
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
}
