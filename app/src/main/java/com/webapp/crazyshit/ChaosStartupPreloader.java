package com.webapp.crazyshit;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One-shot process startup cache used to overlap the splash animation with the first Chaos feed
 * request. It deliberately preloads only regular feed metadata. Shit Show keeps its proven WebView
 * warmup path once the native Chaos screen exists.
 */
final class ChaosStartupPreloader {
    private static final int STARTER_ITEMS = 6;
    private static final Object LOCK = new Object();
    private static final ArrayList<NativeContentItem> READY = new ArrayList<>();

    private static boolean started;
    private static boolean finished;

    private ChaosStartupPreloader() {
    }

    static void start(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (started) return;
            started = true;
        }

        Context appContext = context.getApplicationContext();
        Context safeContext = appContext != null ? appContext : context;
        Thread worker = new Thread(() -> load(safeContext), "ChaosStartupPreload");
        worker.setDaemon(true);
        worker.start();
    }

    static boolean isReady() {
        synchronized (LOCK) {
            return finished || !READY.isEmpty();
        }
    }

    static List<NativeContentItem> takeStarter() {
        synchronized (LOCK) {
            if (READY.isEmpty()) return new ArrayList<>();
            ArrayList<NativeContentItem> result = new ArrayList<>(READY);
            READY.clear();
            return result;
        }
    }

    private static void load(Context context) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        try {
            CrazyShitRepository repository = new CrazyShitRepository();
            Random random = new Random();
            ArrayList<String> sources = new ArrayList<>(Arrays.asList(
                    CrazyShitRepository.HOME,
                    CrazyShitRepository.TRENDING,
                    CrazyShitRepository.BASE + "videos/",
                    CrazyShitRepository.BASE + "submissions/"
            ));
            Collections.shuffle(sources, random);

            for (String url : sources) {
                ArrayList<NativeContentItem> candidates = new ArrayList<>();
                try {
                    for (NativeContentItem item : repository.fetchFeed(context, url, 1)) {
                        if (item == null || item.url == null || item.url.isEmpty()) continue;
                        if (!NativeContentItem.KIND_MEDIA.equals(item.kind)) continue;
                        candidates.add(item);
                    }
                } catch (Exception ignored) {
                }
                if (candidates.isEmpty()) continue;

                Collections.shuffle(candidates, random);
                int take = Math.min(STARTER_ITEMS, candidates.size());
                result.addAll(candidates.subList(0, take));
                break;
            }
        } finally {
            synchronized (LOCK) {
                READY.clear();
                READY.addAll(result);
                finished = true;
                LOCK.notifyAll();
            }
        }
    }
}
