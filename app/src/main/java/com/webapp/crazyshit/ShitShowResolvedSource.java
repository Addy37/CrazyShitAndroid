package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Converts harvested Shit Show story permalinks into direct media URLs before they enter Chaos.
 */
final class ShitShowResolvedSource {
    private static final int MAX_RESOLVE_PER_BATCH = 4;
    private static final long BATCH_TIMEOUT_MS = 9_000L;

    private final ShitShowTapSource stories = new ShitShowTapSource();
    private final ExecutorService resolverPool = Executors.newFixedThreadPool(MAX_RESOLVE_PER_BATCH);
    private final Set<String> seenResolved = Collections.synchronizedSet(new HashSet<>());
    private final Handler main = new Handler(Looper.getMainLooper());

    private int attempts;
    private int resolvedCount;
    private int failedCount;
    private TextView statusView;

    void prewarm(Context context) {
        stories.prewarm(context);
        attachStatus(context);
    }

    List<NativeContentItem> takeBatchOrWarm(Context context, int maxItems) {
        prewarm(context);
        int wanted = Math.max(0, Math.min(maxItems, MAX_RESOLVE_PER_BATCH));
        if (wanted == 0) return new ArrayList<>();

        List<NativeContentItem> harvested = stories.takeBatchOrWarm(context, wanted);
        if (harvested.isEmpty()) {
            refreshStatus();
            return new ArrayList<>();
        }

        ArrayList<Callable<NativeContentItem>> tasks = new ArrayList<>();
        for (NativeContentItem item : harvested) {
            if (item == null || item.url == null || item.url.isEmpty()) continue;
            tasks.add(() -> resolveOne(context, item));
        }

        ArrayList<NativeContentItem> result = new ArrayList<>();
        try {
            List<Future<NativeContentItem>> futures = resolverPool.invokeAll(
                    tasks,
                    BATCH_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS
            );
            for (Future<NativeContentItem> future : futures) {
                if (future == null || future.isCancelled()) continue;
                try {
                    NativeContentItem item = future.get();
                    if (item != null && seenResolved.add(item.url)) result.add(item);
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        refreshStatus();
        return result;
    }

    void resetDeck() {
        stories.resetDeck();
    }

    private NativeContentItem resolveOne(Context context, NativeContentItem story) {
        synchronized (this) {
            attempts++;
        }
        CrazyShitRepository.StreamInfo stream = ShitShowPlayableResolver.resolve(context, story.url);
        if (stream == null || stream.mediaUrl == null || stream.mediaUrl.trim().isEmpty()) {
            synchronized (this) {
                failedCount++;
            }
            refreshStatus();
            return null;
        }

        synchronized (this) {
            resolvedCount++;
        }
        refreshStatus();
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                story.title,
                stream.mediaUrl,
                story.imageUrl,
                story.views,
                "Shit Show",
                story.comments
        );
    }

    private void attachStatus(Context context) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        main.post(() -> {
            if (statusView != null || activity.isFinishing() || activity.isDestroyed()) return;
            ViewGroup host = activity.findViewById(android.R.id.content);
            if (host == null) return;

            TextView view = new TextView(activity);
            statusView = view;
            view.setTextColor(Color.WHITE);
            view.setTextSize(9.6f);
            view.setGravity(Gravity.START);
            view.setPadding(dp(context, 8), dp(context, 5), dp(context, 8), dp(context, 5));
            view.setBackgroundColor(Color.argb(218, 0, 0, 0));
            view.setElevation(dp(context, 33));

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.TOP | Gravity.END;
            params.topMargin = dp(context, 8);
            params.rightMargin = dp(context, 8);
            host.addView(view, params);
            refreshStatus();
        });
    }

    private void refreshStatus() {
        TextView view = statusView;
        if (view == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::refreshStatus);
            return;
        }
        int a;
        int ok;
        int fail;
        synchronized (this) {
            a = attempts;
            ok = resolvedCount;
            fail = failedCount;
        }
        view.setText("Shit Show resolver B22\nattempts:" + a + "  ok:" + ok + "  fail:" + fail);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
