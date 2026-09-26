package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cinematic creator-discovery landing page for OnlyFap.
 *
 * Creator cards always open the existing unified creator gallery. The hero prefers
 * high-resolution creator gallery artwork, with wide headers and avatars as fallbacks.
 */
final class OnlyFapHubView extends FrameLayout {
    interface Listener {
        void onOpenCreator(NativeContentItem creator);
        void onSearch();
        void onMore();
        void onViewAllFavorites();
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final long HERO_ROTATION_MS = 13_000L;
    private static final int HERO_MAX_ITEMS = 8;
    private static final int HERO_RESOLVE_LIMIT = 16;

    private final Listener listener;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final LinearLayout body;
    private final MaterialCardView heroCard;
    private final ImageView heroImage;
    private final TextView heroTitle;
    private final TextView heroHint;
    private final TextView heroAction;
    private final TextView heroDots;
    private final TextView loadingLabel;
    private final LinearLayout favoritesSection;
    private final LinearLayout favoritesRail;
    private final CreatorShelf trendingShelf;
    private final CreatorShelf newShelf;
    private final CreatorShelf hotShelf;
    private final CreatorShelf popularShelf;

    private final Handler heroHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService heroIo = Executors.newFixedThreadPool(3);
    private final Set<String> requestedHeroCreators = new HashSet<>();
    private final Set<String> rejectedHeroUrls = new HashSet<>();
    private final List<HeroCandidate> heroItems = new ArrayList<>();

    private List<NativeContentItem> trendingItems = Collections.emptyList();
    private List<NativeContentItem> newItems = Collections.emptyList();
    private List<NativeContentItem> hotItems = Collections.emptyList();
    private List<NativeContentItem> popularItems = Collections.emptyList();
    private boolean shelvesFinished;
    private HeroCandidate heroItem;
    private int heroIndex = -1;
    private boolean active;
    private boolean closed;
    private volatile int heroGeneration;
    private Bundle pendingRestoreState;

    OnlyFapHubView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(ZeroChillUi.background(context));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(36));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        heroCard = new MaterialCardView(context);
        ZeroChillUi.styleMediaCard(heroCard, R.dimen.zc_radius_large);
        heroCard.setRadius(0f);
        heroCard.setStrokeWidth(0);
        heroCard.setCardElevation(0f);
        heroCard.setClickable(true);
        heroCard.setFocusable(true);
        heroCard.setContentDescription("OnlyFap featured creator");
        ZeroChillMotion.installPressFeedback(heroCard);

        FrameLayout heroFrame = new FrameLayout(context);
        heroCard.addView(heroFrame, new MaterialCardView.LayoutParams(-1, -1));

        heroImage = new ImageView(context);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackground(new ColorDrawable(Color.rgb(13, 16, 19)));
        heroImage.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroFrame.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));

        View heroShade = new View(context);
        heroShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(6, 0, 0, 0),
                        Color.argb(24, 0, 0, 0),
                        Color.argb(92, 0, 0, 0),
                        Color.argb(218, 0, 0, 0),
                        Color.BLACK
                }
        ));
        heroShade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroFrame.addView(heroShade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heroBar = new LinearLayout(context);
        heroBar.setOrientation(LinearLayout.HORIZONTAL);
        heroBar.setGravity(Gravity.CENTER_VERTICAL);
        heroBar.setPadding(dp(20), dp(5), dp(12), 0);

        TextView heroBrand = text("OnlyFap", 28f, Color.WHITE);
        heroBrand.setTypeface(null, android.graphics.Typeface.BOLD);
        heroBrand.setSingleLine(true);
        heroBrand.setShadowLayer(dp(8), 0f, dp(2), Color.argb(150, 0, 0, 0));
        heroBar.addView(heroBrand, new LinearLayout.LayoutParams(0, dp(58), 1f));

        ImageView heroSearch = new ImageView(context);
        heroSearch.setImageResource(R.drawable.ic_nav_search);
        heroSearch.setPadding(dp(12), dp(12), dp(12), dp(12));
        heroSearch.setColorFilter(ZeroChillUi.color(context, R.color.zc_cyan));
        heroSearch.setBackground(BrowseUi.rounded(
                context,
                Color.argb(165, 7, 10, 13),
                24
        ));
        heroSearch.setClickable(true);
        heroSearch.setFocusable(true);
        heroSearch.setContentDescription("Search OnlyFap creators");
        heroSearch.setOnClickListener(v -> listener.onSearch());
        ZeroChillMotion.installPressFeedback(heroSearch);
        heroBar.addView(heroSearch, new LinearLayout.LayoutParams(dp(48), dp(48)));

        ImageView heroMore = new ImageView(context);
        heroMore.setImageResource(R.drawable.ic_nav_more);
        heroMore.setPadding(dp(11), dp(11), dp(11), dp(11));
        heroMore.setColorFilter(Color.WHITE);
        heroMore.setClickable(true);
        heroMore.setFocusable(true);
        heroMore.setContentDescription("OnlyFap More");
        heroMore.setOnClickListener(v -> listener.onMore());
        ZeroChillMotion.installPressFeedback(heroMore);
        LinearLayout.LayoutParams heroMoreParams =
                new LinearLayout.LayoutParams(dp(44), dp(48));
        heroMoreParams.setMarginStart(dp(4));
        heroBar.addView(heroMore, heroMoreParams);

        heroFrame.addView(heroBar, new FrameLayout.LayoutParams(
                -1,
                dp(64),
                Gravity.TOP
        ));

        heroDots = text("", 13f, Color.WHITE);
        heroDots.setGravity(Gravity.CENTER);
        heroDots.setVisibility(View.GONE);
        heroDots.setContentDescription("Featured creator position");

        LinearLayout heroCopy = new LinearLayout(context);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        heroCopy.setPadding(dp(24), dp(28), dp(24), dp(20));
        heroFrame.addView(heroCopy,
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        heroTitle = text("Discover creators", 29f, Color.WHITE);
        heroTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        heroTitle.setGravity(Gravity.CENTER);
        heroTitle.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        heroTitle.setShadowLayer(dp(8), 0f, dp(2), Color.argb(160, 0, 0, 0));
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams heroTitleParams = new LinearLayout.LayoutParams(-1, -2);
        heroTitleParams.topMargin = dp(4);
        heroCopy.addView(heroTitle, heroTitleParams);

        heroHint = text(
                "Featured galleries appear here as creator artwork becomes available.",
                13f,
                ZeroChillUi.color(context, R.color.zc_text_secondary)
        );
        heroHint.setGravity(Gravity.CENTER);
        heroHint.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        heroHint.setMaxLines(2);
        heroHint.setLineSpacing(0f, 1.05f);
        LinearLayout.LayoutParams heroHintParams = new LinearLayout.LayoutParams(-1, -2);
        heroHintParams.topMargin = dp(5);
        heroCopy.addView(heroHint, heroHintParams);

        heroAction = text("VIEW GALLERY", 13f, Color.WHITE);
        heroAction.setTypeface(null, android.graphics.Typeface.BOLD);
        heroAction.setGravity(Gravity.CENTER);
        heroAction.setVisibility(View.GONE);
        GradientDrawable actionBackground = new GradientDrawable();
        actionBackground.setColor(Color.argb(205, 15, 18, 22));
        actionBackground.setCornerRadius(dp(20));
        actionBackground.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        heroAction.setBackground(actionBackground);
        heroAction.setContentDescription("Open featured creator gallery");
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(136), dp(40));
        actionParams.topMargin = dp(14);
        actionParams.gravity = Gravity.CENTER_HORIZONTAL;
        heroCopy.addView(heroAction, actionParams);

        LinearLayout.LayoutParams heroDotsParams =
                new LinearLayout.LayoutParams(-2, dp(28));
        heroDotsParams.topMargin = dp(8);
        heroDotsParams.gravity = Gravity.CENTER_HORIZONTAL;
        heroCopy.addView(heroDots, heroDotsParams);

        HorizontalSwipeFrameLayout heroSwipe = new HorizontalSwipeFrameLayout(context);
        heroSwipe.setListener(new HorizontalSwipeFrameLayout.Listener() {
            @Override public void onSwipeLeft() {
                if (heroItems.size() > 1) showHero(heroIndex + 1, true);
            }

            @Override public void onSwipeRight() {
                if (heroItems.size() > 1) showHero(heroIndex - 1, true);
            }
        });
        heroSwipe.addView(heroCard, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(540));
        content.addView(heroSwipe, heroParams);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), 0);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));

        favoritesSection = new LinearLayout(context);
        favoritesSection.setOrientation(LinearLayout.VERTICAL);
        favoritesSection.setVisibility(View.GONE);

        LinearLayout favoriteHeading = new LinearLayout(context);
        favoriteHeading.setOrientation(LinearLayout.HORIZONTAL);
        favoriteHeading.setGravity(Gravity.CENTER_VERTICAL);

        TextView favoriteTitle = text("Favorite Creators", 19f, Color.WHITE);
        favoriteTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        favoriteHeading.addView(favoriteTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView favoriteAll = text("View all", 12.5f, UiPalette.PRIMARY);
        favoriteAll.setGravity(Gravity.CENTER);
        favoriteAll.setPadding(dp(10), dp(6), dp(4), dp(6));
        favoriteAll.setClickable(true);
        favoriteAll.setFocusable(true);
        favoriteAll.setContentDescription("View all favorite creators");
        favoriteAll.setOnClickListener(v -> listener.onViewAllFavorites());
        favoriteHeading.addView(favoriteAll);
        favoritesSection.addView(favoriteHeading);

        TextView favoriteHint = text(
                "Your saved creators",
                11.5f,
                ZeroChillUi.color(context, R.color.zc_text_muted)
        );
        LinearLayout.LayoutParams favoriteHintParams = new LinearLayout.LayoutParams(-1, -2);
        favoriteHintParams.topMargin = dp(1);
        favoritesSection.addView(favoriteHint, favoriteHintParams);

        HorizontalScrollView favoriteScroll = new HorizontalScrollView(context);
        favoriteScroll.setHorizontalScrollBarEnabled(false);
        favoriteScroll.setClipToPadding(false);
        favoriteScroll.setFillViewport(false);
        favoriteScroll.setContentDescription("Favorite creators shelf");

        favoritesRail = new LinearLayout(context);
        favoritesRail.setOrientation(LinearLayout.HORIZONTAL);
        favoritesRail.setGravity(Gravity.TOP);
        favoritesRail.setPadding(0, dp(10), dp(6), 0);
        favoriteScroll.addView(favoritesRail,
                new HorizontalScrollView.LayoutParams(-2, -1));
        favoritesSection.addView(favoriteScroll,
                new LinearLayout.LayoutParams(-1, dp(114)));

        LinearLayout.LayoutParams favoriteSectionParams =
                new LinearLayout.LayoutParams(-1, -2);
        favoriteSectionParams.setMargins(0, 0, 0, dp(12));
        body.addView(favoritesSection, favoriteSectionParams);

        loadingLabel = text(
                "Loading creators…",
                12f,
                ZeroChillUi.color(context, R.color.zc_text_muted)
        );
        loadingLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.setMargins(0, 0, 0, dp(10));
        body.addView(loadingLabel, loadingParams);

        trendingShelf = addCreatorShelf(
                "Trending",
                "Popular on OnlyHaven",
                "Trending creators shelf"
        );
        newShelf = addCreatorShelf(
                "New Creators",
                "Recently added creators",
                "New creators shelf"
        );
        hotShelf = addCreatorShelf(
                "Hot",
                "Creators getting attention right now",
                "Hot creators shelf"
        );
        popularShelf = addCreatorShelf(
                "Popular",
                "Popular across OnlyFap sources",
                "Popular creators shelf"
        );

        heroCard.setOnClickListener(v -> openHero());
        heroAction.setOnClickListener(v -> openHero());

        refreshFavorites();
    }

    void clear() {
        heroGeneration++;
        trendingItems = Collections.emptyList();
        newItems = Collections.emptyList();
        hotItems = Collections.emptyList();
        popularItems = Collections.emptyList();
        shelvesFinished = false;
        trendingShelf.adapter.replace(Collections.emptyList());
        newShelf.adapter.replace(Collections.emptyList());
        hotShelf.adapter.replace(Collections.emptyList());
        popularShelf.adapter.replace(Collections.emptyList());
        trendingShelf.container.setVisibility(View.GONE);
        newShelf.container.setVisibility(View.GONE);
        hotShelf.container.setVisibility(View.GONE);
        popularShelf.container.setVisibility(View.GONE);
        heroHandler.removeCallbacksAndMessages(null);
        Glide.with(heroImage).clear(heroImage);
        heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
        heroItems.clear();
        requestedHeroCreators.clear();
        rejectedHeroUrls.clear();
        heroItem = null;
        heroIndex = -1;
        heroDots.setVisibility(View.GONE);
        heroTitle.setText("Discover creators");
        heroHint.setText("Featured galleries appear here as creator artwork becomes available.");
        heroAction.setVisibility(View.GONE);
        loadingLabel.setText("Loading creators…");
        loadingLabel.setVisibility(View.VISIBLE);
        refreshFavorites();
    }

    void setTrending(List<NativeContentItem> items) {
        trendingItems = safe(items);
        trendingShelf.adapter.replace(trendingItems);
        trendingShelf.container.setVisibility(trendingItems.isEmpty() ? View.GONE : View.VISIBLE);
        refreshHeroCandidates();
    }

    void setNewCreators(List<NativeContentItem> items) {
        newItems = safe(items);
        newShelf.adapter.replace(newItems);
        newShelf.container.setVisibility(newItems.isEmpty() ? View.GONE : View.VISIBLE);
        refreshHeroCandidates();
    }

    void setHot(List<NativeContentItem> items) {
        hotItems = safe(items);
        hotShelf.adapter.replace(hotItems);
        hotShelf.container.setVisibility(hotItems.isEmpty() ? View.GONE : View.VISIBLE);
        refreshHeroCandidates();
    }

    void setPopular(List<NativeContentItem> items) {
        popularItems = safe(items);
        popularShelf.adapter.replace(popularItems);
        popularShelf.container.setVisibility(popularItems.isEmpty() ? View.GONE : View.VISIBLE);
        refreshHeroCandidates();
    }

    void refreshFavorites() {
        List<NativeContentItem> favorites =
                CreatorCatalog.matching(getContext(), "", true, 12);
        favoritesRail.removeAllViews();
        int count = Math.min(10, favorites.size());
        for (int i = 0; i < count; i++) {
            favoritesRail.addView(
                    favoriteCreatorCard(favorites.get(i)),
                    new LinearLayout.LayoutParams(dp(94), dp(108))
            );
        }
        favoritesSection.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        refreshHeroCandidates();
        trendingShelf.adapter.refreshFavorites();
        newShelf.adapter.refreshFavorites();
        hotShelf.adapter.refreshFavorites();
        popularShelf.adapter.refreshFavorites();
    }

    void finishLoading() {
        shelvesFinished = true;
        refreshHeroCandidates();
        loadingLabel.setVisibility(itemCount() == 0 ? View.VISIBLE : View.GONE);
        if (itemCount() == 0) {
            loadingLabel.setText("OnlyFap creators could not load right now.");
        }
        applyPendingRestoreState();
    }

    int itemCount() {
        return trendingItems.size() + newItems.size() + hotItems.size() + popularItems.size();
    }

    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (!active) {
            heroHandler.removeCallbacksAndMessages(null);
            return;
        }
        scheduleHeroRotation();
    }

    void saveState(Bundle out) {
        if (out == null) return;
        out.putInt("scroll_y", scroll.getScrollY());
        out.putInt("hero_index", heroIndex);
        saveRailState(out, "trending", trendingShelf.rail);
        saveRailState(out, "new", newShelf.rail);
        saveRailState(out, "hot", hotShelf.rail);
        saveRailState(out, "popular", popularShelf.rail);
    }

    void restoreState(Bundle state) {
        pendingRestoreState = state == null ? null : new Bundle(state);
    }

    void close() {
        closed = true;
        active = false;
        heroHandler.removeCallbacksAndMessages(null);
        heroIo.shutdownNow();
        Glide.with(heroImage).clear(heroImage);
        trendingShelf.adapter.close();
        newShelf.adapter.close();
        hotShelf.adapter.close();
        popularShelf.adapter.close();
    }

    private CreatorShelf addCreatorShelf(String title, String subtitle, String description) {
        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setVisibility(View.GONE);

        TextView titleView = text(title, 19f, Color.WHITE);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        section.addView(titleView);

        TextView subtitleView = text(
                subtitle,
                11.5f,
                ZeroChillUi.color(getContext(), R.color.zc_text_muted)
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(1);
        section.addView(subtitleView, subtitleParams);

        RecyclerView rail = new RecyclerView(getContext());
        rail.setLayoutManager(new LinearLayoutManager(
                getContext(),
                LinearLayoutManager.HORIZONTAL,
                false
        ));
        rail.setHorizontalScrollBarEnabled(false);
        rail.setClipToPadding(false);
        rail.setPadding(0, dp(10), dp(8), dp(2));
        rail.setItemAnimator(null);
        rail.setContentDescription(description);

        CreatorPortraitAdapter adapter = new CreatorPortraitAdapter(listener);
        rail.setAdapter(adapter);
        section.addView(rail, new LinearLayout.LayoutParams(-1, dp(236)));

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.setMargins(0, 0, 0, dp(14));
        body.addView(section, sectionParams);

        return new CreatorShelf(section, rail, adapter);
    }

    private View favoriteCreatorCard(NativeContentItem creator) {
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription("Open " + creator.title + " gallery");
        wrapper.setOnClickListener(v -> listener.onOpenCreator(creator));
        ZeroChillMotion.installPressFeedback(wrapper);

        MaterialCardView avatar = new MaterialCardView(getContext());
        avatar.setRadius(dp(36));
        avatar.setCardElevation(0f);
        avatar.setStrokeWidth(0);
        avatar.setCardBackgroundColor(
                ZeroChillUi.color(getContext(), R.color.zc_surface_pressed));

        FrameLayout frame = new FrameLayout(getContext());
        avatar.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        TextView initials = text(
                initials(creator.title),
                20f,
                Color.argb(110, 255, 255, 255)
        );
        initials.setTypeface(null, android.graphics.Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));

        ImageView image = new ImageView(getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadCreatorImage(image, creator, true);

        wrapper.addView(avatar, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView name = text(
                creator.title,
                11.5f,
                ZeroChillUi.color(getContext(), R.color.zc_text_primary)
        );
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(dp(90), -2);
        nameParams.topMargin = dp(5);
        wrapper.addView(name, nameParams);
        return wrapper;
    }

    private void requestHeroCandidates() {
        if (closed) return;
        List<NativeContentItem> candidates = OnlyFapHeroPolicy.select(
                newItems,
                shelvesFinished ? CreatorCatalog.all(getContext()) : Collections.emptyList(),
                CreatorFavoriteStore.names(getContext()),
                trendingItems, hotItems, popularItems,
                HERO_RESOLVE_LIMIT
        );
        for (NativeContentItem creator : candidates) {
            String key = CreatorFavoriteStore.key(creator);
            if (requestedHeroCreators.add(key)) {
                resolveHeroAsync(creator);
            }
        }
    }

    private void refreshHeroCandidates() {
        if (closed) return;
        Set<String> excluded = OnlyFapHeroPolicy.excluded(
                CreatorFavoriteStore.names(getContext()), trendingItems, hotItems, popularItems
        );
        boolean selectedRemoved = heroItem != null &&
                excluded.contains(CreatorFavoriteStore.key(heroItem.creator));
        heroItems.removeIf(item -> excluded.contains(CreatorFavoriteStore.key(item.creator)));
        if (selectedRemoved) {
            heroItem = null;
            heroIndex = -1;
            if (!heroItems.isEmpty()) showHero(0, false);
            else {
                heroHandler.removeCallbacksAndMessages(null);
                Glide.with(heroImage).clear(heroImage);
                heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
                heroTitle.setText("Discover creators");
                heroAction.setVisibility(View.GONE);
                heroDots.setVisibility(View.GONE);
            }
        } else if (heroItem != null) {
            heroIndex = heroItems.indexOf(heroItem);
            updateHeroDots();
        }
        requestHeroCandidates();
    }

    private void resolveHeroAsync(NativeContentItem creator) {
        final int generation = heroGeneration;
        heroIo.execute(() -> {
            if (closed || generation != heroGeneration) return;

            ArrayList<OnlyFapHeroPolicy.Artwork> portraitArtwork = new ArrayList<>();

            // Fapello is the primary source for New Creators. Resolve actual gallery files,
            // validate their decoded proportions, and keep only portrait-oriented images.
            if (FapelloRepository.isModelUrl(creator.url)) {
                try {
                    FapelloRepository fapello = new FapelloRepository();
                    List<NativeContentItem> media = fapello.fetchModelMedia(
                            getContext().getApplicationContext(),
                            new FapelloRepository.Model(creator.title, creator.url, creator.imageUrl),
                            1);
                    int checked = 0;
                    for (NativeContentItem item : media) {
                        if (checked >= 6 || portraitArtwork.size() >= 2) break;
                        if (item == null || !item.isImage()) continue;
                        checked++;
                        try {
                            CrazyShitRepository.StreamInfo full = fapello.resolvePlayable(
                                    getContext().getApplicationContext(), item.url);
                            OnlyFapHeroPolicy.Artwork choice =
                                    new OnlyFapHeroPolicy.Artwork(
                                            full.mediaUrl,
                                            full.requestReferer,
                                            false
                                    );
                            if (isGoodPortraitArtwork(choice)) {
                                portraitArtwork.add(choice);
                            }
                        } catch (IOException ignored) { }
                    }
                } catch (IOException ignored) { }
            }

            // If Fapello did not provide a usable portrait, try OnlyHaven creator media.
            if (portraitArtwork.size() < 2) {
                try {
                    OnlyHavenRepository repository = new OnlyHavenRepository();
                    String query = clean(creator.searchQuery).isEmpty()
                            ? creator.title
                            : creator.searchQuery;
                    List<OnlyHavenRepository.Creator> matches =
                            repository.searchCreators(
                                    getContext().getApplicationContext(),
                                    query,
                                    6
                            );
                    OnlyHavenRepository.Creator best = chooseOnlyHavenMatch(query, matches);
                    if (best != null) {
                        List<NativeContentItem> media = repository.fetchCreatorMedia(
                                getContext().getApplicationContext(),
                                best,
                                1,
                                12
                        );
                        ArrayList<OnlyFapHeroPolicy.Artwork> candidates = new ArrayList<>();
                        OnlyFapHeroPolicy.addDirectImages(candidates, media, best.url);
                        for (OnlyFapHeroPolicy.Artwork choice : candidates) {
                            if (portraitArtwork.size() >= 2) break;
                            if (isGoodPortraitArtwork(choice)) {
                                portraitArtwork.add(choice);
                            }
                        }
                    }
                } catch (IOException ignored) { }
            }

            List<OnlyFapHeroPolicy.Artwork> choices =
                    OnlyFapHeroPolicy.distinctArtwork(portraitArtwork);
            // No portrait image means no hero slot for this creator.
            if (choices.isEmpty()) return;

            post(() -> {
                if (generation == heroGeneration) {
                    addHeroCandidate(new HeroCandidate(creator, choices));
                }
            });
        });
    }

    private boolean isGoodPortraitArtwork(OnlyFapHeroPolicy.Artwork choice) {
        if (choice == null || clean(choice.url).isEmpty()) return false;
        FutureTarget<Bitmap> target = null;
        try {
            target = Glide.with(getContext().getApplicationContext())
                    .asBitmap()
                    .load(remoteImage(choice.url, choice.referer))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .fitCenter()
                    .submit(dp(360), dp(540));
            Bitmap bitmap = target.get();
            return bitmap != null &&
                    OnlyFapHeroPolicy.isGoodPortraitDimensions(
                            bitmap.getWidth(),
                            bitmap.getHeight()
                    );
        } catch (Exception ignored) {
            return false;
        } finally {
            if (target != null) target.cancel(true);
        }
    }

    private OnlyHavenRepository.Creator chooseOnlyHavenMatch(
            String query,
            List<OnlyHavenRepository.Creator> matches
    ) {
        if (matches == null || matches.isEmpty()) return null;
        OnlyHavenRepository.Creator best = null;
        int bestRank = Integer.MAX_VALUE;
        for (OnlyHavenRepository.Creator candidate : matches) {
            if (candidate == null) continue;
            int rank = CreatorNameMatcher.rank(candidate.name, query);
            if (rank < bestRank) {
                best = candidate;
                bestRank = rank;
            }
        }
        return bestRank == Integer.MAX_VALUE ? null : best;
    }

    private void addHeroCandidate(HeroCandidate candidate) {
        if (closed || candidate == null || heroItems.size() >= HERO_MAX_ITEMS) return;
        String key = CreatorFavoriteStore.key(candidate.creator);
        if (key.isEmpty() || findHeroCandidate(key) != null ||
                OnlyFapHeroPolicy.excluded(CreatorFavoriteStore.names(getContext()),
                        trendingItems, hotItems, popularItems).contains(key)) return;
        heroItems.add(candidate);
        if (heroItem == null) {
            showHero(0, false);
        } else {
            updateHeroDots();
            scheduleHeroRotation();
        }
    }

    private HeroCandidate findHeroCandidate(String key) {
        if (key == null || key.isEmpty()) return null;
        for (HeroCandidate candidate : heroItems) {
            if (key.equals(CreatorFavoriteStore.key(candidate.creator))) {
                return candidate;
            }
        }
        return null;
    }

    private void showHero(int requestedIndex, boolean animate) {
        if (heroItems.isEmpty()) return;
        int index = Math.floorMod(requestedIndex, heroItems.size());
        HeroCandidate next = heroItems.get(index);
        while (rejectedHeroUrls.contains(next.currentImageUrl()) && next.advance()) { }
        if (rejectedHeroUrls.contains(next.currentImageUrl())) {
            rejectHero(next);
            return;
        }

        heroIndex = index;
        heroItem = next;
        heroTitle.setText(next.creator.title);
        heroHint.setText(heroDescription(next.creator));
        heroAction.setVisibility(View.VISIBLE);
        heroCard.setContentDescription("Open " + next.creator.title + " gallery");
        updateHeroDots();

        if (animate) {
            heroCard.animate().cancel();
            heroCard.animate()
                    .alpha(0.72f)
                    .setDuration(110L)
                    .withEndAction(() -> {
                        loadHeroArtwork(next);
                        heroCard.animate().alpha(1f).setDuration(180L).start();
                    })
                    .start();
        } else {
            loadHeroArtwork(next);
        }
        scheduleHeroRotation();
    }

    private void loadHeroArtwork(HeroCandidate candidate) {
        if (closed || candidate == null) return;
        final String imageUrl = candidate.currentImageUrl();
        final String referer = candidate.currentReferer();
        if (clean(imageUrl).isEmpty()) {
            handleHeroArtworkFailure(candidate, imageUrl);
            return;
        }
        try {
            Glide.with(heroImage)
                    .load(remoteImage(imageUrl, referer))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transition(DrawableTransitionOptions.withCrossFade(220))
                    .placeholder(new ColorDrawable(Color.rgb(13, 16, 19)))
                    .error(new ColorDrawable(Color.rgb(13, 16, 19)))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                GlideException e,
                                Object model,
                                Target<Drawable> target,
                                boolean first
                        ) {
                            post(() -> handleHeroArtworkFailure(
                                    candidate,
                                    imageUrl
                            ));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                Drawable resource,
                                Object model,
                                Target<Drawable> target,
                                DataSource dataSource,
                                boolean first
                        ) {
                            return false;
                        }
                    })
                    .into(heroImage);
        } catch (Exception ignored) {
            handleHeroArtworkFailure(candidate, imageUrl);
        }
    }

    private void handleHeroArtworkFailure(
            HeroCandidate candidate,
            String failedUrl
    ) {
        if (closed || candidate == null || !heroItems.contains(candidate) ||
                !failedUrl.equals(candidate.currentImageUrl())) return;
        if (!clean(failedUrl).isEmpty()) rejectedHeroUrls.add(failedUrl);
        if (candidate.advance()) {
            if (candidate == heroItem) loadHeroArtwork(candidate);
            return;
        }
        rejectHero(candidate);
    }

    private void rejectHero(HeroCandidate candidate) {
        if (closed || candidate == null) return;
        int removed = heroItems.indexOf(candidate);
        if (removed >= 0) heroItems.remove(removed);
        if (heroItems.isEmpty()) {
            heroItem = null;
            heroIndex = -1;
            heroDots.setVisibility(View.GONE);
            heroAction.setVisibility(View.GONE);
            heroTitle.setText("Discover creators");
            heroHint.setText("Loading featured creator artwork…");
            requestHeroCandidates();
            return;
        }
        int next = Math.min(Math.max(0, removed), heroItems.size() - 1);
        showHero(next, false);
        requestHeroCandidates();
    }

    private String heroDescription(NativeContentItem creator) {
        String description = clean(creator.description);
        if (!description.isEmpty()) return description;
        return "Pictures + videos · Open creator gallery";
    }

    private void updateHeroDots() {
        if (heroItems.size() <= 1) {
            heroDots.setVisibility(View.GONE);
            return;
        }
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < heroItems.size(); i++) {
            if (i > 0) dots.append(' ');
            dots.append(i == heroIndex ? '●' : '○');
        }
        heroDots.setText(dots.toString());
        heroDots.setVisibility(View.VISIBLE);
    }

    private void scheduleHeroRotation() {
        heroHandler.removeCallbacksAndMessages(null);
        if (!active || closed || heroItems.size() <= 1) return;
        heroHandler.postDelayed(
                () -> showHero(heroIndex + 1, true),
                HERO_ROTATION_MS
        );
    }

    private void openHero() {
        if (heroItem == null || heroItem.creator == null) return;
        listener.onOpenCreator(heroItem.creator);
    }

    private void loadCreatorImage(
            ImageView image,
            NativeContentItem creator,
            boolean circle
    ) {
        String url = creator == null ? "" : clean(creator.imageUrl);
        if (url.isEmpty()) {
            image.setImageResource(R.drawable.ic_more_account);
            return;
        }
        try {
            com.bumptech.glide.RequestBuilder<Drawable> request =
                    Glide.with(image)
                            .load(remoteImage(url, creator.uploader.isEmpty()
                                    ? creator.url
                                    : creator.uploader))
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .dontAnimate()
                            .placeholder(new ColorDrawable(
                                    ZeroChillUi.color(getContext(), R.color.zc_surface_pressed)))
                            .error(R.drawable.ic_more_account);
            if (circle) request.circleCrop();
            else request.centerCrop();
            request.into(image);
        } catch (Exception ignored) {
            image.setImageResource(R.drawable.ic_more_account);
        }
    }

    private GlideUrl remoteImage(String imageUrl, String referer) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (!clean(referer).isEmpty()) headers.addHeader("Referer", referer);
        return new GlideUrl(imageUrl, headers.build());
    }

    private List<NativeContentItem> safe(List<NativeContentItem> items) {
        return items == null
                ? Collections.emptyList()
                : new ArrayList<>(items);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String initials(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return "?";
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(java.util.Locale.US);
        }
        return (parts[0].substring(0, 1) +
                parts[parts.length - 1].substring(0, 1))
                .toUpperCase(java.util.Locale.US);
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').trim();
    }

    private void saveRailState(Bundle out, String key, RecyclerView rail) {
        RecyclerView.LayoutManager manager = rail.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        LinearLayoutManager linear = (LinearLayoutManager) manager;
        int position = linear.findFirstVisibleItemPosition();
        if (position < 0) return;
        View child = linear.findViewByPosition(position);
        int offset = child == null ? 0 : child.getLeft() - rail.getPaddingLeft();
        out.putInt(key + "_position", position);
        out.putInt(key + "_offset", offset);
    }

    private void restoreRailState(Bundle state, String key, RecyclerView rail) {
        if (state == null || !state.containsKey(key + "_position")) return;
        RecyclerView.LayoutManager manager = rail.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        int position = state.getInt(key + "_position", 0);
        int offset = state.getInt(key + "_offset", 0);
        rail.post(() -> ((LinearLayoutManager) manager)
                .scrollToPositionWithOffset(position, offset));
    }

    private void applyPendingRestoreState() {
        Bundle state = pendingRestoreState;
        if (state == null) return;
        pendingRestoreState = null;

        int restoredHero = state.getInt("hero_index", -1);
        if (restoredHero >= 0 && restoredHero < heroItems.size()) {
            showHero(restoredHero, false);
        }

        restoreRailState(state, "trending", trendingShelf.rail);
        restoreRailState(state, "new", newShelf.rail);
        restoreRailState(state, "hot", hotShelf.rail);
        restoreRailState(state, "popular", popularShelf.rail);

        int y = Math.max(0, state.getInt("scroll_y", 0));
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class HeroCandidate {
        final NativeContentItem creator;
        final List<OnlyFapHeroPolicy.Artwork> artwork;
        int artworkIndex;

        HeroCandidate(NativeContentItem creator, List<OnlyFapHeroPolicy.Artwork> artwork) {
            this.creator = creator;
            this.artwork = artwork;
        }

        // Kept for the rotation regression fixture, which only needs one image.
        HeroCandidate(NativeContentItem creator, String imageUrl, String referer) {
            this(creator, Collections.singletonList(
                    new OnlyFapHeroPolicy.Artwork(imageUrl, referer, true)));
        }

        boolean advance() {
            if (artworkIndex + 1 >= artwork.size()) return false;
            artworkIndex++;
            return true;
        }

        boolean usingAvatar() {
            return artwork.get(artworkIndex).avatar;
        }

        String currentImageUrl() {
            return artwork.get(artworkIndex).url;
        }

        String currentReferer() {
            return artwork.get(artworkIndex).referer;
        }
    }

    private static final class CreatorShelf {
        final LinearLayout container;
        final RecyclerView rail;
        final CreatorPortraitAdapter adapter;

        CreatorShelf(
                LinearLayout container,
                RecyclerView rail,
                CreatorPortraitAdapter adapter
        ) {
            this.container = container;
            this.rail = rail;
            this.adapter = adapter;
        }
    }

    private static final class CreatorPortraitAdapter
            extends RecyclerView.Adapter<CreatorPortraitAdapter.Holder> {
        private final Listener listener;
        private final List<NativeContentItem> items = new ArrayList<>();
        private boolean closed;

        CreatorPortraitAdapter(Listener listener) {
            this.listener = listener;
            setHasStableIds(true);
        }

        void replace(List<NativeContentItem> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        void refreshFavorites() {
            if (!items.isEmpty()) notifyItemRangeChanged(0, items.size());
        }

        void close() {
            closed = true;
            items.clear();
        }

        @Override
        public long getItemId(int position) {
            NativeContentItem item = items.get(position);
            return CreatorFavoriteStore.key(item).hashCode();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            MaterialCardView card = new MaterialCardView(context);
            ZeroChillUi.styleMediaCard(card, R.dimen.zc_radius_medium);
            card.setRadius(BrowseUi.dp(context, 14));
            card.setCardElevation(0f);
            card.setStrokeWidth(0);
            card.setClickable(true);
            card.setFocusable(true);
            ZeroChillMotion.installPressFeedback(card);

            RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(
                    BrowseUi.dp(context, 148),
                    BrowseUi.dp(context, 216)
            );
            cardParams.setMargins(
                    0,
                    0,
                    BrowseUi.dp(context, 10),
                    0
            );
            card.setLayoutParams(cardParams);

            FrameLayout frame = new FrameLayout(context);
            card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

            TextView initials = new TextView(context);
            initials.setGravity(Gravity.CENTER);
            initials.setTextColor(Color.argb(72, 255, 255, 255));
            initials.setTextSize(34f);
            initials.setTypeface(null, android.graphics.Typeface.BOLD);
            frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));

            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

            View shade = new View(context);
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.argb(32, 0, 0, 0),
                            Color.argb(238, 0, 0, 0)
                    }
            ));
            FrameLayout.LayoutParams shadeParams =
                    new FrameLayout.LayoutParams(-1, BrowseUi.dp(context, 92), Gravity.BOTTOM);
            frame.addView(shade, shadeParams);

            TextView favorite = new TextView(context);
            favorite.setText("★");
            favorite.setTextColor(UiPalette.PRIMARY);
            favorite.setTextSize(17f);
            favorite.setGravity(Gravity.CENTER);
            favorite.setVisibility(View.GONE);
            favorite.setBackground(BrowseUi.rounded(
                    context,
                    Color.argb(185, 9, 12, 15),
                    16
            ));
            FrameLayout.LayoutParams favoriteParams =
                    new FrameLayout.LayoutParams(
                            BrowseUi.dp(context, 32),
                            BrowseUi.dp(context, 32),
                            Gravity.TOP | Gravity.END
                    );
            favoriteParams.setMargins(
                    0,
                    BrowseUi.dp(context, 9),
                    BrowseUi.dp(context, 9),
                    0
            );
            frame.addView(favorite, favoriteParams);

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.BOTTOM);
            copy.setPadding(
                    BrowseUi.dp(context, 12),
                    BrowseUi.dp(context, 8),
                    BrowseUi.dp(context, 12),
                    BrowseUi.dp(context, 10)
            );
            FrameLayout.LayoutParams copyParams =
                    new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            frame.addView(copy, copyParams);

            TextView title = new TextView(context);
            title.setTextColor(Color.WHITE);
            title.setTextSize(14.5f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);

            TextView hint = new TextView(context);
            hint.setText("Open gallery");
            hint.setTextColor(
                    ZeroChillUi.color(context, R.color.zc_text_secondary));
            hint.setTextSize(10.5f);
            LinearLayout.LayoutParams hintParams =
                    new LinearLayout.LayoutParams(-1, -2);
            hintParams.topMargin = BrowseUi.dp(context, 2);
            copy.addView(hint, hintParams);

            return new Holder(card, image, initials, title, favorite);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            NativeContentItem creator = items.get(position);
            Context context = holder.card.getContext();
            holder.title.setText(creator.title);
            holder.initials.setText(initialsStatic(creator.title));
            boolean favorite = CreatorFavoriteStore.contains(context, creator);
            holder.favorite.setVisibility(favorite ? View.VISIBLE : View.GONE);
            holder.card.setContentDescription("Open " + creator.title + " gallery");
            holder.card.setOnClickListener(v -> listener.onOpenCreator(creator));
            holder.card.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                boolean saved = CreatorFavoriteStore.toggle(context, creator);
                holder.favorite.setVisibility(saved ? View.VISIBLE : View.GONE);
                Toast.makeText(
                        context,
                        saved
                                ? creator.title + " added to favorites."
                                : creator.title + " removed from favorites.",
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            });

            if (position < 3) {
                CreatorGalleryPreloader.warm(
                        context,
                        creator,
                        CreatorGalleryPreloader.PRIORITY_HIGH
                );
            } else if (position < 7) {
                CreatorGalleryPreloader.warm(context, creator);
            }

            loadStaticImage(holder.image, creator);
        }

        @Override
        public void onViewRecycled(@NonNull Holder holder) {
            Glide.with(holder.image).clear(holder.image);
            holder.card.setOnClickListener(null);
            holder.card.setOnLongClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private static void loadStaticImage(ImageView image, NativeContentItem creator) {
            String url = creator == null || creator.imageUrl == null
                    ? ""
                    : creator.imageUrl.trim();
            if (url.isEmpty()) {
                image.setImageResource(R.drawable.ic_more_account);
                return;
            }

            LazyHeaders.Builder headers = new LazyHeaders.Builder()
                    .addHeader("User-Agent", USER_AGENT);
            String referer = creator.uploader == null || creator.uploader.trim().isEmpty()
                    ? creator.url
                    : creator.uploader;
            if (referer != null && !referer.trim().isEmpty()) {
                headers.addHeader("Referer", referer);
            }

            try {
                Glide.with(image)
                        .load(new GlideUrl(url, headers.build()))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate()
                        .placeholder(new ColorDrawable(
                                ZeroChillUi.color(
                                        image.getContext(),
                                        R.color.zc_surface_pressed)))
                        .error(R.drawable.ic_more_account)
                        .into(image);
            } catch (Exception ignored) {
                image.setImageResource(R.drawable.ic_more_account);
            }
        }

        private static String initialsStatic(String value) {
            String clean = value == null ? "" : value.trim();
            if (clean.isEmpty()) return "?";
            String[] parts = clean.split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, 1).toUpperCase(java.util.Locale.US);
            }
            return (parts[0].substring(0, 1) +
                    parts[parts.length - 1].substring(0, 1))
                    .toUpperCase(java.util.Locale.US);
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final ImageView image;
            final TextView initials;
            final TextView title;
            final TextView favorite;

            Holder(
                    MaterialCardView card,
                    ImageView image,
                    TextView initials,
                    TextView title,
                    TextView favorite
            ) {
                super(card);
                this.card = card;
                this.image = image;
                this.initials = initials;
                this.title = title;
                this.favorite = favorite;
            }
        }
    }
}
