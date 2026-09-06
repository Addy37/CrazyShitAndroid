package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        items.clear();
        addUnique(incoming);
        notifyDataSetChanged();
        preloadRange(0, Math.min(items.size(), 18));
    }

    void append(List<NativeContentItem> incoming) {
        int start = items.size();
        addUnique(incoming);
        int added = items.size() - start;
        if (added > 0) {
            notifyItemRangeInserted(start, added);
            preloadRange(start, Math.min(items.size(), start + 18));
        }
    }

    void preloadVisible(int first, int last) {
        int from = Math.max(0, first);
        int to = Math.min(items.size(), Math.max(from, last + 12));
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

        return new Holder(tile, image, play);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        holder.tile.setAspectRatio(adaptiveAspectRatios
                ? aspectRatios.getOrDefault(item.url, 1f)
                : 1f);
        holder.play.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        holder.itemView.setContentDescription(
                (item.isVideo() ? "Video, " : "Photo, ") + item.title
        );

        if (item.imageUrl == null || item.imageUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 23)));
        } else {
            RequestBuilder<Drawable> request = Glide.with(holder.image)
                    .load(withHeaders(item.imageUrl, imageReferer(item)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(20, 20, 23)))
                    .error(new ColorDrawable(Color.rgb(20, 20, 23)));
            if (adaptiveAspectRatios) {
                request = request
                        .dontTransform()
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
            request.preload(360, 360);
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

    private String imageReferer(NativeContentItem item) {
        if (item != null && WikiFeetRepository.isWikiFeetUrl(item.url) &&
                WikiFeetRepository.isWikiFeetUrl(item.uploader)) return item.uploader;
        return item == null ? null : item.url;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AspectRatioFrameLayout tile;
        final ImageView image;
        final View play;

        Holder(AspectRatioFrameLayout itemView, ImageView image, View play) {
            super(itemView);
            this.tile = itemView;
            this.image = image;
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
