package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NativeFeedAdapter extends RecyclerView.Adapter<NativeFeedAdapter.Holder> {
    static final String STYLE_TAG = "native_feed_card";
    public static final int VIEW_CARDS = 0;
    public static final int VIEW_LARGE = VIEW_CARDS;
    public static final int VIEW_LIST = 1;
    public static final int VIEW_COMPACT = VIEW_LIST;
    public static final int VIEW_GRID = 2;
    public static final int VIEW_POSTERS = 3;
    private static final int TYPE_SECTION = 100;

    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final long MIN_FEED_PROGRESS_MS = 5_000L;
    private static final int SECTION_ACCENT = UiPalette.PRIMARY;

    public interface Listener {
        void onOpen(NativeContentItem item);
        void onLongPress(NativeContentItem item, View anchor);
        void onComments(NativeContentItem item);
    }

    private final Context context;
    private final List<NativeContentItem> items = new ArrayList<>();
    private final Set<String> itemUrls = new HashSet<>();
    private final Listener listener;
    private final boolean homePresentation;
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final Set<String> requestedThumbnails = new HashSet<>();
    private final Set<String> failedDirectThumbnails = new HashSet<>();
    private final Map<String, RenderedThumbnailResolver> thumbnailJobs = new HashMap<>();
    private final RenderedThumbnailResolver[] thumbnailResolvers;
    private final Map<String, PlaybackHistoryStore.Item> playbackByUrl = new HashMap<>();
    private final SharedPreferences playbackPrefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener playbackListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int resolverCursor;
    private int viewMode = VIEW_LIST;
    private boolean closed;

    public NativeFeedAdapter(Context context, Listener listener) {
        this(context, listener, false);
    }

    NativeFeedAdapter(Context context, Listener listener, boolean homePresentation) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.homePresentation = homePresentation;
        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(this.context, this::setResolvedThumbnail),
                new RenderedThumbnailResolver(this.context, this::setResolvedThumbnail)
        };
        playbackPrefs = this.context.getSharedPreferences("playback_history", Context.MODE_PRIVATE);
        playbackListener = (prefs, key) -> {
            if (!"items".equals(key) || closed) return;
            mainHandler.post(this::refreshPlaybackState);
        };
        playbackPrefs.registerOnSharedPreferenceChangeListener(playbackListener);
        reloadPlaybackStates();
        setHasStableIds(true);
    }

    public void setViewMode(int mode) {
        int next = mode;
        if (next < VIEW_CARDS || next > VIEW_POSTERS) next = VIEW_LIST;
        if (viewMode == next) return;
        viewMode = next;
        notifyDataSetChanged();
    }

    public int getViewMode() {
        return viewMode;
    }

    public void close() {
        if (closed) return;
        closed = true;
        try {
            playbackPrefs.unregisterOnSharedPreferenceChangeListener(playbackListener);
        } catch (Exception ignored) {
        }
        mainHandler.removeCallbacksAndMessages(null);
        for (RenderedThumbnailResolver resolver : thumbnailResolvers) {
            if (resolver != null) resolver.close();
        }
        requestedThumbnails.clear();
        failedDirectThumbnails.clear();
        thumbnailJobs.clear();
    }

    public boolean isSectionAt(int position) {
        return position >= 0 && position < items.size() && items.get(position).isSection();
    }

    List<NativeContentItem> snapshot() { return new ArrayList<>(items); }

    public void replace(List<NativeContentItem> next) {
        items.clear();
        itemUrls.clear();
        if (next != null) {
            for (NativeContentItem item : next) {
                if (item == null) continue;
                items.add(item);
                itemUrls.add(item.url);
            }
        }
        reloadPlaybackStates();
        notifyDataSetChanged();
        preloadRange(0, Math.min(12, items.size()));
    }

    public void append(List<NativeContentItem> next) {
        if (next == null || next.isEmpty()) return;
        int start = items.size();
        String lastSection = lastSectionTitle();

        for (NativeContentItem item : next) {
            if (item == null) continue;
            if (item.isSection() && item.title.equalsIgnoreCase(lastSection)) continue;

            if (!itemUrls.add(item.url)) continue;

            items.add(item);
            if (item.isSection()) lastSection = item.title;
        }

        int added = items.size() - start;
        if (added > 0) {
            notifyItemRangeInserted(start, added);
            preloadRange(start, Math.min(items.size(), start + 10));
        }
    }

    private String lastSectionTitle() {
        for (int i = items.size() - 1; i >= 0; i--) {
            NativeContentItem item = items.get(i);
            if (item != null && item.isSection()) return item.title;
        }
        return "";
    }

    public void preloadVisible(int first, int last) {
        int from = Math.max(0, first);
        int to = Math.min(items.size(), Math.max(from, last + 5));
        preloadRange(from, to);
    }

    public int size() {
        return items.size();
    }

    public void refreshPlaybackState() {
        if (closed) return;
        reloadPlaybackStates();
        notifyItemRangeChanged(0, items.size(), "playback");
    }

    public void setResolvedThumbnail(String pageUrl, String thumbnailUrl) {
        if (closed || pageUrl == null || pageUrl.isEmpty()) return;
        thumbnailJobs.remove(pageUrl);
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
            requestedThumbnails.remove(pageUrl);
            return;
        }
        resolvedThumbnails.put(pageUrl, thumbnailUrl);
        for (int i = 0; i < items.size(); i++) {
            if (pageUrl.equals(items.get(i).url)) {
                notifyItemChanged(i, "thumbnail");
                break;
            }
        }
    }

    private void reloadPlaybackStates() {
        playbackByUrl.clear();
        for (PlaybackHistoryStore.Item item : PlaybackHistoryStore.load(context)) {
            if (item.pageUrl != null && !item.pageUrl.isEmpty()) playbackByUrl.put(item.pageUrl, item);
        }
    }

    private void preloadRange(int start, int end) {
        if (closed) return;
        for (int i = start; i < end; i++) {
            NativeContentItem item = items.get(i);
            preloadDirectThumbnail(item);
            requestThumbnail(item);
        }
    }

    private void requestThumbnail(NativeContentItem item) {
        requestThumbnail(item, "");
    }

    private void requestThumbnail(NativeContentItem item, String rejectedUrl) {
        if (closed || item == null || item.isSection() || item.url == null || item.url.isEmpty()) return;
        if ((rejectedUrl == null || rejectedUrl.isEmpty()) &&
                item.imageUrl != null && !item.imageUrl.trim().isEmpty() &&
                !failedDirectThumbnails.contains(item.url)) return;
        String rejected = rejectedUrl == null ? "" : rejectedUrl;
        if (rejected.isEmpty() && failedDirectThumbnails.contains(item.url)) rejected = item.imageUrl;
        if (resolvedThumbnails.containsKey(item.url) && rejected.isEmpty()) return;
        RenderedThumbnailResolver resolver = thumbnailJobs.get(item.url);
        if (resolver == null) {
            resolver = thumbnailResolvers[resolverCursor++ % thumbnailResolvers.length];
            thumbnailJobs.put(item.url, resolver);
        }
        if (!rejected.isEmpty()) {
            requestedThumbnails.add(item.url);
            resolver.request(item.url, rejected);
        } else if (requestedThumbnails.add(item.url)) {
            resolver.request(item.url);
        }
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).url.hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isSection() ? TYPE_SECTION : viewMode;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SECTION) return createSectionHolder(parent);
        if (viewType == VIEW_LIST) return createListHolder(parent);
        if (viewType == VIEW_GRID) return createGridHolder(parent);
        if (viewType == VIEW_POSTERS) return createPosterHolder(parent);
        return createCardsHolder(parent);
    }

    private Holder createSectionHolder(ViewGroup parent) {
        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setTag(STYLE_TAG);
        card.setCardBackgroundColor(Color.TRANSPARENT);
        card.setStrokeWidth(0);
        card.setCardElevation(0f);
        card.setRadius(0f);
        card.setUseCompatPadding(false);
        card.setClickable(false);
        card.setLongClickable(false);

        boolean landscape = isLandscape(parent);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(card, landscape ? 44 : 50));
        params.setMargins(dp(card, 12), dp(card, landscape ? 6 : 10), dp(card, 12), 0);
        card.setLayoutParams(params);

        TextView header = new TextView(parent.getContext());
        header.setTextSize(landscape ? 20f : 24f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setSingleLine(true);
        header.setEllipsize(TextUtils.TruncateAt.END);
        header.setTextColor(ZeroChillUi.color(parent.getContext(), R.color.zc_text_primary));
        header.setPadding(dp(card, 14), 0, dp(card, 14), 0);
        card.addView(header, new MaterialCardView.LayoutParams(-1, -1));
        return new Holder(card, header);
    }

    private Holder createCardsHolder(ViewGroup parent) {
        MaterialCardView card = baseCard(parent, 12, 8, 16, -1);
        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -2));

        MediaViews media = addMedia(parent, column, Math.min(280, Math.round((parent.getResources().getConfiguration().screenWidthDp - 24) * 9f / 16f)), -1);
        CopyViews copy = addCopy(parent, column, 16, 12, 14, 12, false);
        return new Holder(card, media, copy);
    }

    private Holder createListHolder(ViewGroup parent) {
        boolean landscape = isLandscape(parent);
        int height = landscape ? 106 : 118;
        int width = homePresentation
                ? responsiveHomeListMediaWidthDp(parent, height)
                : landscape ? 150 : 166;
        MaterialCardView card = baseCard(parent, 12, 4, 15, height);
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new MaterialCardView.LayoutParams(-1, -1));

        MediaViews media = addMedia(parent, row, height, width);
        CopyViews copy = addCopy(
                parent,
                row,
                homePresentation ? 14 : landscape ? 14 : 15,
                11,
                homePresentation ? 9 : 12,
                homePresentation ? 7 : landscape ? 7 : 9,
                false
        );
        return new Holder(card, media, copy);
    }

    private Holder createGridHolder(ViewGroup parent) {
        int mediaHeight = responsiveGridMediaHeightDp(parent);
        int cardHeight = mediaHeight + 132;
        MaterialCardView card = baseCard(parent, 6, 5, 14, cardHeight);
        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -1));

        MediaViews media = addMedia(parent, column, mediaHeight, -1);
        CopyViews copy = addCopy(parent, column, 14, 10, 10, 9, true);
        return new Holder(card, media, copy);
    }

    private Holder createPosterHolder(ViewGroup parent) {
        int posterHeight = responsivePosterHeightDp(parent);
        MaterialCardView card = baseCard(parent, 6, 5, 14, posterHeight);
        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -1));

        MediaViews media = addMedia(parent, column, posterHeight, -1);

        LinearLayout overlay = new LinearLayout(parent.getContext());
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.BOTTOM);
        overlay.setPadding(dp(parent, 10), dp(parent, 18), dp(parent, 10), dp(parent, 9));
        GradientDrawable overlayBg = new GradientDrawable();
        overlayBg.setColor(Color.argb(218, 13, 13, 16));
        overlay.setBackground(overlayBg);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(-1, dp(parent, 72));
        overlayParams.gravity = Gravity.BOTTOM;
        media.frame.addView(overlay, overlayParams);

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(13.5f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        overlay.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(parent.getContext());
        info.setTextColor(Color.rgb(190, 190, 199));
        info.setTextSize(10.5f);
        info.setSingleLine(true);
        info.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(parent, 4);
        overlay.addView(info, infoParams);

        TextView unusedComments = new TextView(parent.getContext());
        TextView unusedDescription = new TextView(parent.getContext());
        unusedDescription.setVisibility(View.GONE);
        CopyViews copy = new CopyViews(title, info, unusedComments, unusedDescription);
        return new Holder(card, media, copy);
    }

    private MaterialCardView baseCard(
            ViewGroup parent,
            int horizontalMargin,
            int verticalMargin,
            int radius,
            int fixedHeightDp
    ) {
        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setTag(STYLE_TAG);
        ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_medium);
        card.setRadius(dp(parent, radius));
        ZeroChillMotion.installPressFeedback(card);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                -1,
                fixedHeightDp > 0 ? dp(parent, fixedHeightDp) : -2
        );
        params.setMargins(
                dp(parent, horizontalMargin),
                dp(parent, verticalMargin),
                dp(parent, horizontalMargin),
                dp(parent, verticalMargin)
        );
        card.setLayoutParams(params);
        return card;
    }

    private MediaViews addMedia(ViewGroup parent, LinearLayout host, int heightDp, int widthDp) {
        FrameLayout mediaFrame = new FrameLayout(parent.getContext());
        LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(
                widthDp < 0 ? -1 : dp(parent, widthDp),
                dp(parent, heightDp)
        );
        mediaFrame.setBackground(ZeroChillUi.rounded(
                parent.getContext(),
                ZeroChillUi.color(parent.getContext(), R.color.zc_surface_glass_strong),
                Color.TRANSPARENT,
                R.dimen.zc_radius_medium
        ));
        mediaFrame.setForeground(parent.getContext().getDrawable(R.drawable.zc_media_edge));
        mediaFrame.setClipToOutline(true);
        host.addView(mediaFrame, mediaParams);

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(ZeroChillUi.color(parent.getContext(), R.color.zc_surface_glass_strong));
        mediaFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView play = new TextView(parent.getContext());
        play.setText("▶");
        play.setTextColor(Color.WHITE);
        boolean dense = viewMode == VIEW_GRID || viewMode == VIEW_POSTERS;
        play.setTextSize(dense ? 19 : 23);
        play.setGravity(Gravity.CENTER);
        play.setBackground(ZeroChillUi.rounded(
                parent.getContext(),
                Color.argb(210, 8, 13, 17),
                ZeroChillUi.color(parent.getContext(), R.color.zc_edge),
                R.dimen.zc_radius_small
        ));
        int size = dense ? 40 : 46;
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(parent, size), dp(parent, size));
        playParams.gravity = Gravity.BOTTOM | Gravity.START;
        playParams.setMargins(dp(parent, 8), 0, 0, dp(parent, 8));
        mediaFrame.addView(play, playParams);

        TextView watchBadge = new TextView(parent.getContext());
        watchBadge.setTextColor(Color.WHITE);
        watchBadge.setTextSize(dense ? 9f : 10f);
        watchBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        watchBadge.setGravity(Gravity.CENTER);
        watchBadge.setPadding(dp(parent, 7), dp(parent, 4), dp(parent, 7), dp(parent, 4));
        watchBadge.setVisibility(View.GONE);
        watchBadge.setElevation(dp(parent, 6));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2);
        badgeParams.gravity = Gravity.TOP | Gravity.START;
        badgeParams.setMargins(dp(parent, 7), dp(parent, 7), dp(parent, 7), 0);
        mediaFrame.addView(watchBadge, badgeParams);

        TextView overlayComments = new TextView(parent.getContext());
        overlayComments.setTextColor(Color.WHITE);
        overlayComments.setTextSize(dense ? 10f : 11f);
        overlayComments.setTypeface(null, android.graphics.Typeface.BOLD);
        overlayComments.setGravity(Gravity.CENTER);
        overlayComments.setPadding(dp(parent, 8), dp(parent, 5), dp(parent, 8), dp(parent, 5));
        overlayComments.setBackground(ZeroChillUi.rounded(
                parent.getContext(),
                ZeroChillUi.color(parent.getContext(), R.color.zc_surface_glass_strong),
                ZeroChillUi.color(parent.getContext(), R.color.zc_edge),
                R.dimen.zc_radius_pill
        ));
        overlayComments.setVisibility(View.GONE);
        overlayComments.setElevation(dp(parent, 6));
        FrameLayout.LayoutParams commentsParams = new FrameLayout.LayoutParams(-2, -2);
        commentsParams.gravity = viewMode == VIEW_POSTERS
                ? Gravity.TOP | Gravity.END
                : Gravity.BOTTOM | Gravity.END;
        commentsParams.setMargins(dp(parent, 7), dp(parent, 7), dp(parent, 7), dp(parent, 8));
        mediaFrame.addView(overlayComments, commentsParams);

        FrameLayout progressTrack = new FrameLayout(parent.getContext());
        progressTrack.setBackgroundColor(Color.argb(210, 6, 10, 13));
        progressTrack.setVisibility(View.GONE);
        progressTrack.setElevation(dp(parent, 7));
        FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(-1, dp(parent, 3));
        trackParams.gravity = Gravity.BOTTOM;
        mediaFrame.addView(progressTrack, trackParams);

        View progressFill = new View(parent.getContext());
        progressFill.setBackgroundColor(UiPalette.PRIMARY);
        progressFill.setPivotX(0f);
        progressFill.setScaleX(0f);
        progressTrack.addView(progressFill, new FrameLayout.LayoutParams(-1, -1));

        return new MediaViews(mediaFrame, image, play, watchBadge, overlayComments, progressTrack, progressFill);
    }

    private CopyViews addCopy(
            ViewGroup parent,
            LinearLayout host,
            int titleSize,
            int infoSize,
            int horizontalPadding,
            int verticalPadding,
            boolean fillRemaining
    ) {
        LinearLayout copy = new LinearLayout(parent.getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(
                dp(parent, horizontalPadding),
                dp(parent, verticalPadding),
                dp(parent, horizontalPadding),
                dp(parent, verticalPadding)
        );
        LinearLayout.LayoutParams copyParams;
        if (fillRemaining) {
            copyParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        } else {
            copyParams = new LinearLayout.LayoutParams(
                    viewMode == VIEW_LIST ? 0 : -1,
                    viewMode == VIEW_LIST ? -1 : -2,
                    viewMode == VIEW_LIST ? 1f : 0f
            );
        }
        host.addView(copy, copyParams);

        TextView title = new TextView(parent.getContext());
        title.setTextColor(ZeroChillUi.color(parent.getContext(), R.color.zc_text_primary));
        title.setTextSize(titleSize);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(homePresentation && viewMode == VIEW_LIST ? 4 : 2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout titleRow = new LinearLayout(parent.getContext());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView menu = BrowseUi.action(parent.getContext(), "⋮", "Video options", v -> { });
        menu.setTag("video_options");
        menu.setTextSize(24);
        menu.setTextColor(ZeroChillUi.color(parent.getContext(), R.color.zc_text_secondary));
        menu.setBackgroundColor(Color.TRANSPARENT);
        titleRow.addView(menu, new LinearLayout.LayoutParams(dp(parent, 48), dp(parent, 48)));
        copy.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        if (fillRemaining) {
            View spacer = new View(parent.getContext());
            copy.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        }

        LinearLayout metaRow = new LinearLayout(parent.getContext());
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        boolean compactHomeList = homePresentation && viewMode == VIEW_LIST;
        metaRow.setPadding(0, dp(parent, compactHomeList ? 2 : fillRemaining ? 4 : 6), 0, 0);
        copy.addView(metaRow, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(parent.getContext());
        info.setTextColor(ZeroChillUi.color(parent.getContext(), R.color.zc_text_secondary));
        info.setTextSize(infoSize);
        info.setMaxLines(1);
        info.setEllipsize(TextUtils.TruncateAt.END);
        metaRow.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView comments = new TextView(parent.getContext());
        comments.setTextColor(UiPalette.PRIMARY);
        comments.setTextSize(infoSize);
        comments.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        comments.setPadding(dp(parent, 7), dp(parent, 3), 0, dp(parent, 3));
        metaRow.addView(comments, new LinearLayout.LayoutParams(-2, -2));

        TextView description = new TextView(parent.getContext());
        description.setTextColor(ZeroChillUi.color(parent.getContext(), R.color.zc_text_secondary));
        description.setTextSize(Math.max(10f, infoSize + 0.5f));
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setVisibility(View.GONE);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(parent, 4);
        copy.addView(description, 1, descriptionParams);

        return new CopyViews(title, info, comments, description);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        bind(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        NativeContentItem item = items.get(position);
        if (item.isSection()) {
            bindSection(holder, item);
            return;
        }
        if (!payloads.isEmpty() && payloads.contains("thumbnail")) {
            loadThumbnail(holder, item);
            return;
        }
        if (!payloads.isEmpty() && payloads.contains("playback")) {
            bindPlaybackState(holder, item);
            return;
        }
        bind(holder, position);
    }

    private void bind(Holder holder, int position) {
        NativeContentItem item = items.get(position);
        if (item.isSection()) {
            bindSection(holder, item);
            return;
        }

        boolean meme = item.isMeme();
        holder.title.setText(displayTitle(item));
        holder.info.setText(buildInfo(item));
        boolean showDescription = viewMode != VIEW_CARDS && viewMode != VIEW_POSTERS && item.description != null &&
                !item.description.trim().isEmpty();
        holder.description.setVisibility(showDescription ? View.VISIBLE : View.GONE);
        holder.description.setText(showDescription ? item.description.trim() : "");
        holder.play.setVisibility(meme ? View.GONE : View.VISIBLE);
        holder.image.setScaleType(meme ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        bindPlaybackState(holder, item);

        holder.comments.setVisibility(View.GONE);
        holder.comments.setOnClickListener(null);
        holder.overlayComments.setVisibility(View.GONE);
        holder.overlayComments.setOnClickListener(null);

        if (!meme && item.comments != null && !item.comments.isEmpty()) {
            String compact = compactCount(item.comments);
            if (viewMode == VIEW_CARDS || viewMode == VIEW_POSTERS) {
                holder.overlayComments.setVisibility(View.VISIBLE);
                holder.overlayComments.setText(viewMode == VIEW_CARDS
                        ? compact + " comments"
                        : "💬 " + compact);
                holder.overlayComments.setOnClickListener(v -> listener.onComments(item));
            } else {
                holder.comments.setVisibility(View.VISIBLE);
                holder.comments.setText("💬 " + compact);
                holder.comments.setOnClickListener(v -> listener.onComments(item));
            }
        }

        loadThumbnail(holder, item);
        requestThumbnail(item);

        View options = holder.card.findViewWithTag("video_options");
        if (options != null) options.setOnClickListener(v -> listener.onLongPress(item, v));
        holder.card.setOnClickListener(v -> listener.onOpen(item));
        holder.card.setOnLongClickListener(v -> {
            listener.onLongPress(item, v);
            return true;
        });
    }

    private void bindSection(Holder holder, NativeContentItem item) {
        if (holder.sectionTitle == null) return;
        holder.sectionTitle.setText(styledSectionTitle(item.title));
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
    }

    private SpannableString styledSectionTitle(String rawTitle) {
        String title = publicSectionTitle(rawTitle).toUpperCase(Locale.US);
        SpannableString text = new SpannableString(title);
        int split = title.indexOf(' ');
        int accentEnd = split > 0 ? split : title.length();
        if (accentEnd > 0) {
            text.setSpan(
                    new ForegroundColorSpan(SECTION_ACCENT),
                    0,
                    accentEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        if (accentEnd < title.length()) {
            text.setSpan(
                    new ForegroundColorSpan(Color.WHITE),
                    accentEnd,
                    title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return text;
    }

    private String publicSectionTitle(String rawTitle) {
        return rawTitle == null ? "" : rawTitle.trim();
    }

    private void bindPlaybackState(Holder holder, NativeContentItem item) {
        holder.image.setAlpha(1f);
        holder.watchBadge.setVisibility(View.GONE);
        holder.progressTrack.setVisibility(View.GONE);
        holder.progressFill.setScaleX(0f);
        holder.card.setStrokeColor(ZeroChillUi.color(holder.card.getContext(), R.color.zc_edge));
        if (item == null || item.isMeme()) return;

        PlaybackHistoryStore.Item history = playbackByUrl.get(item.url);
        if (history == null) return;

        if (history.complete) {
            holder.image.setAlpha(0.74f);
            holder.watchBadge.setText("✓ Watched");
            holder.watchBadge.setBackground(ZeroChillUi.rounded(
                    holder.card.getContext(),
                    ZeroChillUi.color(holder.card.getContext(), R.color.zc_surface_glass_strong),
                    ZeroChillUi.color(holder.card.getContext(), R.color.zc_divider),
                    R.dimen.zc_radius_pill
            ));
            holder.watchBadge.setVisibility(View.VISIBLE);
            holder.card.setStrokeColor(ZeroChillUi.color(holder.card.getContext(), R.color.zc_divider));
            return;
        }

        if (history.positionMs < MIN_FEED_PROGRESS_MS) return;
        holder.watchBadge.setText("Continue  " + formatTime(history.positionMs));
        holder.watchBadge.setBackground(ZeroChillUi.rounded(
                holder.card.getContext(),
                ZeroChillUi.color(holder.card.getContext(), R.color.zc_cyan_container),
                ZeroChillUi.color(holder.card.getContext(), R.color.zc_cyan_dim),
                R.dimen.zc_radius_pill
        ));
        holder.watchBadge.setVisibility(View.VISIBLE);
        holder.card.setStrokeColor(ZeroChillUi.color(holder.card.getContext(), R.color.zc_cyan_dim));

        if (history.durationMs > 0L) {
            float fraction = Math.max(0f, Math.min(1f, history.positionMs / (float) history.durationMs));
            holder.progressFill.setScaleX(fraction);
            holder.progressTrack.setVisibility(View.VISIBLE);
        }
    }

    private String formatTime(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0L) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private void loadThumbnail(Holder holder, NativeContentItem item) {
        if (holder.image == null || item == null || item.isSection()) return;
        String resolved = resolvedThumbnails.get(item.url);
        boolean usingDirect = (resolved == null || resolved.isEmpty()) &&
                item.imageUrl != null && !item.imageUrl.isEmpty() &&
                !failedDirectThumbnails.contains(item.url);
        String imageUrl = usingDirect ? item.imageUrl : resolved;

        if (imageUrl == null || imageUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(
                    ZeroChillUi.color(holder.image.getContext(), R.color.zc_surface_glass_strong)));
            return;
        }

        Object source = imageUrl.startsWith("file://") ? imageUrl : withSiteHeaders(imageUrl, item.url);
        com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(holder.image)
                .load(source)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .placeholder(new ColorDrawable(ZeroChillUi.color(
                        holder.image.getContext(), R.color.zc_surface_glass_strong)))
                .error(new ColorDrawable(ZeroChillUi.color(
                        holder.image.getContext(), R.color.zc_surface_glass_strong)));
        if (item.isMeme()) request.fitCenter(); else request.centerCrop();
        final String attemptedUrl = imageUrl;
        request.listener(new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(
                    GlideException error,
                    Object model,
                    Target<Drawable> target,
                    boolean firstResource
            ) {
                if (sameUrl(attemptedUrl, item.imageUrl)) failedDirectThumbnails.add(item.url);
                String alternate = resolvedThumbnails.get(item.url);
                if (sameUrl(alternate, attemptedUrl)) resolvedThumbnails.remove(item.url);
                holder.image.post(() -> {
                    requestThumbnail(item, attemptedUrl);
                    notifyThumbnailChanged(item.url);
                });
                return false;
            }

            @Override
            public boolean onResourceReady(
                    Drawable resource,
                    Object model,
                    Target<Drawable> target,
                    DataSource dataSource,
                    boolean firstResource
            ) {
                return false;
            }
        });
        request.into(holder.image);
    }

    private void notifyThumbnailChanged(String pageUrl) {
        for (int i = 0; i < items.size(); i++) {
            if (pageUrl.equals(items.get(i).url)) {
                notifyItemChanged(i, "thumbnail");
                return;
            }
        }
    }

    private boolean sameUrl(String first, String second) {
        if (first == null || second == null) return false;
        return first.trim().replace("&amp;", "&")
                .equals(second.trim().replace("&amp;", "&"));
    }

    private void preloadDirectThumbnail(NativeContentItem item) {
        if (item == null || item.isSection() || item.imageUrl == null || item.imageUrl.isEmpty()) return;
        Object source = item.imageUrl.startsWith("file://")
                ? item.imageUrl
                : withSiteHeaders(item.imageUrl, item.url);
        Glide.with(context)
                .load(source)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .preload(480, 270);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        if (holder.image != null) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setAlpha(1f);
        }
        if (holder.watchBadge != null) holder.watchBadge.setVisibility(View.GONE);
        if (holder.overlayComments != null) holder.overlayComments.setVisibility(View.GONE);
        if (holder.progressTrack != null) holder.progressTrack.setVisibility(View.GONE);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private GlideUrl withSiteHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null || pageUrl.isEmpty() ? SITE : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");

        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(pageUrl == null ? SITE : pageUrl);
            }
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(SITE);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }

        return new GlideUrl(imageUrl, headers.build());
    }

    private String displayTitle(NativeContentItem item) {
        String title = item == null || item.title == null ? "" : item.title.trim();
        if (!homePresentation || viewMode != VIEW_LIST || title.isEmpty()) return title;
        return titleCaseIfAllCaps(title);
    }

    private static String titleCaseIfAllCaps(String raw) {
        boolean hasLetter = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!Character.isLetter(c)) continue;
            hasLetter = true;
            if (Character.isLowerCase(c)) return raw;
        }
        if (!hasLetter) return raw;

        String[] words = raw.toLowerCase(Locale.US).split("\\s+");
        StringBuilder result = new StringBuilder(raw.length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(' ');
            String token = words[i];
            String core = titleWordCore(token);
            boolean minor = i > 0 && i + 1 < words.length && isMinorTitleWord(core);
            result.append(minor ? token : capitalizeTitleToken(token));
        }
        return result.toString();
    }

    private static String titleWordCore(String token) {
        int start = 0;
        while (start < token.length() && !Character.isLetterOrDigit(token.charAt(start))) start++;
        int end = token.length();
        while (end > start && !Character.isLetterOrDigit(token.charAt(end - 1))) end--;
        return token.substring(start, end);
    }

    private static String capitalizeTitleToken(String token) {
        char[] chars = token.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!Character.isLetter(chars[i])) continue;
            chars[i] = Character.toUpperCase(chars[i]);
            break;
        }
        return new String(chars);
    }

    private static boolean isMinorTitleWord(String word) {
        switch (word) {
            case "a":
            case "an":
            case "and":
            case "as":
            case "at":
            case "but":
            case "by":
            case "for":
            case "from":
            case "in":
            case "of":
            case "on":
            case "or":
            case "the":
            case "to":
            case "with":
                return true;
            default:
                return false;
        }
    }

    private String buildInfo(NativeContentItem item) {
        ArrayList<String> parts = new ArrayList<>();
        boolean reddit = RedditSourceRepository.isRedditUrl(item.url);
        if (viewMode == VIEW_CARDS) parts.add(EfuktRepository.isEfuktUrl(item.url) ? "EFukt"
                : FapelloRepository.isFapelloUrl(item.url) ? "OnlyFap"
                : reddit ? RedditSourceRepository.SOURCE_NAME : "CrazyShit");
        if (!item.isMeme() && item.views != null && !item.views.isEmpty()) {
            String views = viewMode == VIEW_CARDS ? item.views : compactCount(item.views);
            parts.add(views + " views");
        }
        if (item.uploader != null && !item.uploader.isEmpty() &&
                (viewMode == VIEW_CARDS || reddit || "EFukt".equalsIgnoreCase(item.uploader))) {
            parts.add(item.uploader);
        }
        if (item.isMeme() && parts.isEmpty()) parts.add("Image");
        return TextUtils.join("  •  ", parts);
    }

    private String compactCount(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        if (text.isEmpty()) return text;
        String upper = text.toUpperCase(Locale.US);
        if (upper.endsWith("K") || upper.endsWith("M") || upper.endsWith("B")) return upper;
        String numeric = text.replaceAll("[^0-9.]", "");
        if (numeric.isEmpty()) return text;
        try {
            double value = Double.parseDouble(numeric);
            if (value >= 1_000_000_000d) return compactDecimal(value / 1_000_000_000d) + "B";
            if (value >= 1_000_000d) return compactDecimal(value / 1_000_000d) + "M";
            if (value >= 1_000d) return compactDecimal(value / 1_000d) + "K";
            return String.format(Locale.US, "%.0f", value);
        } catch (Exception ignored) {
            return text;
        }
    }

    private String compactDecimal(double value) {
        if (value >= 100d) return String.format(Locale.US, "%.0f", value);
        if (value >= 10d) return String.format(Locale.US, "%.1f", value).replace(".0", "");
        return String.format(Locale.US, "%.1f", value).replace(".0", "");
    }

    private static boolean isLandscape(View view) {
        return view.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static int responsiveHomeListMediaWidthDp(View parent, int heightDp) {
        Configuration config = parent.getResources().getConfiguration();
        int cardContentWidthDp = Math.max(240, config.screenWidthDp - 24);
        int targetByShare = Math.round(cardContentWidthDp * 0.54f);
        int targetByAspect = Math.round(heightDp * 16f / 9f);
        return Math.max(heightDp, Math.min(targetByShare, targetByAspect));
    }

    private static int responsiveGridMediaHeightDp(View parent) {
        Configuration config = parent.getResources().getConfiguration();
        int widthDp = Math.max(320, config.screenWidthDp);
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int columns = landscape ? (widthDp >= 900 ? 3 : 2) : 2;
        int rail = landscape ? 68 : 0;
        float cardWidth = Math.max(140f, (widthDp - rail - columns * 12f) / columns);
        return Math.max(118, Math.min(160, Math.round(cardWidth * 9f / 16f)));
    }

    private static int responsivePosterHeightDp(View parent) {
        Configuration config = parent.getResources().getConfiguration();
        int widthDp = Math.max(320, config.screenWidthDp);
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int columns = landscape ? (widthDp >= 900 ? 3 : 2) : 2;
        int rail = landscape ? 68 : 0;
        float cardWidth = Math.max(150f, (widthDp - rail - columns * 12f) / columns);
        return Math.max(196, Math.min(240, Math.round(cardWidth * 1.06f)));
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static final class MediaViews {
        final FrameLayout frame;
        final ImageView image;
        final TextView play;
        final TextView watchBadge;
        final TextView overlayComments;
        final View progressTrack;
        final View progressFill;

        MediaViews(
                FrameLayout frame,
                ImageView image,
                TextView play,
                TextView watchBadge,
                TextView overlayComments,
                View progressTrack,
                View progressFill
        ) {
            this.frame = frame;
            this.image = image;
            this.play = play;
            this.watchBadge = watchBadge;
            this.overlayComments = overlayComments;
            this.progressTrack = progressTrack;
            this.progressFill = progressFill;
        }
    }

    private static final class CopyViews {
        final TextView title;
        final TextView info;
        final TextView comments;
        final TextView description;

        CopyViews(TextView title, TextView info, TextView comments, TextView description) {
            this.title = title;
            this.info = info;
            this.comments = comments;
            this.description = description;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView play;
        final TextView watchBadge;
        final TextView overlayComments;
        final View progressTrack;
        final View progressFill;
        final TextView title;
        final TextView info;
        final TextView comments;
        final TextView description;
        final TextView sectionTitle;

        Holder(MaterialCardView card, MediaViews media, CopyViews copy) {
            super(card);
            this.card = card;
            this.image = media.image;
            this.play = media.play;
            this.watchBadge = media.watchBadge;
            this.overlayComments = media.overlayComments;
            this.progressTrack = media.progressTrack;
            this.progressFill = media.progressFill;
            this.title = copy.title;
            this.info = copy.info;
            this.comments = copy.comments;
            this.description = copy.description;
            this.sectionTitle = null;
        }

        Holder(MaterialCardView card, TextView sectionTitle) {
            super(card);
            this.card = card;
            this.image = null;
            this.play = null;
            this.watchBadge = null;
            this.overlayComments = null;
            this.progressTrack = null;
            this.progressFill = null;
            this.title = null;
            this.info = null;
            this.comments = null;
            this.description = null;
            this.sectionTitle = sectionTitle;
        }
    }
}
