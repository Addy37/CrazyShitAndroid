package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

/** Creator identity and favorite action shared by the gallery header. */
final class CreatorProfileHeader extends LinearLayout {
    private final NativeContentItem creator;
    private final TextView favorite;

    CreatorProfileHeader(Context context, String title, String query, String url) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(16), dp(12), dp(16), dp(16));
        setBackgroundColor(Color.BLACK);
        NativeContentItem found = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                title, url, "", "", "", "", "", query);
        for (NativeContentItem item : CreatorCatalog.all(context)) {
            if (CreatorFavoriteStore.key(item).equals(CreatorFavoriteStore.key(found))) { found = item; break; }
        }
        creator = found;
        ImageView avatar = new ImageView(context);
        avatar.setBackground(BrowseUi.rounded(context, BrowseUi.SURFACE, 28));
        avatar.setClipToOutline(true);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setImageResource(R.drawable.ic_more_account);
        avatar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(avatar, new LayoutParams(dp(56), dp(56)));
        if (!creator.imageUrl.isEmpty()) Glide.with(avatar).load(new GlideUrl(creator.imageUrl,
                new LazyHeaders.Builder().addHeader("Referer", creator.url).build()))
                .circleCrop().placeholder(R.drawable.ic_more_account).error(R.drawable.ic_more_account).into(avatar);
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView name = BrowseUi.text(context, title, 20, Color.WHITE);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name);
        labels.addView(BrowseUi.text(context, "Creator gallery", 12, BrowseUi.MUTED));
        addView(labels, new LayoutParams(0, -2, 1));
        favorite = BrowseUi.action(context, "", "Favorite creator", v -> {
            CreatorFavoriteStore.toggle(context, creator);
            refresh();
        });
        favorite.setTextSize(13);
        favorite.setTag("creator_favorite");
        addView(favorite, new LayoutParams(-2, dp(48)));
        refresh();
    }

    void refresh() {
        boolean saved = CreatorFavoriteStore.contains(getContext(), creator);
        favorite.setText(saved ? "★  Favorited" : "☆  Favorite");
        favorite.setTextColor(saved ? UiPalette.ON_PRIMARY : UiPalette.PRIMARY);
        favorite.setBackground(BrowseUi.rounded(getContext(), saved ? UiPalette.PRIMARY : BrowseUi.SURFACE, 24));
        favorite.setContentDescription((saved ? "Unfavorite " : "Favorite ") + creator.title);
    }
    private int dp(int value) { return BrowseUi.dp(getContext(), value); }
}
