package com.webapp.crazyshit;

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
import java.util.List;

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

    GlobalSearchAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    void replace(List<Entry> next) {
        entries.clear();
        if (next != null) entries.addAll(next);
        notifyDataSetChanged();
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
        if (viewType == TYPE_SECTION) {
            TextView title = new TextView(parent.getContext());
            title.setTextColor(Color.rgb(245, 245, 248));
            title.setTextSize(17f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setPadding(dp(parent, 16), dp(parent, 15), dp(parent, 16), dp(parent, 6));
            title.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(parent, 52)));
            return new SectionHolder(title);
        }

        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(Color.rgb(25, 25, 29));
        card.setRadius(dp(parent, 14));
        card.setStrokeWidth(dp(parent, 1));
        card.setStrokeColor(Color.rgb(51, 51, 58));
        RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(-1, dp(parent, 108));
        cardParams.setMargins(dp(parent, 10), dp(parent, 5), dp(parent, 10), dp(parent, 5));
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(31, 31, 36));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(parent, 150), -1);
        row.addView(image, imageParams);

        LinearLayout text = new LinearLayout(parent.getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(parent, 13), dp(parent, 9), dp(parent, 13), dp(parent, 9));
        row.addView(text, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(15.5f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = new TextView(parent.getContext());
        meta.setTextColor(Color.rgb(174, 174, 184));
        meta.setTextSize(12.5f);
        meta.setMaxLines(2);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(parent, 5);
        text.addView(meta, metaParams);

        return new ResultHolder(card, image, title, meta);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Entry entry = entries.get(position);
        if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).title.setText(entry.sectionTitle);
            return;
        }

        ResultHolder result = (ResultHolder) holder;
        NativeContentItem item = entry.item;
        if (item == null) return;
        result.boundUrl = item.url == null ? "" : item.url;
        result.title.setText(item.title);
        result.meta.setText(metaText(item, entry.source));
        result.card.setContentDescription(item.title);
        result.card.setOnClickListener(v -> listener.onOpen(item));
        loadImage(result, item);
    }

    private String metaText(NativeContentItem item, int source) {
        if (source == SOURCE_LIBRARY) return "Library";
        if (item.isSeries()) return "Series";
        if (item.isCategory()) return "Category";
        StringBuilder meta = new StringBuilder("Video");
        if (item.views != null && !item.views.trim().isEmpty()) meta.append("  •  ").append(item.views.trim()).append(" views");
        if (item.uploader != null && !item.uploader.trim().isEmpty()) meta.append("  •  ").append(item.uploader.trim());
        return meta.toString();
    }

    private void loadImage(ResultHolder holder, NativeContentItem item) {
        Glide.with(holder.image).clear(holder.image);
        holder.image.setImageDrawable(new ColorDrawable(Color.rgb(31, 31, 36)));

        byte[] embedded = null;
        if (item.isSeries() || item.isCategory()) {
            embedded = EmbeddedBrowseArtwork.get(holder.image.getContext(), item.url);
        }
        if (embedded != null && embedded.length > 512) {
            Glide.with(holder.image)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .dontAnimate()
                    .centerCrop()
                    .placeholder(new ColorDrawable(Color.rgb(31, 31, 36)))
                    .error(new ColorDrawable(Color.rgb(31, 31, 36)))
                    .into(holder.image);
            return;
        }

        if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
            loadRemote(holder.image, item, item.imageUrl.trim());
            return;
        }

        // Search and Library media cards frequently do not expose their thumbnail on the search
        // listing itself. Resolve only visible cards from the individual media page, then cache it.
        if (!item.isSeries() && !item.isCategory() && item.url != null && !item.url.trim().isEmpty()) {
            final String requestedPage = item.url;
            SearchThumbnailResolver.resolve(holder.image.getContext(), requestedPage, (pageUrl, thumbnailUrl) -> {
                if (!requestedPage.equals(holder.boundUrl)) return;
                if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) return;
                loadRemote(holder.image, item, thumbnailUrl.trim());
            });
        }
    }

    private void loadRemote(ImageView image, NativeContentItem item, String url) {
        Object source = url;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            LazyHeaders.Builder headers = new LazyHeaders.Builder()
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", item.url == null || item.url.isEmpty() ? SITE : item.url)
                    .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            try {
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
            } catch (Exception ignored) {
            }
            source = new GlideUrl(url, headers.build());
        }

        Glide.with(image)
                .load(source)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .centerCrop()
                .placeholder(new ColorDrawable(Color.rgb(31, 31, 36)))
                .error(new ColorDrawable(Color.rgb(31, 31, 36)))
                .into(image);
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
