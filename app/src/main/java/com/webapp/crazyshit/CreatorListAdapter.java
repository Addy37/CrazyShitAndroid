package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

final class CreatorListAdapter extends RecyclerView.Adapter<CreatorListAdapter.Holder> {
    private final Context context;
    private final Consumer<NativeContentItem> open;
    private final Runnable favoriteChanged;
    private List<NativeContentItem> items = new ArrayList<>();
    private Set<String> favorites;

    CreatorListAdapter(Context context, Consumer<NativeContentItem> open, Runnable favoriteChanged) {
        this.context = context;
        this.open = open;
        this.favoriteChanged = favoriteChanged;
        favorites = CreatorFavoriteStore.names(context);
    }

    void replace(List<NativeContentItem> next) {
        List<NativeContentItem> old = items;
        Set<String> oldFavorites = favorites;
        Set<String> nextFavorites = CreatorFavoriteStore.names(context);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return old.size(); }
            public int getNewListSize() { return next.size(); }
            public boolean areItemsTheSame(int a, int b) {
                return CreatorCatalog.key(old.get(a)).equals(CreatorCatalog.key(next.get(b)));
            }
            public boolean areContentsTheSame(int a, int b) {
                NativeContentItem x = old.get(a), y = next.get(b);
                return x.title.equals(y.title) && x.imageUrl.equals(y.imageUrl)
                        && oldFavorites.contains(CreatorFavoriteStore.key(x))
                        == nextFavorites.contains(CreatorFavoriteStore.key(y));
            }
        });
        items = new ArrayList<>(next);
        favorites = nextFavorites;
        diff.dispatchUpdatesTo(this);
    }

    @Override public int getItemCount() { return items.size(); }

    @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(BrowseUi.rounded(context, BrowseUi.SURFACE, 16));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
        params.setMargins(dp(12), dp(3), dp(12), dp(3));
        row.setLayoutParams(params);
        ImageView avatar = new ImageView(context);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(BrowseUi.rounded(context, BrowseUi.SURFACE, 24));
        avatar.setClipToOutline(true);
        avatar.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout labels = new LinearLayout(context);
        labels.setPadding(dp(12), 0, dp(4), 0);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = BrowseUi.text(context, "", 16, Color.WHITE);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView subtitle = BrowseUi.text(context, "Open gallery", 12, BrowseUi.MUTED);
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView star = BrowseUi.action(context, "☆", "Favorite creator", v -> { });
        star.setTextSize(25);
        row.addView(star, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row.setFocusable(true);
        return new Holder(row, avatar, name, subtitle, star);
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
        NativeContentItem item = items.get(position);
        boolean favorite = favorites.contains(CreatorFavoriteStore.key(item));
        holder.name.setText(item.title);
        String source = item.description == null ? "" : item.description.trim();
        holder.subtitle.setText((favorite ? "Favorite" : source).isEmpty()
                ? "Open gallery"
                : (favorite ? "Favorite" : source) + " · Open gallery");
        holder.star.setText(favorite ? "★" : "☆");
        holder.star.setTextColor(favorite ? UiPalette.PRIMARY : BrowseUi.MUTED);
        holder.star.setBackgroundColor(Color.TRANSPARENT);
        holder.star.setContentDescription((favorite ? "Unfavorite " : "Favorite ") + item.title);
        holder.itemView.setOnClickListener(v -> open.accept(item));
        CreatorGalleryPreloader.warm(
                holder.itemView.getContext(),
                item,
                position < 3
                        ? CreatorGalleryPreloader.PRIORITY_HIGH
                        : CreatorGalleryPreloader.PRIORITY_NORMAL
        );
        android.view.View.OnClickListener toggle = v -> {
            CreatorFavoriteStore.toggle(context, item);
            favoriteChanged.run();
        };
        holder.star.setOnClickListener(toggle);
        holder.itemView.setOnLongClickListener(v -> { toggle.onClick(v); return true; });
        Glide.with(holder.avatar).clear(holder.avatar);
        holder.avatar.setImageResource(R.drawable.ic_more_account);
        if (!item.imageUrl.isEmpty()) {
            GlideUrl url = new GlideUrl(item.imageUrl, new LazyHeaders.Builder()
                    .addHeader("Referer", item.uploader.isEmpty() ? item.url : item.uploader)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139.0 Mobile Safari/537.36")
                    .build());
            Glide.with(holder.avatar).load(url).circleCrop().dontAnimate()
                    .placeholder(R.drawable.ic_more_account).error(R.drawable.ic_more_account)
                    .into(holder.avatar);
        }
    }

    @Override public void onViewRecycled(Holder holder) {
        Glide.with(holder.avatar).clear(holder.avatar);
        super.onViewRecycled(holder);
    }

    private int dp(int value) { return BrowseUi.dp(context, value); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView name, subtitle, star;
        Holder(LinearLayout row, ImageView avatar, TextView name, TextView subtitle, TextView star) {
            super(row); this.avatar = avatar; this.name = name; this.subtitle = subtitle; this.star = star;
        }
    }
}
