package com.webapp.crazyshit;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import java.util.List;

/** Visual browser cards used by Categories and Series. */
public final class NativeCategoryAdapter extends RecyclerView.Adapter<NativeCategoryAdapter.Holder> {
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    public interface Listener {
        void onOpen(NativeContentItem item);
    }

    private final List<NativeContentItem> items = new ArrayList<>();
    private final Listener listener;

    public NativeCategoryAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void replace(List<NativeContentItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
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
    }

    private void loadImage(Holder holder, NativeContentItem item) {
        if (item.imageUrl == null || item.imageUrl.trim().isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(31, 31, 35)));
            return;
        }

        Object source = withSiteHeaders(item.imageUrl, item.url);
        Glide.with(holder.image)
                .load(source)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .centerCrop()
                .placeholder(new ColorDrawable(Color.rgb(31, 31, 35)))
                .error(new ColorDrawable(Color.rgb(31, 31, 35)))
                .into(holder.image);
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