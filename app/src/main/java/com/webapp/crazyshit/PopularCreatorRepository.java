package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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

/** Builds the monthly creator shelf from real Balbums search matches. */
public final class PopularCreatorRepository {
    public static final String SHELF_TITLE = "Top 20 creators this month";
    public static final String SHELF_HINT = "Tap a creator for one combined gallery";

    private static final String PREFS = "popular_creator_feed_v2";
    private static final String KEY_UPDATED = "updated";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 20;
    private static final long CACHE_AGE_MS = TimeUnit.HOURS.toMillis(24);
    private static final long FETCH_BUDGET_MS = 22_000L;
    private static final Pattern FILE_COUNT = Pattern.compile("(?i)([0-9,]+)\\s+files?");

    // This is a curated shelf, not an official OnlyFans ranking. Extra names provide fallbacks
    // when Balbums has no current album matching one of the first twenty creators.
    private static final Creator[] CREATORS = {
            new Creator("Sophie Rain", "sophieraiin"),
            new Creator("Bonnie Blue"),
            new Creator("Lily Phillips"),
            new Creator("Bhad Bhabie"),
            new Creator("Amouranth"),
            new Creator("Sky Bri", "realskybri"),
            new Creator("Breckie Hill"),
            new Creator("Camilla Araujo"),
            new Creator("Ari Kytsya", "arikytsya"),
            new Creator("Belle Delphine"),
            new Creator("Corinna Kopf"),
            new Creator("Bella Thorne"),
            new Creator("Paige VanZant"),
            new Creator("Lena The Plug", "lenatheplug"),
            new Creator("Aishah Sofey", "aishahsofey"),
            new Creator("Marie Temara"),
            new Creator("Karely Ruiz"),
            new Creator("Mia Khalifa"),
            new Creator("Blac Chyna"),
            new Creator("Tana Mongeau"),
            new Creator("Cardi B"),
            new Creator("Iggy Azalea"),
            new Creator("Annie Knight"),
            new Creator("Jameliz"),
            new Creator("Morgpie"),
            new Creator("Meg Turney"),
            new Creator("Angela White"),
            new Creator("Abella Danger"),
            new Creator("Riley Reid"),
            new Creator("Lana Rhoades"),
            new Creator("Emily Black"),
            new Creator("Astrid Wett"),
            new Creator("Elle Brooke"),
            new Creator("Grace Charis"),
            new Creator("Sami Sheen"),
            new Creator("Denise Richards"),
            new Creator("Trisha Paytas"),
            new Creator("Amber Rose")
    };

    private final BunkrRepository bunkrRepository = new BunkrRepository();

    public List<NativeContentItem> fetch(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        List<NativeContentItem> cached = readCache(appContext, false);
        if (cached.size() >= MAX_ITEMS) return cached;

        ExecutorService workers = Executors.newFixedThreadPool(8);
        ExecutorCompletionService<Match> completed = new ExecutorCompletionService<>(workers);
        for (int index = 0; index < CREATORS.length; index++) {
            final int rank = index;
            final Creator creator = CREATORS[index];
            completed.submit(() -> resolve(appContext, rank, creator));
        }

        ArrayList<Match> matches = new ArrayList<>();
        long deadline = SystemClock.elapsedRealtime() + FETCH_BUDGET_MS;
        try {
            for (int i = 0; i < CREATORS.length; i++) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) break;
                Future<Match> future = completed.poll(remaining, TimeUnit.MILLISECONDS);
                if (future == null) break;
                try {
                    Match match = future.get();
                    if (match != null) {
                        matches.add(match);
                        if (matches.size() >= MAX_ITEMS) break;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            workers.shutdownNow();
        }

        Collections.sort(matches, Comparator.comparingInt(match -> match.rank));
        ArrayList<NativeContentItem> result = new ArrayList<>();
        Set<String> creators = new HashSet<>();
        for (Match match : matches) {
            if (match == null || !creators.add(match.creator.name)) continue;
            NativeContentItem album = match.album;
            result.add(new NativeContentItem(
                    NativeContentItem.KIND_CREATOR,
                    match.creator.name,
                    album.url,
                    album.imageUrl,
                    "",
                    "",
                    "",
                    "",
                    match.creator.name
            ));
            if (result.size() >= MAX_ITEMS) break;
        }

        if (result.size() >= MAX_ITEMS) {
            writeCache(appContext, result);
            return result;
        }

        List<NativeContentItem> stale = readCache(appContext, true);
        if (stale.size() >= MAX_ITEMS) return stale;
        if (!result.isEmpty()) return result;
        throw new IOException("No popular creator albums were available");
    }

    private Match resolve(Context context, int rank, Creator creator) {
        try {
            List<NativeContentItem> albums = bunkrRepository.searchAlbums(context, creator.name, 1);
            NativeContentItem album = chooseAlbum(creator, albums);
            return album == null ? null : new Match(rank, creator, album);
        } catch (Exception ignored) {
            return null;
        }
    }

    private NativeContentItem chooseAlbum(Creator creator, List<NativeContentItem> albums) {
        if (albums == null || albums.isEmpty()) return null;
        NativeContentItem best = null;
        long bestScore = Long.MIN_VALUE;
        for (NativeContentItem album : albums) {
            if (album == null || album.url == null || album.url.trim().isEmpty()) continue;
            String title = compact(album.title);
            int nameScore = creator.matchScore(title);
            if (nameScore < 0) continue;
            long score = ((long) nameScore * 1_000_000L) + fileCount(album.description);
            if (best == null || score > bestScore) {
                best = album;
                bestScore = score;
            }
        }
        return best;
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

    private List<NativeContentItem> readCache(Context context, boolean allowStale) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long updated = prefs.getLong(KEY_UPDATED, 0L);
            if (!allowStale && (updated <= 0L || System.currentTimeMillis() - updated > CACHE_AGE_MS)) {
                return result;
            }
            JSONArray values = new JSONArray(prefs.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < values.length() && result.size() < MAX_ITEMS; i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                String name = value.optString("name", "").trim();
                String query = value.optString("query", name).trim();
                String url = value.optString("url", "").trim();
                if (name.isEmpty() || query.isEmpty() || !BunkrRepository.isAlbumUrl(url)) continue;
                result.add(new NativeContentItem(
                        NativeContentItem.KIND_CREATOR,
                        name,
                        url,
                        value.optString("image", ""),
                        "",
                        "",
                        "",
                        "",
                        query
                ));
            }
        } catch (Exception ignored) {
            result.clear();
        }
        return result;
    }

    private void writeCache(Context context, List<NativeContentItem> items) {
        try {
            JSONArray values = new JSONArray();
            for (NativeContentItem item : items) {
                JSONObject value = new JSONObject();
                value.put("name", item.title);
                value.put("query", item.searchQuery);
                value.put("url", item.url);
                value.put("image", item.imageUrl);
                values.put(value);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_UPDATED, System.currentTimeMillis())
                    .putString(KEY_ITEMS, values.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private static String compact(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static final class Creator {
        final String name;
        final String[] acceptedNames;

        Creator(String name, String... aliases) {
            this.name = name;
            this.acceptedNames = new String[aliases.length + 1];
            this.acceptedNames[0] = compact(name);
            for (int i = 0; i < aliases.length; i++) {
                this.acceptedNames[i + 1] = compact(aliases[i]);
            }
        }

        int matchScore(String compactTitle) {
            if (compactTitle == null || compactTitle.isEmpty()) return -1;
            int score = -1;
            for (String accepted : acceptedNames) {
                if (accepted.isEmpty()) continue;
                if (compactTitle.equals(accepted)) score = Math.max(score, 3);
                else if (compactTitle.contains(accepted)) score = Math.max(score, 2);
            }
            return score;
        }
    }

    private static final class Match {
        final int rank;
        final Creator creator;
        final NativeContentItem album;

        Match(int rank, Creator creator, NativeContentItem album) {
            this.rank = rank;
            this.creator = creator;
            this.album = album;
        }
    }
}
