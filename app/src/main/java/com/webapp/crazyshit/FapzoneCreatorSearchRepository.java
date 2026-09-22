package com.webapp.crazyshit;

import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Fast creator lookup across every source that feeds the unified Fapzone gallery. */
final class FapzoneCreatorSearchRepository {
    private static final ExecutorService SEARCH_IO = Executors.newFixedThreadPool(4);
    private static final long SEARCH_BUDGET_MS = 7_000L;

    List<NativeContentItem> search(Context context, String query, int limit) throws IOException {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.length() < 2) return new ArrayList<>();
        int safeLimit = Math.max(1, Math.min(20, limit));
        ExecutorCompletionService<List<NativeContentItem>> completed =
                new ExecutorCompletionService<>(SEARCH_IO);
        ArrayList<Future<List<NativeContentItem>>> requests = new ArrayList<>();
        requests.add(completed.submit(() -> fromFapello(context, cleanQuery, safeLimit)));
        requests.add(completed.submit(() -> fromWikiFeet(
                context, WikiFeetRepository.Site.WIKIFEET, cleanQuery, safeLimit)));
        requests.add(completed.submit(() -> fromWikiFeet(
                context, WikiFeetRepository.Site.WIKIFEET_X, cleanQuery, safeLimit)));
        requests.add(completed.submit(() -> fromOnlyHaven(context, cleanQuery, safeLimit)));

        LinkedHashMap<String, CreatorGroup> groups = new LinkedHashMap<>();
        int replies = 0;
        int successes = 0;
        long deadline = SystemClock.elapsedRealtime() + SEARCH_BUDGET_MS;
        while (replies < requests.size()) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) break;
            try {
                Future<List<NativeContentItem>> reply = completed.poll(remaining, TimeUnit.MILLISECONDS);
                if (reply == null) break;
                replies++;
                List<NativeContentItem> items = reply.get();
                successes++;
                if (items != null) for (NativeContentItem item : items) add(groups, item);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) { }
        }
        for (Future<?> request : requests) if (!request.isDone()) request.cancel(true);
        if (successes == 0) throw new IOException("OnlyFap creator search could not be reached");

        ArrayList<NativeContentItem> output = new ArrayList<>();
        for (CreatorGroup group : groups.values()) {
            output.add(group.item());
            if (output.size() >= safeLimit) break;
        }
        return output;
    }

    private List<NativeContentItem> fromFapello(Context context, String query, int limit)
            throws IOException {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (FapelloRepository.Model model :
                new FapelloRepository().searchConfirmedModels(context, query, limit)) {
            NativeContentItem item = CreatorCatalog.fromModel(model);
            result.add(new NativeContentItem(item.kind, item.title, item.url, item.imageUrl,
                    item.views, item.uploader, item.comments, "Fapello", item.searchQuery));
        }
        return result;
    }

    private List<NativeContentItem> fromWikiFeet(
            Context context,
            WikiFeetRepository.Site site,
            String query,
            int limit
    ) throws IOException {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (WikiFeetRepository.Creator creator :
                new WikiFeetRepository().searchCreators(context, site, query, limit)) {
            result.add(creator.asItem());
        }
        return result;
    }

    private List<NativeContentItem> fromOnlyHaven(
            Context context,
            String query,
            int limit
    ) throws IOException {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (OnlyHavenRepository.Creator creator :
                new OnlyHavenRepository().searchCreators(context, query, limit)) {
            result.add(new NativeContentItem(
                    NativeContentItem.KIND_CREATOR,
                    creator.name,
                    creator.url,
                    creator.imageUrl,
                    "",
                    creator.url,
                    "",
                    "OnlyHaven",
                    creator.name
            ));
        }
        return result;
    }

    private void add(Map<String, CreatorGroup> groups, NativeContentItem item) {
        if (item == null || !item.isCreator() || item.title.trim().isEmpty()) return;
        String key = CreatorNameMatcher.normalized(item.title);
        CreatorGroup group = groups.get(key);
        if (group == null) groups.put(key, new CreatorGroup(item));
        else group.add(item);
    }

    private static final class CreatorGroup {
        private NativeContentItem preferred;
        private String fapelloProfileUrl = "";
        private final Set<String> sources = new LinkedHashSet<>();

        CreatorGroup(NativeContentItem first) { add(first); }

        void add(NativeContentItem item) {
            if (FapelloRepository.isModelUrl(item.url)) fapelloProfileUrl = item.url;
            if (preferred == null || (preferred.imageUrl.isEmpty() && !item.imageUrl.isEmpty())) {
                preferred = item;
            } else {
                preferred = preferred.merge(item);
            }
            String label = item.description == null ? "" : item.description.split(" ·", 2)[0].trim();
            if (!label.isEmpty()) sources.add(label);
        }

        NativeContentItem item() {
            return new NativeContentItem(NativeContentItem.KIND_CREATOR, preferred.title,
                    fapelloProfileUrl.isEmpty() ? preferred.url : fapelloProfileUrl,
                    preferred.imageUrl, "", preferred.uploader, "",
                    sourceLabel(), preferred.searchQuery);
        }

        private String sourceLabel() {
            ArrayList<String> ordered = new ArrayList<>();
            for (String label : new String[]{"Fapello", "OnlyHaven", "WikiFeet", "WikiFeet X"}) {
                if (sources.contains(label)) ordered.add(label);
            }
            for (String label : sources) if (!ordered.contains(label)) ordered.add(label);
            return String.join(" + ", ordered);
        }
    }
}
