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

/** Builds the monthly creator shelf from matching Bunkr albums with Fapello fallbacks. */
public final class PopularCreatorRepository {
    public static final String SHELF_TITLE = "Top 50 creators this month";
    public static final String SHELF_HINT = "Bunkr + Fapello in one gallery";

    private static final String PREFS = "popular_creator_feed_v3";
    private static final String LEGACY_PREFS = "popular_creator_feed_v2";
    private static final String KEY_UPDATED = "updated";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 50;
    private static final int PROGRESS_STEP = 10;
    private static final long CACHE_AGE_MS = TimeUnit.HOURS.toMillis(24);
    private static final long FETCH_BUDGET_MS = 42_000L;
    private static final Pattern FILE_COUNT = Pattern.compile("(?i)([0-9,]+)\\s+files?");

    public interface ProgressListener {
        void onProgress(List<NativeContentItem> items);
    }

    // This is a curated shelf, not an official OnlyFans ranking. Extra names provide fallbacks
    // when Fapzone has no current media matching one of the first fifty creators.
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
            new Creator("Amber Rose"),
            new Creator("Coco Austin"),
            new Creator("Carmen Electra"),
            new Creator("Salice Rose"),
            new Creator("Erica Mena"),
            new Creator("Skylar Mae"),
            new Creator("Jessica Nigri"),
            new Creator("Ana Cheri"),
            new Creator("Chloe Cherry"),
            new Creator("Megan Barton-Hanson", "meganbartonhanson"),
            new Creator("Key Alves"),
            new Creator("Savannah Bond"),
            new Creator("Ebanie Bridges"),
            new Creator("Asa Akira"),
            new Creator("Mia Malkova"),
            new Creator("Alexis Texas"),
            new Creator("Brandi Love"),
            new Creator("Kendra Lust"),
            new Creator("Kendra Sunderland"),
            new Creator("Eva Elfie"),
            new Creator("Autumn Falls"),
            new Creator("Nicole Aniston"),
            new Creator("Ava Addams"),
            new Creator("Dani Daniels"),
            new Creator("Vanna Bardot"),
            new Creator("Skylar Vox"),
            new Creator("Gianna Dior"),
            new Creator("Alina Lopez"),
            new Creator("Gabbie Carter"),
            new Creator("Leah Gotti"),
            new Creator("Jessa Rhodes"),
            new Creator("Lena Paul"),
            new Creator("Violet Myers"),
            new Creator("Rae Lil Black", "raelilblack"),
            new Creator("Alexis Fawx"),
            new Creator("Adriana Chechik"),
            new Creator("Elsa Jean"),
            new Creator("Piper Perri"),
            new Creator("Dillon Harper"),
            new Creator("Remy LaCroix", "remylacroix"),
            new Creator("Ariella Ferrera"),
            new Creator("Kayden Kross"),
            new Creator("Lauren Phillips"),
            new Creator("Madison Ivy"),
            new Creator("Romi Rain"),
            new Creator("Phoenix Marie"),
            new Creator("Cherie DeVille", "cheriedeville"),
            new Creator("Jailyne Ojeda"),
            new Creator("Holly Sonders"),
            new Creator("Ana Lorde"),
            new Creator("Jem Wolfie"),
            new Creator("Amanda Cerny"),
            new Creator("Demi Rose"),
            new Creator("Lyna Perez"),
            new Creator("Sommer Ray"),
            new Creator("Lindsey Pelas"),
            new Creator("Katie Sigmond"),
            new Creator("Nala Ray"),
            new Creator("Kira Noir"),
            new Creator("Kazumi"),
            new Creator("Emma Magnolia"),
            new Creator("Gali Golan"),
            new Creator("Teanna Trump")
    };

    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final FapelloRepository fapelloRepository = new FapelloRepository();

    public List<NativeContentItem> fetch(Context context) throws IOException {
        return fetch(context, null);
    }

    public List<NativeContentItem> fetch(
            Context context,
            ProgressListener progressListener
    ) throws IOException {
        Context appContext = context.getApplicationContext();
        List<NativeContentItem> cached = readCache(appContext, PREFS, false, MAX_ITEMS);
        if (cached.size() >= MAX_ITEMS) return cached;

        List<NativeContentItem> warm = cached.isEmpty()
                ? readCache(appContext, LEGACY_PREFS, true, 20)
                : cached;
        if (progressListener != null && !warm.isEmpty()) {
            progressListener.onProgress(new ArrayList<>(warm));
        }

        ExecutorService workers = Executors.newFixedThreadPool(12);
        ExecutorCompletionService<Match> completed = new ExecutorCompletionService<>(workers);
        for (int index = 0; index < CREATORS.length; index++) {
            final int rank = index;
            final Creator creator = CREATORS[index];
            completed.submit(() -> resolve(appContext, rank, creator));
        }

        ArrayList<Match> matches = new ArrayList<>();
        int lastPublished = warm.size();
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
                        if (progressListener != null) {
                            ArrayList<NativeContentItem> progress = buildItems(matches);
                            int nextMilestone = Math.min(
                                    MAX_ITEMS,
                                    ((lastPublished / PROGRESS_STEP) + 1) * PROGRESS_STEP
                            );
                            if (progress.size() >= nextMilestone) {
                                lastPublished = progress.size();
                                progressListener.onProgress(progress);
                            }
                        }
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

        ArrayList<NativeContentItem> result = buildItems(matches);
        result = mergeWarm(result, warm);
        if (progressListener != null && !result.isEmpty()) {
            progressListener.onProgress(new ArrayList<>(result));
        }

        if (result.size() >= MAX_ITEMS) {
            writeCache(appContext, result);
            return result;
        }

        List<NativeContentItem> stale = readCache(appContext, PREFS, true, MAX_ITEMS);
        if (stale.size() >= MAX_ITEMS) return stale;
        if (!result.isEmpty()) return result;
        throw new IOException("No popular creator albums were available");
    }

    private ArrayList<NativeContentItem> buildItems(List<Match> matches) {
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
                    String.valueOf(match.rank + 1),
                    "",
                    "",
                    "",
                    match.creator.name
            ));
            if (result.size() >= MAX_ITEMS) break;
        }
        return result;
    }

    private ArrayList<NativeContentItem> mergeWarm(
            List<NativeContentItem> resolved,
            List<NativeContentItem> warm
    ) {
        ArrayList<NativeContentItem> merged = new ArrayList<>();
        if (resolved != null) merged.addAll(resolved);
        if (warm != null) {
            for (NativeContentItem candidate : warm) {
                if (candidate == null || containsCreator(merged, candidate.title)) continue;
                merged.add(candidate);
            }
        }
        Collections.sort(merged, Comparator.comparingInt(item -> creatorRank(item.title)));
        if (merged.size() > MAX_ITEMS) {
            return new ArrayList<>(merged.subList(0, MAX_ITEMS));
        }
        return merged;
    }

    private boolean containsCreator(List<NativeContentItem> items, String name) {
        if (name == null) return false;
        for (NativeContentItem item : items) {
            if (item != null && name.equalsIgnoreCase(item.title)) return true;
        }
        return false;
    }

    private int creatorRank(String name) {
        for (int index = 0; index < CREATORS.length; index++) {
            if (CREATORS[index].name.equalsIgnoreCase(name == null ? "" : name)) return index;
        }
        return Integer.MAX_VALUE;
    }

    private Match resolve(Context context, int rank, Creator creator) {
        try {
            List<NativeContentItem> albums = bunkrRepository.searchAlbums(context, creator.name, 1);
            NativeContentItem album = chooseAlbum(creator, albums);
            if (album != null) return new Match(rank, creator, album);
        } catch (Exception ignored) {
        }
        try {
            FapelloRepository.Model model = chooseModel(
                    creator,
                    fapelloRepository.searchModels(context, creator.name, 4)
            );
            if (model == null) return null;
            String preview = model.imageUrl;
            if (preview == null || preview.trim().isEmpty()) {
                preview = chooseFapelloPreview(
                        fapelloRepository.fetchModelMedia(context, model, 1)
                );
            }
            NativeContentItem source = new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    model.name,
                    model.url,
                    preview,
                    "Fapello",
                    "",
                    "",
                    "Fapello"
            );
            return new Match(rank, creator, source);
        } catch (Exception ignored) {
            return null;
        }
    }

    private FapelloRepository.Model chooseModel(
            Creator creator,
            List<FapelloRepository.Model> models
    ) {
        if (models == null) return null;
        FapelloRepository.Model best = null;
        int bestScore = -1;
        for (FapelloRepository.Model model : models) {
            if (model == null || !FapelloRepository.isModelUrl(model.url)) continue;
            int score = creator.matchScore(compact(model.name));
            if (score > bestScore) {
                best = model;
                bestScore = score;
            }
        }
        return bestScore < 0 ? null : best;
    }

    private String chooseFapelloPreview(List<NativeContentItem> media) {
        if (media == null) return "";
        for (NativeContentItem item : media) {
            if (item != null && item.isVideo() && item.imageUrl != null &&
                    !item.imageUrl.trim().isEmpty()) return item.imageUrl.trim();
        }
        for (NativeContentItem item : media) {
            if (item != null && item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
                return item.imageUrl.trim();
            }
        }
        return "";
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

    private List<NativeContentItem> readCache(
            Context context,
            String preferencesName,
            boolean allowStale,
            int limit
    ) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    preferencesName,
                    Context.MODE_PRIVATE
            );
            long updated = prefs.getLong(KEY_UPDATED, 0L);
            if (!allowStale && (updated <= 0L || System.currentTimeMillis() - updated > CACHE_AGE_MS)) {
                return result;
            }
            JSONArray values = new JSONArray(prefs.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < values.length() && result.size() < limit; i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                String name = value.optString("name", "").trim();
                String query = value.optString("query", name).trim();
                String url = value.optString("url", "").trim();
                if (name.isEmpty() || query.isEmpty() ||
                        (!BunkrRepository.isAlbumUrl(url) &&
                                !FapelloRepository.isModelUrl(url))) continue;
                result.add(new NativeContentItem(
                        NativeContentItem.KIND_CREATOR,
                        name,
                        url,
                        value.optString("image", ""),
                        value.optString("rank", ""),
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
                value.put("rank", item.views);
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
