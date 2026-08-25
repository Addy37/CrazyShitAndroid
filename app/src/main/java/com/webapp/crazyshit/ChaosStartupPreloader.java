package com.webapp.crazyshit;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One-shot process startup cache used to overlap the splash animation with useful Chaos work.
 * The first regular item is exposed as soon as its feed metadata arrives, then its playable page
 * is resolved once during the remaining splash time to warm DNS/TLS/page caches before Chaos asks
 * for the same clip. Shit Show keeps its proven WebView warmup path once native Chaos exists.
 */
final class ChaosStartupPreloader {
    private static final int STARTER_ITEMS = 1;
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
            return finished;
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
        CrazyShitRepository repository = new CrazyShitRepository();
        NativeContentItem first = null;
        try {
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
                first = candidates.get(0);
                synchronized (LOCK) {
                    READY.clear();
                    READY.add(first);
                }
                break;
            }

            // Warm the exact first clip while the branded intro is still on screen. The native
            // feed keeps the original story/page URL and resolves it normally again if necessary,
            // so this cannot change comments, sharing, history, or Shit Show routing semantics.
            if (first != null) {
                try {
                    repository.resolvePlayable(context, first.url);
                } catch (Exception ignored) {
                }
            }
        } finally {
            synchronized (LOCK) {
                finished = true;
                LOCK.notifyAll();
            }
        }
    }
}
