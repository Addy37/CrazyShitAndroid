package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
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
        void openComments(NativeContentItem item);
    }

    private static final String PREFS = "chaos_feed";
    private static final String KEY_RECENT = "recent_urls";
    private static final int MAX_RECENT = 180;
    private static final int MAX_SOURCE_PAGE = 8;
    private static final int LOAD_AHEAD_AT = 5;
    private static final String SITE = "https://crazyshit.com/";

    private final Activity activity;
    private final Host host;
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final Set<String> sessionUrls = new HashSet<>();
    private final Deque<String> recentUrls = new ArrayDeque<>();
    private final Set<String> recentSet = new HashSet<>();
    private final Map<String, CrazyShitRepository.StreamInfo> streamCache = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();
    private final Set<String> unplayable = new HashSet<>();
    private final Random random = new Random();

    private ViewPager2 pager;
    private ChaosAdapter adapter;
    private TextView empty;
    private ProgressBar initialProgress;
    private boolean active;
    private boolean hostResumed = true;
    private boolean poolLoading;
    private boolean autoAdvancePending;
    private int autoAdvanceFrom = -1;
    private int sourcePage = 1;
    private int selectedPosition;

    public ChaosFeedView(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        setBackgroundColor(Color.BLACK);
        loadRecent();
        buildUi();
        loadMorePool();
    }

    private void buildUi() {
        pager = new ViewPager2(activity);
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        pager.setOffscreenPageLimit(1);
        adapter = new ChaosAdapter();
        pager.setAdapter(adapter);
        addView(pager, new FrameLayout.LayoutParams(-1, -1));

        initialProgress = new ProgressBar(activity);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(48), dp(48));
        pp.gravity = Gravity.CENTER;
        addView(initialProgress, pp);

        empty = new TextView(activity);
        empty.setTextColor(Color.rgb(205, 205, 212));
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(30), dp(30), dp(30), dp(30));
        empty.setText("Loading Chaos…");
        empty.setVisibility(View.GONE);
        addView(empty, new FrameLayout.LayoutParams(-1, -1));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (autoAdvancePending && position != autoAdvanceFrom) {
                    autoAdvancePending = false;
                    autoAdvanceFrom = -1;
                }
                selectedPosition = position;
                markSeen(position);
                pauseNonSelected(position);
                resolveAhead(position);
                playSelected();
                if (items.size() - position <= LOAD_AHEAD_AT) loadMorePool();
            }
        });
    }

    public void setActive(boolean value) {
        active = value;
        if (active && hostResumed) {
            resolveAhead(selectedPosition);
            playSelected();
        } else {
            pauseAll();
        }
    }

    public void onHostResume() {
        hostResumed = true;
        if (active) playSelected();
    }

    public void onHostPause() {
        hostResumed = false;
        pauseAll();
    }

    public void refresh() {
        pauseAll();
        sourcePage = 1;
        poolLoading = false;
        autoAdvancePending = false;
        autoAdvanceFrom = -1;
        streamCache.clear();
        resolving.clear();
        unplayable.clear();
        sessionUrls.clear();
        items.clear();
        adapter.notifyDataSetChanged();
        initialProgress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        selectedPosition = 0;
        pager.setCurrentItem(0, false);
        loadMorePool();
    }

    public void close() {
        pauseAll();
        releaseVisiblePlayers();
        io.shutdownNow();
    }

    private void loadMorePool() {
        if (poolLoading) return;
        poolLoading = true;
        final int requestPage = sourcePage;
        sourcePage++;

        io.execute(() -> {
            LinkedHashMap<String, NativeContentItem> combined = new LinkedHashMap<>();
            try {
                for (NativeContentItem item : repository.fetchFeed(activity, CrazyShitRepository.HOME, requestPage)) {
                    if (item != null && !item.url.isEmpty()) combined.put(item.url, item);
                }
            } catch (Exception ignored) {
            }
            try {
                for (NativeContentItem item : repository.fetchFeed(activity, CrazyShitRepository.TRENDING, requestPage)) {
                    if (item != null && !item.url.isEmpty()) combined.putIfAbsent(item.url, item);
                }
            } catch (Exception ignored) {
            }

            ArrayList<NativeContentItem> fresh = new ArrayList<>();
            ArrayList<NativeContentItem> recentFallback = new ArrayList<>();
            for (NativeContentItem item : combined.values()) {
                if (item == null || item.url.isEmpty()) continue;
                if (recentSet.contains(item.url)) recentFallback.add(item);
                else fresh.add(item);
            }
            Collections.shuffle(fresh, random);
            Collections.shuffle(recentFallback, random);

            activity.runOnUiThread(() -> {
                poolLoading = false;
                int before = items.size();
                appendUnique(fresh);

                // Prefer unseen videos strongly. Only recycle older recent videos after
                // several source pages have been exhausted and the feed would otherwise stall.
                if (items.size() - before < 6 && requestPage >= MAX_SOURCE_PAGE) {
                    Collections.reverse(recentFallback);
                    appendUnique(recentFallback);
                }

                int added = items.size() - before;
                if (added > 0) {
                    adapter.notifyItemRangeInserted(before, added);
                    initialProgress.setVisibility(View.GONE);
                    empty.setVisibility(View.GONE);
                    resolveAhead(selectedPosition);
                    if (active && hostResumed) playSelected();
                    tryPendingAutoAdvance();
                }

                if (items.size() < 10 && requestPage < MAX_SOURCE_PAGE) {
                    loadMorePool();
                } else if (items.isEmpty() && requestPage >= MAX_SOURCE_PAGE) {
                    initialProgress.setVisibility(View.GONE);
                    empty.setText("Chaos couldn't find a playable pool right now.\nPull away and come back to retry.");
                    empty.setVisibility(View.VISIBLE);
                } else if (autoAdvancePending && requestPage >= MAX_SOURCE_PAGE
                        && autoAdvanceFrom + 1 >= items.size()) {
                    autoAdvancePending = false;
                    autoAdvanceFrom = -1;
                }
            });
        });
    }

    private void appendUnique(List<NativeContentItem> candidates) {
        if (candidates == null) return;
        for (NativeContentItem item : candidates) {
            if (item == null || item.url == null || item.url.isEmpty()) continue;
            if (!sessionUrls.add(item.url)) continue;
            items.add(item);
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

    private void resolveAhead(int position) {
        for (int offset = 0; offset <= 2; offset++) {
            resolveAt(position + offset);
        }
        if (position > 0) resolveAt(position - 1);
    }

    private void resolveAt(int position) {
        if (position < 0 || position >= items.size()) return;
        NativeContentItem item = items.get(position);
        if (streamCache.containsKey(item.url) || unplayable.contains(item.url) || !resolving.add(item.url)) {
            prepareVisible(position);
            return;
        }

        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = repository.resolvePlayable(activity, item.url);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            activity.runOnUiThread(() -> {
                resolving.remove(item.url);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    unplayable.add(item.url);
                } else {
                    streamCache.put(item.url, resolved);
                }
                prepareVisible(position);
                if (position == selectedPosition) playSelected();
            });
        });
    }

    private void prepareVisible(int position) {
        ChaosHolder holder = holderAt(position);
        if (holder == null || position < 0 || position >= items.size()) return;
        NativeContentItem item = items.get(position);
        CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
        if (stream != null) {
            holder.prepare(stream, active && hostResumed && position == selectedPosition);
        } else if (unplayable.contains(item.url)) {
            holder.showPlaybackFailure();
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
        saveRecent();
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

    private void saveRecent() {
        JSONArray array = new JSONArray();
        for (String url : recentUrls) array.put(url);
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENT, array.toString())
                .apply();
    }

    private void share(NativeContentItem item) {
        if (item == null || item.url.isEmpty()) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, item.url);
        share.putExtra(Intent.EXTRA_SUBJECT, item.title);
        activity.startActivity(Intent.createChooser(share, "Share"));
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
        button.setText(FavoriteStore.contains(activity, item.url) ? "★\nSaved" : "☆\nSave");
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
            if (stream != null) {
                holder.prepare(stream, active && hostResumed && position == selectedPosition);
            } else {
                resolveAt(position);
            }
        }

        @Override
        public void onViewRecycled(@NonNull ChaosHolder holder) {
            holder.pauseAndRecord();
            holder.releasePlayer();
            super.onViewRecycled(holder);
        }
    }

    private final class ChaosHolder extends RecyclerView.ViewHolder {
        final FrameLayout root;
        final PlayerView playerView;
        final ImageView poster;
        final ProgressBar loading;
        final TextView failure;
        final TextView title;
        final TextView meta;
        final TextView save;
        ExoPlayer player;
        NativeContentItem item;
        CrazyShitRepository.StreamInfo stream;
        int boundPosition = -1;

        ChaosHolder(ViewGroup parent) {
            super(new FrameLayout(parent.getContext()));
            root = (FrameLayout) itemView;
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            root.setBackgroundColor(Color.BLACK);

            poster = new ImageView(activity);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(Color.BLACK);
            root.addView(poster, new FrameLayout.LayoutParams(-1, -1));

            // BETA19_CHAOS_OVERLAY_CLEANUP
            playerView = (PlayerView) LayoutInflater.from(activity)
                    .inflate(R.layout.view_video_player_texture, root, false);
            playerView.setUseController(false);
            playerView.setControllerAutoShow(false);
            playerView.hideController();
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setBackgroundColor(Color.BLACK);
            root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

            loading = new ProgressBar(activity);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(44), dp(44));
            lp.gravity = Gravity.CENTER;
            root.addView(loading, lp);

            failure = new TextView(activity);
            failure.setTextColor(Color.WHITE);
            failure.setTextSize(14);
            failure.setGravity(Gravity.CENTER);
            failure.setText("Couldn't play this one\nSwipe up for the next video");
            failure.setPadding(dp(28), dp(28), dp(28), dp(28));
            failure.setVisibility(View.GONE);
            root.addView(failure, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout lower = new LinearLayout(activity);
            lower.setOrientation(LinearLayout.HORIZONTAL);
            lower.setGravity(Gravity.BOTTOM);
            lower.setPadding(dp(16), dp(18), dp(10), dp(18));
            lower.setBackgroundColor(Color.TRANSPARENT);
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

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.VERTICAL);
            actions.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            actions.setBackgroundColor(Color.TRANSPARENT);
            lower.addView(actions, new LinearLayout.LayoutParams(dp(82), -2));

            save = actionButton("☆\nSave");
            actions.addView(save, actionParams());

            TextView comments = actionButton("💬\nComments");
            actions.addView(comments, actionParams());

            TextView share = actionButton("↗\nShare");
            actions.addView(share, actionParams());

            TextView details = actionButton("⋯\nDetails");
            actions.addView(details, actionParams());

            playerView.setOnClickListener(v -> {
                haptic(v);
                if (player == null) return;
                if (player.isPlaying()) player.pause();
                else player.play();
            });
            save.setOnClickListener(v -> {
                haptic(v);
                toggleSaved(item, save);
            });
            comments.setOnClickListener(v -> {
                haptic(v);
                if (item != null) host.openComments(item);
            });
            share.setOnClickListener(v -> {
                haptic(v);
                share(item);
            });
            details.setOnClickListener(v -> {
                haptic(v);
                pauseAndRecord();
                if (item != null) host.openDetails(item);
            });
        }

        private TextView actionButton(String value) {
            TextView button = new TextView(activity);
            button.setText(value);
            button.setTextColor(Color.WHITE);
            button.setTextSize(10);
            button.setGravity(Gravity.CENTER);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setShadowLayer(dp(2), 0f, dp(1), Color.BLACK);
            button.setPadding(dp(2), dp(3), dp(2), dp(3));
            button.setClickable(true);
            button.setFocusable(true);
            return button;
        }

        private LinearLayout.LayoutParams actionParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(48));
            params.setMargins(0, dp(2), 0, dp(2));
            return params;
        }

        void bind(NativeContentItem next, int position) {
            pauseAndRecord();
            releasePlayer();
            item = next;
            stream = null;
            boundPosition = position;
            title.setText(next.title == null || next.title.isEmpty() ? "Random video" : next.title);
            StringBuilder info = new StringBuilder();
            if (next.uploader != null && !next.uploader.isEmpty()) info.append(next.uploader);
            if (next.views != null && !next.views.isEmpty()) {
                if (info.length() > 0) info.append("  •  ");
                info.append(next.views).append(" views");
            }
            meta.setText(info);
            updateSaveButton(next, save);
            loading.setVisibility(View.VISIBLE);
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
        }

        void prepare(CrazyShitRepository.StreamInfo nextStream, boolean autoplay) {
            if (item == null || nextStream == null || nextStream.mediaUrl == null || nextStream.mediaUrl.isEmpty()) return;
            if (stream != null && stream.mediaUrl.equals(nextStream.mediaUrl) && player != null) {
                loading.setVisibility(View.GONE);
                if (autoplay) player.play(); else player.pause();
                return;
            }

            releasePlayer();
            stream = nextStream;
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
            try {
                http.setUserAgent(WebSettings.getDefaultUserAgent(activity));
            } catch (Exception ignored) {
            }
            Map<String, String> headers = new HashMap<>();
            if (nextStream.pageUrl != null && !nextStream.pageUrl.isEmpty()) {
                headers.put("Referer", nextStream.pageUrl);
                try {
                    Uri page = Uri.parse(nextStream.pageUrl);
                    if (page.getScheme() != null && page.getHost() != null) {
                        headers.put("Origin", page.getScheme() + "://" + page.getHost());
                    }
                } catch (Exception ignored) {
                }
            }
            try {
                String cookies = CookieManager.getInstance().getCookie(nextStream.mediaUrl);
                if ((cookies == null || cookies.isEmpty()) && nextStream.pageUrl != null) {
                    cookies = CookieManager.getInstance().getCookie(nextStream.pageUrl);
                }
                if (cookies != null && !cookies.isEmpty()) headers.put("Cookie", cookies);
            } catch (Exception ignored) {
            }
            if (!headers.isEmpty()) http.setDefaultRequestProperties(headers);

            DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(activity)
                    .setDataSourceFactory(http);
            player = new ExoPlayer.Builder(activity)
                    .setMediaSourceFactory(sourceFactory)
                    .build();
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
            playerView.setPlayer(player);

            MediaItem.Builder media = new MediaItem.Builder().setUri(nextStream.mediaUrl);
            String lower = nextStream.mediaUrl.toLowerCase(Locale.US);
            if (lower.contains(".m3u8")) media.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if (lower.contains(".mpd")) media.setMimeType(MimeTypes.APPLICATION_MPD);
            player.setMediaItem(media.build());
            player.setPlayWhenReady(autoplay);
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        loading.setVisibility(View.GONE);
                        poster.setVisibility(View.GONE);
                    } else if (state == Player.STATE_ENDED) {
                        loading.setVisibility(View.GONE);
                        if (item != null && player != null) {
                            try {
                                long duration = Math.max(0L, player.getDuration());
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
                public void onPlayerError(PlaybackException error) {
                    loading.setVisibility(View.GONE);
                    showPlaybackFailure();
                }
            });
            player.prepare();
        }

        void showPlaybackFailure() {
            loading.setVisibility(View.GONE);
            failure.setVisibility(View.VISIBLE);
            poster.setVisibility(View.VISIBLE);
        }

        void pauseAndRecord() {
            if (player == null || item == null) return;
            try {
                long position = Math.max(0L, player.getCurrentPosition());
                long duration = Math.max(0L, player.getDuration());
                PlaybackHistoryStore.record(activity, item.title, item.url, position, duration, false);
                player.pause();
            } catch (Exception ignored) {
            }
        }

        void releasePlayer() {
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
