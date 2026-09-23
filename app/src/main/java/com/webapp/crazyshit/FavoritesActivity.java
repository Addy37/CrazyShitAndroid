package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Local Library with three eagerly built pages. BETA14_LIBRARY_SWIPE is kept as a
 * marker so the old gesture-patch is skipped. ViewPager2 now owns the drag itself.
 */
public class FavoritesActivity extends Activity {
    public static final String EXTRA_SELECTED_URL = "selected_url";
    public static final String EXTRA_START_TAB = "start_tab";
    public static final int START_CONTINUE = 0;
    public static final int START_HISTORY = 1;
    public static final int START_WATCH_LATER = 2;

    private static final int TAB_CONTINUE = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_WATCH_LATER = 2;
    private static final int PAGE_COUNT = 3;
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final LinearLayout[] listContainers = new LinearLayout[PAGE_COUNT];
    private final View[] pageViews = new View[PAGE_COUNT];
    private MaterialButton continueTab;
    private MaterialButton historyTab;
    private MaterialButton watchLaterTab;
    private TextView clearAction;
    private ViewPager2 pager;
    private int tab = TAB_CONTINUE;

    private final Map<String, List<ImageView>> thumbnailTargets = new HashMap<>();
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final Set<String> requestedThumbnails = new HashSet<>();
    private RenderedThumbnailResolver[] thumbnailResolvers;
    private int resolverCursor;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        tab = getIntent() == null
                ? TAB_CONTINUE
                : getIntent().getIntExtra(EXTRA_START_TAB, TAB_CONTINUE);
        if (tab < TAB_CONTINUE || tab > TAB_WATCH_LATER) tab = TAB_CONTINUE;
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(this, this::onThumbnailResolved),
                new RenderedThumbnailResolver(this, this::onThumbnailResolved)
        };
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAllPages();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 13, 15));
        root.setPadding(dp(18), dp(18), dp(18), dp(24));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton back = new MaterialButton(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setContentDescription("Back");
        back.setMinWidth(dp(48));
        back.setMinimumWidth(dp(48));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("Library", 28, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitle = text("Continue Watching • History • Watch Later", 12, Color.rgb(170, 170, 180));
        heading.addView(title);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        clearAction = text("CLEAR", 12, UiPalette.PRIMARY);
        clearAction.setTypeface(null, android.graphics.Typeface.BOLD);
        clearAction.setGravity(Gravity.CENTER);
        clearAction.setPadding(dp(10), dp(10), dp(10), dp(10));
        clearAction.setClickable(true);
        clearAction.setOnClickListener(v -> confirmClear());
        header.addView(clearAction, new LinearLayout.LayoutParams(dp(62), dp(52)));
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(12), 0, dp(8));
        continueTab = tabButton("Continue", TAB_CONTINUE);
        historyTab = tabButton("History", TAB_HISTORY);
        watchLaterTab = tabButton("Watch Later", TAB_WATCH_LATER);
        tabs.addView(continueTab, tabParams());
        tabs.addView(historyTab, tabParams());
        tabs.addView(watchLaterTab, tabParams());
        root.addView(tabs);

        for (int i = 0; i < PAGE_COUNT; i++) pageViews[i] = buildPage(i);

        pager = new ViewPager2(this);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setOffscreenPageLimit(PAGE_COUNT - 1);
        pager.setAdapter(new LibraryPagerAdapter());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tab = position;
                updateTabs();
            }
        });
        pager.setCurrentItem(tab, false);
        root.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        updateTabs();
    }

    private View buildPage(int index) {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.rgb(13, 13, 15));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, dp(24));
        listContainers[index] = list;
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return page;
    }

    private MaterialButton tabButton(String label, int target) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(v -> {
            if (pager == null || pager.getCurrentItem() == target) return;
            haptic(v);
            pager.setCurrentItem(target, true);
        });
        return button;
    }

    private LinearLayout.LayoutParams tabParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private void updateTabs() {
        continueTab.setEnabled(tab != TAB_CONTINUE);
        historyTab.setEnabled(tab != TAB_HISTORY);
        watchLaterTab.setEnabled(tab != TAB_WATCH_LATER);
        clearAction.setText("CLEAR");
    }

    private void renderAllPages() {
        thumbnailTargets.clear();
        for (LinearLayout list : listContainers) {
            if (list != null) list.removeAllViews();
        }
        renderHistory(listContainers[TAB_CONTINUE], true);
        renderHistory(listContainers[TAB_HISTORY], false);
        renderWatchLater(listContainers[TAB_WATCH_LATER]);
    }

    private void renderHistory(LinearLayout target, boolean continueOnly) {
        if (target == null) return;
        List<PlaybackHistoryStore.Item> items = continueOnly
                ? PlaybackHistoryStore.continueWatching(this)
                : PlaybackHistoryStore.load(this);

        if (items.isEmpty()) {
            showEmpty(
                    target,
                    continueOnly ? "Nothing to continue" : "No watch history yet",
                    continueOnly
                            ? "Videos watched for at least 30 seconds appear here until they're nearly finished."
                            : "Videos you watch in the native player will appear here."
            );
            return;
        }

        for (PlaybackHistoryStore.Item item : items) {
            target.addView(makeHistoryCard(item, continueOnly), cardParams());
        }
    }

    private MaterialCardView makeHistoryCard(PlaybackHistoryStore.Item item, boolean continueOnly) {
        MaterialCardView card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(8), dp(8), dp(10), dp(8));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> select(item.pageUrl));

        row.addView(makeThumbnail(item.pageUrl), new LinearLayout.LayoutParams(dp(138), dp(88)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), dp(5), 0, 0);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text(item.title, 15, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);

        int percent = item.progressPercent();
        String progressText;
        if (item.complete) {
            progressText = "Finished";
        } else if (item.durationMs > 0L) {
            progressText = formatTime(item.positionMs) + " / " + formatTime(item.durationMs) + "  •  " + percent + "%";
        } else {
            progressText = formatTime(item.positionMs) + " watched";
        }
        TextView progressLabel = text(progressText, 12, Color.rgb(190, 190, 198));
        progressLabel.setPadding(0, dp(5), 0, 0);
        copy.addView(progressLabel);

        if (item.durationMs > 0L && !item.complete) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(percent);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(4));
            barParams.setMargins(0, dp(6), 0, dp(2));
            copy.addView(bar, barParams);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(5), 0, 0);

        String watched = item.lastWatched > 0L
                ? "Watched " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(item.lastWatched))
                : "Watched";
        TextView date = text(watched, 10, Color.rgb(135, 135, 145));
        date.setMaxLines(1);
        date.setEllipsize(TextUtils.TruncateAt.END);
        footer.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton remove = compactAction(continueOnly ? "Remove" : "Delete");
        remove.setOnClickListener(v -> {
            haptic(v);
            PlaybackHistoryStore.remove(this, item.pageUrl);
            renderAllPages();
        });
        footer.addView(remove, new LinearLayout.LayoutParams(-2, dp(38)));
        copy.addView(footer);

        card.addView(row);
        return card;
    }

    private void renderWatchLater(LinearLayout target) {
        if (target == null) return;
        List<FavoriteStore.Item> items = FavoriteStore.load(this);
        if (items.isEmpty()) {
            showEmpty(target, "Nothing saved yet", "Long-press a video card and choose Save to Watch Later.");
            return;
        }
        for (FavoriteStore.Item item : items) {
            target.addView(makeWatchLaterCard(item), cardParams());
        }
    }

    private MaterialCardView makeWatchLaterCard(FavoriteStore.Item item) {
        MaterialCardView card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(8), dp(8), dp(10), dp(8));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> select(item.url));

        row.addView(makeThumbnail(item.url), new LinearLayout.LayoutParams(dp(138), dp(88)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), dp(5), 0, 0);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text(item.title, 15, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);

        String date = item.savedAt > 0L
                ? "Saved " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(item.savedAt))
                : "Saved";
        TextView saved = text(date, 11, Color.rgb(145, 145, 155));
        saved.setPadding(0, dp(6), 0, 0);
        saved.setMaxLines(1);
        saved.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(saved);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(8), 0, 0);
        MaterialButton remove = compactAction("Remove");
        remove.setOnClickListener(v -> {
            haptic(v);
            FavoriteStore.remove(this, item.url);
            Toast.makeText(this, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
            renderAllPages();
        });
        actions.addView(remove, new LinearLayout.LayoutParams(-2, dp(38)));
        copy.addView(actions);

        card.addView(row);
        return card;
    }

    private FrameLayout makeThumbnail(String pageUrl) {
        FrameLayout media = new FrameLayout(this);
        media.setBackgroundColor(Color.rgb(18, 18, 21));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(18, 18, 21));
        media.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView play = text("▶", 20, Color.WHITE);
        play.setGravity(Gravity.CENTER);
        play.setBackground(new ColorDrawable(Color.argb(115, 0, 0, 0)));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(38), dp(38));
        playParams.gravity = Gravity.CENTER;
        media.addView(play, playParams);

        thumbnailTargets.computeIfAbsent(pageUrl, key -> new ArrayList<>()).add(image);
        String resolved = resolvedThumbnails.get(pageUrl);
        if (resolved != null && !resolved.isEmpty()) {
            loadThumbnail(image, resolved, pageUrl);
        } else {
            requestThumbnail(pageUrl);
        }
        return media;
    }

    private void requestThumbnail(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty() || thumbnailResolvers == null) return;
        if (!requestedThumbnails.add(pageUrl)) return;
        RenderedThumbnailResolver resolver = thumbnailResolvers[resolverCursor++ % thumbnailResolvers.length];
        resolver.request(pageUrl);
    }

    private void onThumbnailResolved(String pageUrl, String thumbnailUrl) {
        if (pageUrl == null || pageUrl.isEmpty() || thumbnailUrl == null || thumbnailUrl.isEmpty()) return;
        resolvedThumbnails.put(pageUrl, thumbnailUrl);
        List<ImageView> targets = thumbnailTargets.get(pageUrl);
        if (targets == null) return;
        for (ImageView target : new ArrayList<>(targets)) {
            loadThumbnail(target, thumbnailUrl, pageUrl);
        }
    }

    private void loadThumbnail(ImageView image, String imageUrl, String pageUrl) {
        if (isFinishing() || image == null || imageUrl == null || imageUrl.isEmpty()) return;
        Object source = imageUrl.startsWith("file://") ? imageUrl : withSiteHeaders(imageUrl, pageUrl);
        try {
            Glide.with(image)
                    .load(source)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(18, 18, 21)))
                    .error(new ColorDrawable(Color.rgb(18, 18, 21)))
                    .into(image);
        } catch (Exception ignored) {
        }
    }

    private GlideUrl withSiteHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null || pageUrl.isEmpty() ? SITE : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(pageUrl == null ? SITE : pageUrl);
            }
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(SITE);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private MaterialButton compactAction(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private void showEmpty(LinearLayout target, String titleValue, String bodyValue) {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(72), dp(24), dp(24));
        TextView title = text(titleValue, 20, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView body = text(bodyValue, 14, Color.rgb(170, 170, 180));
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(8), 0, 0);
        empty.addView(title);
        empty.addView(body);
        target.addView(empty);
    }

    private void select(String url) {
        haptic(pager == null ? clearAction : pager);
        Intent data = new Intent();
        data.putExtra(EXTRA_SELECTED_URL, url);
        setResult(RESULT_OK, data);
        finish();
    }

    private void confirmClear() {
        boolean watchLater = tab == TAB_WATCH_LATER;
        new AlertDialog.Builder(this)
                .setTitle(watchLater ? "Clear Watch Later?" : "Clear watch history?")
                .setMessage(watchLater
                        ? "This removes every saved Watch Later item from this device."
                        : "This clears History and Continue Watching from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    if (watchLater) FavoriteStore.clear(this);
                    else PlaybackHistoryStore.clear(this);
                    renderAllPages();
                })
                .show();
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(24, 24, 28));
        card.setRadius(dp(20));
        card.setStrokeWidth(1);
        card.setStrokeColor(Color.rgb(45, 45, 52));
        card.setCardElevation(0f);
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String formatTime(long milliseconds) {
        long total = Math.max(0L, milliseconds) / 1000L;
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0L) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void haptic(View view) {
        if (view == null) return;
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class LibraryPagerAdapter extends RecyclerView.Adapter<PageHolder> {
        @Override
        public int getItemCount() {
            return PAGE_COUNT;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            return new PageHolder(container);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            View page = pageViews[position];
            if (page.getParent() instanceof ViewGroup) {
                ((ViewGroup) page.getParent()).removeView(page);
            }
            holder.container.removeAllViews();
            holder.container.addView(page, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;

        PageHolder(FrameLayout container) {
            super(container);
            this.container = container;
        }
    }
}
