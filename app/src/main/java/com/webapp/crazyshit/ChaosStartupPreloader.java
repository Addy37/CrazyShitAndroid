package com.webapp.crazyshit;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * One-shot process startup cache used to overlap the splash animation with useful Chaos work.
 * A small regular-video starter queue is exposed as soon as one feed page arrives, then the first
 * item's playable page is resolved during the remaining splash time and handed to Chaos when ready.
 * Shit Show
 * keeps its proven WebView warmup path once native Chaos exists.
 */
final class ChaosStartupPreloader {
    static final int STARTER_ITEMS = 6;
    private static final Object LOCK = new Object();
    private static final ArrayList<NativeContentItem> READY = new ArrayList<>();
    private static final java.util.HashMap<String, CrazyShitRepository.StreamInfo> RESOLVED =
            new java.util.HashMap<>();

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
            // NativeMainActivity can begin underneath the matching wordmark as soon as the first
            // item exists. The resolver is allowed to keep warming in parallel while the handoff
            // overlay waits for Media3's actual first rendered frame.
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

    static CrazyShitRepository.StreamInfo takeResolved(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) return null;
        synchronized (LOCK) {
            return RESOLVED.remove(pageUrl);
        }
    }

    private static void load(Context context) {
        CrazyShitRepository repository = new CrazyShitRepository();
        NativeContentItem first = null;
        try {
            Random random = new Random();
            List<NativeContentItem> starterItems = ChaosStarterSources.first(
                    ChaosStarterSources.live(context, repository, random),
                    random, ChaosStarterSources.STARTUP_MILLIS);
            if (!starterItems.isEmpty()) {
                first = starterItems.get(0);
                synchronized (LOCK) {
                    READY.clear();
                    READY.addAll(starterItems);
                }
            }

            // Warm the exact first clip while the branded intro is still on screen. The native
            // feed keeps the original story/page URL and resolves it normally if the handoff is
            // not ready, so comments, sharing, history, and Shit Show routing stay unchanged.
            if (first != null) {
                try {
                    CrazyShitRepository.StreamInfo stream =
                            PlayableSourceRouter.resolve(context, first);
                    if (stream != null) {
                        synchronized (LOCK) {
                            RESOLVED.put(first.url, stream);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (LOCK) {
                finished = true;
                LOCK.notifyAll();
            }
        }
    }
}
