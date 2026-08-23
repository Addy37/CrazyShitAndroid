package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NativeFeedAdapter extends RecyclerView.Adapter<NativeFeedAdapter.Holder> {
    public static final int VIEW_LARGE = 0;
    public static final int VIEW_COMPACT = 1;
    public static final int VIEW_GRID = 2;
    private static final int TYPE_SECTION = 100;

    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final long MIN_FEED_PROGRESS_MS = 5_000L;
    private static final int SECTION_ACCENT = Color.rgb(244, 183, 28);
    private static final int APP_BG = Color.rgb(13, 13, 15);

    public interface Listener {
        void onOpen(NativeContentItem item);
        void onLongPress(NativeContentItem item, View anchor);
        void onComments(NativeContentItem item);
    }

    private final Context context;
    private final List<NativeContentItem> items = new ArrayList<>();
    private final Listener listener;
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final Set<String> requestedThumbnails = new HashSet<>();
    private final RenderedThumbnailResolver[] thumbnailResolvers;
    private final Map<String, PlaybackHistoryStore.Item> playbackByUrl = new HashMap<>();
    private final SharedPreferences playbackPrefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener playbackListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int resolverCursor;
    private int viewMode = VIEW_LARGE;

    public NativeFeedAdapter(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(this.context, this::setResolvedThumbnail),
                new RenderedThumbnailResolver(this.context, this::setResolvedThumbnail)
        };
        playbackPrefs = this.context.getSharedPreferences("playback_history", Context.MODE_PRIVATE);
        playbackListener = (prefs, key) -> {
            if (!"items".equals(key)) return;
            mainHandler.post(this::refreshPlaybackState);
        };
        playbackPrefs.registerOnSharedPreferenceChangeListener(playbackListener);
        reloadPlaybackStates();
        setHasStableIds(true);
    }

    public void setViewMode(int mode) {
        int next = mode;
        if (next < VIEW_LARGE || next > VIEW_GRID) next = VIEW_LARGE;
        if (viewMode == next) return;
        viewMode = next;
        notifyDataSetChanged();
    }

    public int getViewMode() {
        return viewMode;
    }

    public boolean isSectionAt(int position) {
        return position >= 0 && position < items.size() && items.get(position).isSection();
    }

    public void replace(List<NativeContentItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
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

            boolean duplicate = false;
            for (NativeContentItem old : items) {
                if (old.url.equals(item.url)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;

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
        reloadPlaybackStates();
        notifyDataSetChanged();
    }

    public void setResolvedThumbnail(String pageUrl, String thumbnailUrl) {
        if (pageUrl == null || pageUrl.isEmpty() || thumbnailUrl == null || thumbnailUrl.isEmpty()) return;
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
        for (int i = start; i < end; i++) requestThumbnail(items.get(i));
    }

    private void requestThumbnail(NativeContentItem item) {
        if (item == null || item.isSection() || item.url == null || item.url.isEmpty()) return;
        if (item.isMeme() && item.imageUrl != null && !item.imageUrl.isEmpty()) return;
        if (resolvedThumbnails.containsKey(item.url)) return;
        if (!requestedThumbnails.add(item.url)) return;
        RenderedThumbnailResolver resolver = thumbnailResolvers[resolverCursor++ % thumbnailResolvers.length];
        resolver.request(item.url);
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
        if (viewType == VIEW_COMPACT) return createCompactHolder(parent);
        if (viewType == VIEW_GRID) return createGridHolder(parent);
        return createLargeHolder(parent);
    }

    private Holder createSectionHolder(ViewGroup parent) {
        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(APP_BG);
        card.setCardElevation(0f);
        card.setRadius(0f);
        card.setStrokeWidth(0);
        card.setClickable(false);
        card.setLongClickable(false);

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(card, 52));
        params.setMargins(dp(card, 12), dp(card, 10), dp(card, 12), dp(card, 2));
        card.setLayoutParams(params);

        TextView header = new TextView(parent.getContext());
        header.setTextSize(17f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setSingleLine(true);
        header.setEllipsize(TextUtils.TruncateAt.END);
        header.setPadding(dp(card, 4), 0, dp(card, 4), 0);
        card.addView(header, new MaterialCardView.LayoutParams(-1, -1));
        return new Holder(card, header);
    }

    private Holder createLargeHolder(ViewGroup parent) {
        MaterialCardView card = baseCard(parent, 12, 7, 20);
        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -2));

        MediaViews media = addMedia(parent, column, 218, -1);
        CopyViews copy = addCopy(parent, column, 17, 13, 15, 15);
        return new Holder(card, media, copy);
    }

    private Holder createCompactHolder(ViewGroup parent) {
        MaterialCardView card = baseCard(parent, 12, 5, 16);
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new MaterialCardView.LayoutParams(-1, -2));

        MediaViews media = addMedia(parent, row, 104, 148);
        CopyViews copy = addCopy(parent, row, 15, 12, 13, 11);
        return new Holder(card, media, copy);
    }

    private Holder createGridHolder(ViewGroup parent) {
        MaterialCardView card = baseCard(parent, 6, 6, 15);
        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -2));

        MediaViews media = addMedia(parent, column, 128, -1);
        CopyViews copy = addCopy(parent, column, 14, 11, 10, 11);
        return new Holder(card, media, copy);
    }

    private MaterialCardView baseCard(ViewGroup parent, int horizontalMargin, int verticalMargin, int radius) {
        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setRadius(dp(parent, radius));
        card.setCardElevation(dp(parent, 1));
        card.setStrokeColor(Color.rgb(50, 50, 57));
        card.setStrokeWidth(dp(parent, 1));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
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
        host.addView(mediaFrame, mediaParams);

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(11, 11, 13));
        mediaFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView play = new TextView(parent.getContext());
        play.setText("▶");
        play.setTextColor(Color.WHITE);
        play.setTextSize(viewMode == VIEW_GRID ? 20 : 25);
        play.setGravity(Gravity.CENTER);
        play.setBackground(new ColorDrawable(Color.argb(135, 0, 0, 0)));
        int size = viewMode == VIEW_GRID ? 42 : 50;
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(parent, size), dp(parent, size));
        playParams.gravity = Gravity.CENTER;
        mediaFrame.addView(play, playParams);

        TextView watchBadge = new TextView(parent.getContext());
        watchBadge.setTextColor(Color.WHITE);
        watchBadge.setTextSize(viewMode == VIEW_GRID ? 9.5f : 10.5f);
        watchBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        watchBadge.setGravity(Gravity.CENTER);
        watchBadge.setPadding(dp(parent, 8), dp(parent, 4), dp(parent, 8), dp(parent, 4));
        watchBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2);
        badgeParams.gravity = Gravity.TOP | Gravity.START;
        badgeParams.setMargins(dp(parent, 7), dp(parent, 7), dp(parent, 7), 0);
        mediaFrame.addView(watchBadge, badgeParams);

        FrameLayout progressTrack = new FrameLayout(parent.getContext());
        progressTrack.setBackgroundColor(Color.argb(175, 17, 17, 20));
        progressTrack.setVisibility(View.GONE);
        FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(-1, dp(parent, 3));
        trackParams.gravity = Gravity.BOTTOM;
        mediaFrame.addView(progressTrack, trackParams);

        View progressFill = new View(parent.getContext());
        progressFill.setBackgroundColor(Color.rgb(255, 90, 31));
        progressFill.setPivotX(0f);
        progressFill.setScaleX(0f);
        progressTrack.addView(progressFill, new FrameLayout.LayoutParams(-1, -1));

        return new MediaViews(image, play, watchBadge, progressTrack, progressFill);
    }

    private CopyViews addCopy(
            ViewGroup parent,
            LinearLayout host,
            int titleSize,
            int infoSize,
            int horizontalPadding,
            int verticalPadding
    ) {
        LinearLayout copy = new LinearLayout(parent.getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(
                dp(parent, horizontalPadding),
                dp(parent, verticalPadding),
                dp(parent, horizontalPadding),
                dp(parent, verticalPadding)
        );
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                viewMode == VIEW_COMPACT ? 0 : -1,
                -2,
                viewMode == VIEW_COMPACT ? 1f : 0f
        );
        host.addView(copy, copyParams);

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(titleSize);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout metaRow = new LinearLayout(parent.getContext());
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setPadding(0, dp(parent, 6), 0, 0);
        copy.addView(metaRow, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(parent.getContext());
        info.setTextColor(Color.rgb(170, 170, 180));
        info.setTextSize(infoSize);
        info.setMaxLines(1);
        info.setEllipsize(TextUtils.TruncateAt.END);
        metaRow.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView comments = new TextView(parent.getContext());
        comments.setTextColor(Color.rgb(255, 112, 60));
        comments.setTextSize(infoSize);
        comments.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        comments.setPadding(dp(parent, 8), dp(parent, 4), 0, dp(parent, 4));
        metaRow.addView(comments, new LinearLayout.LayoutParams(-2, -2));

        return new CopyViews(title, info, comments);
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
        bind(holder, position);
    }

    private void bind(Holder holder, int position) {
        NativeContentItem item = items.get(position);
        if (item.isSection()) {
            bindSection(holder, item);
            return;
        }

        boolean meme = item.isMeme();
        holder.title.setText(item.title);
        holder.info.setText(buildInfo(item));
        holder.play.setVisibility(meme ? View.GONE : View.VISIBLE);
        holder.image.setScaleType(meme ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        bindPlaybackState(holder, item);

        if (!meme && item.comments != null && !item.comments.isEmpty()) {
            holder.comments.setVisibility(View.VISIBLE);
            if (viewMode == VIEW_COMPACT || viewMode == VIEW_GRID) {
                holder.comments.setText("💬 " + compactCount(item.comments));
            } else {
                holder.comments.setText(item.comments + " comments");
            }
            holder.comments.setOnClickListener(v -> listener.onComments(item));
        } else {
            holder.comments.setVisibility(View.GONE);
            holder.comments.setOnClickListener(null);
        }

        loadThumbnail(holder, item);
        requestThumbnail(item);

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
        String title = rawTitle == null ? "" : rawTitle.trim().toUpperCase(Locale.US);
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

    private void bindPlaybackState(Holder holder, NativeContentItem item) {
        holder.image.setAlpha(1f);
        holder.watchBadge.setVisibility(View.GONE);
        holder.progressTrack.setVisibility(View.GONE);
        holder.progressFill.setScaleX(0f);
        holder.card.setStrokeColor(Color.rgb(50, 50, 57));
        if (item == null || item.isMeme()) return;

        PlaybackHistoryStore.Item history = playbackByUrl.get(item.url);
        if (history == null) return;

        if (history.complete) {
            holder.image.setAlpha(0.74f);
            holder.watchBadge.setText("✓ Watched");
            holder.watchBadge.setBackground(rounded(Color.argb(220, 23, 23, 27), dp(holder.watchBadge, 12)));
            holder.watchBadge.setVisibility(View.VISIBLE);
            holder.card.setStrokeColor(Color.rgb(79, 61, 55));
            return;
        }

        if (history.positionMs < MIN_FEED_PROGRESS_MS) return;
        holder.watchBadge.setText("Continue  " + formatTime(history.positionMs));
        holder.watchBadge.setBackground(rounded(Color.argb(230, 133, 47, 17), dp(holder.watchBadge, 12)));
        holder.watchBadge.setVisibility(View.VISIBLE);

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

    private GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private void loadThumbnail(Holder holder, NativeContentItem item) {
        if (holder.image == null || item == null || item.isSection()) return;
        String imageUrl = resolvedThumbnails.get(item.url);
        if (imageUrl == null || imageUrl.isEmpty()) imageUrl = item.imageUrl;

        if (imageUrl == null || imageUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 23)));
            return;
        }

        Object source = imageUrl.startsWith("file://") ? imageUrl : withSiteHeaders(imageUrl, item.url);
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request = Glide.with(holder.image)
                .load(source)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.rgb(20, 20, 23)))
                .error(new ColorDrawable(Color.rgb(20, 20, 23)));
        if (item.isMeme()) request.fitCenter(); else request.centerCrop();
        request.into(holder.image);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        if (holder.image != null) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setAlpha(1f);
        }
        if (holder.watchBadge != null) holder.watchBadge.setVisibility(View.GONE);
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

    private String buildInfo(NativeContentItem item) {
        ArrayList<String> parts = new ArrayList<>();
        if (!item.isMeme() && item.views != null && !item.views.isEmpty()) {
            String views = (viewMode == VIEW_COMPACT || viewMode == VIEW_GRID)
                    ? compactCount(item.views)
                    : item.views;
            parts.add(views + " views");
        }
        if (viewMode == VIEW_LARGE && item.uploader != null && !item.uploader.isEmpty()) {
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

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static final class MediaViews {
        final ImageView image;
        final TextView play;
        final TextView watchBadge;
        final View progressTrack;
        final View progressFill;

        MediaViews(ImageView image, TextView play, TextView watchBadge, View progressTrack, View progressFill) {
            this.image = image;
            this.play = play;
            this.watchBadge = watchBadge;
            this.progressTrack = progressTrack;
            this.progressFill = progressFill;
        }
    }

    private static final class CopyViews {
        final TextView title;
        final TextView info;
        final TextView comments;

        CopyViews(TextView title, TextView info, TextView comments) {
            this.title = title;
            this.info = info;
            this.comments = comments;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView play;
        final TextView watchBadge;
        final View progressTrack;
        final View progressFill;
        final TextView title;
        final TextView info;
        final TextView comments;
        final TextView sectionTitle;

        Holder(MaterialCardView card, MediaViews media, CopyViews copy) {
            super(card);
            this.card = card;
            this.image = media.image;
            this.play = media.play;
            this.watchBadge = media.watchBadge;
            this.progressTrack = media.progressTrack;
            this.progressFill = media.progressFill;
            this.title = copy.title;
            this.info = copy.info;
            this.comments = copy.comments;
            this.sectionTitle = null;
        }

        Holder(MaterialCardView card, TextView sectionTitle) {
            super(card);
            this.card = card;
            this.image = null;
            this.play = null;
            this.watchBadge = null;
            this.progressTrack = null;
            this.progressFill = null;
            this.title = null;
            this.info = null;
            this.comments = null;
            this.sectionTitle = sectionTitle;
        }
    }
}
