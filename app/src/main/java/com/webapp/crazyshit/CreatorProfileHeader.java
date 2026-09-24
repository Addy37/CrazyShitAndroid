package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creator identity and favorite action shared by the gallery header. */
final class CreatorProfileHeader extends LinearLayout {
    private final NativeContentItem creator;
    private final TextView favorite;
    private final ImageView banner;
    private final FrameLayout hero;
    private final ExecutorService heroIo = Executors.newSingleThreadExecutor();
    private boolean detached;

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

        hero = new FrameLayout(context);
        hero.setBackgroundColor(Color.rgb(13, 15, 18));

        banner = new ImageView(context);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setImageDrawable(new ColorDrawable(Color.rgb(13, 15, 18)));
        banner.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(banner, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(context);
        shade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable heroShade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(20, 0, 0, 0),
                        Color.argb(72, 0, 0, 0),
                        Color.argb(242, 0, 0, 0)
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
            Glide.with(avatar)
                    .load(withReferer(creator.imageUrl, creator.url))
                    .circleCrop()
                    .placeholder(R.drawable.ic_more_account)
                    .error(R.drawable.ic_more_account)
                    .into(avatar);
        }

        loadOnlyHavenHero(query);
        refresh();
    }

    private void loadOnlyHavenHero(String query) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.length() < 2) return;
        Context appContext = getContext().getApplicationContext();
        heroIo.execute(() -> {
            try {
                OnlyHavenRepository repository = new OnlyHavenRepository();
                List<OnlyHavenRepository.Creator> matches =
                        repository.searchCreators(appContext, cleanQuery, 6);
                if (matches == null) return;
                for (OnlyHavenRepository.Creator match : matches) {
                    String heroUrl = repository.creatorHeaderUrl(match);
                    if (heroUrl.isEmpty()) continue;
                    post(() -> showHero(heroUrl, match.url));
                    return;
                }
            } catch (IOException ignored) {
            }
        });
    }

    private void showHero(String heroUrl, String referer) {
        if (detached || heroUrl == null || heroUrl.isEmpty()) return;
        Glide.with(banner)
                .load(withReferer(heroUrl, referer))
                .centerCrop()
                .placeholder(new ColorDrawable(Color.rgb(13, 15, 18)))
                .error(new ColorDrawable(Color.rgb(13, 15, 18)))
                .into(banner);
    }

    private GlideUrl withReferer(String imageUrl, String referer) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder();
        if (referer != null && !referer.trim().isEmpty()) {
            headers.addHeader("Referer", referer);
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    int expandedHeightPx() {
        return dp(184);
    }

    void setCollapseOffsetPx(int offsetPx) {
        int expanded = expandedHeightPx();
        int safeOffset = Math.max(0, Math.min(expanded, offsetPx));
        int nextHeight = expanded - safeOffset;

        android.view.ViewGroup.LayoutParams params = hero.getLayoutParams();
        if (params != null && params.height != nextHeight) {
            params.height = nextHeight;
            hero.setLayoutParams(params);
        }

        float progress = expanded == 0 ? 1f : (float) safeOffset / expanded;
        hero.setAlpha(1f - Math.min(1f, progress * 1.08f));
        hero.setTranslationY(-dp(10) * progress);
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

    @Override
    protected void onDetachedFromWindow() {
        detached = true;
        heroIo.shutdownNow();
        Glide.with(banner).clear(banner);
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return BrowseUi.dp(getContext(), value);
    }
}
