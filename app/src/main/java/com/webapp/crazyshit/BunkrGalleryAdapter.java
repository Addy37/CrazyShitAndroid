package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.ArrayList;
import java.util.List;

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
    private final Context context;
    private final Listener listener;

    BunkrGalleryAdapter(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
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
        SquareFrameLayout tile = new SquareFrameLayout(parent.getContext());
        tile.setBackgroundColor(Color.rgb(20, 20, 23));
        RecyclerView.LayoutParams tileParams = new RecyclerView.LayoutParams(-1, -2);
        int gap = dp(parent, 1);
        tileParams.setMargins(gap, gap, gap, gap);
        tile.setLayoutParams(tileParams);

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(20, 20, 23));
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout play = new FrameLayout(parent.getContext());
        GradientDrawable playBackground = new GradientDrawable();
        playBackground.setShape(GradientDrawable.OVAL);
        playBackground.setColor(Color.argb(185, 0, 0, 0));
        play.setBackground(playBackground);
        play.setElevation(dp(parent, 5));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                dp(parent, 42),
                dp(parent, 42)
        );
        playParams.gravity = Gravity.CENTER;
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
        holder.play.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        holder.itemView.setContentDescription(
                (item.isVideo() ? "Video, " : "Photo, ") + item.title
        );

        if (item.imageUrl == null || item.imageUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 23)));
        } else {
            Glide.with(holder.image)
                    .load(withHeaders(item.imageUrl, item.url))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(20, 20, 23)))
                    .error(new ColorDrawable(Color.rgb(20, 20, 23)))
                    .into(holder.image);
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
            Glide.with(context)
                    .load(withHeaders(item.imageUrl, item.url))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .preload(360, 360);
        }
    }

    private GlideUrl withHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null ? "https://bunkr.cr/" : pageUrl)
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

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final View play;

        Holder(View itemView, ImageView image, View play) {
            super(itemView);
            this.image = image;
            this.play = play;
        }
    }

    private static final class SquareFrameLayout extends FrameLayout {
        SquareFrameLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
            int width = getMeasuredWidth();
            setMeasuredDimension(width, width);
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
