package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
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

    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

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
    private int resolverCursor;
    private int viewMode = VIEW_LARGE;

    public NativeFeedAdapter(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(this.context, this::setResolvedThumbnail),
                new RenderedThumbnailResolver(this.context, this::setResolvedThumbnail)
        };
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

    public void replace(List<NativeContentItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
        preloadRange(0, Math.min(12, items.size()));
    }

    public void append(List<NativeContentItem> next) {
        if (next == null || next.isEmpty()) return;
        int start = items.size();
        for (NativeContentItem item : next) {
            boolean duplicate = false;
            for (NativeContentItem old : items) {
                if (old.url.equals(item.url)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) items.add(item);
        }
        int added = items.size() - start;
        if (added > 0) {
            notifyItemRangeInserted(start, added);
            preloadRange(start, Math.min(items.size(), start + 10));
        }
    }

    public void preloadVisible(int first, int last) {
        int from = Math.max(0, first);
        int to = Math.min(items.size(), Math.max(from, last + 5));
        preloadRange(from, to);
    }

    public int size() {
        return items.size();
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

    private void preloadRange(int start, int end) {
        for (int i = start; i < end; i++) requestThumbnail(items.get(i));
    }

    private void requestThumbnail(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
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
        return viewMode;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_COMPACT) return createCompactHolder(parent);
        if (viewType == VIEW_GRID) return createGridHolder(parent);
        return createLargeHolder(parent);
    }

    private Holder createLargeHolder(ViewGroup parent) {
        MaterialCardView card = baseCard(parent, 12, 7, 20);
        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -2));

        MediaViews media = addMedia(parent, column, 218, -1);
        CopyViews copy = addCopy(parent, column, 17, 13, 15, 15);
        return new Holder(card, media.image, media.play, copy.title, copy.info, copy.comments);
    }

    private Holder createCompactHolder(ViewGroup parent) {
        MaterialCardView card = baseCard(parent, 12, 5, 16);
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new MaterialCardView.LayoutParams(-1, -2));

        MediaViews media = addMedia(parent, row, 104, 148);
        CopyViews copy = addCopy(parent, row, 15, 12, 13, 11);
        return new Holder(card, media.image, media.play, copy.title, copy.info, copy.comments);
    }

    private Holder createGridHolder(ViewGroup parent) {
        MaterialCardView card = baseCard(parent, 6, 6, 15);
        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -2));

        MediaViews media = addMedia(parent, column, 128, -1);
        CopyViews copy = addCopy(parent, column, 14, 11, 10, 11);
        return new Holder(card, media.image, media.play, copy.title, copy.info, copy.comments);
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
        return new MediaViews(image, play);
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
        if (!payloads.isEmpty() && payloads.contains("thumbnail")) {
            loadThumbnail(holder, items.get(position));
            return;
        }
        bind(holder, position);
    }

    private void bind(Holder holder, int position) {
        NativeContentItem item = items.get(position);
        boolean meme = item.isMeme();

        holder.title.setText(item.title);
        holder.info.setText(buildInfo(item));
        holder.play.setVisibility(meme ? View.GONE : View.VISIBLE);
        holder.image.setScaleType(meme ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);

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

    private void loadThumbnail(Holder holder, NativeContentItem item) {
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
        Glide.with(holder.image).clear(holder.image);
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
        MediaViews(ImageView image, TextView play) {
            this.image = image;
            this.play = play;
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
        final TextView title;
        final TextView info;
        final TextView comments;

        Holder(MaterialCardView card, ImageView image, TextView play, TextView title, TextView info, TextView comments) {
            super(card);
            this.card = card;
            this.image = image;
            this.play = play;
            this.title = title;
            this.info = info;
            this.comments = comments;
        }
    }
}
