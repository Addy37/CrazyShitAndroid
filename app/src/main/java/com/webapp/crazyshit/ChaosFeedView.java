package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import org.json.JSONArray;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-height random video feed used by the Chaos tab.
 * Swipe manually at any time, or let a finished clip advance to the next video automatically.
 */
@UnstableApi
public final class ChaosFeedView extends FrameLayout {
    public interface Host {
        void openDetails(NativeContentItem item);
    }

    private static final String PREFS = "chaos_feed";
    private static final String KEY_RECENT = "recent_urls";
    private static final String KEY_HIDDEN = "hidden_urls";
    private static final String KEY_MUTED = "muted";
    private static final int MAX_RECENT = 500;
    private static final int MAX_HIDDEN = 600;
    private static final int LOAD_AHEAD_AT = 5;
    private static final int MAX_STREAM_CACHE = 32;
    private static final long RECENT_SAVE_DELAY_MS = 750L;
    private static final long STREAM_RETRY_DELAY_MS = 450L;
    private static final long FAILED_CLIP_SKIP_DELAY_MS = 1200L;
    private static final int SHITTOK_MAX_VIDEO_WIDTH = 1920;
    private static final int SHITTOK_MAX_VIDEO_HEIGHT = 1080;
    private static final int SHITTOK_MAX_VIDEO_BITRATE = 8_000_000;
    private static final int NEXT_PRELOAD_MIN_BUFFER_MS = 2_500;
    private static final int NEXT_PRELOAD_MAX_BUFFER_MS = 6_000;
    private static final String SITE = "https://crazyshit.com/";

    private final Activity activity;
    private final Host host;
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(6);
    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final Set<String> sessionUrls = new HashSet<>();
    private final Deque<String> recentUrls = new ArrayDeque<>();
    private final Set<String> recentSet = new HashSet<>();
    private final LinkedHashSet<String> hiddenUrls = new LinkedHashSet<>();
    private final Map<String, CrazyShitRepository.StreamInfo> streamCache =
            new LinkedHashMap<String, CrazyShitRepository.StreamInfo>(MAX_STREAM_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, CrazyShitRepository.StreamInfo> eldest
                ) {
                    return size() > MAX_STREAM_CACHE;
                }
            };
    private final Set<String> resolving = new HashSet<>();
    private final Set<String> unplayable = new HashSet<>();
    private final Set<String> resolveRetried = new HashSet<>();
    private final Random random = new Random();
    private final ChaosSourceMixer sourceMixer = new ChaosSourceMixer(repository, random);

    private ViewPager2 pager;
    private ChaosAdapter adapter;
    private TextView empty;
    private ProgressBar initialProgress;
    private InlineCommentsDialog commentsDialog;
    private boolean active;
    private boolean hostResumed = true;
    private boolean poolLoading;
    private volatile boolean closed;
    private boolean autoAdvancePending;
    private boolean chaosMuted;
    private boolean manualFullscreen;
    private int autoAdvanceFrom = -1;
    private int consecutiveDryLoads;
    private int selectedPosition;
    private final Runnable saveRecentRunnable = this::saveRecentNow;

    public ChaosFeedView(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        setBackgroundColor(Color.BLACK);
        loadRecent();
        loadHidden();
        chaosMuted = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_MUTED, false);
        buildUi();
        loadMorePool();
    }

    private void buildUi() {
        pager = new ViewPager2(activity);
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        pager.setOffscreenPageLimit(3);
        adapter = new ChaosAdapter();
        pager.setAdapter(adapter);
        addView(pager, new FrameLayout.LayoutParams(-1, -1));

        RecyclerView rv = pagerRecycler();
        if (rv != null) rv.setItemViewCacheSize(3);

        initialProgress = new ProgressBar(activity);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(48), dp(48));
        pp.gravity = Gravity.CENTER;
        addView(initialProgress, pp);

        empty = new TextView(activity);
        empty.setTextColor(Color.rgb(205, 205, 212));
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(30), dp(30), dp(30), dp(30));
        empty.setText("Loading ShitTok…");
        empty.setVisibility(View.GONE);
        addView(empty, new FrameLayout.LayoutParams(-1, -1));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (autoAdvancePending && position != autoAdvanceFrom) {
                    autoAdvancePending = false;
                    autoAdvanceFrom = -1;
                }
                if (manualFullscreen && position != selectedPosition) exitManualFullscreen();
                selectedPosition = position;
                markSeen(position);
                pauseNonSelected(position);
                releaseDistantPlayers(position);
                resolveAhead(position);
                playSelected();
                warmCreatorGalleries(position);
                ChaosHolder holder = holderAt(position);
                if (holder != null) holder.showControlsTemporarily();
                if (items.size() - position <= LOAD_AHEAD_AT) loadMorePool();
            }
        });
    }

    public void setActive(boolean value) {
        active = value;
        if (active && hostResumed) {
            pager.setUserInputEnabled(true);
            resolveAhead(selectedPosition);
            playSelected();
            warmCreatorGalleries(selectedPosition);
            syncVisibleChrome();
        } else {
            pauseAll();
            releaseVisiblePlayers();
        }
        if (!active) exitManualFullscreen();
    }

    public void onHostResume() {
        hostResumed = true;
        if (active) {
            resolveAhead(selectedPosition);
            playSelected();
            syncVisibleChrome();
        }
    }

    public void onHostPause() {
        hostResumed = false;
        pauseAll();
        releaseVisiblePlayers();
        flushRecent();
    }

    public void onConfigurationChanged() {
        if (commentsDialog != null && commentsDialog.isShowing()) commentsDialog.dismiss();
        syncVisibleChrome();
    }

    public void refresh() {
    pauseAll();
    consecutiveDryLoads = 0;
    sourceMixer.resetDeck();
    poolLoading = false;
    autoAdvancePending = false;
    autoAdvanceFrom = -1;
    streamCache.clear();
    resolving.clear();
    unplayable.clear();
    resolveRetried.clear();
    // Keep sessionUrls so Refresh cannot immediately deal the same clips back again.
    items.clear();
    exitManualFullscreen();
    adapter.notifyDataSetChanged();
    initialProgress.setVisibility(View.VISIBLE);
    empty.setVisibility(View.GONE);
    selectedPosition = 0;
    pager.setCurrentItem(0, false);
    loadMorePool();
}

    public void close() {
        closed = true;
        poolLoading = false;
        active = false;
        hostResumed = false;
        exitManualFullscreen();
        if (commentsDialog != null && commentsDialog.isShowing()) commentsDialog.dismiss();
        pauseAll();
        releaseVisiblePlayers();
        flushRecent();
        removeCallbacks(saveRecentRunnable);
        io.shutdownNow();
    }

    private void loadMorePool() {
    if (closed || poolLoading) return;
    poolLoading = true;

    io.execute(() -> {
        List<NativeContentItem> mixed;
        try {
            mixed = sourceMixer.loadRandomBatch(activity);
        } catch (Exception ignored) {
            mixed = Collections.emptyList();
        }

        ArrayList<NativeContentItem> fresh = new ArrayList<>();
        ArrayList<NativeContentItem> recentFallback = new ArrayList<>();
        for (NativeContentItem item : mixed) {
            if (!isMedia(item) || hiddenUrls.contains(item.url)) continue;
            if (recentSet.contains(item.url)) recentFallback.add(item);
            else fresh.add(item);
        }
        Collections.shuffle(fresh, random);
        Collections.shuffle(recentFallback, random);

        activity.runOnUiThread(() -> {
            if (closed) return;
            poolLoading = false;
            int before = items.size();
            appendUnique(fresh);

            int freshAdded = items.size() - before;
            if (freshAdded == 0) consecutiveDryLoads++;
            else consecutiveDryLoads = 0;

            // Previously watched clips stay out of the normal draw. Only recycle them if
            // several broad random batches in a row genuinely cannot produce fresh media.
            if (freshAdded < 4 && consecutiveDryLoads >= 3) {
                appendUnique(recentFallback);
            }

            int added = items.size() - before;
            if (added > 0 && freshAdded == 0) consecutiveDryLoads = 0;

            if (added > 0) {
                adapter.notifyItemRangeInserted(before, added);
                initialProgress.setVisibility(View.GONE);
                empty.setVisibility(View.GONE);
                resolveAhead(selectedPosition);
                if (active && hostResumed) playSelected();
                warmCreatorGalleries(selectedPosition);
                tryPendingAutoAdvance();
            }

            boolean needsMore = items.size() < 14
                    || (autoAdvancePending && autoAdvanceFrom + 1 >= items.size());
            if (needsMore && consecutiveDryLoads < 4) {
                loadMorePool();
            } else if (items.isEmpty()) {
                initialProgress.setVisibility(View.GONE);
                empty.setText("ShitTok couldn't find a playable pool right now.\nPull away and come back to retry.");
                empty.setVisibility(View.VISIBLE);
            } else if (autoAdvancePending && autoAdvanceFrom + 1 >= items.size()) {
                autoAdvancePending = false;
                autoAdvanceFrom = -1;
            }
        });
    });
}

    static boolean shouldPreparePlayer(int position, int selectedPosition) {
        return position >= selectedPosition && position <= selectedPosition + 2;
    }

    private static boolean isMedia(NativeContentItem item) {
        return item != null
                && NativeContentItem.KIND_MEDIA.equals(item.kind)
                && item.url != null
                && !item.url.isEmpty();
    }

    private void appendUnique(List<NativeContentItem> candidates) {
        if (candidates == null) return;
        for (NativeContentItem item : candidates) {
            if (!isMedia(item)) continue;
            if (hiddenUrls.contains(item.url)) continue;
            if (!sessionUrls.add(item.url)) continue;
            items.add(item);
            CrazyShitRepository.StreamInfo preloaded = ChaosStartupPreloader.takeResolved(item.url);
            if (preloaded != null) streamCache.put(item.url, preloaded);
        }
    }

    private void requestAutoAdvance(int fromPosition) {
        if (!active || !hostResumed || fromPosition != selectedPosition) return;

        if (fromPosition + 1 < items.size()) {
            autoAdvancePending = false;
            autoAdvanceFrom = -1;
            if (items.size() - fromPosition <= LOAD_AHEAD_AT) loadMorePool();
            pager.post(() -> {
                if (!active || !hostResumed || selectedPosition != fromPosition) return;
                if (fromPosition + 1 >= items.size()) return;
                pager.setCurrentItem(fromPosition + 1, true);
            });
            return;
        }

        autoAdvancePending = true;
        autoAdvanceFrom = fromPosition;
        loadMorePool();
    }

    private void tryPendingAutoAdvance() {
        if (!autoAdvancePending) return;
        int fromPosition = autoAdvanceFrom;
        if (!active || !hostResumed || selectedPosition != fromPosition) {
            autoAdvancePending = false;
            autoAdvanceFrom = -1;
            return;
        }
        if (fromPosition + 1 < items.size()) requestAutoAdvance(fromPosition);
    }

    private void warmCreatorGalleries(int position) {
        if (closed || !active || !hostResumed || position < 0 || position >= items.size()) return;
        ShitTokCreatorGalleryPreloader.warm(activity, items.get(position));

        postDelayed(() -> {
            if (closed || selectedPosition != position) return;
            for (int next = position + 1; next < Math.min(items.size(), position + 8); next++) {
                NativeContentItem candidate = items.get(next);
                if (!ShitTokCreatorMetadata.hasCreator(candidate)) continue;
                ShitTokCreatorGalleryPreloader.warm(activity, candidate);
                break;
            }
        }, 700L);
    }

    private void resolveAhead(int position) {
        int ahead = ChaosPreloadPolicy.aheadCount(activity);
        for (int offset = 0; offset <= ahead; offset++) {
            resolveAt(position + offset);
        }
        if (ahead > 0 && position > 0) resolveAt(position - 1);
    }

    private boolean shouldResolvePosition(int position) {
        if (position == selectedPosition) return true;
        if (!ChaosPreloadPolicy.allowsLookAhead(activity)) return false;
        return position >= selectedPosition - 1 && position <= selectedPosition + 3;
    }

    private void resolveAt(int position) {
        if (closed) return;
        if (position < 0 || position >= items.size()) return;
        NativeContentItem item = items.get(position);
        if (streamCache.containsKey(item.url)) {
            prepareVisible(position);
            return;
        }
        if (unplayable.contains(item.url)) {
            if (shouldRetryResolution(item, position)) {
                unplayable.remove(item.url);
            } else {
                prepareVisible(position);
                return;
            }
        }
        if (!resolving.add(item.url)) {
            prepareVisible(position);
            return;
        }

        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = ChaosStartupPreloader.takeResolved(item.url);
            try {
                if (stream == null) stream = resolvePlayable(item);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            activity.runOnUiThread(() -> {
                if (closed) return;
                resolving.remove(item.url);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    if (shouldRetryResolution(item, position)) {
                        scheduleResolutionRetry(item, position);
                        return;
                    }
                    unplayable.add(item.url);
                } else {
                    unplayable.remove(item.url);
                    streamCache.put(item.url, resolved);
                }
                prepareVisible(position);
                if (position == selectedPosition) playSelected();
            });
        });
    }

    private CrazyShitRepository.StreamInfo resolvePlayable(NativeContentItem item)
            throws Exception {
        if (item == null || item.url == null || item.url.isEmpty()) return null;
        return PlayableSourceRouter.resolve(activity, item);
    }

    private boolean shouldRetryResolution(NativeContentItem item, int position) {
        if (item == null || item.url == null || item.url.isEmpty()) return false;
        if (!active || !hostResumed || position != selectedPosition) return false;
        if (position < 0 || position >= items.size()) return false;
        if (!item.url.equals(items.get(position).url)) return false;
        return resolveRetried.add(item.url);
    }

    private void scheduleResolutionRetry(NativeContentItem item, int position) {
        if (closed) return;
        ChaosHolder holder = holderAt(position);
        if (holder != null && holder.isBoundTo(item.url, position)) {
            holder.noteResolutionRetry();
            holder.showRetrying();
        }
        pager.postDelayed(() -> {
            if (closed) return;
            if (position < 0 || position >= items.size()) return;
            if (!item.url.equals(items.get(position).url)) return;
            if ((!active || !hostResumed || position != selectedPosition)
                    && !ChaosPreloadPolicy.allowsLookAhead(activity)) {
                resolveRetried.remove(item.url);
                unplayable.add(item.url);
                return;
            }
            unplayable.remove(item.url);
            resolveAt(position);
        }, STREAM_RETRY_DELAY_MS);
    }

    private void prepareVisible(int position) {
        if (!active || !hostResumed) return;
        ChaosHolder holder = holderAt(position);
        if (holder == null || position < 0 || position >= items.size()) return;
        NativeContentItem item = items.get(position);
        CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
        if (stream != null) {
            holder.noteResolutionRetryIfNeeded(resolveRetried.contains(item.url));
            if (shouldPreparePlayer(position, selectedPosition)) {
                holder.prepare(stream, active && hostResumed && position == selectedPosition);
            } else {
                holder.releasePlayer();
            }
        } else if (unplayable.contains(item.url)) {
            holder.showResolutionFailureAndSkip(resolveRetried.contains(item.url));
        }
    }

    private void playSelected() {
        if (!active || !hostResumed) return;
        if (selectedPosition < 0 || selectedPosition >= items.size()) return;
        resolveAt(selectedPosition);
        ChaosHolder holder = holderAt(selectedPosition);
        if (holder == null) return;
        NativeContentItem item = items.get(selectedPosition);
        CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
        if (stream != null) holder.prepare(stream, true);
    }

    private void pauseNonSelected(int selected) {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (!(raw instanceof ChaosHolder)) continue;
            ChaosHolder holder = (ChaosHolder) raw;
            if (holder.getBindingAdapterPosition() != selected) holder.pauseAndRecord();
        }
    }

    private void releaseDistantPlayers(int selected) {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (!(raw instanceof ChaosHolder)) continue;
            ChaosHolder holder = (ChaosHolder) raw;
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && !shouldPreparePlayer(position, selected)) {
                holder.releasePlayer();
            }
        }
    }

    private void pauseAll() {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder) ((ChaosHolder) raw).pauseAndRecord();
        }
    }

    private void releaseVisiblePlayers() {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder) ((ChaosHolder) raw).releasePlayer();
        }
    }

    private void syncVisibleChrome() {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder) ((ChaosHolder) raw).syncOrientationChrome();
        }
    }

    private int portraitViewportBottomInset() {
        if (activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE) {
            return 0;
        }
        // Keep the old resting viewport while leaving the vertical pager full-height so
        // incoming/outgoing pages can pass visibly behind the floating glass navbar.
        return ZeroChillUi.dimension(activity, R.dimen.zc_bottom_nav_height) + dp(8);
    }

    private ChaosHolder holderAt(int position) {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return null;
        RecyclerView.ViewHolder raw = rv.findViewHolderForAdapterPosition(position);
        return raw instanceof ChaosHolder ? (ChaosHolder) raw : null;
    }

    private RecyclerView pagerRecycler() {
        if (pager == null || pager.getChildCount() == 0) return null;
        View child = pager.getChildAt(0);
        return child instanceof RecyclerView ? (RecyclerView) child : null;
    }

    private void markSeen(int position) {
        if (position < 0 || position >= items.size()) return;
        String url = items.get(position).url;
        if (url == null || url.isEmpty()) return;
        recentUrls.remove(url);
        recentUrls.addFirst(url);
        recentSet.add(url);
        while (recentUrls.size() > MAX_RECENT) {
            String removed = recentUrls.removeLast();
            recentSet.remove(removed);
        }
        removeCallbacks(saveRecentRunnable);
        postDelayed(saveRecentRunnable, RECENT_SAVE_DELAY_MS);
    }

    private void loadRecent() {
        String raw = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RECENT, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length() && recentUrls.size() < MAX_RECENT; i++) {
                String url = array.optString(i, "").trim();
                if (url.isEmpty() || recentSet.contains(url)) continue;
                recentUrls.addLast(url);
                recentSet.add(url);
            }
        } catch (Exception ignored) {
        }
    }

    private void flushRecent() {
        removeCallbacks(saveRecentRunnable);
        saveRecentNow();
    }

    private void saveRecentNow() {
        JSONArray array = new JSONArray();
        for (String url : recentUrls) array.put(url);
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENT, array.toString())
                .apply();
    }

    private void loadHidden() {
        String raw = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_HIDDEN, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length() && hiddenUrls.size() < MAX_HIDDEN; i++) {
                String url = array.optString(i, "").trim();
                if (!url.isEmpty()) hiddenUrls.add(url);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveHidden() {
        JSONArray array = new JSONArray();
        for (String url : hiddenUrls) array.put(url);
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HIDDEN, array.toString())
                .apply();
    }

    private void hideFromChaos(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        hiddenUrls.remove(item.url);
        hiddenUrls.add(item.url);
        while (hiddenUrls.size() > MAX_HIDDEN) {
            String oldest = hiddenUrls.iterator().next();
            hiddenUrls.remove(oldest);
        }
        saveHidden();

        int index = items.indexOf(item);
        if (index < 0) return;
        ChaosHolder holder = holderAt(index);
        if (holder != null) holder.releasePlayer();
        streamCache.remove(item.url);
        unplayable.remove(item.url);
        resolving.remove(item.url);
        resolveRetried.remove(item.url);
        items.remove(index);
        adapter.notifyDataSetChanged();
        Toast.makeText(activity, "Won't show this clip again.", Toast.LENGTH_SHORT).show();

        if (items.isEmpty()) {
            exitManualFullscreen();
            selectedPosition = 0;
            initialProgress.setVisibility(View.VISIBLE);
            loadMorePool();
            return;
        }

        int target = Math.min(index, items.size() - 1);
        selectedPosition = target;
        pager.setCurrentItem(target, false);
        markSeen(target);
        resolveAhead(target);
        playSelected();
    }

    static boolean shouldOfferLandscapeFullscreen(float aspectRatio) {
        return aspectRatio > 1.1f;
    }

    private void enterManualFullscreen(ChaosHolder holder) {
        if (manualFullscreen || holder == null || !holder.horizontalVideo) return;
        manualFullscreen = true;
        PhoneOrientationPolicy.enterSensorFullscreen(activity);
        holder.syncOrientationChrome();
    }

    private void exitManualFullscreen() {
        if (!manualFullscreen) return;
        manualFullscreen = false;
        PhoneOrientationPolicy.exitFullscreenVideo(activity);
        syncVisibleChrome();
    }

    boolean exitSensorFullscreenForBack() {
        if (!manualFullscreen) return false;
        exitManualFullscreen();
        return true;
    }

    private void setChaosMuted(boolean muted) {
        chaosMuted = muted;
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MUTED, muted)
                .apply();
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder) ((ChaosHolder) raw).applyMuteState();
        }
    }

    private void share(NativeContentItem item) {
        if (item == null || item.url.isEmpty()) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, item.url);
        share.putExtra(Intent.EXTRA_SUBJECT, item.title);
        activity.startActivity(Intent.createChooser(share, "Share"));
    }

    private void retryPlayback(ChaosHolder holder, PlaybackException originalError) {
        if (closed) return;
        if (holder == null || holder.item == null || holder.item.url.isEmpty()) return;
        NativeContentItem retryItem = holder.item;
        String pageUrl = retryItem.url;
        int position = holder.boundPosition;

        holder.showRetrying();
        holder.releasePlayer();
        streamCache.remove(pageUrl);
        unplayable.remove(pageUrl);
        resolveRetried.add(pageUrl);
        resolving.add(pageUrl);

        io.execute(() -> {
            CrazyShitRepository.StreamInfo refreshed = null;
            try {
                refreshed = resolvePlayable(retryItem);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = refreshed;
            activity.runOnUiThread(() -> {
                if (closed) return;
                resolving.remove(pageUrl);
                boolean valid = resolved != null
                        && resolved.mediaUrl != null
                        && !resolved.mediaUrl.isEmpty();
                if (valid) {
                    unplayable.remove(pageUrl);
                    streamCache.put(pageUrl, resolved);
                } else {
                    unplayable.add(pageUrl);
                }

                if (!holder.isBoundTo(pageUrl, position)) {
                    prepareVisible(position);
                    return;
                }
                if (!valid) {
                    holder.showPlayerFailureAndSkip(originalError, "Fresh stream lookup failed");
                    return;
                }
                holder.prepare(resolved, active && hostResumed && position == selectedPosition);
            });
        });
    }

    private void showPlaybackReport(ChaosHolder holder) {
        if (holder == null || holder.item == null) return;
        String report = ChaosPlaybackDiagnostics.build(
                holder.item,
                holder.diagnosticStream(),
                holder.lastPlaybackError,
                holder.lastFailureStage,
                holder.retryAttempted
        );
        new AlertDialog.Builder(activity)
                .setTitle("Playback report")
                .setMessage(report)
                .setPositiveButton("Copy", (dialog, which) -> copyPlaybackReport(report))
                .setNeutralButton("Share", (dialog, which) -> sharePlaybackReport(report))
                .setNegativeButton("Close", null)
                .show();
    }

    private void copyPlaybackReport(String report) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(
                Context.CLIPBOARD_SERVICE
        );
        if (clipboard == null) {
            Toast.makeText(activity, "Clipboard isn't available.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("CrazyShit playback report", report));
        Toast.makeText(activity, "Playback report copied.", Toast.LENGTH_SHORT).show();
    }

    private void sharePlaybackReport(String report) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "CrazyShit playback report");
        share.putExtra(Intent.EXTRA_TEXT, report);
        activity.startActivity(Intent.createChooser(share, "Share playback report"));
    }

    private void toggleSaved(NativeContentItem item, TextView button) {
        if (item == null) return;
        if (FavoriteStore.contains(activity, item.url)) {
            FavoriteStore.remove(activity, item.url);
            Toast.makeText(activity, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(activity, item.title, item.url);
            Toast.makeText(activity, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
        }
        updateSaveButton(item, button);
    }

    private void updateSaveButton(NativeContentItem item, TextView button) {
        if (button == null || item == null) return;
        boolean saved = FavoriteStore.contains(activity, item.url);
        button.setCompoundDrawablesWithIntrinsicBounds(
                0,
                saved ? R.drawable.ic_nav_saved : R.drawable.ic_action_save_outline,
                0,
                0
        );
        button.setCompoundDrawableTintList(ColorStateList.valueOf(saved ? UiPalette.PRIMARY : Color.WHITE));
        button.setContentDescription(saved ? "Remove from Watch Later" : "Save to Watch Later");
    }

    private void openInlineComments(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        if (!supportsComments(item)) {
            Toast.makeText(
                    activity,
                    "Comments are not available for this source in ShitTok.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (commentsDialog != null && commentsDialog.isShowing()) return;

        pager.animate().cancel();
        pager.setScaleX(1f);
        pager.setScaleY(1f);
        pager.setUserInputEnabled(false);
        commentsDialog = new InlineCommentsDialog(
                activity,
                item.url,
                item.title,
                item.comments,
                new InlineCommentsDialog.ResizeListener() {
                    @Override
                    public void onSheetTopChanged(int topOnScreen) {
                        resizeForComments(topOnScreen);
                    }

                    @Override
                    public void onSheetClosed() {
                        commentsDialog = null;
                        restoreAfterComments();
                    }
                }
        );
        commentsDialog.show();
    }

    private void resizeForComments(int sheetTopOnScreen) {
        ChaosHolder holder = holderAt(selectedPosition);
        if (holder != null) holder.resizeMediaForComments(sheetTopOnScreen);
    }

    private void restoreAfterComments() {
        if (pager == null) return;
        pager.animate().cancel();
        pager.setScaleX(1f);
        pager.setScaleY(1f);
        ChaosHolder holder = holderAt(selectedPosition);
        if (holder == null) {
            pager.setUserInputEnabled(true);
            return;
        }
        holder.restoreMediaAfterComments(() -> {
            pager.setUserInputEnabled(true);
            holder.showControlsTemporarily();
        });
    }

    private void preloadReadyComments(NativeContentItem item, int position) {
        if (item == null || !supportsComments(item)) return;
        if (position != selectedPosition || !active || !hostResumed) return;
        if (!ChaosPreloadPolicy.allowsCommentPreload(activity)) return;
        String url = item.url;
        pager.postDelayed(() -> {
            if (!active || !hostResumed || position != selectedPosition) return;
            if (!ChaosPreloadPolicy.allowsCommentPreload(activity)) return;
            if (selectedPosition < 0 || selectedPosition >= items.size()) return;
            NativeContentItem selected = items.get(selectedPosition);
            if (selected == null || !url.equals(selected.url)) return;
            NativeCommentsLoader.preload(activity, url);
        }, 850L);
    }

    private boolean supportsComments(NativeContentItem item) {
        return item != null
                && !EfuktRepository.isEfuktUrl(item.url)
                && !BunkrRepository.isBunkrUrl(item.url)
                && !FapelloRepository.isFapelloUrl(item.url)
                && !WebVideoSourceRepository.isKaoticUrl(item.url)
                && !OnlyHavenRepository.isOnlyHavenUrl(item.url);
    }

    private void haptic(View view) {
        if (view == null) return;
        if (!activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private GlideUrl imageWithHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("Referer", pageUrl == null || pageUrl.isEmpty() ? SITE : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            headers.addHeader("User-Agent", WebSettings.getDefaultUserAgent(activity));
        } catch (Exception ignored) {
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.trim().isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ChaosAdapter extends RecyclerView.Adapter<ChaosHolder> {
        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ChaosHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ChaosHolder(parent);
        }

        @Override
        public void onBindViewHolder(@NonNull ChaosHolder holder, int position) {
            holder.bind(items.get(position), position);
            CrazyShitRepository.StreamInfo stream = streamCache.get(items.get(position).url);
            if (stream != null && active && hostResumed) {
                holder.prepare(stream, active && hostResumed && position == selectedPosition);
            } else if (shouldResolvePosition(position)) {
                resolveAt(position);
            }
        }

        @Override
        public void onViewRecycled(@NonNull ChaosHolder holder) {
            holder.pauseAndRecord();
            holder.releasePlayer();
            super.onViewRecycled(holder);
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull ChaosHolder holder) {
            holder.pauseAndRecord();
            int position = holder.getBindingAdapterPosition();
            if (!active || position == RecyclerView.NO_POSITION ||
                    Math.abs(position - selectedPosition) > 1) {
                holder.releasePlayer();
            }
            super.onViewDetachedFromWindow(holder);
        }
    }

    private final class ChaosHolder extends RecyclerView.ViewHolder {
        final FrameLayout root;
        final FrameLayout mediaLayer;
        final PlayerView playerView;
        final ImageView poster;
        final ProgressBar loading;
        final TextView failure;
        final LinearLayout lower;
        final LinearLayout actionRail;
        final LinearLayout playbackRail;
        final TextView title;
        final TextView meta;
        final TextView save;
        final TextView comments;
        final TextView mute;
        final ImageView fullscreen;
        final TextView speedBadge;
        final SeekBar seekBar;
        ExoPlayer player;
        NativeContentItem item;
        CrazyShitRepository.StreamInfo stream;
        CrazyShitRepository.StreamInfo lastAttemptedStream;
        PlaybackException lastPlaybackError;
        String lastFailureStage = "";
        int boundPosition = -1;
        boolean controlsVisible = true;
        boolean speedBoosting;
        boolean scrubbing;
        boolean everStarted;
        boolean retryAttempted;
        boolean failurePending;
        boolean horizontalVideo;
        float videoAspectRatio;
        float restoreSpeed = 1f;

        private final Runnable hideControlsRunnable = this::hideControlsNow;
        private final Runnable hideSeekBarRunnable = this::hideSeekBarNow;
        private final Runnable skipFailedClipRunnable = () -> {
            if (!failurePending || boundPosition != selectedPosition) return;
            requestAutoAdvance(boundPosition);
        };
        private final Runnable progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                if (player != null && player.isPlaying()) root.postDelayed(this, 250L);
            }
        };

        ChaosHolder(ViewGroup parent) {
            super(new FrameLayout(parent.getContext()));
            root = (FrameLayout) itemView;
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            root.setBackgroundColor(Color.BLACK);
            applyViewportInset();

            mediaLayer = new FrameLayout(activity);
            mediaLayer.setBackgroundColor(Color.BLACK);
            root.addView(mediaLayer, new FrameLayout.LayoutParams(-1, -1));

            poster = new ImageView(activity);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(Color.BLACK);
            poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mediaLayer.addView(poster, new FrameLayout.LayoutParams(-1, -1));

            playerView = (PlayerView) LayoutInflater.from(activity)
                    .inflate(R.layout.view_video_player_texture, mediaLayer, false);
            playerView.setUseController(false);
            playerView.setControllerAutoShow(false);
            playerView.hideController();
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setBackgroundColor(Color.BLACK);
            playerView.setContentDescription("Play or pause video");
            mediaLayer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

            loading = new ProgressBar(activity);
            loading.setContentDescription("Loading video");
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(44), dp(44));
            lp.gravity = Gravity.CENTER;
            mediaLayer.addView(loading, lp);

            failure = new TextView(activity);
            failure.setTextColor(Color.WHITE);
            failure.setTextSize(14);
            failure.setGravity(Gravity.CENTER);
            failure.setText("Couldn't play this one\nSwipe up for the next video");
            failure.setPadding(dp(28), dp(28), dp(28), dp(28));
            failure.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            failure.setVisibility(View.GONE);
            mediaLayer.addView(failure, new FrameLayout.LayoutParams(-1, -1));

            lower = new LinearLayout(activity);
            lower.setOrientation(LinearLayout.HORIZONTAL);
            lower.setGravity(Gravity.BOTTOM);
            lower.setPadding(dp(16), dp(18), dp(10), dp(30));
            lower.setBackgroundColor(Color.TRANSPARENT);
            lower.setClickable(false);
            lower.setLongClickable(false);
            lower.setFocusable(false);
            FrameLayout.LayoutParams lowerParams = new FrameLayout.LayoutParams(-1, -2);
            lowerParams.gravity = Gravity.BOTTOM;
            root.addView(lower, lowerParams);

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.BOTTOM);
            copy.setBackgroundColor(Color.TRANSPARENT);
            lower.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

            title = new TextView(activity);
            title.setTextColor(Color.WHITE);
            title.setTextSize(17);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(3);
            copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

            meta = new TextView(activity);
            meta.setTextColor(Color.rgb(215, 215, 222));
            meta.setTextSize(12);
            meta.setPadding(0, dp(5), 0, 0);
            copy.addView(meta, new LinearLayout.LayoutParams(-1, -2));

            actionRail = new LinearLayout(activity);
            actionRail.setOrientation(LinearLayout.VERTICAL);
            actionRail.setGravity(Gravity.CENTER);
            actionRail.setPadding(dp(4), dp(5), dp(4), dp(5));
            actionRail.setBackground(activity.getDrawable(R.drawable.zc_shittok_control_rail));
            actionRail.setTag("shittok_action_rail");
            lower.addView(actionRail, new LinearLayout.LayoutParams(dp(58), -2));

            save = textIconActionButton(
                    R.drawable.ic_action_save_outline,
                    "Save to Watch Later",
                    "shittok_save"
            );
            actionRail.addView(save, actionParams());

            comments = textIconActionButton(
                    R.drawable.ic_action_comments,
                    "Open comments",
                    "shittok_comments"
            );
            actionRail.addView(comments, actionParams());

            TextView share = textIconActionButton(
                    R.drawable.ic_action_share,
                    "Share video",
                    "shittok_share"
            );
            actionRail.addView(share, actionParams());

            TextView more = textIconActionButton(
                    R.drawable.ic_nav_more,
                    "More video actions",
                    "shittok_more"
            );
            actionRail.addView(more, actionParams());

            playbackRail = new LinearLayout(activity);
            playbackRail.setOrientation(LinearLayout.VERTICAL);
            playbackRail.setGravity(Gravity.CENTER);
            playbackRail.setPadding(dp(4), dp(4), dp(4), dp(4));
            playbackRail.setBackground(activity.getDrawable(R.drawable.zc_shittok_control_rail));
            playbackRail.setTag("shittok_playback_rail");

            mute = textIconActionButton(
                    chaosMuted ? R.drawable.ic_action_volume_off : R.drawable.ic_action_volume_on,
                    chaosMuted ? "Unmute video" : "Mute video",
                    "shittok_mute"
            );
            playbackRail.addView(mute, actionParams());

            fullscreen = imageActionButton(
                    R.drawable.ic_action_fullscreen,
                    "Watch horizontal video fullscreen",
                    "shittok_fullscreen"
            );
            fullscreen.setVisibility(View.GONE);
            playbackRail.addView(fullscreen, actionParams());

            FrameLayout.LayoutParams playbackParams =
                    new FrameLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT);
            playbackParams.gravity = Gravity.TOP | Gravity.END;
            playbackParams.setMargins(0, dp(14), dp(12), 0);
            root.addView(playbackRail, playbackParams);

            speedBadge = new TextView(activity);
            speedBadge.setText("2×");
            speedBadge.setTextColor(Color.WHITE);
            speedBadge.setTextSize(16);
            speedBadge.setGravity(Gravity.CENTER);
            speedBadge.setBackgroundColor(Color.argb(175, 0, 0, 0));
            speedBadge.setPadding(dp(10), dp(5), dp(10), dp(5));
            speedBadge.setVisibility(View.GONE);
            FrameLayout.LayoutParams speedParams = new FrameLayout.LayoutParams(-2, -2);
            speedParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            speedParams.setMargins(0, dp(18), 0, 0);
            root.addView(speedBadge, speedParams);

            seekBar = new SeekBar(activity);
            seekBar.setMax(1000);
            seekBar.setProgress(0);
            seekBar.setPadding(0, dp(18), 0, 0);
            seekBar.setContentDescription("Video progress");
            seekBar.setProgressTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(150, 210, 210, 215)));
            seekBar.setThumbTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
            FrameLayout.LayoutParams seekParams = new FrameLayout.LayoutParams(-1, dp(48));
            seekParams.gravity = Gravity.BOTTOM;
            seekParams.setMargins(dp(8), 0, dp(8), dp(1));
            root.addView(seekBar, seekParams);

            playerView.setOnClickListener(v -> {
                haptic(v);
                if (!controlsVisible) {
                    showControlsTemporarily();
                    return;
                }
                if (player == null) {
                    showControlsTemporarily();
                    return;
                }
                if (player.isPlaying()) {
                    player.pause();
                    showControlsPersistent();
                } else {
                    everStarted = true;
                    player.play();
                    showControlsTemporarily();
                }
            });

            playerView.setLongClickable(true);
            playerView.setOnLongClickListener(v -> {
                if (player == null) return false;
                haptic(v);
                restoreSpeed = player.getPlaybackParameters().speed;
                if (restoreSpeed <= 0f) restoreSpeed = 1f;
                speedBoosting = true;
                player.setPlaybackSpeed(2f);
                speedBadge.setVisibility(View.VISIBLE);
                return true;
            });
            playerView.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && speedBoosting) {
                    restorePlaybackSpeed();
                    return true;
                }
                return false;
            });

            View.OnLongClickListener menuLongPress = v -> {
                haptic(v);
                showMoreMenu();
                return true;
            };
            title.setOnLongClickListener(menuLongPress);
            meta.setOnLongClickListener(menuLongPress);
            title.setOnClickListener(v -> {
                String creator = ShitTokCreatorMetadata.creatorName(item);
                if (creator.isEmpty()) return;
                haptic(v);
                pauseAndRecord();
                activity.startActivity(NativeFeedBrowserActivity.createCreatorGallery(
                        activity,
                        creator,
                        creator,
                        "",
                        ShitTokCreatorGalleryPreloader.sessionId(creator)
                ));
            });

            mute.setOnClickListener(v -> {
                haptic(v);
                setChaosMuted(!chaosMuted);
                showControlsTemporarily();
            });
            fullscreen.setOnClickListener(v -> {
                if (!horizontalVideo) return;
                haptic(v);
                if (manualFullscreen) exitManualFullscreen();
                else enterManualFullscreen(this);
                showControlsTemporarily();
            });
            save.setOnClickListener(v -> {
                haptic(v);
                toggleSaved(item, save);
                showControlsTemporarily();
            });
            comments.setOnClickListener(v -> {
                haptic(v);
                if (item != null) openInlineComments(item);
            });
            share.setOnClickListener(v -> {
                haptic(v);
                share(item);
                showControlsTemporarily();
            });
            more.setOnClickListener(v -> {
                haptic(v);
                showMoreMenu();
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (!fromUser || player == null) return;
                    long duration = player.getDuration();
                    if (duration <= 0L) return;
                    player.seekTo((duration * progress) / 1000L);
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    scrubbing = true;
                    pager.setUserInputEnabled(false);
                    showControlsPersistent();
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    scrubbing = false;
                    pager.setUserInputEnabled(true);
                    updateProgress();
                    if (player != null && player.isPlaying()) showControlsTemporarily();
                    else showControlsPersistent();
                }
            });
        }

        private TextView textIconActionButton(int icon, String description, String tag) {
            TextView button = new TextView(activity);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription(description);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setPadding(dp(12), dp(12), dp(12), dp(12));
            button.setClickable(true);
            button.setFocusable(false);
            button.setTag(tag);
            button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
            button.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            return button;
        }

        private ImageView imageActionButton(int icon, String description, String tag) {
            ImageView button = new ImageView(activity);
            button.setImageResource(icon);
            button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setContentDescription(description);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setPadding(dp(12), dp(12), dp(12), dp(12));
            button.setClickable(true);
            button.setFocusable(false);
            button.setTag(tag);
            return button;
        }

        private LinearLayout.LayoutParams actionParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
            params.setMargins(0, dp(1), 0, dp(1));
            return params;
        }

        void bind(NativeContentItem next, int position) {
            pauseAndRecord();
            releasePlayer();
            root.removeCallbacks(skipFailedClipRunnable);
            mediaLayer.animate().cancel();
            mediaLayer.setScaleX(1f);
            mediaLayer.setScaleY(1f);
            mediaLayer.setTranslationY(0f);
            item = next;
            stream = null;
            lastAttemptedStream = null;
            lastPlaybackError = null;
            lastFailureStage = "";
            boundPosition = position;
            everStarted = false;
            controlsVisible = true;
            speedBoosting = false;
            scrubbing = false;
            retryAttempted = false;
            failurePending = false;
            horizontalVideo = false;
            videoAspectRatio = 0f;
            fullscreen.setVisibility(View.GONE);
            seekBar.setProgress(0);
            seekBar.setEnabled(false);
            seekBar.setAlpha(1f);
            seekBar.setVisibility(View.VISIBLE);
            lower.setAlpha(1f);
            lower.setVisibility(View.VISIBLE);
            playbackRail.setAlpha(1f);
            playbackRail.setVisibility(View.VISIBLE);
            speedBadge.setVisibility(View.GONE);
            applyMuteState();
            String creator = ShitTokCreatorMetadata.creatorName(next);
            boolean creatorClip = !creator.isEmpty();
            String displayTitle = creatorClip
                    ? creator
                    : (next.title == null || next.title.isEmpty() ? "Random video" : next.title);
            title.setText(displayTitle);
            title.setClickable(creatorClip);
            title.setContentDescription(
                    creatorClip ? "Open " + creator + " gallery" : displayTitle
            );
            StringBuilder info = new StringBuilder();
            if (!creatorClip) {
                if (next.uploader != null && !next.uploader.isEmpty()) info.append(next.uploader);
                if (next.views != null && !next.views.isEmpty()) {
                    if (info.length() > 0) info.append("  •  ");
                    info.append(next.views).append(" views");
                }
            }
            meta.setText(info);
            meta.setVisibility(creatorClip ? View.GONE : View.VISIBLE);
            comments.setVisibility(supportsComments(next) ? View.VISIBLE : View.GONE);
            updateSaveButton(next, save);
            loading.setVisibility(View.VISIBLE);
            failure.setText("Couldn't play this one\nSwipe up for the next video");
            failure.setContentDescription("Couldn't play this video");
            failure.setVisibility(View.GONE);
            poster.setVisibility(View.VISIBLE);
            Glide.with(poster).clear(poster);
            if (next.imageUrl == null || next.imageUrl.isEmpty()) {
                poster.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 22)));
            } else {
                Glide.with(poster)
                        .load(imageWithHeaders(next.imageUrl, next.url))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate()
                        .placeholder(new ColorDrawable(Color.rgb(20, 20, 22)))
                        .error(new ColorDrawable(Color.rgb(20, 20, 22)))
                        .into(poster);
            }
            syncOrientationChrome();
        }

        void resizeMediaForComments(int sheetTopOnScreen) {
            if (root.getHeight() <= 0 || mediaLayer.getWidth() <= 0) return;
            mediaLayer.animate().cancel();
            mediaLayer.setScaleX(1f);
            mediaLayer.setScaleY(1f);
            if (!horizontalVideo || videoAspectRatio <= 1f) {
                mediaLayer.setTranslationY(0f);
                return;
            }

            int[] rootLocation = new int[2];
            root.getLocationOnScreen(rootLocation);
            float available = Math.max(0f, sheetTopOnScreen - rootLocation[1]);
            float viewportHeight = mediaLayer.getHeight();
            float renderedVideoHeight = Math.min(
                    viewportHeight,
                    mediaLayer.getWidth() / videoAspectRatio
            );
            float currentTop = (viewportHeight - renderedVideoHeight) / 2f;
            float targetTop = Math.max(dp(8), (available - renderedVideoHeight) / 2f);
            mediaLayer.setTranslationY(Math.min(0f, targetTop - currentTop));
        }

        void restoreMediaAfterComments(Runnable endAction) {
            mediaLayer.animate().cancel();
            mediaLayer.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(190L)
                    .withEndAction(() -> {
                        mediaLayer.setScaleX(1f);
                        mediaLayer.setScaleY(1f);
                        mediaLayer.setTranslationY(0f);
                        if (endAction != null) endAction.run();
                    })
                    .start();
        }

        void prepare(CrazyShitRepository.StreamInfo nextStream, boolean autoplay) {
            if (item == null || nextStream == null || nextStream.mediaUrl == null || nextStream.mediaUrl.isEmpty()) return;
            if (stream != null && stream.mediaUrl.equals(nextStream.mediaUrl) && player != null) {
                PlaybackException currentError = player.getPlayerError();
                if (currentError != null) {
                    lastPlaybackError = currentError;
                    lastFailureStage = "Player stopped before ready";
                    if (!retryAttempted) {
                        retryAttempted = true;
                        retryPlayback(this, currentError);
                    } else {
                        showPlayerFailureAndSkip(currentError, lastFailureStage);
                    }
                    return;
                }
                loading.setVisibility(View.GONE);
                applyMuteState();
                if (autoplay) {
                    everStarted = true;
                    player.play();
                    startProgressUpdates();
                } else {
                    player.pause();
                    stopProgressUpdates();
                }
                maybeCompleteStartupHandoff();
                preloadReadyComments(item, boundPosition);
                return;
            }

            releasePlayer();
            stream = nextStream;
            lastAttemptedStream = nextStream;
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
            try {
                http.setUserAgent(WebSettings.getDefaultUserAgent(activity));
            } catch (Exception ignored) {
            }

            Map<String, String> headers = new HashMap<>();
            try {
                headers.putAll(ShitShowPlayableResolver.playbackHeaders(nextStream.mediaUrl));
            } catch (Exception ignored) {
            }
            if (nextStream.pageUrl != null && !nextStream.pageUrl.isEmpty()) {
                putHeaderIfMissing(headers, "Referer", nextStream.requestReferer);
                try {
                    Uri page = Uri.parse(nextStream.pageUrl);
                    if (page.getScheme() != null && page.getHost() != null) {
                        putHeaderIfMissing(headers, "Origin", page.getScheme() + "://" + page.getHost());
                    }
                } catch (Exception ignored) {
                }
            }
            try {
                String cookies = CookieManager.getInstance().getCookie(nextStream.mediaUrl);
                if ((cookies == null || cookies.isEmpty()) && nextStream.pageUrl != null) {
                    cookies = CookieManager.getInstance().getCookie(nextStream.pageUrl);
                }
                if (cookies != null && !cookies.isEmpty()) putHeaderIfMissing(headers, "Cookie", cookies);
            } catch (Exception ignored) {
            }
            if (!headers.isEmpty()) http.setDefaultRequestProperties(headers);

            DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(activity)
                    .setDataSourceFactory(ShitTokMediaCache.wrap(activity, http));
            ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(activity)
                    .setMediaSourceFactory(sourceFactory);
            if (!autoplay) {
                playerBuilder.setLoadControl(
                        new DefaultLoadControl.Builder()
                                .setBufferDurationsMs(
                                        NEXT_PRELOAD_MIN_BUFFER_MS,
                                        NEXT_PRELOAD_MAX_BUFFER_MS,
                                        350,
                                        1_000
                                )
                                .setPrioritizeTimeOverSizeThresholds(true)
                                .build()
                );
            }
            player = playerBuilder.build();
            ExoPlayer createdPlayer = player;
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
            player.setVolume(chaosMuted ? 0f : 1f);
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters()
                            .buildUpon()
                            .setMaxVideoSize(SHITTOK_MAX_VIDEO_WIDTH, SHITTOK_MAX_VIDEO_HEIGHT)
                            .setMaxVideoBitrate(SHITTOK_MAX_VIDEO_BITRATE)
                            .build()
            );
            playerView.setPlayer(player);

            MediaItem.Builder media = new MediaItem.Builder().setUri(nextStream.mediaUrl);
            String lowerUrl = nextStream.mediaUrl.toLowerCase(Locale.US);
            if (lowerUrl.contains(".m3u8")) media.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if (lowerUrl.contains(".mpd")) media.setMimeType(MimeTypes.APPLICATION_MPD);
            else if (lowerUrl.contains(".mp4") || lowerUrl.contains(".m4v")) media.setMimeType(MimeTypes.VIDEO_MP4);
            player.setMediaItem(media.build());
            player.setPlayWhenReady(autoplay);
            if (autoplay) everStarted = true;
            player.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    if (player != createdPlayer || videoSize.width <= 0 || videoSize.height <= 0) return;
                    float width = videoSize.width * Math.max(0.01f, videoSize.pixelWidthHeightRatio);
                    float height = videoSize.height;
                    videoAspectRatio = width / Math.max(1f, height);
                    horizontalVideo = shouldOfferLandscapeFullscreen(videoAspectRatio);
                    root.post(ChaosHolder.this::syncOrientationChrome);
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (player != createdPlayer) return;
                    if (state == Player.STATE_READY) {
                        RatingFeedbackPrompt.recordSuccessfulPlayback(activity, nextStream.mediaUrl);
                        failurePending = false;
                        root.removeCallbacks(skipFailedClipRunnable);
                        loading.setVisibility(View.GONE);
                        failure.setVisibility(View.GONE);
                        poster.setVisibility(View.GONE);
                        updateProgress();
                        if (player != null && player.isPlaying()) startProgressUpdates();
                        maybeCompleteStartupHandoff();
                        preloadReadyComments(item, boundPosition);
                    } else if (state == Player.STATE_ENDED) {
                        loading.setVisibility(View.GONE);
                        stopProgressUpdates();
                        seekBar.setProgress(1000);
                        if (item != null) {
                            try {
                                long duration = Math.max(0L, createdPlayer.getDuration());
                                PlaybackHistoryStore.record(
                                        activity,
                                        item.title,
                                        item.url,
                                        duration,
                                        duration,
                                        true
                                );
                            } catch (Exception ignored) {
                            }
                        }
                        requestAutoAdvance(boundPosition);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (player != createdPlayer) return;
                    if (isPlaying) {
                        everStarted = true;
                        startProgressUpdates();
                        showControlsTemporarily();
                    } else {
                        stopProgressUpdates();
                        updateProgress();
                        if (!scrubbing) showControlsPersistent();
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (player != createdPlayer) return;
                    loading.setVisibility(View.GONE);
                    stopProgressUpdates();
                    lastPlaybackError = error;
                    lastFailureStage = "Player error";
                    if (!retryAttempted) {
                        retryAttempted = true;
                        root.post(() -> retryPlayback(ChaosHolder.this, error));
                    } else {
                        showPlayerFailureAndSkip(error, lastFailureStage);
                    }
                }
            });
            createdPlayer.prepare();
        }

        private void maybeCompleteStartupHandoff() {
            if (!ChaosStartupHandoff.isWaiting() || player == null) return;
            if (!active || !hostResumed || boundPosition != selectedPosition) return;
            if (player.getPlaybackState() != Player.STATE_READY) return;
            ChaosStartupHandoff.markFirstChaosPlayerReady();
        }

        private boolean isBoundTo(String pageUrl, int position) {
            return item != null
                    && pageUrl != null
                    && pageUrl.equals(item.url)
                    && boundPosition == position;
        }

        private void noteResolutionRetry() {
            retryAttempted = true;
            lastFailureStage = "Stream resolution retry";
        }

        private void noteResolutionRetryIfNeeded(boolean retried) {
            if (!retried) return;
            retryAttempted = true;
            if (lastFailureStage.isEmpty()) lastFailureStage = "Stream resolution retry";
        }

        private CrazyShitRepository.StreamInfo diagnosticStream() {
            return stream != null ? stream : lastAttemptedStream;
        }

        private void putHeaderIfMissing(Map<String, String> headers, String name, String value) {
            if (headers == null || name == null || value == null || value.trim().isEmpty()) return;
            for (String key : headers.keySet()) {
                if (key != null && name.equalsIgnoreCase(key)) return;
            }
            headers.put(name, value);
        }

        private void showMoreMenu() {
            if (item == null) return;
            String savedLabel = FavoriteStore.contains(activity, item.url)
                    ? "Remove from Watch Later"
                    : "Watch Later";
            VideoActionSheet.show(
                    activity,
                    item.title,
                    VideoActionSheet.section(
                            "SAVE",
                            VideoActionSheet.action(
                                    R.drawable.ic_action_download,
                                    "Download",
                                    "Save this video for offline playback",
                                    this::downloadCurrentVideo
                            ),
                            VideoActionSheet.action(
                                    R.drawable.ic_more_library,
                                    savedLabel,
                                    "Keep this video in your library",
                                    () -> toggleSaved(item, save)
                            )
                    ),
                    VideoActionSheet.section(
                            "ACTIONS",
                            VideoActionSheet.action(
                                    R.drawable.ic_action_comments,
                                    "Comments",
                                    "Read and reply without leaving ShitTok",
                                    () -> openInlineComments(item)
                            ),
                            VideoActionSheet.action(
                                    R.drawable.ic_action_share,
                                    "Share",
                                    "Send the video page",
                                    () -> share(item)
                            ),
                            VideoActionSheet.action(
                                    R.drawable.ic_more_website,
                                    "Video details",
                                    "View the full video page",
                                    this::openCurrentDetails
                            )
                    ),
                    VideoActionSheet.section(
                            "FEED",
                            VideoActionSheet.action(
                                    R.drawable.ic_action_hide,
                                    "Not interested",
                                    "Hide this clip from your ShitTok feed",
                                    () -> hideFromChaos(item)
                            ),
                            VideoActionSheet.action(
                                    R.drawable.ic_action_report,
                                    "Report problem",
                                    "Tell us what went wrong",
                                    () -> showPlaybackReport(this)
                            )
                    )
            );
        }

        private void downloadCurrentVideo() {
            if (item == null) return;
            if (stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty()) {
                VideoDownloadStore.downloadPage(activity, item);
                return;
            }
            String userAgent = "";
            String cookies = "";
            try {
                userAgent = WebSettings.getDefaultUserAgent(activity);
            } catch (Exception ignored) {
            }
            try {
                cookies = CookieManager.getInstance().getCookie(stream.mediaUrl);
                if ((cookies == null || cookies.isEmpty()) && stream.pageUrl != null) {
                    cookies = CookieManager.getInstance().getCookie(stream.pageUrl);
                }
            } catch (Exception ignored) {
            }
            VideoDownloadStore.downloadKnown(
                    activity,
                    item.title,
                    stream.pageUrl == null || stream.pageUrl.isEmpty() ? item.url : stream.pageUrl,
                    item.imageUrl,
                    stream.mediaUrl,
                    userAgent,
                    cookies,
                    stream.requestReferer
            );
        }

        private void openCurrentDetails() {
            if (item == null) return;
            pauseAndRecord();
            host.openDetails(item);
        }

        void applyMuteState() {
            mute.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    chaosMuted ? R.drawable.ic_action_volume_off : R.drawable.ic_action_volume_on,
                    0,
                    0
            );
            mute.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            mute.setContentDescription(chaosMuted ? "Unmute video" : "Mute video");
            if (player != null) player.setVolume(chaosMuted ? 0f : 1f);
        }

        private void restorePlaybackSpeed() {
            if (!speedBoosting) return;
            speedBoosting = false;
            speedBadge.setVisibility(View.GONE);
            if (player != null) player.setPlaybackSpeed(restoreSpeed <= 0f ? 1f : restoreSpeed);
        }

        void showControlsTemporarily() {
            showControls(true);
        }

        private void showControlsPersistent() {
            showControls(false);
        }

        private void applyViewportInset() {
            int bottom = portraitViewportBottomInset();
            if (root.getPaddingLeft() == 0 && root.getPaddingTop() == 0 &&
                    root.getPaddingRight() == 0 && root.getPaddingBottom() == bottom) {
                return;
            }
            root.setPadding(0, 0, 0, bottom);
        }

        private boolean portrait() {
            return activity.getResources().getConfiguration().orientation
                    != Configuration.ORIENTATION_LANDSCAPE;
        }

        void syncOrientationChrome() {
            applyViewportInset();
            root.removeCallbacks(hideControlsRunnable);
            root.removeCallbacks(hideSeekBarRunnable);
            lower.animate().cancel();
            playbackRail.animate().cancel();
            seekBar.animate().cancel();

            controlsVisible = true;
            lower.setVisibility(View.VISIBLE);
            playbackRail.setVisibility(View.VISIBLE);
            lower.setAlpha(1f);
            playbackRail.setAlpha(1f);
            fullscreen.setImageResource(manualFullscreen
                    ? R.drawable.ic_action_fullscreen_exit
                    : R.drawable.ic_action_fullscreen);
            fullscreen.setContentDescription(manualFullscreen
                    ? "Exit fullscreen"
                    : "Watch horizontal video fullscreen");
            fullscreen.setVisibility(horizontalVideo ? View.VISIBLE : View.GONE);
            playbackRail.setAlpha(1f);

            if (seekBar.getVisibility() != View.VISIBLE) {
                seekBar.setVisibility(View.VISIBLE);
                seekBar.setAlpha(1f);
            }
            if (!scrubbing) root.postDelayed(hideSeekBarRunnable, 2200L);

            if (!portrait() && player != null && player.isPlaying() && !scrubbing) {
                root.postDelayed(hideControlsRunnable, 2200L);
            }
        }

        private void showControls(boolean autoHide) {
            root.removeCallbacks(hideControlsRunnable);
            root.removeCallbacks(hideSeekBarRunnable);
            lower.animate().cancel();
            playbackRail.animate().cancel();
            seekBar.animate().cancel();
            controlsVisible = true;
            lower.setVisibility(View.VISIBLE);
            playbackRail.setVisibility(View.VISIBLE);
            fullscreen.setVisibility(horizontalVideo ? View.VISIBLE : View.GONE);
            seekBar.setVisibility(View.VISIBLE);
            lower.setAlpha(1f);
            playbackRail.setAlpha(1f);
            seekBar.setAlpha(1f);
            if (!scrubbing) root.postDelayed(hideSeekBarRunnable, 2200L);
            if (autoHide && !scrubbing && !portrait()) {
                root.postDelayed(hideControlsRunnable, 2200L);
            }
        }

        private void hideControlsNow() {
            if (portrait()) {
                controlsVisible = true;
                lower.setVisibility(View.VISIBLE);
                playbackRail.setVisibility(View.VISIBLE);
                fullscreen.setVisibility(horizontalVideo ? View.VISIBLE : View.GONE);
                lower.setAlpha(1f);
                playbackRail.setAlpha(1f);
                return;
            }
            if (scrubbing || player == null || !player.isPlaying()) return;
            controlsVisible = false;
            lower.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (!controlsVisible) lower.setVisibility(View.INVISIBLE);
                    })
                    .start();
            playbackRail.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (!controlsVisible) playbackRail.setVisibility(View.INVISIBLE);
                    })
                    .start();
        }

        private void hideSeekBarNow() {
            if (scrubbing) return;
            seekBar.animate().cancel();
            seekBar.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (!scrubbing) seekBar.setVisibility(View.INVISIBLE);
                    })
                    .start();
        }

        private void startProgressUpdates() {
            root.removeCallbacks(progressRunnable);
            updateProgress();
            root.postDelayed(progressRunnable, 250L);
        }

        private void stopProgressUpdates() {
            root.removeCallbacks(progressRunnable);
        }

        private void updateProgress() {
            if (player == null || scrubbing) return;
            long duration = player.getDuration();
            long position = Math.max(0L, player.getCurrentPosition());
            if (duration <= 0L) {
                seekBar.setEnabled(false);
                seekBar.setProgress(0);
                return;
            }
            seekBar.setEnabled(true);
            int progress = (int) Math.max(0L, Math.min(1000L, (position * 1000L) / duration));
            seekBar.setProgress(progress);
        }

        void showRetrying() {
            failurePending = false;
            root.removeCallbacks(skipFailedClipRunnable);
            loading.setVisibility(View.VISIBLE);
            failure.setText("Trying another link…");
            failure.setContentDescription("Playback failed. Trying another link.");
            failure.setVisibility(View.VISIBLE);
            poster.setVisibility(View.VISIBLE);
        }

        void showResolutionFailureAndSkip(boolean retried) {
            retryAttempted = retryAttempted || retried;
            lastFailureStage = "Stream resolution failed";
            lastPlaybackError = null;
            showFinalFailure();
        }

        void showPlayerFailureAndSkip(PlaybackException error, String stage) {
            lastPlaybackError = error;
            lastFailureStage = stage == null ? "Player error" : stage;
            showFinalFailure();
        }

        private void showFinalFailure() {
            RatingFeedbackPrompt.recordPlaybackError(activity);
            loading.setVisibility(View.GONE);
            failurePending = true;
            failure.setText("Couldn't play this one\nMoving to the next video…");
            failure.setContentDescription("Couldn't play this video. Moving to the next video.");
            failure.setVisibility(View.VISIBLE);
            poster.setVisibility(View.VISIBLE);
            showControlsPersistent();
            root.removeCallbacks(skipFailedClipRunnable);
            root.postDelayed(skipFailedClipRunnable, FAILED_CLIP_SKIP_DELAY_MS);
        }

        void pauseAndRecord() {
            if (player == null || item == null) return;
            restorePlaybackSpeed();
            stopProgressUpdates();
            try {
                long position = Math.max(0L, player.getCurrentPosition());
                long duration = Math.max(0L, player.getDuration());
                if (everStarted || position > 1000L) {
                    PlaybackHistoryStore.record(activity, item.title, item.url, position, duration, false);
                }
                player.pause();
            } catch (Exception ignored) {
            }
        }

        void releasePlayer() {
            root.removeCallbacks(hideControlsRunnable);
            root.removeCallbacks(hideSeekBarRunnable);
            root.removeCallbacks(skipFailedClipRunnable);
            failurePending = false;
            stopProgressUpdates();
            restorePlaybackSpeed();
            if (scrubbing) {
                scrubbing = false;
                pager.setUserInputEnabled(true);
            }
            if (player != null) {
                try {
                    playerView.setPlayer(null);
                    player.release();
                } catch (Exception ignored) {
                }
                player = null;
            }
            stream = null;
        }
    }
}
