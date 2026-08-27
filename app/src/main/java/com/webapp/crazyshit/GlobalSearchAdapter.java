package com.webapp.crazyshit;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
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
import java.util.Map;
import java.util.Set;

/** Mixed native search results for videos, Series, Categories and the local Library. */
final class GlobalSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface Listener {
        void onOpen(NativeContentItem item);
    }

    static final int SOURCE_REMOTE = 0;
    static final int SOURCE_LIBRARY = 1;

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_RESULT = 1;
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    static final class Entry {
        final boolean section;
        final String sectionTitle;
        final NativeContentItem item;
        final int source;

        private Entry(boolean section, String sectionTitle, NativeContentItem item, int source) {
            this.section = section;
            this.sectionTitle = sectionTitle == null ? "" : sectionTitle;
            this.item = item;
            this.source = source;
        }

        static Entry section(String title) {
            return new Entry(true, title, null, SOURCE_REMOTE);
        }

        static Entry item(NativeContentItem item, int source) {
            return new Entry(false, "", item, source);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Listener listener;
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final Set<String> requestedThumbnails = new HashSet<>();
    private RenderedThumbnailResolver[] thumbnailResolvers;
    private Context appContext;
    private int resolverCursor;

    GlobalSearchAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    void replace(List<Entry> next) {
        entries.clear();
        if (next != null) entries.addAll(next);
        notifyDataSetChanged();
        preloadDirectThumbnails();
    }

    void close() {
        if (thumbnailResolvers != null) {
            for (RenderedThumbnailResolver resolver : thumbnailResolvers) {
                if (resolver != null) resolver.close();
            }
        }
        thumbnailResolvers = null;
        appContext = null;
        requestedThumbnails.clear();
    }

    @Override
    public long getItemId(int position) {
        Entry entry = entries.get(position);
        if (entry.section) return ("section:" + entry.sectionTitle).hashCode();
        if (entry.item == null) return position;
        return (entry.item.kind + ":" + entry.item.url + ":" + entry.source).hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return entries.get(position).section ? TYPE_SECTION : TYPE_RESULT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean landscape = parent.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        if (viewType == TYPE_SECTION) {
            TextView title = new TextView(parent.getContext());
            title.setTextColor(Color.rgb(245, 245, 248));
            title.setTextSize(landscape ? 16f : 17f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setPadding(dp(parent, 16), dp(parent, landscape ? 10 : 15), dp(parent, 16), dp(parent, 6));
            title.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(parent, landscape ? 46 : 52)));
            return new SectionHolder(title);
        }

        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(Color.rgb(25, 25, 29));
        card.setRadius(dp(parent, 14));
        card.setStrokeWidth(dp(parent, 1));
        card.setStrokeColor(Color.rgb(51, 51, 58));
        RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(-1, dp(parent, landscape ? 112 : 124));
        cardParams.setMargins(dp(parent, 10), dp(parent, 5), dp(parent, 10), dp(parent, 5));
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(31, 31, 36));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(parent, landscape ? 136 : 150), -1);
        row.addView(image, imageParams);

        LinearLayout text = new LinearLayout(parent.getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(parent, 13), dp(parent, landscape ? 7 : 9), dp(parent, 13), dp(parent, landscape ? 7 : 9));
        row.addView(text, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(landscape ? 15f : 15.5f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = new TextView(parent.getContext());
        meta.setTextColor(Color.rgb(174, 174, 184));
        meta.setTextSize(landscape ? 12f : 12.5f);
        meta.setMaxLines(3);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(parent, 5);
        text.addView(meta, metaParams);

        return new ResultHolder(card, image, title, meta);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        bind(holder, position);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        Entry entry = entries.get(position);
        if (entry.section || !(holder instanceof ResultHolder) || entry.item == null) {
            bind(holder, position);
            return;
        }
        if (!payloads.isEmpty() && payloads.contains("thumbnail")) {
            ResultHolder result = (ResultHolder) holder;
            result.boundUrl = entry.item.url == null ? "" : entry.item.url;
            loadImage(result.image, entry.item);
            return;
        }
        bind(holder, position);
    }

    private void bind(RecyclerView.ViewHolder holder, int position) {
        Entry entry = entries.get(position);
        if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).title.setText(entry.sectionTitle);
            return;
        }

        ResultHolder result = (ResultHolder) holder;
        NativeContentItem item = entry.item;
        if (item == null) return;
        ensureContext(result.image.getContext());
        result.boundUrl = item.url == null ? "" : item.url;
        result.title.setText(item.title);
        result.meta.setText(metaText(item, entry.source));
        result.card.setContentDescription(item.title);
        result.card.setOnClickListener(v -> listener.onOpen(item));
        loadImage(result.image, item);
        requestThumbnail(item);
    }

    private String metaText(NativeContentItem item, int source) {
        StringBuilder meta;
        if (source == SOURCE_LIBRARY) {
            meta = new StringBuilder("Library");
        } else if (item.isSeries()) {
            meta = new StringBuilder("Series");
        } else if (item.isCategory()) {
            meta = new StringBuilder("Category");
        } else {
            meta = new StringBuilder("Video");
        }
        if (item.views != null && !item.views.trim().isEmpty()) {
            meta.append("  •  ").append(item.views.trim()).append(" views");
        }
        if (item.uploader != null && !item.uploader.trim().isEmpty()) {
            meta.append("  •  ").append(item.uploader.trim());
        }
        if (item.description != null && !item.description.trim().isEmpty()) {
            meta.append('\n').append(item.description.trim());
        }
        return meta.toString();
    }

    private void ensureContext(Context context) {
        if (appContext != null || context == null) return;
        appContext = context.getApplicationContext();
        preloadDirectThumbnails();
    }

    /** Search only starts rendered-page fallback workers for results without direct artwork. */
    private void ensureResolvers() {
        if (thumbnailResolvers != null || appContext == null) return;
        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(appContext, this::setResolvedThumbnail),
                new RenderedThumbnailResolver(appContext, this::setResolvedThumbnail),
                new RenderedThumbnailResolver(appContext, this::setResolvedThumbnail)
        };
    }

    private void requestThumbnail(NativeContentItem item) {
        if (item == null || item.isSection() || item.isSeries() || item.isCategory()) return;
        if (item.url == null || item.url.isEmpty()) return;
        if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) return;
        if (resolvedThumbnails.containsKey(item.url)) return;
        if (!requestedThumbnails.add(item.url)) return;
        ensureResolvers();
        if (thumbnailResolvers == null || thumbnailResolvers.length == 0) {
            requestedThumbnails.remove(item.url);
            return;
        }
        RenderedThumbnailResolver resolver =
                thumbnailResolvers[resolverCursor++ % thumbnailResolvers.length];
        resolver.request(item.url);
    }

    private void setResolvedThumbnail(String pageUrl, String thumbnailUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) return;
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
            // A failed first pass should not poison this SearchActivity for the rest of its life.
            requestedThumbnails.remove(pageUrl);
            return;
        }
        resolvedThumbnails.put(pageUrl, thumbnailUrl);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.section || entry.item == null) continue;
            if (pageUrl.equals(entry.item.url)) {
                notifyItemChanged(i, "thumbnail");
            }
        }
    }

    private void loadImage(ImageView image, NativeContentItem item) {
        Glide.with(image).clear(image);
        image.setImageDrawable(new ColorDrawable(Color.rgb(31, 31, 36)));

        byte[] embedded = null;
        if (item.isSeries() || item.isCategory()) {
            embedded = EmbeddedBrowseArtwork.get(image.getContext(), item.url);
        }
        if (embedded != null && embedded.length > 512) {
            Glide.with(image)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .dontAnimate()
                    .centerCrop()
                    .placeholder(new ColorDrawable(Color.rgb(31, 31, 36)))
                    .error(new ColorDrawable(Color.rgb(31, 31, 36)))
                    .into(image);
            return;
        }

        String imageUrl = item.imageUrl;
        if (imageUrl == null || imageUrl.isEmpty()) imageUrl = resolvedThumbnails.get(item.url);
        if (imageUrl == null || imageUrl.isEmpty()) return;

        Object source = imageUrl.startsWith("file://")
                ? imageUrl
                : withSiteHeaders(imageUrl, item.url);
        Glide.with(image)
                .load(source)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .centerCrop()
                .placeholder(new ColorDrawable(Color.rgb(31, 31, 36)))
                .error(new ColorDrawable(Color.rgb(31, 31, 36)))
                .into(image);
    }

    private void preloadDirectThumbnails() {
        if (appContext == null) return;
        int loaded = 0;
        for (Entry entry : entries) {
            if (entry.section || entry.item == null) continue;
            NativeContentItem item = entry.item;
            if (item.imageUrl == null || item.imageUrl.trim().isEmpty()) continue;
            Object source = item.imageUrl.startsWith("file://")
                    ? item.imageUrl
                    : withSiteHeaders(item.imageUrl, item.url);
            Glide.with(appContext)
                    .load(source)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .preload(480, 270);
            if (++loaded >= 18) break;
        }
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

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ResultHolder) {
            ResultHolder result = (ResultHolder) holder;
            result.boundUrl = "";
            Glide.with(result.image).clear(result.image);
            result.card.setOnClickListener(null);
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title;

        SectionHolder(TextView title) {
            super(title);
            this.title = title;
        }
    }

    static final class ResultHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView title;
        final TextView meta;
        String boundUrl = "";

        ResultHolder(MaterialCardView card, ImageView image, TextView title, TextView meta) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
            this.meta = meta;
        }
    }
}
