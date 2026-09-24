package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
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
        setOrientation(VERTICAL);
        setBackgroundColor(Color.BLACK);

        NativeContentItem found = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                title,
                url,
                "",
                "",
                "",
                "",
                "",
                query
        );
        for (NativeContentItem item : CreatorCatalog.all(context)) {
            if (CreatorFavoriteStore.key(item).equals(CreatorFavoriteStore.key(found))) {
                found = item;
                break;
            }
        }
        creator = found;

        FrameLayout hero = new FrameLayout(context);
        hero.setBackgroundColor(Color.rgb(13, 15, 18));

        ImageView banner = new ImageView(context);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setImageResource(R.drawable.ic_more_account);
        banner.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(banner, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(context);
        shade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable heroShade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(38, 0, 0, 0),
                        Color.argb(78, 0, 0, 0),
                        Color.argb(238, 0, 0, 0)
                }
        );
        shade.setBackground(heroShade);
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout identity = new LinearLayout(context);
        identity.setOrientation(HORIZONTAL);
        identity.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        identity.setPadding(dp(16), dp(16), dp(16), dp(14));

        ImageView avatar = new ImageView(context);
        avatar.setBackground(BrowseUi.rounded(context, BrowseUi.SURFACE, 30));
        avatar.setClipToOutline(true);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setImageResource(R.drawable.ic_more_account);
        avatar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        identity.addView(avatar, new LayoutParams(dp(60), dp(60)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        labels.setGravity(Gravity.BOTTOM);
        labels.setPadding(dp(12), 0, dp(10), dp(2));

        TextView name = BrowseUi.text(context, title, 28, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name, new LayoutParams(-1, -2));

        TextView subtitle = BrowseUi.text(context, "OnlyFap creator", 12, Color.rgb(205, 209, 216));
        subtitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(subtitle, new LayoutParams(-1, -2));

        identity.addView(labels, new LayoutParams(0, -2, 1f));

        favorite = BrowseUi.action(context, "", "Favorite creator", v -> {
            CreatorFavoriteStore.toggle(context, creator);
            refresh();
        });
        favorite.setTextSize(13);
        favorite.setTag("creator_favorite");
        identity.addView(favorite, new LayoutParams(-2, dp(46)));

        FrameLayout.LayoutParams identityParams = new FrameLayout.LayoutParams(-1, -2);
        identityParams.gravity = Gravity.BOTTOM;
        hero.addView(identity, identityParams);

        addView(hero, new LayoutParams(-1, dp(184)));

        if (!creator.imageUrl.isEmpty()) {
            GlideUrl image = new GlideUrl(
                    creator.imageUrl,
                    new LazyHeaders.Builder()
                            .addHeader("Referer", creator.url)
                            .build()
            );
            Glide.with(banner)
                    .load(image)
                    .centerCrop()
                    .placeholder(R.drawable.ic_more_account)
                    .error(R.drawable.ic_more_account)
                    .into(banner);
            Glide.with(avatar)
                    .load(image)
                    .circleCrop()
                    .placeholder(R.drawable.ic_more_account)
                    .error(R.drawable.ic_more_account)
                    .into(avatar);
        }

        refresh();
    }

    void refresh() {
        boolean saved = CreatorFavoriteStore.contains(getContext(), creator);
        favorite.setText(saved ? "★  Favorited" : "☆  Favorite");
        favorite.setTextColor(saved ? UiPalette.ON_PRIMARY : Color.WHITE);
        favorite.setBackground(BrowseUi.rounded(
                getContext(),
                saved ? UiPalette.PRIMARY : Color.argb(190, 20, 24, 29),
                23
        ));
        favorite.setContentDescription(
                (saved ? "Unfavorite " : "Favorite ") + creator.title
        );
    }

    private int dp(int value) {
        return BrowseUi.dp(getContext(), value);
    }
}
