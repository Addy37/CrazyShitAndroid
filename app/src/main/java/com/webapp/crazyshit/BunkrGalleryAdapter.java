package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dense mixed-media grid used for Bunkr albums. */
final class BunkrGalleryAdapter extends RecyclerView.Adapter<BunkrGalleryAdapter.Holder> {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    interface Listener {
        void onOpen(int position, NativeContentItem item);

        void onLongPress(NativeContentItem item, View anchor);
    }

    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final Map<String, Float> aspectRatios = new HashMap<>();
    private final LinkedHashSet<String> highlightedUrls = new LinkedHashSet<>();
    private final Context context;
    private final Listener listener;
    private final boolean adaptiveAspectRatios;

    BunkrGalleryAdapter(Context context, Listener listener) {
        this(context, listener, false);
    }

    BunkrGalleryAdapter(Context context, Listener listener, boolean adaptiveAspectRatios) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.adaptiveAspectRatios = adaptiveAspectRatios;
        setHasStableIds(true);
    }

    void replace(List<NativeContentItem> incoming) {
        replace(incoming, true);
    }

    void replace(List<NativeContentItem> incoming, boolean preloadAhead) {
        items.clear();
        addUnique(incoming);
        notifyDataSetChanged();
        if (preloadAhead) preloadRange(0, Math.min(items.size(), adaptiveAspectRatios ? 6 : 12));
    }

    void append(List<NativeContentItem> incoming) {
        append(incoming, true);
    }

    void append(List<NativeContentItem> incoming, boolean preloadAhead) {
        int start = items.size();
        addUnique(incoming);
        int added = items.size() - start;
        if (added > 0) {
            notifyItemRangeInserted(start, added);
            if (preloadAhead) preloadRange(start, Math.min(items.size(), start + (adaptiveAspectRatios ? 6 : 12)));
        }
    }

    void setHighlightedUrls(Collection<String> urls) {
        highlightedUrls.clear();
        if (urls != null) {
            for (String url : urls) {
                if (url != null && !url.trim().isEmpty()) highlightedUrls.add(url.trim());
            }
        }
        if (!items.isEmpty()) notifyDataSetChanged();
    }


    void preloadVisible(int first, int last) {
        int from = Math.max(0, first);
        int lookAhead = adaptiveAspectRatios ? 6 : 12;
        int to = Math.min(items.size(), Math.max(from, last + lookAhead));
        preloadRange(from, to);
    }

    ArrayList<NativeContentItem> snapshot() {
        return new ArrayList<>(items);
    }

    int indexOfUrl(String url) {
        if (url == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (url.equals(items.get(i).url)) return i;
        }
        return -1;
    }

    int size() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).url.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AspectRatioFrameLayout tile = new AspectRatioFrameLayout(parent.getContext());
        tile.setBackgroundColor(Color.rgb(20, 20, 23));
        RecyclerView.LayoutParams tileParams = new RecyclerView.LayoutParams(-1, -2);
        int gap = dp(parent, 3);
        tile.setBackground(BrowseUi.rounded(parent.getContext(), BrowseUi.SURFACE, 10));
        tile.setClipToOutline(true);
        tileParams.setMargins(gap, gap, gap, gap);
        tile.setLayoutParams(tileParams);

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(20, 20, 23));
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout source = new FrameLayout(parent.getContext());
        source.setBackground(BrowseUi.rounded(
                parent.getContext(),
                Color.argb(215, 0, 0, 0),
                7
        ));
        source.setElevation(dp(parent, 5));
        source.setAlpha(0.85f);
        FrameLayout.LayoutParams sourceParams = new FrameLayout.LayoutParams(
                dp(parent, 26),
                dp(parent, 26)
        );
        sourceParams.gravity = Gravity.BOTTOM | Gravity.START;
        sourceParams.setMargins(dp(parent, 6), 0, 0, dp(parent, 6));
        tile.addView(source, sourceParams);

        ImageView sourceIcon = new ImageView(parent.getContext());
        sourceIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sourceIcon.setPadding(dp(parent, 3), dp(parent, 3), dp(parent, 3), dp(parent, 3));
        source.addView(sourceIcon, new FrameLayout.LayoutParams(-1, -1));

        TextView sourceVariant = new TextView(parent.getContext());
        GradientDrawable variantBackground = new GradientDrawable();
        variantBackground.setShape(GradientDrawable.OVAL);
        variantBackground.setColor(Color.rgb(190, 24, 93));
        sourceVariant.setBackground(variantBackground);
        sourceVariant.setGravity(Gravity.CENTER);
        sourceVariant.setIncludeFontPadding(false);
        sourceVariant.setText("X");
        sourceVariant.setTextColor(Color.WHITE);
        sourceVariant.setTextSize(7);
        sourceVariant.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        FrameLayout.LayoutParams variantParams = new FrameLayout.LayoutParams(
                dp(parent, 12),
                dp(parent, 12)
        );
        variantParams.gravity = Gravity.BOTTOM | Gravity.END;
        source.addView(sourceVariant, variantParams);

        ImageView fresh = new ImageView(parent.getContext());
        fresh.setImageResource(R.drawable.ic_new_content);
        fresh.setContentDescription("New content");
        fresh.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fresh.setVisibility(View.GONE);
        fresh.setElevation(dp(parent, 7));
        FrameLayout.LayoutParams freshParams = new FrameLayout.LayoutParams(
                dp(parent, 26),
                dp(parent, 26)
        );
        freshParams.gravity = Gravity.TOP | Gravity.END;
        freshParams.setMargins(0, dp(parent, 6), dp(parent, 6), 0);
        tile.addView(fresh, freshParams);

        FrameLayout play = new FrameLayout(parent.getContext());
        GradientDrawable playBackground = new GradientDrawable();
        playBackground.setCornerRadius(dp(parent, 8));
        playBackground.setColor(Color.argb(185, 0, 0, 0));
        play.setBackground(playBackground);
        play.setElevation(dp(parent, 5));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                dp(parent, 42),
                dp(parent, 42)
        );
        playParams.gravity = Gravity.BOTTOM | Gravity.END;
        playParams.setMargins(0, 0, dp(parent, 6), dp(parent, 6));
        tile.addView(play, playParams);

        ImageView playIcon = new ImageView(parent.getContext());
        playIcon.setImageResource(R.drawable.ic_player_play);
        playIcon.setColorFilter(Color.WHITE);
        playIcon.setPadding(dp(parent, 11), dp(parent, 11), dp(parent, 9), dp(parent, 11));
        play.addView(playIcon, new FrameLayout.LayoutParams(-1, -1));

        return new Holder(tile, image, source, sourceIcon, sourceVariant, fresh, play);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        holder.tile.setAspectRatio(adaptiveAspectRatios
                ? aspectRatios.getOrDefault(item.url, 1f)
                : 1f);
        holder.play.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        boolean fresh = isHighlighted(item);
        holder.fresh.setVisibility(fresh ? View.VISIBLE : View.GONE);
        SourceBadge source = sourceBadge(item);
        holder.source.setVisibility(source == null ? View.GONE : View.VISIBLE);
        if (source != null) {
            holder.sourceIcon.setImageResource(source.drawableRes);
            holder.sourceVariant.setVisibility(source.wikiFeetX ? View.VISIBLE : View.GONE);
        } else {
            holder.sourceIcon.setImageDrawable(null);
            holder.sourceVariant.setVisibility(View.GONE);
        }
        holder.itemView.setContentDescription(
                (item.isVideo() ? "Video, " : "Photo, ") + item.title +
                        (source == null ? "" : ", source " + source.name) +
                        (fresh ? ", new from Updates" : "")
        );

        if (item.imageUrl == null || item.imageUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 23)));
        } else {
            RequestBuilder<Drawable> request = Glide.with(holder.image)
                    .load(withHeaders(item.imageUrl, imageReferer(item)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transition(DrawableTransitionOptions.withCrossFade(130))
                    .placeholder(new ColorDrawable(Color.rgb(20, 20, 23)))
                    .error(new ColorDrawable(Color.rgb(20, 20, 23)));
            if (adaptiveAspectRatios) {
                request = request
                        .dontTransform()
                        .override(384, 384)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(
                                    GlideException error,
                                    Object model,
                                    Target<Drawable> target,
                                    boolean firstResource
                            ) {
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
                                applyAspectRatio(holder, item, resource);
                                return false;
                            }
                        });
            } else {
                request = request.centerCrop().override(360, 360);
            }
            if (isOnlyHavenImagePreview(item)) {
                RequestBuilder<Drawable> fallback = Glide.with(holder.image)
                        .load(withHeaders(item.url, imageReferer(item)))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .dontAnimate();
                fallback = adaptiveAspectRatios
                        ? fallback.dontTransform().override(384, 384)
                        : fallback.centerCrop().override(360, 360);
                request = request.error(fallback);
            }
            request.into(holder.image);
        }

        holder.itemView.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION || current >= items.size()) return;
            listener.onOpen(current, items.get(current));
        });
        holder.itemView.setOnLongClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION || current >= items.size()) return false;
            listener.onLongPress(items.get(current), v);
            return true;
        });
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

    private void addUnique(List<NativeContentItem> incoming) {
        if (incoming == null) return;
        for (NativeContentItem candidate : incoming) {
            if (candidate == null || candidate.url == null || candidate.url.isEmpty()) continue;
            if (indexOfUrl(candidate.url) < 0) items.add(candidate);
        }
    }

    private void preloadRange(int start, int end) {
        for (int i = start; i < end; i++) {
            NativeContentItem item = items.get(i);
            if (item.imageUrl == null || item.imageUrl.isEmpty()) continue;
            RequestBuilder<Drawable> request = Glide.with(context)
                    .load(withHeaders(item.imageUrl, imageReferer(item)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL);
            if (adaptiveAspectRatios) request = request.dontTransform();
            else request = request.centerCrop();
            request.preload(adaptiveAspectRatios ? 384 : 360, adaptiveAspectRatios ? 384 : 360);
        }
    }

    private void applyAspectRatio(
            Holder holder,
            NativeContentItem item,
            Drawable resource
    ) {
        if (resource == null || resource.getIntrinsicWidth() <= 0 ||
                resource.getIntrinsicHeight() <= 0) return;
        float ratio = (float) resource.getIntrinsicWidth() / resource.getIntrinsicHeight();
        ratio = Math.max(0.56f, Math.min(1.78f, ratio));
        aspectRatios.put(item.url, ratio);
        int current = holder.getBindingAdapterPosition();
        if (current == RecyclerView.NO_POSITION || current >= items.size() ||
                !item.url.equals(items.get(current).url)) return;
        holder.tile.setAspectRatio(ratio);
    }

    private GlideUrl withHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null
                        ? BunkrRepository.DEFAULT_PAGE_ORIGIN + "/" : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private boolean isOnlyHavenImagePreview(NativeContentItem item) {
        return item != null && item.isImage() &&
                OnlyHavenRepository.isOnlyHavenUrl(item.uploader) &&
                item.imageUrl != null && !item.imageUrl.isEmpty() &&
                item.url != null && !item.url.equals(item.imageUrl);
    }

    private String imageReferer(NativeContentItem item) {
        if (item != null && WikiFeetRepository.isWikiFeetUrl(item.url) &&
                WikiFeetRepository.isWikiFeetUrl(item.uploader)) return item.uploader;
        if (item != null && !FapelloRepository.isPostUrl(item.url) &&
                FapelloRepository.isModelUrl(item.uploader)) return item.uploader;
        if (item != null && OnlyHavenRepository.isOnlyHavenUrl(item.uploader)) {
            return item.uploader;
        }
        return item == null ? null : item.url;
    }

    private SourceBadge sourceBadge(NativeContentItem item) {
        if (item == null) return null;
        if (FapelloRepository.isFapelloUrl(item.url) ||
                FapelloRepository.isModelUrl(item.uploader)) {
            return new SourceBadge(R.drawable.ic_source_fapello, "Fapello", false);
        }
        if (OnlyHavenRepository.isOnlyHavenUrl(item.url) ||
                OnlyHavenRepository.isOnlyHavenUrl(item.uploader) ||
                containsIgnoreCase(item.description, "OnlyHaven")) {
            return new SourceBadge(R.drawable.ic_source_onlyhaven, "OnlyHaven", false);
        }
        if (WikiFeetRepository.isWikiFeetUrl(item.url) ||
                WikiFeetRepository.isWikiFeetUrl(item.uploader)) {
            if (containsIgnoreCase(item.uploader, "wikifeetx") ||
                    containsIgnoreCase(item.views, "wikifeet x") ||
                    containsIgnoreCase(item.description, "wikifeet x")) {
                return new SourceBadge(R.drawable.ic_source_wikifeet, "WikiFeet X", true);
            }
            return new SourceBadge(R.drawable.ic_source_wikifeet, "WikiFeet", false);
        }
        if (BunkrRepository.isBunkrUrl(item.url)) {
            return new SourceBadge(R.drawable.ic_source_bunkr, "Bunkr", false);
        }
        return null;
    }

    private boolean isHighlighted(NativeContentItem item) {
        if (item == null || highlightedUrls.isEmpty()) return false;
        String itemUrl = canonicalHighlightUrl(item.url);
        String uploaderUrl = canonicalHighlightUrl(item.uploader);
        for (String raw : highlightedUrls) {
            String highlighted = canonicalHighlightUrl(raw);
            if (highlighted.isEmpty()) continue;
            if (highlighted.equals(itemUrl) || highlighted.equals(uploaderUrl)) return true;
        }
        return false;
    }

    private String canonicalHighlightUrl(String value) {
        if (value == null) return "";
        String clean = value.trim();
        int fragment = clean.indexOf('#');
        if (fragment >= 0) clean = clean.substring(0, fragment);
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        return clean.toLowerCase(Locale.US);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.US).contains(query);
    }

    private static final class SourceBadge {
        final int drawableRes;
        final String name;
        final boolean wikiFeetX;

        SourceBadge(int drawableRes, String name, boolean wikiFeetX) {
            this.drawableRes = drawableRes;
            this.name = name;
            this.wikiFeetX = wikiFeetX;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AspectRatioFrameLayout tile;
        final ImageView image;
        final FrameLayout source;
        final ImageView sourceIcon;
        final TextView sourceVariant;
        final ImageView fresh;
        final View play;

        Holder(
                AspectRatioFrameLayout itemView,
                ImageView image,
                FrameLayout source,
                ImageView sourceIcon,
                TextView sourceVariant,
                ImageView fresh,
                View play
        ) {
            super(itemView);
            this.tile = itemView;
            this.image = image;
            this.source = source;
            this.sourceIcon = sourceIcon;
            this.sourceVariant = sourceVariant;
            this.fresh = fresh;
            this.play = play;
        }
    }

    private static final class AspectRatioFrameLayout extends FrameLayout {
        private float aspectRatio = 1f;

        AspectRatioFrameLayout(Context context) {
            super(context);
        }

        void setAspectRatio(float next) {
            float safe = Math.max(0.56f, Math.min(1.78f, next));
            if (Math.abs(aspectRatio - safe) < 0.01f) return;
            aspectRatio = safe;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (width <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            int height = Math.max(1, Math.round(width / aspectRatio));
            super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            );
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
