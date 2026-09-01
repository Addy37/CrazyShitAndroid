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
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private static final int VIEW_TYPE_STANDARD = 0;
    private static final int VIEW_TYPE_WIDE_CREATOR = 1;
    private static final int COMPACT_COPY_HEIGHT_DP = 52;
    private static final int DESCRIPTION_COPY_HEIGHT_DP = 132;
    private static final int WIDE_CREATOR_COPY_HEIGHT_DP = 66;
    private static final int WIDE_CREATOR_SHADE_HEIGHT_DP = 104;
    private static final String CREATOR_ARTWORK_PREFS = "creator_artwork_cache_v1";
    private static final float CREATOR_CARD_ASPECT_RATIO = 16f / 9f;

    public interface Listener {
        void onOpen(NativeContentItem item);
    }

    private final List<NativeContentItem> items = new ArrayList<>();
    private final Listener listener;
    private final Map<String, Artwork> resolvedArtwork = new HashMap<>();
    private final Set<String> requestedArtwork = new HashSet<>();
    private final Set<String> failedArtworkRetry = new HashSet<>();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bunkrArtworkIo = Executors.newFixedThreadPool(3);
    private boolean wideCreatorCards;
    private volatile boolean closed;

    public NativeCategoryAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void replace(List<NativeContentItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    public void setWideCreatorCards(boolean enabled) {
        if (wideCreatorCards == enabled) return;
        wideCreatorCards = enabled;
        notifyDataSetChanged();
    }

    /**
     * Browse artwork is bundled inside the APK now, so there is no rendered/network artwork pass.
     */
    public boolean hasMissingArtwork() {
        return false;
    }

    /** Kept for source compatibility with the pager while remote artwork resolution is retired. */
    public void applyArtwork(Map<String, String> artwork) {
        // Intentionally no-op. Series/Categories thumbnails come from EmbeddedBrowseArtwork.
    }

    public void close() {
        closed = true;
        bunkrArtworkIo.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        requestedArtwork.clear();
    }

    @Override
    public long getItemId(int position) {
        NativeContentItem item = items.get(position);
        return (item.kind + "\n" + item.title + "\n" + item.url).hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return wideCreatorCards && items.get(position).isCreator()
                ? VIEW_TYPE_WIDE_CREATOR
                : VIEW_TYPE_STANDARD;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean wideCreator = viewType == VIEW_TYPE_WIDE_CREATOR;
        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setRadius(dp(parent, wideCreator ? 6 : 16));
        card.setStrokeWidth(dp(parent, 1));
        card.setStrokeColor(Color.rgb(52, 52, 59));
        card.setCardElevation(dp(parent, wideCreator ? 0 : 1));

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                -1,
                dp(parent, wideCreator ? wideCreatorHeightDp(parent) : responsiveHeightDp(parent))
        );
        int horizontalMargin = dp(parent, wideCreator ? 4 : 7);
        int verticalMargin = dp(parent, wideCreator ? 5 : 7);
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        card.setLayoutParams(params);

        FrameLayout frame = new FrameLayout(parent.getContext());
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(wideCreator
                ? ImageView.ScaleType.FIT_CENTER
                : ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(18, 18, 21));
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(parent.getContext());
        if (wideCreator) {
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, Color.argb(225, 0, 0, 0)}
            );
            shade.setBackground(gradient);
        } else {
            shade.setBackgroundColor(Color.argb(170, 0, 0, 0));
        }
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(
                -1,
                dp(parent, wideCreator ? WIDE_CREATOR_SHADE_HEIGHT_DP : COMPACT_COPY_HEIGHT_DP)
        );
        shadeParams.gravity = Gravity.BOTTOM;
        frame.addView(shade, shadeParams);

        LinearLayout copy = new LinearLayout(parent.getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(
                dp(parent, wideCreator ? 16 : 11),
                dp(parent, 6),
                dp(parent, wideCreator ? 16 : 11),
                dp(parent, wideCreator ? 10 : 7)
        );
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(
                -1,
                dp(parent, wideCreator ? WIDE_CREATOR_COPY_HEIGHT_DP : COMPACT_COPY_HEIGHT_DP)
        );
        copyParams.gravity = Gravity.BOTTOM;
        frame.addView(copy, copyParams);

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(wideCreator ? 19f : 14.5f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = new TextView(parent.getContext());
        description.setTextColor(Color.rgb(210, 210, 218));
        description.setTextSize(11f);
        description.setMaxLines(5);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description.setVisibility(View.GONE);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(parent, 3);
        copy.addView(description, descriptionParams);

        return new Holder(card, image, shade, copy, title, description, wideCreator);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        holder.title.setText(item.title);
        boolean hasDescription = item.description != null && !item.description.trim().isEmpty();
        holder.description.setText(hasDescription ? item.description.trim() : "");
        holder.description.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
        resizeForDescription(holder, hasDescription);
        holder.card.setContentDescription(hasDescription
                ? item.title + ". " + item.description.trim()
                : item.title);
        holder.card.setOnClickListener(v -> listener.onOpen(item));
        restoreCreatorArtwork(holder.image.getContext(), item);
        loadImage(holder, item);
        requestBunkrArtwork(holder.image.getContext(), item);
    }

    private void loadImage(Holder holder, NativeContentItem item) {
        byte[] embedded = EmbeddedBrowseArtwork.get(holder.image.getContext(), item.url);
        if (embedded != null && embedded.length >= 512) {
            RequestBuilder<Drawable> request = Glide.with(holder.image)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(31, 31, 35)))
                    .error(new ColorDrawable(Color.rgb(31, 31, 35)));
            request = holder.wideCreator ? request.fitCenter() : request.centerCrop();
            sizeImageRequest(holder, request).into(holder.image);
            return;
        }

        Artwork artwork = BunkrRepository.isAlbumUrl(item.url)
                ? resolvedArtwork.get(item.url)
                : null;
        String imageUrl = artwork == null ? item.imageUrl : artwork.imageUrl;
        if (imageUrl == null || imageUrl.trim().isEmpty()) imageUrl = item.imageUrl;
        if (imageUrl != null) imageUrl = imageUrl.trim();
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(31, 31, 35)));
            return;
        }
        RequestBuilder<Drawable> request = Glide.with(holder.image)
                .load(remoteImage(
                        imageUrl,
                        artwork == null ? item.url : artwork.referer
                ))
                .diskCacheStrategy(holder.wideCreator
                        ? DiskCacheStrategy.ALL
                        : DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.rgb(31, 31, 35)))
                .error(new ColorDrawable(Color.rgb(31, 31, 35)));
        request = holder.wideCreator ? request.fitCenter() : request.centerCrop();
        if (holder.wideCreator && artwork != null) {
            String requestedUrl = imageUrl;
            request = request.listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(
                        GlideException error,
                        Object model,
                        Target<Drawable> target,
                        boolean firstResource
                ) {
                    mainHandler.post(() -> onResolvedArtworkFailed(
                            holder.image.getContext(),
                            item,
                            requestedUrl
                    ));
                    return false;
                }

                @Override
                public boolean onResourceReady(
                        Drawable resource,
                        Object model,
                        Target<Drawable> target,
                        DataSource source,
                        boolean firstResource
                ) {
                    return false;
                }
            });
        }
        sizeImageRequest(holder, request).into(holder.image);
    }

    private void resizeForDescription(Holder holder, boolean hasDescription) {
        int copyHeight = holder.wideCreator
                ? WIDE_CREATOR_COPY_HEIGHT_DP
                : hasDescription ? DESCRIPTION_COPY_HEIGHT_DP : COMPACT_COPY_HEIGHT_DP;
        int shadeHeight = holder.wideCreator ? WIDE_CREATOR_SHADE_HEIGHT_DP : copyHeight;
        RecyclerView.LayoutParams cardParams = (RecyclerView.LayoutParams) holder.card.getLayoutParams();
        cardParams.height = dp(
                holder.card,
                holder.wideCreator
                        ? wideCreatorHeightDp(holder.card)
                        : hasDescription
                        ? responsiveDescriptionHeightDp(holder.card)
                        : responsiveHeightDp(holder.card)
        );
        holder.card.setLayoutParams(cardParams);

        FrameLayout.LayoutParams shadeParams = (FrameLayout.LayoutParams) holder.shade.getLayoutParams();
        shadeParams.height = dp(holder.card, shadeHeight);
        holder.shade.setLayoutParams(shadeParams);

        FrameLayout.LayoutParams copyParams = (FrameLayout.LayoutParams) holder.copy.getLayoutParams();
        copyParams.height = dp(holder.card, copyHeight);
        holder.copy.setLayoutParams(copyParams);
    }

    private RequestBuilder<Drawable> sizeImageRequest(
            Holder holder,
            RequestBuilder<Drawable> request
    ) {
        if (!holder.wideCreator) return request;
        int width = Math.max(
                720,
                Math.min(1440, holder.image.getResources().getDisplayMetrics().widthPixels)
        );
        return request.override(width, Math.max(1, Math.round(width * 9f / 16f)));
    }

    private GlideUrl remoteImage(String imageUrl, String requestReferer) {
        String referer = requestReferer == null || requestReferer.isEmpty()
                ? EfuktRepository.BASE
                : requestReferer;
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", EfuktRepository.USER_AGENT)
                .addHeader("Referer", referer)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.isEmpty()) && !referer.isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(referer);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private void requestBunkrArtwork(android.content.Context context, NativeContentItem item) {
        if (closed || context == null || item == null || !BunkrRepository.isAlbumUrl(item.url)) return;
        Artwork resolved = resolvedArtwork.get(item.url);
        if (resolved != null && !resolved.imageUrl.isEmpty()) return;
        if (!requestedArtwork.add(item.url)) return;
        android.content.Context appContext = context.getApplicationContext();
        String albumUrl = item.url;
        try {
            bunkrArtworkIo.execute(() -> {
                Artwork artwork = null;
                try {
                    if (item.isCreator()) {
                        CrazyShitRepository.StreamInfo resolvedImage =
                                bunkrRepository.fetchBestCreatorArtwork(
                                        appContext,
                                        item.searchQuery,
                                        albumUrl,
                                        CREATOR_CARD_ASPECT_RATIO
                                );
                        artwork = new Artwork(
                                resolvedImage.mediaUrl,
                                resolvedImage.requestReferer,
                                true
                        );
                    } else {
                        String imageUrl = bunkrRepository.fetchAlbumArtwork(appContext, albumUrl);
                        artwork = new Artwork(imageUrl, albumUrl, false);
                    }
                } catch (Exception ignored) {
                }
                Artwork result = artwork;
                mainHandler.post(() -> onBunkrArtwork(appContext, albumUrl, result));
            });
        } catch (RuntimeException ignored) {
            requestedArtwork.remove(albumUrl);
        }
    }

    private void onBunkrArtwork(Context context, String pageUrl, Artwork artwork) {
        if (closed || pageUrl == null || pageUrl.isEmpty()) return;
        if (artwork == null || artwork.imageUrl.isEmpty()) {
            requestedArtwork.remove(pageUrl);
            return;
        }
        resolvedArtwork.put(pageUrl, artwork);
        if (artwork.persistent) writeCreatorArtworkCache(context, pageUrl, artwork);
        for (int i = 0; i < items.size(); i++) {
            NativeContentItem item = items.get(i);
            if (item != null && pageUrl.equals(item.url)) notifyItemChanged(i);
        }
    }

    private void restoreCreatorArtwork(Context context, NativeContentItem item) {
        if (context == null || item == null || !item.isCreator() ||
                resolvedArtwork.containsKey(item.url)) return;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                CREATOR_ARTWORK_PREFS,
                Context.MODE_PRIVATE
        );
        String key = artworkCacheKey(item.url);
        String imageUrl = prefs.getString(key + "_url", "");
        if (imageUrl == null || imageUrl.isEmpty()) return;
        String referer = prefs.getString(key + "_referer", item.url);
        resolvedArtwork.put(item.url, new Artwork(imageUrl, referer, true));
    }

    private void writeCreatorArtworkCache(Context context, String pageUrl, Artwork artwork) {
        if (context == null || artwork == null || artwork.imageUrl.isEmpty()) return;
        String key = artworkCacheKey(pageUrl);
        context.getApplicationContext().getSharedPreferences(
                CREATOR_ARTWORK_PREFS,
                Context.MODE_PRIVATE
        ).edit()
                .putString(key + "_url", artwork.imageUrl)
                .putString(key + "_referer", artwork.referer)
                .apply();
    }

    private void onResolvedArtworkFailed(
            Context context,
            NativeContentItem item,
            String failedUrl
    ) {
        if (closed || context == null || item == null || !item.isCreator()) return;
        Artwork current = resolvedArtwork.get(item.url);
        if (current == null || !current.imageUrl.equals(failedUrl)) return;
        if (!failedArtworkRetry.add(item.url)) return;
        resolvedArtwork.remove(item.url);
        requestedArtwork.remove(item.url);
        String key = artworkCacheKey(item.url);
        context.getApplicationContext().getSharedPreferences(
                CREATOR_ARTWORK_PREFS,
                Context.MODE_PRIVATE
        ).edit()
                .remove(key + "_url")
                .remove(key + "_referer")
                .apply();
        requestBunkrArtwork(context, item);
    }

    private String artworkCacheKey(String pageUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    pageUrl.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder key = new StringBuilder("cover_");
            for (byte value : digest) key.append(String.format("%02x", value & 0xff));
            return key.toString();
        } catch (Exception ignored) {
            return "cover_" + Integer.toHexString(pageUrl.hashCode());
        }
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

    private static int responsiveHeightDp(View parent) {
        Configuration config = parent.getResources().getConfiguration();
        int widthDp = Math.max(320, config.screenWidthDp);
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int columns;
        int rail = 0;
        if (landscape) {
            columns = widthDp >= 900 ? 4 : 3;
            rail = 68;
        } else {
            columns = 2;
        }
        float available = Math.max(280f, widthDp - rail - (columns * 14f));
        float cardWidth = available / columns;
        return Math.max(136, Math.min(180, Math.round(cardWidth * 0.72f)));
    }

    private static int responsiveDescriptionHeightDp(View parent) {
        return Math.max(218, Math.min(260, responsiveHeightDp(parent) + 78));
    }

    private static int wideCreatorHeightDp(View parent) {
        Configuration config = parent.getResources().getConfiguration();
        int rail = config.orientation == Configuration.ORIENTATION_LANDSCAPE ? 68 : 0;
        int available = Math.max(320, config.screenWidthDp - rail - 8);
        return Math.max(184, Math.min(420, Math.round(available * 9f / 16f)));
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final View shade;
        final LinearLayout copy;
        final TextView title;
        final TextView description;
        final boolean wideCreator;

        Holder(
                MaterialCardView card,
                ImageView image,
                View shade,
                LinearLayout copy,
                TextView title,
                TextView description,
                boolean wideCreator
        ) {
            super(card);
            this.card = card;
            this.image = image;
            this.shade = shade;
            this.copy = copy;
            this.title = title;
            this.description = description;
            this.wideCreator = wideCreator;
        }
    }

    private static final class Artwork {
        final String imageUrl;
        final String referer;
        final boolean persistent;

        Artwork(String imageUrl, String referer, boolean persistent) {
            this.imageUrl = imageUrl == null ? "" : imageUrl.trim();
            this.referer = referer == null ? "" : referer.trim();
            this.persistent = persistent;
        }
    }
}
