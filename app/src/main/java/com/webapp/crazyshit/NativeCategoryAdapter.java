package com.webapp.crazyshit;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import java.util.List;
import java.util.Map;

/** Visual browser cards used by Categories and Series. */
public final class NativeCategoryAdapter extends RecyclerView.Adapter<NativeCategoryAdapter.Holder> {
    private static final int COMPACT_COPY_HEIGHT_DP = 52;
    private static final int DESCRIPTION_COPY_HEIGHT_DP = 132;

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

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(parent, responsiveHeightDp(parent)));
        params.setMargins(dp(parent, 7), dp(parent, 7), dp(parent, 7), dp(parent, 7));
        card.setLayoutParams(params);

        FrameLayout frame = new FrameLayout(parent.getContext());
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(18, 18, 21));
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(parent.getContext());
        shade.setBackgroundColor(Color.argb(170, 0, 0, 0));
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(
                -1,
                dp(parent, COMPACT_COPY_HEIGHT_DP)
        );
        shadeParams.gravity = Gravity.BOTTOM;
        frame.addView(shade, shadeParams);

        LinearLayout copy = new LinearLayout(parent.getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(parent, 11), dp(parent, 6), dp(parent, 11), dp(parent, 7));
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(
                -1,
                dp(parent, COMPACT_COPY_HEIGHT_DP)
        );
        copyParams.gravity = Gravity.BOTTOM;
        frame.addView(copy, copyParams);

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(14.5f);
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

        return new Holder(card, image, shade, copy, title, description);
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
        loadImage(holder, item);
    }

    private void loadImage(Holder holder, NativeContentItem item) {
        byte[] embedded = EmbeddedBrowseArtwork.get(holder.image.getContext(), item.url);
        if (embedded != null && embedded.length >= 512) {
            Glide.with(holder.image)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .dontAnimate()
                    .centerCrop()
                    .placeholder(new ColorDrawable(Color.rgb(31, 31, 35)))
                    .error(new ColorDrawable(Color.rgb(31, 31, 35)))
                    .into(holder.image);
            return;
        }

        if (item.imageUrl == null || item.imageUrl.trim().isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(31, 31, 35)));
            return;
        }
        Glide.with(holder.image)
                .load(remoteImage(item))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .centerCrop()
                .placeholder(new ColorDrawable(Color.rgb(31, 31, 35)))
                .error(new ColorDrawable(Color.rgb(31, 31, 35)))
                .into(holder.image);
    }

    private void resizeForDescription(Holder holder, boolean hasDescription) {
        int copyHeight = hasDescription ? DESCRIPTION_COPY_HEIGHT_DP : COMPACT_COPY_HEIGHT_DP;
        RecyclerView.LayoutParams cardParams = (RecyclerView.LayoutParams) holder.card.getLayoutParams();
        cardParams.height = dp(
                holder.card,
                hasDescription ? responsiveDescriptionHeightDp(holder.card) : responsiveHeightDp(holder.card)
        );
        holder.card.setLayoutParams(cardParams);

        FrameLayout.LayoutParams shadeParams = (FrameLayout.LayoutParams) holder.shade.getLayoutParams();
        shadeParams.height = dp(holder.card, copyHeight);
        holder.shade.setLayoutParams(shadeParams);

        FrameLayout.LayoutParams copyParams = (FrameLayout.LayoutParams) holder.copy.getLayoutParams();
        copyParams.height = dp(holder.card, copyHeight);
        holder.copy.setLayoutParams(copyParams);
    }

    private GlideUrl remoteImage(NativeContentItem item) {
        String referer = item.url == null || item.url.isEmpty() ? EfuktRepository.BASE : item.url;
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", EfuktRepository.USER_AGENT)
                .addHeader("Referer", referer)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(item.imageUrl);
            if ((cookies == null || cookies.isEmpty()) && !referer.isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(referer);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(item.imageUrl, headers.build());
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

        Holder(
                MaterialCardView card,
                ImageView image,
                View shade,
                LinearLayout copy,
                TextView title,
                TextView description
        ) {
            super(card);
            this.card = card;
            this.image = image;
            this.shade = shade;
            this.copy = copy;
            this.title = title;
            this.description = description;
        }
    }
}
