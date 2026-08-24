package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visual browser cards used by Categories and Series. */
public final class NativeCategoryAdapter extends RecyclerView.Adapter<NativeCategoryAdapter.Holder> {
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    public interface Listener {
        void onOpen(NativeContentItem item);
    }

    private final Context context;
    private final List<NativeContentItem> items = new ArrayList<>();
    private final Listener listener;
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final ExecutorService collectionIo = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences fallbackCache;
    private final Set<String> fallbackPending = new HashSet<>();
    private final Map<String, List<String>> collectionsByMedia = new HashMap<>();
    private final RenderedThumbnailResolver[] mediaResolvers;
    private int resolverCursor;
    private boolean closed;

    public NativeCategoryAdapter(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.fallbackCache = this.context.getSharedPreferences(
                "browse_card_artwork_cache",
                Context.MODE_PRIVATE
        );
        this.mediaResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(this.context, this::onMediaArtworkResolved),
                new RenderedThumbnailResolver(this.context, this::onMediaArtworkResolved)
        };
        setHasStableIds(true);
    }

    public void replace(List<NativeContentItem> next) {
        items.clear();
        if (next != null) {
            for (NativeContentItem item : next) items.add(withCachedArtwork(item));
        }
        notifyDataSetChanged();
    }

    public boolean hasMissingArtwork() {
        for (NativeContentItem item : items) {
            if (item != null && !usableImage(item.imageUrl)) return true;
        }
        return false;
    }

    /** Fill only missing browse artwork so direct/static image URLs keep priority. */
    public void applyArtwork(Map<String, String> artwork) {
        if (artwork == null || artwork.isEmpty()) return;
        for (int i = 0; i < items.size(); i++) {
            NativeContentItem item = items.get(i);
            if (item == null || usableImage(item.imageUrl)) continue;
            String image = artwork.get(BrowseArtworkResolver.normalizeKey(item.url));
            if (!usableImage(image)) continue;
            items.set(i, copyWithImage(item, image.trim()));
            notifyItemChanged(i);
        }
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).url.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setRadius(dp(parent, 16));
        card.setStrokeWidth(dp(parent, 1));
        card.setStrokeColor(Color.rgb(52, 52, 59));
        card.setCardElevation(dp(parent, 1));

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(parent, 154));
        params.setMargins(dp(parent, 7), dp(parent, 7), dp(parent, 7), dp(parent, 7));
        card.setLayoutParams(params);

        FrameLayout frame = new FrameLayout(parent.getContext());
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(18, 18, 21));
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(parent.getContext());
        shade.setBackgroundColor(Color.argb(118, 0, 0, 0));
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(-1, dp(parent, 52));
        shadeParams.gravity = Gravity.BOTTOM;
        frame.addView(shade, shadeParams);

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(14.5f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(parent, 11), dp(parent, 6), dp(parent, 11), dp(parent, 7));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, dp(parent, 52));
        titleParams.gravity = Gravity.BOTTOM;
        frame.addView(title, titleParams);

        return new Holder(card, image, title);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        holder.title.setText(item.title);
        holder.card.setContentDescription(item.title);
        holder.card.setOnClickListener(v -> listener.onOpen(item));
        loadImage(holder, item);
        if (!usableImage(item.imageUrl)) requestFallbackArtwork(item);
    }

    private void loadImage(Holder holder, NativeContentItem item) {
        if (!usableImage(item.imageUrl)) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(31, 31, 35)));
            return;
        }

        String imageUrl = item.imageUrl.trim();
        Object source = imageUrl.startsWith("http://") || imageUrl.startsWith("https://")
                ? withSiteHeaders(imageUrl, item.url)
                : imageUrl;
        Glide.with(holder.image)
                .load(source)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .centerCrop()
                .placeholder(new ColorDrawable(Color.rgb(31, 31, 35)))
                .error(new ColorDrawable(Color.rgb(31, 31, 35)))
                .into(holder.image);
    }

    /**
     * Last-resort artwork path for collection cards. If the listing page does not expose an image,
     * load the collection's own first page and borrow artwork from its first usable video card.
     * If that card is also lazy/rendered, reuse the normal media thumbnail resolver that already
     * powers native video feeds. Only cards that are actually bound on screen trigger this work.
     */
    private void requestFallbackArtwork(NativeContentItem item) {
        if (closed || item == null || usableImage(item.imageUrl)) return;
        String collectionUrl = cleanUrl(item.url);
        if (collectionUrl.isEmpty()) return;

        String cached = cachedArtwork(collectionUrl);
        if (usableImage(cached)) {
            applyFallbackArtwork(collectionUrl, cached);
            return;
        }

        synchronized (fallbackPending) {
            if (!fallbackPending.add(collectionUrl)) return;
        }

        collectionIo.execute(() -> {
            String directArtwork = "";
            String firstMediaUrl = "";
            try {
                List<NativeContentItem> feed = repository.fetchFeed(context, collectionUrl, 1);
                for (NativeContentItem candidate : feed) {
                    if (candidate == null || candidate.isSection()) continue;
                    if (firstMediaUrl.isEmpty() && candidate.url != null) {
                        firstMediaUrl = cleanUrl(candidate.url);
                    }
                    if (usableImage(candidate.imageUrl)) {
                        directArtwork = candidate.imageUrl.trim();
                        break;
                    }
                }
            } catch (Exception ignored) {
            }

            final String artwork = directArtwork;
            final String mediaUrl = firstMediaUrl;
            main.post(() -> {
                if (closed) {
                    clearPending(collectionUrl);
                    return;
                }
                if (usableImage(artwork)) {
                    finishFallback(collectionUrl, artwork);
                    return;
                }
                if (mediaUrl.isEmpty()) {
                    clearPending(collectionUrl);
                    return;
                }

                synchronized (collectionsByMedia) {
                    List<String> waiting = collectionsByMedia.get(mediaUrl);
                    if (waiting == null) {
                        waiting = new ArrayList<>();
                        collectionsByMedia.put(mediaUrl, waiting);
                    }
                    if (!waiting.contains(collectionUrl)) waiting.add(collectionUrl);
                }
                RenderedThumbnailResolver resolver =
                        mediaResolvers[resolverCursor++ % mediaResolvers.length];
                resolver.request(mediaUrl);
            });
        });
    }

    private void onMediaArtworkResolved(String mediaUrl, String thumbnailUrl) {
        List<String> collections;
        synchronized (collectionsByMedia) {
            collections = collectionsByMedia.remove(mediaUrl);
        }
        if (collections == null || collections.isEmpty()) return;
        for (String collectionUrl : collections) {
            if (usableImage(thumbnailUrl)) finishFallback(collectionUrl, thumbnailUrl);
            else clearPending(collectionUrl);
        }
    }

    private void finishFallback(String collectionUrl, String artwork) {
        clearPending(collectionUrl);
        if (closed || !usableImage(artwork)) return;
        try {
            fallbackCache.edit().putString(collectionUrl, artwork.trim()).apply();
        } catch (Exception ignored) {
        }
        applyFallbackArtwork(collectionUrl, artwork);
    }

    private void applyFallbackArtwork(String collectionUrl, String artwork) {
        if (closed || !usableImage(artwork)) return;
        String key = BrowseArtworkResolver.normalizeKey(collectionUrl);
        for (int i = 0; i < items.size(); i++) {
            NativeContentItem current = items.get(i);
            if (current == null || usableImage(current.imageUrl)) continue;
            if (!key.equals(BrowseArtworkResolver.normalizeKey(current.url))) continue;
            items.set(i, copyWithImage(current, artwork.trim()));
            notifyItemChanged(i);
            return;
        }
    }

    private NativeContentItem withCachedArtwork(NativeContentItem item) {
        if (item == null || usableImage(item.imageUrl)) return item;
        String cached = cachedArtwork(cleanUrl(item.url));
        return usableImage(cached) ? copyWithImage(item, cached.trim()) : item;
    }

    private String cachedArtwork(String collectionUrl) {
        if (collectionUrl == null || collectionUrl.isEmpty()) return "";
        try {
            return fallbackCache.getString(collectionUrl, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private NativeContentItem copyWithImage(NativeContentItem item, String image) {
        return new NativeContentItem(
                item.kind,
                item.title,
                item.url,
                image,
                item.views,
                item.uploader,
                item.comments
        );
    }

    private void clearPending(String collectionUrl) {
        synchronized (fallbackPending) {
            fallbackPending.remove(collectionUrl);
        }
    }

    public void close() {
        closed = true;
        collectionIo.shutdownNow();
        synchronized (fallbackPending) {
            fallbackPending.clear();
        }
        synchronized (collectionsByMedia) {
            collectionsByMedia.clear();
        }
    }

    private GlideUrl withSiteHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null || pageUrl.isEmpty() ? SITE : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if (cookies == null || cookies.trim().isEmpty()) cookies = CookieManager.getInstance().getCookie(SITE);
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private boolean usableImage(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String image = value.trim().toLowerCase(java.util.Locale.US);
        return image.startsWith("http://") || image.startsWith("https://") || image.startsWith("file://");
    }

    private String cleanUrl(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.image).clear(holder.image);
        holder.card.setOnClickListener(null);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView title;

        Holder(MaterialCardView card, ImageView image, TextView title) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
        }
    }
}
