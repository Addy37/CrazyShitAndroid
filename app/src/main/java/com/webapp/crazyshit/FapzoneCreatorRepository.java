package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds Fapzone creator shelves whose profiles open the unified multi-source gallery. */
final class FapzoneCreatorRepository {
    static final int MODE_TOP_50 = 0;
    static final int MODE_NEW = 1;
    static final int MODE_HOT = 2;
    static final int MODE_POPULAR = 3;

    private static final int LIVE_ITEMS = 30;
    private static final int LIVE_PAGES = 4;
    private static final int PROGRESS_STEP = 6;
    private static final long CACHE_AGE_MS = TimeUnit.HOURS.toMillis(6);
    private static final long FETCH_BUDGET_MS = 45_000L;
    private static final Pattern FILE_COUNT = Pattern.compile("(?i)([0-9,]+)\\s+files?");

    interface ProgressListener {
        void onProgress(List<NativeContentItem> items);
    }

    private final PopularCreatorRepository topCreators = new PopularCreatorRepository();
    private final FapelloRepository fapello = new FapelloRepository();
    private final BunkrRepository bunkr = new BunkrRepository();

    List<NativeContentItem> fetch(
            Context context,
            int mode,
            ProgressListener listener
    ) throws IOException {
        if (mode == MODE_TOP_50) {
            return topCreators.fetch(context, listener == null
                    ? null
                    : listener::onProgress);
        }
        String listing = listingFor(mode);
        Context appContext = context.getApplicationContext();
        List<NativeContentItem> fresh = readCache(appContext, mode, false);
        if (fresh.size() >= LIVE_ITEMS) return fresh;
        List<NativeContentItem> stale = fresh.isEmpty()
                ? readCache(appContext, mode, true)
                : fresh;
        if (listener != null && !stale.isEmpty()) {
            listener.onProgress(new ArrayList<>(stale));
        }

        LinkedHashMap<String, FapelloRepository.Model> models = new LinkedHashMap<>();
        IOException listingError = null;
        boolean usedBrowserFallback = false;
        boolean attemptedBrowserFallback = false;
        for (int page = 1; page <= LIVE_PAGES && models.size() < LIVE_ITEMS; page++) {
            List<FapelloRepository.Model> pageModels = null;
            try {
                pageModels = fapello.fetchModelListing(appContext, listing, page);
            } catch (IOException error) {
                listingError = error;
            }
            if ((pageModels == null || pageModels.isEmpty()) && models.isEmpty() &&
                    !attemptedBrowserFallback) {
                attemptedBrowserFallback = true;
                try {
                    pageModels = FapelloWebViewFetcher.fetchModelListing(context, listing, page);
                    usedBrowserFallback = true;
                } catch (IOException browserError) {
                    listingError = browserError;
                }
            }
            if (pageModels == null || pageModels.isEmpty()) {
                if (models.isEmpty() && !attemptedBrowserFallback) continue;
                break;
            }
            for (FapelloRepository.Model model : pageModels) {
                if (model == null || !FapelloRepository.isModelUrl(model.url)) continue;
                models.putIfAbsent(model.url, model);
                if (models.size() >= LIVE_ITEMS) break;
            }
            // One browser-rendered page is enough to restore the shelf without making the user
            // wait for several sequential hidden page loads.
            if (usedBrowserFallback) break;
        }
        if (models.isEmpty()) {
            if (!stale.isEmpty()) return stale;
            throw listingError == null
                    ? new IOException("No Fapello creators were available")
                    : listingError;
        }

        ArrayList<FapelloRepository.Model> ordered = new ArrayList<>(models.values());
        ExecutorService workers = Executors.newFixedThreadPool(10);
        ExecutorCompletionService<ResolvedCreator> completed =
                new ExecutorCompletionService<>(workers);
        for (int index = 0; index < ordered.size(); index++) {
            int rank = index;
            FapelloRepository.Model model = ordered.get(index);
            completed.submit(() -> resolve(appContext, rank, model));
        }

        ArrayList<ResolvedCreator> resolved = new ArrayList<>();
        int lastPublished = stale.size();
        long deadline = SystemClock.elapsedRealtime() + FETCH_BUDGET_MS;
        try {
            for (int i = 0; i < ordered.size(); i++) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) break;
                Future<ResolvedCreator> future = completed.poll(remaining, TimeUnit.MILLISECONDS);
                if (future == null) break;
                try {
                    ResolvedCreator creator = future.get();
                    if (creator == null) continue;
                    resolved.add(creator);
                    if (listener != null) {
                        ArrayList<NativeContentItem> progress = buildItems(resolved);
                        int milestone = Math.min(
                                LIVE_ITEMS,
                                ((lastPublished / PROGRESS_STEP) + 1) * PROGRESS_STEP
                        );
                        if (progress.size() >= milestone) {
                            lastPublished = progress.size();
                            listener.onProgress(progress);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            workers.shutdownNow();
        }

        ArrayList<NativeContentItem> result = mergeWarm(buildItems(resolved), stale);
        if (listener != null && !result.isEmpty()) {
            listener.onProgress(new ArrayList<>(result));
        }
        if (!result.isEmpty()) {
            writeCache(appContext, mode, result);
            return result;
        }
        if (!stale.isEmpty()) return stale;
        throw new IOException("No Fapzone creators were available");
    }

    static String titleFor(int mode) {
        if (mode == MODE_NEW) return "New creators";
        if (mode == MODE_HOT) return "Hot creators";
        if (mode == MODE_POPULAR) return "Popular creators";
        return PopularCreatorRepository.SHELF_TITLE;
    }

    static String hintFor(int mode) {
        if (mode == MODE_NEW) return "Recently added on Fapello • one combined gallery";
        if (mode == MODE_HOT) return "Hot on Fapello • matched across Fapzone";
        if (mode == MODE_POPULAR) return "Popular on Fapello • matched across Fapzone";
        return PopularCreatorRepository.SHELF_HINT;
    }

    static String badgeFor(int mode) {
        if (mode == MODE_NEW) return "NEW";
        if (mode == MODE_HOT) return "HOT";
        if (mode == MODE_POPULAR) return "POP";
        return "50";
    }

    private ResolvedCreator resolve(
            Context context,
            int rank,
            FapelloRepository.Model model
    ) {
        NativeContentItem album = null;
        try {
            album = chooseAlbum(model.name, bunkr.searchAlbums(context, model.name, 1));
        } catch (Exception ignored) {
        }

        String fapelloPreview = clean(model.imageUrl);
        if (fapelloPreview.isEmpty()) {
            try {
                fapelloPreview = chooseFapelloPreview(fapello.fetchModelMedia(context, model, 1));
            } catch (Exception ignored) {
            }
        }

        // Keep the exact Fapello profile on the card. The creator gallery uses it directly
        // instead of having to rediscover or guess the profile slug from the display name.
        String cardUrl = model.url;
        String bunkrPreview = album == null ? "" : clean(album.imageUrl);
        String imageUrl = chooseThumbnail(fapelloPreview, bunkrPreview);
        String imageReferer = imageUrl.equals(fapelloPreview)
                ? model.url
                : album == null ? model.url : album.url;
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                model.name,
                cardUrl,
                imageUrl,
                String.valueOf(rank + 1),
                imageReferer,
                "",
                "Bunkr + Fapello + WikiFeet + WikiFeet X",
                model.name
        );
        return new ResolvedCreator(rank, item);
    }

    private NativeContentItem chooseAlbum(String creatorName, List<NativeContentItem> albums) {
        if (albums == null || albums.isEmpty()) return null;
        String creator = compact(creatorName);
        NativeContentItem best = null;
        long bestScore = Long.MIN_VALUE;
        for (NativeContentItem album : albums) {
            if (album == null || !BunkrRepository.isAlbumUrl(album.url)) continue;
            String title = compact(album.title);
            int nameScore = title.equals(creator) ? 3 : title.contains(creator) ? 2 : -1;
            if (creator.isEmpty() || nameScore < 0) continue;
            long score = (nameScore * 1_000_000L) + fileCount(album.description);
            if (best == null || score > bestScore) {
                best = album;
                bestScore = score;
            }
        }
        return best;
    }

    private String chooseFapelloPreview(List<NativeContentItem> media) {
        if (media == null) return "";
        for (NativeContentItem item : media) {
            if (item != null && item.isVideo() && !clean(item.imageUrl).isEmpty()) {
                return clean(item.imageUrl);
            }
        }
        for (NativeContentItem item : media) {
            if (item != null && !clean(item.imageUrl).isEmpty()) return clean(item.imageUrl);
        }
        return "";
    }

    private String chooseThumbnail(String fapelloUrl, String bunkrUrl) {
        int fapelloScore = thumbnailScore(fapelloUrl, true);
        int bunkrScore = thumbnailScore(bunkrUrl, false);
        return fapelloScore >= bunkrScore ? clean(fapelloUrl) : clean(bunkrUrl);
    }

    private int thumbnailScore(String value, boolean fapelloSource) {
        String url = clean(value);
        if (url.isEmpty()) return Integer.MIN_VALUE;
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("load.svg") || lower.contains("/data/avatars/default/") ||
                lower.contains("placeholder") || lower.contains("/banners/")) {
            return -1000;
        }
        int score = 20;
        if (lower.contains("/content/") || lower.contains("poster")) score += 35;
        if (lower.contains("_300px") || lower.contains("thumb")) score += 15;
        if (fapelloSource) score += 8;
        return score;
    }

    private long fileCount(String description) {
        Matcher matcher = FILE_COUNT.matcher(description == null ? "" : description);
        if (!matcher.find()) return 0L;
        try {
            return Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private ArrayList<NativeContentItem> buildItems(List<ResolvedCreator> creators) {
        creators.sort(Comparator.comparingInt(value -> value.rank));
        ArrayList<NativeContentItem> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ResolvedCreator creator : creators) {
            if (creator == null || creator.item == null ||
                    !names.add(compact(creator.item.title))) continue;
            result.add(creator.item);
            if (result.size() >= LIVE_ITEMS) break;
        }
        return result;
    }

    private ArrayList<NativeContentItem> mergeWarm(
            List<NativeContentItem> resolved,
            List<NativeContentItem> warm
    ) {
        LinkedHashMap<String, NativeContentItem> merged = new LinkedHashMap<>();
        if (resolved != null) {
            for (NativeContentItem item : resolved) {
                if (item != null) merged.putIfAbsent(compact(item.title), item);
            }
        }
        if (warm != null) {
            for (NativeContentItem item : warm) {
                if (item != null) merged.putIfAbsent(compact(item.title), item);
            }
        }
        ArrayList<NativeContentItem> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingInt(item -> parseRank(item.views)));
        if (result.size() > LIVE_ITEMS) {
            return new ArrayList<>(result.subList(0, LIVE_ITEMS));
        }
        return result;
    }

    private List<NativeContentItem> readCache(Context context, int mode, boolean allowStale) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(cacheName(mode), Context.MODE_PRIVATE);
            long updated = prefs.getLong("updated", 0L);
            if (!allowStale && (updated <= 0L ||
                    System.currentTimeMillis() - updated > CACHE_AGE_MS)) return result;
            JSONArray values = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < values.length() && result.size() < LIVE_ITEMS; i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                String name = clean(value.optString("name", ""));
                String url = clean(value.optString("url", ""));
                String query = clean(value.optString("query", name));
                if (name.isEmpty() || query.isEmpty() ||
                        (!BunkrRepository.isAlbumUrl(url) && !FapelloRepository.isModelUrl(url))) {
                    continue;
                }
                result.add(new NativeContentItem(
                        NativeContentItem.KIND_CREATOR,
                        name,
                        url,
                        value.optString("image", ""),
                        value.optString("rank", ""),
                        value.optString("referer", url),
                        "",
                        "Bunkr + Fapello + WikiFeet + WikiFeet X",
                        query
                ));
            }
        } catch (Exception ignored) {
            result.clear();
        }
        return result;
    }

    private void writeCache(Context context, int mode, List<NativeContentItem> items) {
        try {
            JSONArray values = new JSONArray();
            for (NativeContentItem item : items) {
                JSONObject value = new JSONObject();
                value.put("name", item.title);
                value.put("url", item.url);
                value.put("query", item.searchQuery);
                value.put("image", item.imageUrl);
                value.put("referer", item.uploader);
                value.put("rank", item.views);
                values.put(value);
            }
            context.getSharedPreferences(cacheName(mode), Context.MODE_PRIVATE)
                    .edit()
                    .putLong("updated", System.currentTimeMillis())
                    .putString("items", values.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private String listingFor(int mode) throws IOException {
        if (mode == MODE_NEW) return FapelloRepository.LIST_NEW;
        if (mode == MODE_HOT) return FapelloRepository.LIST_HOT;
        if (mode == MODE_POPULAR) return FapelloRepository.LIST_POPULAR;
        throw new IOException("Unknown Fapzone creator mode");
    }

    private String cacheName(int mode) {
        return "fapzone_creator_feed_v2_" + mode;
    }

    private int parseRank(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static String compact(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static final class ResolvedCreator {
        final int rank;
        final NativeContentItem item;

        ResolvedCreator(int rank, NativeContentItem item) {
            this.rank = rank;
            this.item = item;
        }
    }
}
