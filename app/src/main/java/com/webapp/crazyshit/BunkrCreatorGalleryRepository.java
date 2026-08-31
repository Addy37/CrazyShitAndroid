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

/** Incrementally combines every album returned by one Balbums creator search. */
final class BunkrCreatorGalleryRepository {
    private static final int MAX_SESSIONS = 4;
    private static final int MAX_SEARCH_PAGES = 5;
    private static final int ALBUMS_PER_BATCH = 4;
    private static final int BATCH_TARGET = 48;
    private static final int MAX_MEDIA_ITEMS = 10_000;
    private static final long ALBUM_BATCH_BUDGET_MS = 16_000L;
    private static final ExecutorService ALBUM_IO = Executors.newFixedThreadPool(4);
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
        loadAlbumsIfNeeded(context, state);
        if (state.pending.isEmpty()) {
            return new Batch(new ArrayList<>(), state.searchFinished);
        }

        ArrayList<AlbumCursor> selected = new ArrayList<>();
        while (!state.pending.isEmpty() && selected.size() < ALBUMS_PER_BATCH) {
            selected.add(state.pending.removeFirst());
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

        ArrayList<NativeContentItem> result = new ArrayList<>();
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
                applyPage(state, result, future.get());
                if (result.size() >= BATCH_TARGET) break;
            } catch (Exception ignored) {
                retry(state, requests.get(future));
            }
        }

        for (Map.Entry<Future<AlbumPage>, AlbumCursor> request : requests.entrySet()) {
            if (finishedRequests.contains(request.getKey())) continue;
            request.getKey().cancel(true);
            retry(state, request.getValue());
        }

        if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) {
            state.pending.clear();
            state.searchFinished = true;
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
        final Set<String> albumUrls = new HashSet<>();
        final Set<String> loadedMediaUrls = new HashSet<>();
        int nextSearchPage = 1;
        boolean searchFinished;

        State(String query) {
            this.query = query == null ? "" : query.trim();
        }

        boolean finished() {
            return searchFinished && pending.isEmpty();
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
}
