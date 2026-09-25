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

/** Incrementally combines Bunkr, Fapello, OnlyHaven, WikiFeet and WikiFeet X media for OnlyFap. */
final class BunkrCreatorGalleryRepository {
    interface ProgressListener {
        /** Called on the fetching thread with newly accepted media. */
        void onItems(List<NativeContentItem> items);
    }
    private static final int MAX_SESSIONS = 4;
    private static final int MAX_SEARCH_PAGES = 5;
    private static final int ALBUMS_PER_BATCH = 4;
    private static final int FAPELLO_MODELS_PER_BATCH = 2;
    private static final int FAPELLO_MODEL_LIMIT = 4;
    private static final int WIKIFEET_CREATORS_PER_BATCH = 2;
    private static final int WIKIFEET_CREATOR_LIMIT = 4;
    private static final int WIKIFEET_PAGE_SIZE = 24;
    private static final int ONLYHAVEN_CREATORS_PER_BATCH = 2;
    private static final int ONLYHAVEN_CREATOR_LIMIT = 4;
    private static final int ONLYHAVEN_PAGE_SIZE = 36;
    private static final int MAX_FAPELLO_PAGES = 250;
    private static final int BATCH_TARGET = 48;
    private static final int MAX_MEDIA_ITEMS = 10_000;
    private static final long FAST_FIRST_PAINT_BUDGET_MS = 1_200L;
    private static final long ALBUM_BATCH_BUDGET_MS = 16_000L;
    private static final ExecutorService ALBUM_IO = Executors.newFixedThreadPool(8);
    private static final LinkedHashMap<String, State> STATES =
            new LinkedHashMap<>(8, 0.75f, true);

    static final class Batch {
        final ArrayList<NativeContentItem> items;
        final boolean endReached;
        final FapelloSourceException fapelloFailure;

        Batch(ArrayList<NativeContentItem> items, boolean endReached) {
            this(items, endReached, null);
        }

        Batch(
                ArrayList<NativeContentItem> items,
                boolean endReached,
                FapelloSourceException fapelloFailure
        ) {
            this.items = items;
            this.endReached = endReached;
            this.fapelloFailure = fapelloFailure;
        }
    }

    void reset(String sessionId, String query) {
        reset(sessionId, query, "", query);
    }

    void reset(String sessionId, String query, String fapelloProfileUrl, String creatorName) {
        if (sessionId == null || sessionId.trim().isEmpty()) return;
        synchronized (STATES) {
            State state = new State(query);
            seedFapelloProfile(state, fapelloProfileUrl, creatorName);
            STATES.put(sessionId, state);
            trimLocked();
        }
    }

    Batch fetchNext(Context context, String sessionId, String query) throws IOException {
        return fetchNext(context, sessionId, query, "", query);
    }

    Batch fetchNext(Context context, String sessionId, String query,
            String fapelloProfileUrl, String creatorName) throws IOException {
        return fetchNext(context, sessionId, query, fapelloProfileUrl, creatorName, null);
    }

    Batch fetchNext(Context context, String sessionId, String query,
            String fapelloProfileUrl, String creatorName, ProgressListener listener) throws IOException {
        State state = state(context, sessionId, query, fapelloProfileUrl, creatorName);
        synchronized (state) {
            Batch batch = fetchNextLocked(context, state, listener);
            saveCursor(context, sessionId, state, batch);
            return batch;
        }
    }

    private Batch fetchNextLocked(Context context, State state, ProgressListener listener) throws IOException {
        if (state.finished()) return new Batch(new ArrayList<>(), true, state.lastFapelloFailure);

        Context appContext = context.getApplicationContext();

        // All catalog discovery runs concurrently. If we already know a creator profile from
        // the tapped card or a restored cursor, use that known profile as a fast lane while the
        // broader catalogs keep loading in parallel.
        Future<IOException> wikiCatalog = ALBUM_IO.submit(() -> loadWikiFeetCatalog(appContext, state));
        Future<IOException> havenCatalog = ALBUM_IO.submit(() -> loadOnlyHavenCatalog(appContext, state));
        Future<IOException> fapelloCatalog = ALBUM_IO.submit(() -> {
            try {
                loadFapelloModelsIfNeeded(appContext, state);
                state.fapelloSearchFailures = 0;
                return null;
            } catch (IOException error) {
                state.lastFapelloFailure = fapelloFailure(error);
                state.fapelloSearchFailures++;
                if (!retryableFapello(state.lastFapelloFailure) ||
                        state.fapelloSearchFailures >= 2) state.fapelloCatalogLoaded = true;
                return error;
            }
        });
        Future<IOException> bunkrCatalog = ALBUM_IO.submit(() -> {
            try {
                loadAlbumsIfNeeded(appContext, state);
                state.bunkrSearchFailures = 0;
                return null;
            } catch (IOException error) {
                state.bunkrSearchFailures++;
                if (state.bunkrSearchFailures >= 2) state.searchFinished = true;
                return error;
            }
        });

        FastLaneResult fastLane = runFastFirstPaint(
                context,
                appContext,
                state,
                listener,
                bunkrCatalog,
                fapelloCatalog,
                wikiCatalog,
                havenCatalog
        );

        IOException bunkrCatalogError = catalogResult(bunkrCatalog);
        IOException fapelloCatalogError = catalogResult(fapelloCatalog);
        IOException wikiFeetCatalogError = catalogResult(wikiCatalog);
        IOException onlyHavenCatalogError = catalogResult(havenCatalog);

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
        for (WikiFeetRepository.Site site : WikiFeetRepository.Site.values()) {
            WikiFeetCursor cursor = pollWikiFeet(state, site);
            if (cursor != null) selectedWikiFeet.add(cursor);
        }
        while (!state.wikiFeetPending.isEmpty() &&
                selectedWikiFeet.size() < WIKIFEET_CREATORS_PER_BATCH) {
            selectedWikiFeet.add(state.wikiFeetPending.removeFirst());
        }

        ArrayList<OnlyHavenCursor> selectedOnlyHaven = new ArrayList<>();
        while (!state.onlyHavenPending.isEmpty() &&
                selectedOnlyHaven.size() < ONLYHAVEN_CREATORS_PER_BATCH) {
            selectedOnlyHaven.add(state.onlyHavenPending.removeFirst());
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
                            repository.fetchAlbum(appContext, cursor.albumUrl, cursor.nextPage),
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
                            new FapelloRepository().fetchModelMediaPage(
                                    context,
                                    cursor.model,
                                    cursor.nextPage
                            ),
                            null
                    );
                } catch (Exception error) {
                    return new FapelloPage(cursor, null, error);
                }
            });
            fapelloRequests.put(request, cursor);
        }

        LinkedHashMap<Future<OnlyHavenPage>, OnlyHavenCursor> onlyHavenRequests =
                new LinkedHashMap<>();
        for (OnlyHavenCursor cursor : selectedOnlyHaven) {
            Future<OnlyHavenPage> request = ALBUM_IO.submit(() -> {
                try {
                    return new OnlyHavenPage(
                            cursor,
                            new OnlyHavenRepository().fetchCreatorMedia(
                                    appContext, cursor.creator, cursor.nextPage, ONLYHAVEN_PAGE_SIZE),
                            null
                    );
                } catch (Exception error) {
                    return new OnlyHavenPage(cursor, new ArrayList<>(), error);
                }
            });
            onlyHavenRequests.put(request, cursor);
        }

        LinkedHashMap<Future<WikiFeetPage>, WikiFeetCursor> wikiFeetRequests =
                new LinkedHashMap<>();
        for (WikiFeetCursor cursor : selectedWikiFeet) {
            Future<WikiFeetPage> request = ALBUM_IO.submit(() -> {
                try {
                    return new WikiFeetPage(
                            cursor,
                            new WikiFeetRepository().fetchCreatorMedia(
                                    appContext, cursor.creator, cursor.nextPage, WIKIFEET_PAGE_SIZE),
                            null
                    );
                } catch (Exception error) {
                    return new WikiFeetPage(cursor, new ArrayList<>(), error);
                }
            });
            wikiFeetRequests.put(request, cursor);
        }

        ArrayList<NativeContentItem> bunkrResult = new ArrayList<>();
        ArrayList<NativeContentItem> progressiveResult =
                new ArrayList<>(fastLane.progressiveItems);
        int bunkrPublished = 0;
        ArrayList<NativeContentItem> fapelloResult =
                new ArrayList<>(fastLane.fapelloItems);
        int fapelloPublished = fapelloResult.size();
        Set<Future<FapelloPage>> finishedFapello = new HashSet<>();
        ArrayList<NativeContentItem> onlyHavenResult =
                new ArrayList<>(fastLane.onlyHavenItems);
        int onlyHavenPublished = onlyHavenResult.size();
        Set<Future<OnlyHavenPage>> finishedOnlyHaven = new HashSet<>();
        ArrayList<NativeContentItem> wikiFeetResult = new ArrayList<>();
        int wikiFeetPublished = 0;
        Set<Future<WikiFeetPage>> finishedWikiFeet = new HashSet<>();
        Set<Future<AlbumPage>> finishedRequests = new HashSet<>();
        long deadline = SystemClock.elapsedRealtime() + ALBUM_BATCH_BUDGET_MS;
        while (finishedRequests.size() < requests.size() ||
                finishedFapello.size() < fapelloRequests.size() ||
                finishedOnlyHaven.size() < onlyHavenRequests.size() ||
                finishedWikiFeet.size() < wikiFeetRequests.size()) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) break;
            Future<AlbumPage> future;
            try {
                future = completed.poll(Math.min(remaining, 250L),
                        java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            if (future != null) {
                finishedRequests.add(future);
                try {
                    applyPage(state, bunkrResult, future.get());
                    bunkrPublished = publishNew(bunkrResult, bunkrPublished, listener, progressiveResult);
                } catch (Exception ignored) {
                    retry(state, requests.get(future));
                }
            }
            // A fast Fapello profile should display even while a Bunkr album is slow.
            for (Map.Entry<Future<FapelloPage>, FapelloCursor> request : fapelloRequests.entrySet()) {
                if (!request.getKey().isDone() || !finishedFapello.add(request.getKey())) continue;
                try {
                    applyFapelloPage(state, fapelloResult, request.getKey().get());
                    fapelloPublished = publishNew(fapelloResult, fapelloPublished,
                            listener, progressiveResult);
                } catch (Exception error) {
                    state.lastFapelloFailure = fapelloFailure(error);
                    retryFapello(state, request.getValue());
                }
            }
            for (Map.Entry<Future<OnlyHavenPage>, OnlyHavenCursor> request : onlyHavenRequests.entrySet()) {
                if (!request.getKey().isDone() || !finishedOnlyHaven.add(request.getKey())) continue;
                try {
                    applyOnlyHavenPage(state, onlyHavenResult, request.getKey().get());
                    onlyHavenPublished = publishNew(onlyHavenResult, onlyHavenPublished,
                            listener, progressiveResult);
                } catch (Exception ignored) {
                    retryOnlyHaven(state, request.getValue());
                }
            }
            for (Map.Entry<Future<WikiFeetPage>, WikiFeetCursor> request : wikiFeetRequests.entrySet()) {
                if (!request.getKey().isDone() || !finishedWikiFeet.add(request.getKey())) continue;
                try {
                    applyWikiFeetPage(state, wikiFeetResult, request.getKey().get());
                    wikiFeetPublished = publishNew(wikiFeetResult, wikiFeetPublished,
                            listener, progressiveResult);
                } catch (Exception ignored) {
                    retryWikiFeet(state, request.getValue());
                }
            }
            if (bunkrResult.size() >= BATCH_TARGET) break;
        }

        for (Map.Entry<Future<AlbumPage>, AlbumCursor> request : requests.entrySet()) {
            if (finishedRequests.contains(request.getKey())) continue;
            request.getKey().cancel(true);
            retry(state, request.getValue());
        }

        for (Map.Entry<Future<FapelloPage>, FapelloCursor> request :
                fapelloRequests.entrySet()) {
            Future<FapelloPage> future = request.getKey();
            if (finishedFapello.contains(future)) continue;
            try {
                long remaining = deadline - SystemClock.elapsedRealtime();
                FapelloPage page;
                if (future.isDone()) page = future.get();
                else if (remaining > 0L) {
                    page = future.get(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
                } else {
                    future.cancel(true);
                    state.lastFapelloFailure = new FapelloSourceException(
                            FapelloSourceException.Reason.NETWORK,
                            "Fapello request timed out"
                    );
                    retryFapello(state, request.getValue());
                    continue;
                }
                applyFapelloPage(state, fapelloResult, page);
                fapelloPublished = publishNew(fapelloResult, fapelloPublished, listener, progressiveResult);
            } catch (Exception error) {
                future.cancel(true);
                state.lastFapelloFailure = fapelloFailure(error);
                retryFapello(state, request.getValue());
            }
        }

        for (Map.Entry<Future<OnlyHavenPage>, OnlyHavenCursor> request :
                onlyHavenRequests.entrySet()) {
            Future<OnlyHavenPage> future = request.getKey();
            if (finishedOnlyHaven.contains(future)) continue;
            try {
                long remaining = deadline - SystemClock.elapsedRealtime();
                OnlyHavenPage page;
                if (future.isDone()) page = future.get();
                else if (remaining > 0L) {
                    page = future.get(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
                } else {
                    future.cancel(true);
                    retryOnlyHaven(state, request.getValue());
                    continue;
                }
                applyOnlyHavenPage(state, onlyHavenResult, page);
                onlyHavenPublished = publishNew(onlyHavenResult, onlyHavenPublished, listener, progressiveResult);
            } catch (Exception ignored) {
                future.cancel(true);
                retryOnlyHaven(state, request.getValue());
            }
        }

        for (Map.Entry<Future<WikiFeetPage>, WikiFeetCursor> request :
                wikiFeetRequests.entrySet()) {
            Future<WikiFeetPage> future = request.getKey();
            if (finishedWikiFeet.contains(future)) continue;
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
                wikiFeetPublished = publishNew(wikiFeetResult, wikiFeetPublished, listener, progressiveResult);
            } catch (Exception ignored) {
                future.cancel(true);
                retryWikiFeet(state, request.getValue());
            }
        }

        ArrayList<NativeContentItem> result = listener == null
                ? interleave(bunkrResult, fapelloResult, onlyHavenResult, wikiFeetResult)
                : progressiveResult;

        if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) {
            state.pending.clear();
            state.fapelloPending.clear();
            state.wikiFeetPending.clear();
            state.onlyHavenPending.clear();
            state.searchFinished = true;
            state.fapelloCatalogLoaded = true;
            state.wikiFeetCatalogLoaded = true;
            state.wikiFeetXCatalogLoaded = true;
            state.onlyHavenCatalogLoaded = true;
        }
        if (result.isEmpty() && bunkrCatalogError != null && fapelloCatalogError != null &&
                wikiFeetCatalogError != null && onlyHavenCatalogError != null &&
                state.loadedMediaUrls.isEmpty()) {
            throw new IOException("OnlyFap sources could not be reached", fapelloCatalogError);
        }
        return new Batch(result, state.finished(), state.lastFapelloFailure);
    }

    private FastLaneResult runFastFirstPaint(
            Context context,
            Context appContext,
            State state,
            ProgressListener listener,
            Future<IOException> bunkrCatalog,
            Future<IOException> fapelloCatalog,
            Future<IOException> wikiCatalog,
            Future<IOException> havenCatalog
    ) {
        FastLaneResult result = new FastLaneResult();
        if (listener == null) return result;

        ExecutorCompletionService<FastPage> completed =
                new ExecutorCompletionService<>(ALBUM_IO);
        ArrayList<Future<FastPage>> requests = new ArrayList<>();
        LinkedHashMap<Future<FastPage>, FastPage> metadata = new LinkedHashMap<>();

        FapelloCursor fapello = state.fapelloPending.pollFirst();
        if (fapello != null) {
            FastPage meta = FastPage.fapello(fapello);
            Future<FastPage> request = completed.submit(() -> {
                try {
                    return FastPage.fapello(new FapelloPage(
                            fapello,
                            new FapelloRepository().fetchModelMediaPage(
                                    context, fapello.model, fapello.nextPage),
                            null
                    ));
                } catch (Exception error) {
                    return FastPage.fapello(new FapelloPage(fapello, null, error));
                }
            });
            requests.add(request);
            metadata.put(request, meta);
        }

        OnlyHavenCursor haven = state.onlyHavenPending.pollFirst();
        if (haven != null) {
            FastPage meta = FastPage.onlyHaven(haven);
            Future<FastPage> request = completed.submit(() -> {
                try {
                    return FastPage.onlyHaven(new OnlyHavenPage(
                            haven,
                            new OnlyHavenRepository().fetchCreatorMedia(
                                    appContext, haven.creator, haven.nextPage, ONLYHAVEN_PAGE_SIZE),
                            null
                    ));
                } catch (Exception error) {
                    return FastPage.onlyHaven(new OnlyHavenPage(
                            haven, new ArrayList<>(), error));
                }
            });
            requests.add(request);
            metadata.put(request, meta);
        }

        if (requests.isEmpty()) return result;

        long deadline = SystemClock.elapsedRealtime() + FAST_FIRST_PAINT_BUDGET_MS;
        int completedCount = 0;
        while (completedCount < requests.size()) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) break;
            if (bunkrCatalog.isDone() && fapelloCatalog.isDone()
                    && wikiCatalog.isDone() && havenCatalog.isDone()) {
                break;
            }
            try {
                Future<FastPage> future = completed.poll(
                        Math.min(remaining, 60L),
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                if (future == null) continue;
                completedCount++;
                FastPage page = future.get();
                if (page.fapelloPage != null) {
                    int before = result.fapelloItems.size();
                    applyFapelloPage(state, result.fapelloItems, page.fapelloPage);
                    if (result.fapelloItems.size() > before) {
                        ArrayList<NativeContentItem> added = new ArrayList<>(
                                result.fapelloItems.subList(
                                        before, result.fapelloItems.size()));
                        result.progressiveItems.addAll(added);
                        listener.onItems(added);
                    }
                } else if (page.onlyHavenPage != null) {
                    int before = result.onlyHavenItems.size();
                    applyOnlyHavenPage(state, result.onlyHavenItems, page.onlyHavenPage);
                    if (result.onlyHavenItems.size() > before) {
                        ArrayList<NativeContentItem> added = new ArrayList<>(
                                result.onlyHavenItems.subList(
                                        before, result.onlyHavenItems.size()));
                        result.progressiveItems.addAll(added);
                        listener.onItems(added);
                    }
                }
                metadata.remove(future);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
            }
        }

        for (Map.Entry<Future<FastPage>, FastPage> pending : metadata.entrySet()) {
            pending.getKey().cancel(true);
            FastPage page = pending.getValue();
            if (page.fapelloCursor != null) {
                state.fapelloPending.addFirst(page.fapelloCursor);
            } else if (page.onlyHavenCursor != null) {
                state.onlyHavenPending.addFirst(page.onlyHavenCursor);
            }
        }
        return result;
    }

    private IOException catalogResult(Future<IOException> request) {
        try {
            return request.get();
        } catch (Exception error) {
            request.cancel(true);
            return new IOException("Creator catalog request failed", error);
        }
    }

    private int publishNew(ArrayList<NativeContentItem> source, int published,
            ProgressListener listener, ArrayList<NativeContentItem> progressiveResult) {
        if (source.size() > published) {
            ArrayList<NativeContentItem> newItems =
                    new ArrayList<>(source.subList(published, source.size()));
            progressiveResult.addAll(newItems);
            if (listener != null) listener.onItems(newItems);
        }
        return source.size();
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

    private IOException loadOnlyHavenCatalog(Context context, State state) {
        if (state.onlyHavenCatalogLoaded) return null;
        try {
            List<OnlyHavenRepository.Creator> creators =
                    new OnlyHavenRepository().searchCreators(
                            context, state.query, ONLYHAVEN_CREATOR_LIMIT);
            if (creators != null) {
                for (OnlyHavenRepository.Creator creator : creators) {
                    if (creator == null || !OnlyHavenRepository.isOnlyHavenUrl(creator.url) ||
                            !state.onlyHavenProfileUrls.add(creator.url)) continue;
                    state.onlyHavenPending.addLast(new OnlyHavenCursor(creator));
                }
            }
            state.onlyHavenCatalogLoaded = true;
            state.onlyHavenSearchFailures = 0;
            return null;
        } catch (IOException error) {
            if (++state.onlyHavenSearchFailures >= 2) state.onlyHavenCatalogLoaded = true;
            return error;
        }
    }

    private IOException loadWikiFeetCatalog(Context context, State state) {
        LinkedHashMap<WikiFeetRepository.Site, Future<List<WikiFeetRepository.Creator>>> requests =
                new LinkedHashMap<>();
        if (!state.wikiFeetCatalogLoaded) requests.put(
                WikiFeetRepository.Site.WIKIFEET,
                ALBUM_IO.submit(() -> new WikiFeetRepository().searchCreators(
                        context, WikiFeetRepository.Site.WIKIFEET,
                        state.query, WIKIFEET_CREATOR_LIMIT)));
        if (!state.wikiFeetXCatalogLoaded) requests.put(
                WikiFeetRepository.Site.WIKIFEET_X,
                ALBUM_IO.submit(() -> new WikiFeetRepository().searchCreators(
                        context, WikiFeetRepository.Site.WIKIFEET_X,
                        state.query, WIKIFEET_CREATOR_LIMIT)));
        IOException firstError = null;
        long deadline = SystemClock.elapsedRealtime() + 8_000L;
        for (Map.Entry<WikiFeetRepository.Site, Future<List<WikiFeetRepository.Creator>>> request :
                requests.entrySet()) {
            WikiFeetRepository.Site site = request.getKey();
            try {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) throw new IOException(site.label + " search timed out");
                List<WikiFeetRepository.Creator> creators = request.getValue().get(
                        remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
                addWikiFeetCreators(state, site, creators);
                if (site == WikiFeetRepository.Site.WIKIFEET) {
                    state.wikiFeetCatalogLoaded = true;
                    state.wikiFeetSearchFailures = 0;
                } else {
                    state.wikiFeetXCatalogLoaded = true;
                    state.wikiFeetXSearchFailures = 0;
                }
            } catch (Exception error) {
                request.getValue().cancel(true);
                IOException failure = error instanceof IOException
                        ? (IOException) error
                        : new IOException(site.label + " search failed", error);
                if (firstError == null) firstError = failure;
                if (site == WikiFeetRepository.Site.WIKIFEET) {
                    if (++state.wikiFeetSearchFailures >= 2) state.wikiFeetCatalogLoaded = true;
                } else if (++state.wikiFeetXSearchFailures >= 2) {
                    state.wikiFeetXCatalogLoaded = true;
                }
            }
        }
        return firstError;
    }

    private void addWikiFeetCreators(
            State state,
            WikiFeetRepository.Site site,
            List<WikiFeetRepository.Creator> creators
    ) {
        if (creators == null) return;
        for (WikiFeetRepository.Creator creator : creators) {
            if (creator == null || !WikiFeetRepository.isProfileUrl(creator.url, site) ||
                    !state.wikiFeetProfileUrls.add(creator.url)) continue;
            state.wikiFeetPending.addLast(new WikiFeetCursor(creator));
        }
    }

    private WikiFeetCursor pollWikiFeet(State state, WikiFeetRepository.Site site) {
        java.util.Iterator<WikiFeetCursor> iterator = state.wikiFeetPending.iterator();
        while (iterator.hasNext()) {
            WikiFeetCursor cursor = iterator.next();
            if (cursor.creator.site != site) continue;
            iterator.remove();
            return cursor;
        }
        return null;
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
            state.lastFapelloFailure = fapelloFailure(page.error);
            retryFapello(state, page.cursor);
            return;
        }
        FapelloRepository.MediaPage media = page.media;
        if (media == null) {
            state.lastFapelloFailure = new FapelloSourceException(
                    FapelloSourceException.Reason.MALFORMED,
                    "Fapello returned no media page"
            );
            return;
        }
        state.lastFapelloFailure = null;

        int added = 0;
        for (NativeContentItem item : media.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            added++;
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        if (added > 0 && media.hasNext && state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS &&
                page.cursor.nextPage < MAX_FAPELLO_PAGES) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.fapelloPending.addLast(page.cursor);
        }
    }

    private void retryFapello(State state, FapelloCursor cursor) {
        if (cursor == null) return;
        if (!retryableFapello(state.lastFapelloFailure)) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.fapelloPending.addLast(cursor);
    }

    private boolean retryableFapello(FapelloSourceException failure) {
        return failure == null || failure.reason == FapelloSourceException.Reason.NETWORK ||
                failure.reason == FapelloSourceException.Reason.HTTP;
    }

    private FapelloSourceException fapelloFailure(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof FapelloSourceException) {
                return (FapelloSourceException) current;
            }
            current = current.getCause();
        }
        return new FapelloSourceException(
                FapelloSourceException.Reason.NETWORK,
                "Fapello request failed",
                error
        );
    }

    private void applyOnlyHavenPage(
            State state,
            ArrayList<NativeContentItem> destination,
            OnlyHavenPage page
    ) {
        if (page == null || page.cursor == null) return;
        if (page.error != null) {
            retryOnlyHaven(state, page.cursor);
            return;
        }
        if (page.items == null || page.items.isEmpty()) return;
        int added = 0;
        for (NativeContentItem item : page.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            added++;
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        if (added > 0 && page.items.size() >= ONLYHAVEN_PAGE_SIZE &&
                state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.onlyHavenPending.addLast(page.cursor);
        }
    }

    private void retryOnlyHaven(State state, OnlyHavenCursor cursor) {
        if (cursor == null) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.onlyHavenPending.addLast(cursor);
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
            List<NativeContentItem> bunkr,
            List<NativeContentItem> fapello,
            List<NativeContentItem> onlyHaven,
            List<NativeContentItem> wikiFeet
    ) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        int count = Math.max(Math.max(size(bunkr), size(fapello)),
                Math.max(size(onlyHaven), size(wikiFeet)));
        for (int i = 0; i < count; i++) {
            addAt(result, bunkr, i);
            addAt(result, fapello, i);
            addAt(result, onlyHaven, i);
            addAt(result, wikiFeet, i);
        }
        return result;
    }

    private int size(List<NativeContentItem> items) {
        return items == null ? 0 : items.size();
    }

    private void addAt(
            List<NativeContentItem> output,
            List<NativeContentItem> source,
            int index
    ) {
        if (source != null && index < source.size()) output.add(source.get(index));
    }

    private State state(Context context, String sessionId, String query,
            String fapelloProfileUrl, String creatorName) throws IOException {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IOException("Creator gallery session was missing");
        }
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) throw new IOException("Creator name was missing");
        synchronized (STATES) {
            State current = STATES.get(sessionId);
            if (current == null || !cleanQuery.equalsIgnoreCase(current.query)) {
                current = restoreCursor(context, sessionId, cleanQuery);
                seedFapelloProfile(current, fapelloProfileUrl, creatorName);
                STATES.put(sessionId, current);
                trimLocked();
            }
            return current;
        }
    }

    private void seedFapelloProfile(State state, String profileUrl, String creatorName) {
        if (state == null || !FapelloRepository.isModelUrl(profileUrl) ||
                !state.fapelloModelUrls.add(profileUrl)) return;
        String name = creatorName == null || creatorName.trim().isEmpty()
                ? state.query
                : creatorName.trim();
        state.fapelloPending.addFirst(new FapelloCursor(
                new FapelloRepository.Model(name, profileUrl, "")));
        state.fapelloCatalogLoaded = true;
    }

    private void saveCursor(Context context, String id, State state, Batch batch) {
        try {
            org.json.JSONObject json = new org.json.JSONObject().put("query", state.query)
                    .put("next", state.nextSearchPage).put("searchFinished", state.searchFinished)
                    .put("fapelloLoaded", state.fapelloCatalogLoaded)
                    .put("wikiFeetLoaded", state.wikiFeetCatalogLoaded)
                    .put("wikiFeetXLoaded", state.wikiFeetXCatalogLoaded)
                    .put("onlyHavenLoaded", state.onlyHavenCatalogLoaded)
                    .put("albums", new org.json.JSONArray(state.albumUrls))
                    .put("models", new org.json.JSONArray(state.fapelloModelUrls))
                    .put("wikiFeetProfiles", new org.json.JSONArray(state.wikiFeetProfileUrls))
                    .put("onlyHavenProfiles", new org.json.JSONArray(state.onlyHavenProfileUrls))
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
            org.json.JSONArray onlyHaven = new org.json.JSONArray();
            for (OnlyHavenCursor cursor : state.onlyHavenPending) {
                OnlyHavenRepository.Creator creator = cursor.creator;
                onlyHaven.put(new org.json.JSONObject()
                        .put("service", creator.service).put("id", creator.id)
                        .put("name", creator.name).put("url", creator.url)
                        .put("image", creator.imageUrl).put("next", cursor.nextPage));
            }
            json.put("pendingOnlyHaven", onlyHaven);
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
        state.onlyHavenCatalogLoaded = json.optBoolean("onlyHavenLoaded");
        restoreSet(json.optJSONArray("albums"), state.albumUrls);
        restoreSet(json.optJSONArray("models"), state.fapelloModelUrls);
        restoreSet(json.optJSONArray("wikiFeetProfiles"), state.wikiFeetProfileUrls);
        restoreSet(json.optJSONArray("onlyHavenProfiles"), state.onlyHavenProfileUrls);
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
        org.json.JSONArray onlyHaven = json.optJSONArray("pendingOnlyHaven");
        if (onlyHaven != null) for (int i = 0; i < onlyHaven.length(); i++) {
            org.json.JSONObject item = onlyHaven.optJSONObject(i);
            if (item == null || !OnlyHavenRepository.isOnlyHavenUrl(item.optString("url"))) continue;
            OnlyHavenCursor cursor = new OnlyHavenCursor(new OnlyHavenRepository.Creator(
                    item.optString("service"), item.optString("id"), item.optString("name"),
                    item.optString("url"), item.optString("image")));
            cursor.nextPage = Math.max(1, item.optInt("next", 1));
            state.onlyHavenPending.add(cursor);
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

    private static final class FastLaneResult {
        final ArrayList<NativeContentItem> progressiveItems = new ArrayList<>();
        final ArrayList<NativeContentItem> fapelloItems = new ArrayList<>();
        final ArrayList<NativeContentItem> onlyHavenItems = new ArrayList<>();
    }

    private static final class FastPage {
        final FapelloPage fapelloPage;
        final OnlyHavenPage onlyHavenPage;
        final FapelloCursor fapelloCursor;
        final OnlyHavenCursor onlyHavenCursor;

        private FastPage(
                FapelloPage fapelloPage,
                OnlyHavenPage onlyHavenPage,
                FapelloCursor fapelloCursor,
                OnlyHavenCursor onlyHavenCursor
        ) {
            this.fapelloPage = fapelloPage;
            this.onlyHavenPage = onlyHavenPage;
            this.fapelloCursor = fapelloCursor;
            this.onlyHavenCursor = onlyHavenCursor;
        }

        static FastPage fapello(FapelloCursor cursor) {
            return new FastPage(null, null, cursor, null);
        }

        static FastPage fapello(FapelloPage page) {
            return new FastPage(page, null, null, null);
        }

        static FastPage onlyHaven(OnlyHavenCursor cursor) {
            return new FastPage(null, null, null, cursor);
        }

        static FastPage onlyHaven(OnlyHavenPage page) {
            return new FastPage(null, page, null, null);
        }
    }

    private static final class State {
        final String query;
        final ArrayDeque<AlbumCursor> pending = new ArrayDeque<>();
        final ArrayDeque<FapelloCursor> fapelloPending = new ArrayDeque<>();
        final ArrayDeque<WikiFeetCursor> wikiFeetPending = new ArrayDeque<>();
        final ArrayDeque<OnlyHavenCursor> onlyHavenPending = new ArrayDeque<>();
        final Set<String> albumUrls = new HashSet<>();
        final Set<String> fapelloModelUrls = new HashSet<>();
        final Set<String> wikiFeetProfileUrls = new HashSet<>();
        final Set<String> onlyHavenProfileUrls = new HashSet<>();
        final Set<String> loadedMediaUrls = new HashSet<>();
        int nextSearchPage = 1;
        int bunkrSearchFailures;
        int fapelloSearchFailures;
        int wikiFeetSearchFailures;
        int wikiFeetXSearchFailures;
        int onlyHavenSearchFailures;
        boolean searchFinished;
        boolean fapelloCatalogLoaded;
        boolean wikiFeetCatalogLoaded;
        boolean wikiFeetXCatalogLoaded;
        boolean onlyHavenCatalogLoaded;
        FapelloSourceException lastFapelloFailure;

        State(String query) {
            this.query = query == null ? "" : query.trim();
        }

        boolean finished() {
            return searchFinished && pending.isEmpty() &&
                    fapelloCatalogLoaded && fapelloPending.isEmpty() &&
                    wikiFeetCatalogLoaded && wikiFeetXCatalogLoaded &&
                    wikiFeetPending.isEmpty() &&
                    onlyHavenCatalogLoaded && onlyHavenPending.isEmpty();
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
        final FapelloRepository.MediaPage media;
        final Exception error;

        FapelloPage(
                FapelloCursor cursor,
                FapelloRepository.MediaPage media,
                Exception error
        ) {
            this.cursor = cursor;
            this.media = media;
            this.error = error;
        }
    }

    private static final class OnlyHavenCursor {
        final OnlyHavenRepository.Creator creator;
        int nextPage = 1;
        int failures;

        OnlyHavenCursor(OnlyHavenRepository.Creator creator) {
            this.creator = creator;
        }
    }

    private static final class OnlyHavenPage {
        final OnlyHavenCursor cursor;
        final List<NativeContentItem> items;
        final Exception error;

        OnlyHavenPage(OnlyHavenCursor cursor, List<NativeContentItem> items, Exception error) {
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
