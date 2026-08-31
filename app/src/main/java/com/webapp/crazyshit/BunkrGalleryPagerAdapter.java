package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Full-screen horizontal pager for Bunkr photos and videos. */
final class BunkrGalleryPagerAdapter
        extends RecyclerView.Adapter<BunkrGalleryPagerAdapter.Holder> {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    interface Listener {
        void onMediaTap(int position, NativeContentItem item);

        void onResolvedImageFailed(int position, NativeContentItem item);
    }

    private final Context context;
    private final Listener listener;
    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final Map<String, String> resolvedUrls = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private int activeVideoPosition = RecyclerView.NO_POSITION;
    private Player activePlayer;

    BunkrGalleryPagerAdapter(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        setHasStableIds(true);
    }

    void replace(List<NativeContentItem> incoming, Map<String, String> resolved) {
        items.clear();
        if (incoming != null) items.addAll(incoming);
        resolvedUrls.clear();
        if (resolved != null) resolvedUrls.putAll(resolved);
        loading.clear();
        failed.clear();
        activeVideoPosition = RecyclerView.NO_POSITION;
        activePlayer = null;
        notifyDataSetChanged();
    }

    int append(List<NativeContentItem> incoming) {
        if (incoming == null || incoming.isEmpty()) return 0;
        int start = items.size();
        for (NativeContentItem candidate : incoming) {
            if (candidate == null || indexOfUrl(candidate.url) >= 0) continue;
            items.add(candidate);
        }
        int added = items.size() - start;
        if (added > 0) notifyItemRangeInserted(start, added);
        return added;
    }

    NativeContentItem itemAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    int indexOfUrl(String url) {
        if (url == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (url.equals(items.get(i).url)) return i;
        }
        return -1;
    }

    void setLoading(int position, boolean value) {
        NativeContentItem item = itemAt(position);
        if (item == null) return;
        if (value) {
            failed.remove(item.url);
            loading.add(item.url);
        } else {
            loading.remove(item.url);
        }
        notifyItemChanged(position);
    }

    boolean isLoading(int position) {
        NativeContentItem item = itemAt(position);
        return item != null && loading.contains(item.url);
    }

    boolean isFailed(int position) {
        NativeContentItem item = itemAt(position);
        return item != null && failed.contains(item.url);
    }

    void setFailed(int position, boolean value) {
        NativeContentItem item = itemAt(position);
        if (item == null) return;
        if (value) {
            loading.remove(item.url);
            failed.add(item.url);
        } else {
            failed.remove(item.url);
        }
        notifyItemChanged(position);
    }

    void setResolvedUrl(int position, String mediaUrl) {
        NativeContentItem item = itemAt(position);
        if (item == null || mediaUrl == null || mediaUrl.isEmpty()) return;
        resolvedUrls.put(item.url, mediaUrl);
        loading.remove(item.url);
        failed.remove(item.url);
        notifyItemChanged(position);
    }

    String resolvedUrl(int position) {
        NativeContentItem item = itemAt(position);
        return item == null ? "" : value(resolvedUrls.get(item.url));
    }

    void activateVideo(int position, Player player) {
        int old = activeVideoPosition;
        activeVideoPosition = position;
        activePlayer = player;
        if (old != RecyclerView.NO_POSITION && old < items.size()) notifyItemChanged(old);
        if (position >= 0 && position < items.size()) notifyItemChanged(position);
    }

    void clearActiveVideo() {
        int old = activeVideoPosition;
        activeVideoPosition = RecyclerView.NO_POSITION;
        activePlayer = null;
        if (old != RecyclerView.NO_POSITION && old < items.size()) notifyItemChanged(old);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).url.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout root = new FrameLayout(parent.getContext());
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));

        ZoomableImageView image = new ZoomableImageView(parent.getContext());
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));

        PlayerView playerView = new PlayerView(parent.getContext());
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setVisibility(View.GONE);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        TextView play = new TextView(parent.getContext());
        play.setText("▶");
        play.setTextColor(Color.WHITE);
        play.setTextSize(30);
        play.setGravity(Gravity.CENTER);
        GradientDrawable playBackground = new GradientDrawable();
        playBackground.setShape(GradientDrawable.OVAL);
        playBackground.setColor(Color.argb(190, 0, 0, 0));
        play.setBackground(playBackground);
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                dp(parent, 62),
                dp(parent, 62)
        );
        playParams.gravity = Gravity.CENTER;
        root.addView(play, playParams);

        ProgressBar progress = new ProgressBar(parent.getContext());
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                dp(parent, 48),
                dp(parent, 48)
        );
        progressParams.gravity = Gravity.CENTER;
        root.addView(progress, progressParams);

        TextView failure = new TextView(parent.getContext());
        failure.setText("Couldn't load this item.\nTap to retry or use the menu to open its page.");
        failure.setTextColor(Color.rgb(205, 205, 212));
        failure.setTextSize(14);
        failure.setGravity(Gravity.CENTER);
        failure.setPadding(dp(parent, 28), dp(parent, 28), dp(parent, 28), dp(parent, 28));
        failure.setBackgroundColor(Color.argb(185, 0, 0, 0));
        failure.setVisibility(View.GONE);
        root.addView(failure, new FrameLayout.LayoutParams(-1, -1));

        return new Holder(root, image, playerView, play, progress, failure);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        holder.image.resetZoom();
        holder.image.setZoomEnabled(item.isImage());
        String resolved = value(resolvedUrls.get(item.url));
        String preview = resolved.isEmpty() ? value(item.imageUrl) : resolved;
        boolean activeVideo = item.isVideo() && position == activeVideoPosition &&
                activePlayer != null;

        holder.playerView.setPlayer(activeVideo ? activePlayer : null);
        holder.playerView.setVisibility(activeVideo ? View.VISIBLE : View.GONE);
        holder.image.setVisibility(activeVideo ? View.GONE : View.VISIBLE);
        holder.play.setVisibility(item.isVideo() && !activeVideo ? View.VISIBLE : View.GONE);
        holder.progress.setVisibility(loading.contains(item.url) ? View.VISIBLE : View.GONE);
        boolean showFailure = failed.contains(item.url) && !activeVideo;
        holder.failure.setVisibility(showFailure ? View.VISIBLE : View.GONE);

        if (!activeVideo) {
            if (preview.isEmpty()) {
                Glide.with(holder.image).clear(holder.image);
                holder.image.setImageDrawable(new ColorDrawable(Color.BLACK));
            } else {
                Glide.with(holder.image)
                        .load(withHeaders(preview, item.url))
                        .fitCenter()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .dontAnimate()
                        .placeholder(item.imageUrl == null || item.imageUrl.isEmpty()
                                ? new ColorDrawable(Color.BLACK)
                                : null)
                        .error(new ColorDrawable(Color.BLACK))
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(
                                    GlideException error,
                                    Object model,
                                    Target<Drawable> target,
                                    boolean firstResource
                            ) {
                                if (!item.isImage() || resolved.isEmpty()) return false;
                                holder.itemView.post(() -> {
                                    int current = holder.getBindingAdapterPosition();
                                    if (current == RecyclerView.NO_POSITION ||
                                            current >= items.size() ||
                                            !item.url.equals(items.get(current).url) ||
                                            !resolved.equals(resolvedUrls.get(item.url))) return;
                                    resolvedUrls.remove(item.url);
                                    loading.remove(item.url);
                                    failed.add(item.url);
                                    listener.onResolvedImageFailed(current, item);
                                    notifyItemChanged(current);
                                });
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
                        })
                        .into(holder.image);
            }
        }

        holder.itemView.setContentDescription(
                (item.isVideo() ? "Video, " : "Photo, ") + item.title
        );
        View.OnClickListener openItem = v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION || current >= items.size()) return;
            listener.onMediaTap(current, items.get(current));
        };
        holder.itemView.setOnClickListener(openItem);
        // The preview sits above the page root, so give it the same action for videos too.
        // Leaving a recycled preview clickable with a cleared listener swallows the play tap.
        holder.image.setOnClickListener(openItem);
        holder.play.setOnClickListener(openItem);
        holder.failure.setOnClickListener(openItem);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        holder.playerView.setPlayer(null);
        holder.image.setOnClickListener(null);
        holder.play.setOnClickListener(null);
        holder.failure.setOnClickListener(null);
        holder.image.resetZoom();
        Glide.with(holder.image).clear(holder.image);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private GlideUrl withHeaders(String url, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null ? "https://bunkr.cr/" : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(url, headers.build());
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ZoomableImageView image;
        final PlayerView playerView;
        final TextView play;
        final ProgressBar progress;
        final TextView failure;

        Holder(
                View root,
                ZoomableImageView image,
                PlayerView playerView,
                TextView play,
                ProgressBar progress,
                TextView failure
        ) {
            super(root);
            this.image = image;
            this.playerView = playerView;
            this.play = play;
            this.progress = progress;
            this.failure = failure;
        }
    }
}
