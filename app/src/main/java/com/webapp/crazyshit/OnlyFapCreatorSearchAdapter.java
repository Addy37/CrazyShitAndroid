package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Compact creator cards used by contextual OnlyFap search.
 *
 * The layout mirrors OnlyHaven's dense creator-result treatment while keeping
 * ZEROCHILL's black/electric-blue visual language.
 */
final class OnlyFapCreatorSearchAdapter
        extends RecyclerView.Adapter<OnlyFapCreatorSearchAdapter.Holder> {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final Consumer<NativeContentItem> open;
    private List<NativeContentItem> items = new ArrayList<>();

    OnlyFapCreatorSearchAdapter(Consumer<NativeContentItem> open) {
        this.open = open;
        setHasStableIds(true);
    }

    void replace(List<NativeContentItem> next) {
        List<NativeContentItem> replacement =
                next == null ? new ArrayList<>() : new ArrayList<>(next);
        List<NativeContentItem> old = items;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return replacement.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                NativeContentItem a = old.get(oldPosition);
                NativeContentItem b = replacement.get(newPosition);
                return creatorKey(a).equals(creatorKey(b));
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                NativeContentItem a = old.get(oldPosition);
                NativeContentItem b = replacement.get(newPosition);
                return a.title.equals(b.title) &&
                        a.imageUrl.equals(b.imageUrl) &&
                        a.description.equals(b.description) &&
                        a.views.equals(b.views);
            }
        });
        items = replacement;
        diff.dispatchUpdatesTo(this);
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public long getItemId(int position) {
        return creatorKey(items.get(position)).hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();

        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(Color.rgb(16, 19, 23));
        card.setRadius(dp(context, 16));
        card.setStrokeWidth(dp(context, 1));
        card.setStrokeColor(Color.rgb(41, 62, 72));
        card.setClickable(true);
        card.setFocusable(true);

        RecyclerView.LayoutParams cardParams =
                new RecyclerView.LayoutParams(-1, dp(context, 116));
        cardParams.setMargins(
                dp(context, 10),
                dp(context, 5),
                dp(context, 10),
                dp(context, 5)
        );
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                dp(context, 10),
                dp(context, 9),
                dp(context, 9),
                dp(context, 9)
        );
        card.addView(row, new MaterialCardView.LayoutParams(-1, -1));

        ImageView avatar = new ImageView(context);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackgroundColor(Color.rgb(28, 33, 38));
        avatar.setContentDescription(null);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(context, 54), dp(context, 54)));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(context, 11), 0, dp(context, 8), 0);
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView badge = new TextView(context);
        badge.setText("OnlyFap");
        badge.setTextColor(Color.BLACK);
        badge.setTextSize(10.5f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(BrowseUi.rounded(context, UiPalette.PRIMARY, 8));
        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(-2, dp(context, 20));
        badgeParams.bottomMargin = dp(context, 4);
        copy.addView(badge, badgeParams);

        TextView name = new TextView(context);
        name.setTextColor(Color.WHITE);
        name.setTextSize(15.5f);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView handle = new TextView(context);
        handle.setTextColor(Color.rgb(161, 176, 184));
        handle.setTextSize(11.5f);
        handle.setSingleLine(true);
        handle.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(handle, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = new TextView(context);
        meta.setTextColor(Color.rgb(192, 199, 204));
        meta.setTextSize(11f);
        meta.setMaxLines(2);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(context, 3);
        copy.addView(meta, metaParams);

        FrameLayout previewFrame = new FrameLayout(context);
        previewFrame.setBackground(BrowseUi.rounded(context, Color.rgb(24, 29, 34), 12));
        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(dp(context, 112), -1);
        row.addView(previewFrame, previewParams);

        ImageView preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setContentDescription(null);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        return new Holder(card, avatar, preview, name, handle, meta);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        holder.name.setText(item.title);
        holder.handle.setText(creatorHandle(item));
        holder.meta.setText(metaText(item));
        holder.card.setContentDescription(item.title + ", OnlyFap creator");
        holder.card.setOnClickListener(v -> open.accept(item));

        load(holder.avatar, item, true);
        load(holder.preview, item, false);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.avatar).clear(holder.avatar);
        Glide.with(holder.preview).clear(holder.preview);
        holder.card.setOnClickListener(null);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void load(ImageView target, NativeContentItem item, boolean circle) {
        Glide.with(target).clear(target);
        target.setImageResource(R.drawable.ic_more_account);
        if (item.imageUrl == null || item.imageUrl.trim().isEmpty()) return;

        String referer = item.uploader != null &&
                (item.uploader.startsWith("https://") || item.uploader.startsWith("http://"))
                ? item.uploader
                : item.url;
        GlideUrl source = new GlideUrl(
                item.imageUrl,
                new LazyHeaders.Builder()
                        .addHeader("Referer", referer == null ? "" : referer)
                        .addHeader("User-Agent", USER_AGENT)
                        .build()
        );

        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                Glide.with(target)
                        .load(source)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate()
                        .placeholder(new ColorDrawable(Color.rgb(28, 33, 38)))
                        .error(new ColorDrawable(Color.rgb(28, 33, 38)));
        if (circle) request.circleCrop();
        else request.centerCrop();
        request.into(target);
    }

    private String creatorHandle(NativeContentItem item) {
        if (OnlyHavenRepository.isOnlyHavenUrl(item.url)) {
            try {
                Uri uri = Uri.parse(item.url);
                List<String> parts = uri.getPathSegments();
                if (!parts.isEmpty()) {
                    String id = parts.get(parts.size() - 1);
                    if (!id.trim().isEmpty()) return "@" + id;
                }
            } catch (Exception ignored) {
            }
        }
        String query = item.searchQuery == null ? "" : item.searchQuery.trim();
        if (!query.isEmpty() && !query.equalsIgnoreCase(item.title)) {
            return "@" + query.replace(" ", "");
        }
        return "Creator";
    }

    private String metaText(NativeContentItem item) {
        String description = item.description == null ? "" : item.description.trim();
        if (!description.isEmpty()) return description;
        String views = item.views == null ? "" : item.views.trim();
        if (!views.isEmpty()) return views + " posts";
        return "Open unified gallery";
    }

    private String creatorKey(NativeContentItem item) {
        if (item == null) return "";
        String title = item.title == null ? "" : item.title.trim().toLowerCase(Locale.US);
        String url = item.url == null ? "" : item.url.trim().toLowerCase(Locale.US);
        return title + "\n" + url;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView avatar;
        final ImageView preview;
        final TextView name;
        final TextView handle;
        final TextView meta;

        Holder(
                MaterialCardView card,
                ImageView avatar,
                ImageView preview,
                TextView name,
                TextView handle,
                TextView meta
        ) {
            super(card);
            this.card = card;
            this.avatar = avatar;
            this.preview = preview;
            this.name = name;
            this.handle = handle;
            this.meta = meta;
        }
    }
}
