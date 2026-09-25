package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Nuvio-inspired proof-of-concept landing page for the Shows tab. */
final class ShowsHubView extends FrameLayout {
    interface Listener {
        void onOpen(NativeContentItem item);
    }

    interface PrewarmListener {
        void onPrewarm(NativeContentItem item);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final long HERO_ROTATION_MS = 14_000L;
    private static final int HERO_MAX_ITEMS = 5;

    private final Listener listener;
    private final Listener videoListener;
    private final PrewarmListener prewarmListener;
    private final Handler heroHandler = new Handler(Looper.getMainLooper());
    private final ScrollView scroll;
    private final LinearLayout content;
    private final MaterialCardView heroCard;
    private final ImageView heroImage;
    private final TextView heroSource;
    private final TextView heroTitle;
    private final TextView heroHint;
    private final TextView heroAction;
    private final TextView heroDots;
    private final TextView loadingLabel;
    private final ContinueShelf continueShelf;
    private final Shelf crazyShelf;
    private final Shelf efuktShelf;
    private final Shelf categoryShelf;
    private final Shelf kaoticCategoryShelf;

    private List<NativeContentItem> crazyItems = Collections.emptyList();
    private List<NativeContentItem> efuktItems = Collections.emptyList();
    private List<NativeContentItem> categoryItems = Collections.emptyList();
    private List<NativeContentItem> kaoticCategoryItems = Collections.emptyList();
    private List<NativeContentItem> heroItems = Collections.emptyList();
    private NativeContentItem heroItem;
    private int heroIndex = -1;
    private boolean active;
    private Bundle pendingRestoreState;

    ShowsHubView(
            Context context,
            Listener listener,
            Listener videoListener,
            PrewarmListener prewarmListener
    ) {
        super(context);
        this.listener = listener;
        this.videoListener = videoListener;
        this.prewarmListener = prewarmListener;
        setBackgroundColor(ZeroChillUi.background(context));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(66), dp(12), dp(34));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        TextView eyebrow = text("ZEROCHILL SHOWS", 11f,
                ZeroChillUi.color(context, R.color.zc_cyan));
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(-1, -2);
        eyebrowParams.setMargins(dp(4), dp(8), dp(4), dp(7));
        content.addView(eyebrow, eyebrowParams);

        heroCard = new MaterialCardView(context);
        ZeroChillUi.styleMaterialCard(heroCard, R.dimen.zc_radius_large);
        heroCard.setRadius(dp(22));
        heroCard.setCardElevation(0f);
        heroCard.setStrokeWidth(dp(1));
        heroCard.setStrokeColor(ZeroChillUi.color(context, R.color.zc_edge));
        heroCard.setClickable(true);
        heroCard.setFocusable(true);
        ZeroChillMotion.installPressFeedback(heroCard);

        FrameLayout heroFrame = new FrameLayout(context);
        heroCard.addView(heroFrame, new MaterialCardView.LayoutParams(-1, -1));

        heroImage = new ImageView(context);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackground(new ColorDrawable(Color.rgb(13, 16, 19)));
        heroFrame.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));

        View heroShade = new View(context);
        heroShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(18, 0, 0, 0),
                        Color.argb(64, 0, 0, 0),
                        Color.argb(246, 0, 0, 0)
                }
        ));
        heroFrame.addView(heroShade, new FrameLayout.LayoutParams(-1, -1));

        heroDots = text("", 15f, Color.WHITE);
        heroDots.setGravity(Gravity.CENTER);
        heroDots.setVisibility(View.GONE);
        heroDots.setContentDescription("Featured show position");
        FrameLayout.LayoutParams heroDotsParams = new FrameLayout.LayoutParams(
                -2,
                dp(32),
                Gravity.TOP | Gravity.END
        );
        heroDotsParams.setMargins(0, dp(12), dp(12), 0);
        heroFrame.addView(heroDots, heroDotsParams);

        LinearLayout heroCopy = new LinearLayout(context);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setGravity(Gravity.BOTTOM);
        heroCopy.setPadding(dp(18), dp(18), dp(18), dp(18));
        heroFrame.addView(heroCopy,
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        heroSource = text("FEATURED", 11f, UiPalette.PRIMARY);
        heroSource.setTypeface(null, android.graphics.Typeface.BOLD);
        heroSource.setLetterSpacing(0.08f);
        heroCopy.addView(heroSource);

        heroTitle = text("A new way to browse Shows", 27f, Color.WHITE);
        heroTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams heroTitleParams = new LinearLayout.LayoutParams(-1, -2);
        heroTitleParams.topMargin = dp(4);
        heroCopy.addView(heroTitle, heroTitleParams);

        heroHint = text(
                "CrazyShit, EFukt and Kaotic together in one media hub.",
                13f,
                ZeroChillUi.color(context, R.color.zc_text_secondary)
        );
        heroHint.setMaxLines(2);
        heroHint.setLineSpacing(0f, 1.06f);
        LinearLayout.LayoutParams heroHintParams = new LinearLayout.LayoutParams(-1, -2);
        heroHintParams.topMargin = dp(5);
        heroCopy.addView(heroHint, heroHintParams);

        heroAction = text("VIEW SHOW", 13f, Color.BLACK);
        heroAction.setTypeface(null, android.graphics.Typeface.BOLD);
        heroAction.setGravity(Gravity.CENTER);
        heroAction.setVisibility(View.GONE);
        GradientDrawable actionBackground = new GradientDrawable();
        actionBackground.setColor(UiPalette.PRIMARY);
        actionBackground.setCornerRadius(dp(19));
        heroAction.setBackground(actionBackground);
        LinearLayout.LayoutParams heroActionParams =
                new LinearLayout.LayoutParams(dp(116), dp(38));
        heroActionParams.topMargin = dp(13);
        heroCopy.addView(heroAction, heroActionParams);

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(258));
        heroParams.setMargins(0, 0, 0, dp(18));
        content.addView(heroCard, heroParams);

        loadingLabel = text(
                "Loading Shows…",
                12f,
                ZeroChillUi.color(context, R.color.zc_text_muted)
        );
        loadingLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.setMargins(0, 0, 0, dp(10));
        content.addView(loadingLabel, loadingParams);

        continueShelf = addContinueShelf();
        crazyShelf = addShelf("CrazyShit Shows", "Series and recurring collections", false);
        efuktShelf = addShelf("EFukt Series", "Browse EFukt by series", false);
        categoryShelf = addShelf("CrazyShit Categories", "Jump into a type of content", true);
        kaoticCategoryShelf = addShelf("Kaotic Categories", "Browse Kaotic by category", true);

        heroCard.setOnClickListener(v -> openHero());
        heroAction.setOnClickListener(v -> openHero());
        refreshContinueWatching();
    }

    void clear() {
        crazyItems = Collections.emptyList();
        efuktItems = Collections.emptyList();
        categoryItems = Collections.emptyList();
        kaoticCategoryItems = Collections.emptyList();
        heroHandler.removeCallbacksAndMessages(null);
        heroCard.animate().cancel();
        heroCard.setAlpha(1f);
        heroItems = Collections.emptyList();
        heroItem = null;
        heroIndex = -1;
        heroDots.setVisibility(View.GONE);
        crazyShelf.adapter.replace(Collections.emptyList());
        efuktShelf.adapter.replace(Collections.emptyList());
        categoryShelf.adapter.replace(Collections.emptyList());
        kaoticCategoryShelf.adapter.replace(Collections.emptyList());
        crazyShelf.container.setVisibility(View.GONE);
        efuktShelf.container.setVisibility(View.GONE);
        categoryShelf.container.setVisibility(View.GONE);
        kaoticCategoryShelf.container.setVisibility(View.GONE);
        loadingLabel.setText("Loading Shows…");
        loadingLabel.setVisibility(View.VISIBLE);
        heroSource.setText("FEATURED");
        heroTitle.setText("A new way to browse Shows");
        heroHint.setText("CrazyShit, EFukt and Kaotic together in one media hub.");
        heroAction.setVisibility(View.GONE);
        Glide.with(heroImage).clear(heroImage);
        heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
    }

    void setCrazyShit(List<NativeContentItem> items) {
        crazyItems = safe(items);
        crazyShelf.adapter.replace(crazyItems);
        preloadShelfArtwork(crazyItems, 4);
        crazyShelf.container.setVisibility(crazyItems.isEmpty() ? View.GONE : View.VISIBLE);
        rebuildHeroCandidates();
    }

    void setEfukt(List<NativeContentItem> items) {
        efuktItems = safe(items);
        efuktShelf.adapter.replace(efuktItems);
        preloadShelfArtwork(efuktItems, 4);
        efuktShelf.container.setVisibility(efuktItems.isEmpty() ? View.GONE : View.VISIBLE);
        rebuildHeroCandidates();
    }

    void setCategories(List<NativeContentItem> items) {
        categoryItems = safe(items);
        categoryShelf.adapter.replace(categoryItems);
        preloadShelfArtwork(categoryItems, 4);
        categoryShelf.container.setVisibility(categoryItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void setKaoticCategories(List<NativeContentItem> items) {
        kaoticCategoryItems = safe(items);
        kaoticCategoryShelf.adapter.replace(kaoticCategoryItems);
        preloadShelfArtwork(kaoticCategoryItems, 4);
        kaoticCategoryShelf.container.setVisibility(
                kaoticCategoryItems.isEmpty() ? View.GONE : View.VISIBLE
        );
    }

    void finishLoading() {
        loadingLabel.setVisibility(itemCount() == 0 ? View.VISIBLE : View.GONE);
        if (itemCount() == 0) {
            loadingLabel.setText("Shows could not load right now.");
        }
        applyPendingRestoreState();
    }

    void saveState(Bundle out) {
        if (out == null) return;
        out.putInt("scroll_y", scroll.getScrollY());
        out.putInt("hero_index", heroIndex);
        saveRailState(out, "continue", continueShelf.rail);
        saveRailState(out, "crazy", crazyShelf.rail);
        saveRailState(out, "efukt", efuktShelf.rail);
        saveRailState(out, "categories", categoryShelf.rail);
        saveRailState(out, "kaotic_categories", kaoticCategoryShelf.rail);
    }

    void restoreState(Bundle state) {
        pendingRestoreState = state == null ? null : new Bundle(state);
    }

    int itemCount() {
        return crazyItems.size() + efuktItems.size()
                + categoryItems.size() + kaoticCategoryItems.size();
    }

    void refreshContinueWatching() {
        List<PlaybackHistoryStore.Item> items =
                PlaybackHistoryStore.continueWatchingShows(getContext());
        continueShelf.adapter.replace(items);
        continueShelf.container.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (!active) {
            heroHandler.removeCallbacksAndMessages(null);
            heroCard.animate().cancel();
            heroCard.setAlpha(1f);
            return;
        }
        prewarmNextHero();
        scheduleHeroRotation();
    }

    private void rebuildHeroCandidates() {
        ArrayList<NativeContentItem> next = new ArrayList<>();
        appendHeroCandidates(next, crazyItems, 3);
        appendHeroCandidates(next, efuktItems, 2);

        String currentUrl = heroItem == null ? "" : heroItem.url;
        heroItems = next;
        heroIndex = indexOfUrl(heroItems, currentUrl);
        if (heroIndex < 0 && !heroItems.isEmpty()) {
            showHero(0, false);
        } else {
            updateHeroDots();
            prewarmNextHero();
        }
        if (itemCount() > 0) loadingLabel.setVisibility(View.GONE);
        scheduleHeroRotation();
    }

    private void appendHeroCandidates(
            List<NativeContentItem> target,
            List<NativeContentItem> source,
            int limit
    ) {
        if (source == null || target.size() >= HERO_MAX_ITEMS || limit <= 0) return;
        int added = 0;
        for (NativeContentItem item : source) {
            if (item == null || !hasArtwork(item) || containsUrl(target, item.url)) continue;
            target.add(item);
            added++;
            if (added >= limit || target.size() >= HERO_MAX_ITEMS) break;
        }
    }

    private boolean hasArtwork(NativeContentItem item) {
        return EmbeddedBrowseArtwork.has(getContext(), item.url)
                || (item.imageUrl != null && !item.imageUrl.trim().isEmpty());
    }

    private boolean containsUrl(List<NativeContentItem> items, String url) {
        return indexOfUrl(items, url) >= 0;
    }

    private int indexOfUrl(List<NativeContentItem> items, String url) {
        if (url == null || url.isEmpty() || items == null) return -1;
        for (int index = 0; index < items.size(); index++) {
            NativeContentItem item = items.get(index);
            if (item != null && url.equals(item.url)) return index;
        }
        return -1;
    }

    private void showHero(int index, boolean animate) {
        if (index < 0 || index >= heroItems.size()) return;
        NativeContentItem next = heroItems.get(index);
        Runnable apply = () -> {
            heroIndex = index;
            heroItem = next;
            boolean efukt = EfuktRepository.isEfuktUrl(next.url);
            heroSource.setText(efukt ? "EFUKT" : "CRAZYSHIT");
            heroTitle.setText(next.title);
            heroHint.setText(efukt
                    ? "Featured from EFukt Series"
                    : "Featured from CrazyShit Shows");
            heroAction.setVisibility(View.VISIBLE);
            loadArtwork(heroImage, next, true);
            updateHeroDots();
            loadingLabel.setVisibility(View.GONE);
            prewarmNextHero();
        };

        heroCard.animate().cancel();
        if (!animate || !ZeroChillMotion.animationsEnabled(getContext())) {
            heroCard.setAlpha(1f);
            apply.run();
            return;
        }
        heroCard.animate()
                .alpha(0.48f)
                .setDuration(180L)
                .withEndAction(() -> {
                    apply.run();
                    heroCard.animate().alpha(1f).setDuration(320L).start();
                })
                .start();
    }

    private void updateHeroDots() {
        if (heroItems.size() <= 1 || heroIndex < 0) {
            heroDots.setVisibility(View.GONE);
            return;
        }
        StringBuilder dots = new StringBuilder();
        for (int index = 0; index < heroItems.size(); index++) {
            if (index > 0) dots.append("  ");
            dots.append(index == heroIndex ? "●" : "○");
        }
        heroDots.setText(dots.toString());
        heroDots.setTextColor(UiPalette.PRIMARY);
        heroDots.setContentDescription(
                "Featured show " + (heroIndex + 1) + " of " + heroItems.size()
        );
        heroDots.setVisibility(View.VISIBLE);
    }

    private void scheduleHeroRotation() {
        heroHandler.removeCallbacksAndMessages(null);
        if (!active || heroItems.size() <= 1 || !isAttachedToWindow()
                || !ZeroChillMotion.animationsEnabled(getContext())) return;
        heroHandler.postDelayed(this::rotateHero, HERO_ROTATION_MS);
    }

    private void rotateHero() {
        if (!active || !isAttachedToWindow() || getWindowVisibility() != View.VISIBLE
                || heroItems.size() <= 1) {
            scheduleHeroRotation();
            return;
        }
        int next = (Math.max(0, heroIndex) + 1) % heroItems.size();
        showHero(next, true);
        scheduleHeroRotation();
    }

    private void prewarmNextHero() {
        if (!active || heroItems.size() <= 1 || heroIndex < 0) return;
        preloadArtwork(heroItems.get((heroIndex + 1) % heroItems.size()));
    }

    private void preloadShelfArtwork(List<NativeContentItem> items, int limit) {
        if (items == null || items.isEmpty() || limit <= 0) return;
        int count = Math.min(limit, items.size());
        for (int index = 0; index < count; index++) preloadArtwork(items.get(index));
    }

    private void preloadArtwork(NativeContentItem item) {
        if (item == null || EmbeddedBrowseArtwork.has(getContext(), item.url)) return;
        String imageUrl = item.imageUrl == null ? "" : item.imageUrl.trim();
        if (imageUrl.isEmpty()) return;

        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (item.url != null && item.url.startsWith("http")) {
            headers.addHeader("Referer", item.url);
        }
        Glide.with(this)
                .load(new GlideUrl(imageUrl, headers.build()))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload();
    }

    private void saveRailState(Bundle out, String key, RecyclerView rail) {
        if (out == null || rail == null) return;
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
        if (state == null || rail == null || !state.containsKey(key + "_position")) return;
        RecyclerView.LayoutManager manager = rail.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        int position = state.getInt(key + "_position", 0);
        int offset = state.getInt(key + "_offset", 0);
        LinearLayoutManager linear = (LinearLayoutManager) manager;
        rail.post(() -> linear.scrollToPositionWithOffset(position, offset));
    }

    private void applyPendingRestoreState() {
        Bundle state = pendingRestoreState;
        if (state == null) return;
        pendingRestoreState = null;

        int restoredHero = state.getInt("hero_index", -1);
        if (restoredHero >= 0 && restoredHero < heroItems.size()) {
            showHero(restoredHero, false);
        }

        restoreRailState(state, "continue", continueShelf.rail);
        restoreRailState(state, "crazy", crazyShelf.rail);
        restoreRailState(state, "efukt", efuktShelf.rail);
        restoreRailState(state, "categories", categoryShelf.rail);
        restoreRailState(state, "kaotic_categories", kaoticCategoryShelf.rail);

        int y = Math.max(0, state.getInt("scroll_y", 0));
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleHeroRotation();
    }

    @Override
    protected void onDetachedFromWindow() {
        heroHandler.removeCallbacksAndMessages(null);
        heroCard.animate().cancel();
        super.onDetachedFromWindow();
    }

    private void openHero() {
        if (heroItem != null && listener != null) listener.onOpen(heroItem);
    }

    private ContinueShelf addContinueShelf() {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
        blockParams.setMargins(0, 0, 0, dp(20));
        content.addView(block, blockParams);

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(3), 0, dp(3), dp(8));

        TextView titleView = text("Continue Watching", 20f, Color.WHITE);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.addView(titleView);

        TextView subtitleView = text(
                "Shows videos you started",
                12f,
                ZeroChillUi.color(getContext(), R.color.zc_text_secondary)
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(1);
        heading.addView(subtitleView, subtitleParams);
        block.addView(heading);

        RecyclerView rail = new RecyclerView(getContext());
        rail.setClipToPadding(false);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayoutManager manager =
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false);
        manager.setInitialPrefetchItemCount(4);
        rail.setLayoutManager(manager);

        ContinueAdapter adapter = new ContinueAdapter();
        rail.setAdapter(adapter);
        rail.setPadding(dp(2), 0, dp(22), 0);
        block.addView(rail, new LinearLayout.LayoutParams(-1, dp(154)));

        block.setVisibility(View.GONE);
        return new ContinueShelf(block, rail, adapter);
    }

    private Shelf addShelf(String title, String subtitle, boolean wideCards) {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
        blockParams.setMargins(0, 0, 0, dp(20));
        content.addView(block, blockParams);

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(3), 0, dp(3), dp(8));

        TextView titleView = text(title, 20f, Color.WHITE);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.addView(titleView);

        TextView subtitleView = text(
                subtitle,
                12f,
                ZeroChillUi.color(getContext(), R.color.zc_text_secondary)
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(1);
        heading.addView(subtitleView, subtitleParams);
        block.addView(heading);

        RecyclerView rail = new RecyclerView(getContext());
        rail.setClipToPadding(false);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayoutManager manager =
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false);
        manager.setInitialPrefetchItemCount(5);
        rail.setLayoutManager(manager);

        RailAdapter adapter = new RailAdapter(wideCards);
        rail.setAdapter(adapter);
        rail.setPadding(dp(2), 0, dp(22), 0);
        block.addView(rail, new LinearLayout.LayoutParams(
                -1,
                dp(wideCards ? 150 : 218)
        ));

        block.setVisibility(View.GONE);
        return new Shelf(block, rail, adapter);
    }

    private void loadArtwork(ImageView view, NativeContentItem item, boolean hero) {
        byte[] embedded = EmbeddedBrowseArtwork.get(getContext(), item.url);
        if (embedded != null && embedded.length >= 512) {
            Glide.with(view)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .centerCrop()
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(20, 22, 25)))
                    .error(new ColorDrawable(Color.rgb(20, 22, 25)))
                    .into(view);
            return;
        }

        String imageUrl = item.imageUrl == null ? "" : item.imageUrl.trim();
        if (imageUrl.isEmpty()) {
            Glide.with(view).clear(view);
            if (WebVideoSourceRepository.isKaoticUrl(item.url)) {
                view.setImageDrawable(new GradientDrawable(
                        GradientDrawable.Orientation.BR_TL,
                        new int[] {
                                Color.rgb(4, 7, 9),
                                Color.rgb(7, 42, 55),
                                Color.rgb(4, 7, 9)
                        }
                ));
            } else {
                view.setImageDrawable(new ColorDrawable(Color.rgb(20, 22, 25)));
            }
            return;
        }

        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (item.url != null && item.url.startsWith("http")) {
            headers.addHeader("Referer", item.url);
        }

        Glide.with(view)
                .load(new GlideUrl(imageUrl, headers.build()))
                .diskCacheStrategy(hero ? DiskCacheStrategy.ALL : DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.rgb(20, 22, 25)))
                .error(new ColorDrawable(Color.rgb(20, 22, 25)))
                .into(view);
    }

    private List<NativeContentItem> safe(List<NativeContentItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(items);
    }

    private String formatTime(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0L) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ContinueAdapter extends RecyclerView.Adapter<ContinueHolder> {
        private final List<PlaybackHistoryStore.Item> items = new ArrayList<>();

        ContinueAdapter() {
            setHasStableIds(true);
        }

        void replace(List<PlaybackHistoryStore.Item> next) {
            items.clear();
            if (next != null) {
                int count = Math.min(12, next.size());
                items.addAll(next.subList(0, count));
            }
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).pageUrl.hashCode();
        }

        @NonNull
        @Override
        public ContinueHolder onCreateViewHolder(
                @NonNull android.view.ViewGroup parent,
                int viewType
        ) {
            MaterialCardView card = new MaterialCardView(getContext());
            ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_medium);
            card.setRadius(dp(16));
            card.setCardElevation(0f);
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(ZeroChillUi.color(getContext(), R.color.zc_edge));
            card.setClickable(true);
            card.setFocusable(true);
            ZeroChillMotion.installPressFeedback(card);

            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(dp(220), dp(140));
            params.setMargins(dp(3), dp(2), dp(8), dp(4));
            card.setLayoutParams(params);

            FrameLayout frame = new FrameLayout(getContext());
            card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(new ColorDrawable(Color.rgb(20, 22, 25)));
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

            View shade = new View(getContext());
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            Color.argb(0, 0, 0, 0),
                            Color.argb(54, 0, 0, 0),
                            Color.argb(244, 0, 0, 0)
                    }
            ));
            frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

            TextView meta = text("", 10.5f, UiPalette.PRIMARY);
            meta.setTypeface(null, android.graphics.Typeface.BOLD);
            meta.setLetterSpacing(0.05f);
            FrameLayout.LayoutParams metaParams =
                    new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            metaParams.setMargins(dp(11), dp(10), dp(11), 0);
            frame.addView(meta, metaParams);

            TextView title = text("", 14.5f, Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.BOTTOM);
            title.setPadding(dp(11), dp(8), dp(11), dp(12));
            frame.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

            FrameLayout progressTrack = new FrameLayout(getContext());
            progressTrack.setBackgroundColor(Color.argb(190, 5, 8, 10));
            FrameLayout.LayoutParams trackParams =
                    new FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM);
            frame.addView(progressTrack, trackParams);

            View progressFill = new View(getContext());
            progressFill.setBackgroundColor(UiPalette.PRIMARY);
            progressFill.setPivotX(0f);
            progressTrack.addView(progressFill, new FrameLayout.LayoutParams(-1, -1));

            return new ContinueHolder(card, image, title, meta, progressTrack, progressFill);
        }

        @Override
        public void onBindViewHolder(@NonNull ContinueHolder holder, int position) {
            PlaybackHistoryStore.Item history = items.get(position);
            NativeContentItem item = new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    history.title,
                    history.pageUrl,
                    history.posterUrl,
                    "",
                    "",
                    ""
            );
            holder.title.setText(history.title);
            holder.meta.setText("CONTINUE · " + formatTime(history.positionMs));
            holder.card.setContentDescription("Resume " + history.title);
            float progress = history.durationMs <= 0L
                    ? 0f
                    : Math.max(0f, Math.min(1f,
                    history.positionMs / (float) history.durationMs));
            holder.progressFill.setScaleX(progress);
            holder.progressTrack.setVisibility(history.durationMs > 0L ? View.VISIBLE : View.GONE);
            holder.card.setOnClickListener(v -> {
                if (videoListener != null) videoListener.onOpen(item);
            });
            loadArtwork(holder.image, item, false);
        }

        @Override
        public void onViewRecycled(@NonNull ContinueHolder holder) {
            Glide.with(holder.image).clear(holder.image);
            holder.card.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class RailAdapter extends RecyclerView.Adapter<RailHolder> {
        private final boolean wide;
        private final List<NativeContentItem> items = new ArrayList<>();

        RailAdapter(boolean wide) {
            this.wide = wide;
            setHasStableIds(true);
        }

        void replace(List<NativeContentItem> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            NativeContentItem item = items.get(position);
            return (item.kind + "\n" + item.url + "\n" + item.title).hashCode();
        }

        @NonNull
        @Override
        public RailHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(getContext());
            ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_medium);
            card.setRadius(dp(16));
            card.setCardElevation(0f);
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(ZeroChillUi.color(getContext(), R.color.zc_edge));
            card.setClickable(true);
            card.setFocusable(true);
            ZeroChillMotion.installPressFeedback(card);

            int width = dp(wide ? 205 : 146);
            int height = dp(wide ? 132 : 202);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(width, height);
            params.setMargins(dp(3), dp(2), dp(8), dp(4));
            card.setLayoutParams(params);

            FrameLayout frame = new FrameLayout(getContext());
            card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

            View shade = new View(getContext());
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            Color.argb(0, 0, 0, 0),
                            Color.argb(36, 0, 0, 0),
                            Color.argb(235, 0, 0, 0)
                    }
            ));
            frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

            TextView title = text("", wide ? 14f : 14.5f, Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.BOTTOM);
            title.setPadding(dp(11), dp(8), dp(11), dp(10));
            frame.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

            return new RailHolder(card, image, title);
        }

        @Override
        public void onBindViewHolder(@NonNull RailHolder holder, int position) {
            NativeContentItem item = items.get(position);
            holder.title.setText(item.title);
            holder.card.setContentDescription("Open " + item.title);
            holder.card.setOnClickListener(v -> {
                if (listener != null) listener.onOpen(item);
            });
            loadArtwork(holder.image, item, false);
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RailHolder holder) {
            super.onViewAttachedToWindow(holder);
            int position = holder.getBindingAdapterPosition();
            if (position < 0 || position >= items.size()) return;
            NativeContentItem item = items.get(position);
            preloadArtwork(item);
            if (prewarmListener != null) prewarmListener.onPrewarm(item);
        }

        @Override
        public void onViewRecycled(@NonNull RailHolder holder) {
            Glide.with(holder.image).clear(holder.image);
            holder.card.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class Shelf {
        final LinearLayout container;
        final RecyclerView rail;
        final RailAdapter adapter;

        Shelf(LinearLayout container, RecyclerView rail, RailAdapter adapter) {
            this.container = container;
            this.rail = rail;
            this.adapter = adapter;
        }
    }

    private final class ContinueShelf {
        final LinearLayout container;
        final RecyclerView rail;
        final ContinueAdapter adapter;

        ContinueShelf(LinearLayout container, RecyclerView rail, ContinueAdapter adapter) {
            this.container = container;
            this.rail = rail;
            this.adapter = adapter;
        }
    }

    private static final class ContinueHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView title;
        final TextView meta;
        final View progressTrack;
        final View progressFill;

        ContinueHolder(
                MaterialCardView card,
                ImageView image,
                TextView title,
                TextView meta,
                View progressTrack,
                View progressFill
        ) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
            this.meta = meta;
            this.progressTrack = progressTrack;
            this.progressFill = progressFill;
        }
    }

    private static final class RailHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView title;

        RailHolder(MaterialCardView card, ImageView image, TextView title) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
        }
    }
}
