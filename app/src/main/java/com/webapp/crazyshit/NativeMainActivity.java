package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class NativeMainActivity extends Activity implements NativeMiniPlayer.Host {
    private static final int NAV_HOME = 1;
    private static final int NAV_SERIES = 2;
    @Deprecated private static final int NAV_CATEGORIES = 3;
    private static final int NAV_ONLYFAP = NAV_CATEGORIES;
    private static final int NAV_CHAOS = 4;
    private static final int NAV_MORE = 5;
    private static final int PLAYER_REQUEST = 3001;
    static final int FAVORITES_REQUEST = 3002;

    private enum Screen {
        HOME,
        SERIES,
        ONLYFAP,
        CHAOS,
        SEARCH
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CrazyShitRepository repository = new CrazyShitRepository();

    private FrameLayout overlayRoot;
    private LinearLayout shell;
    private TextView headerTitle;
    private TextView headerSubtitle;
    private RecyclerView recycler;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progress;
    private TextView emptyView;
    private BottomNavigationView bottomNavigation;
    private NativeFeedAdapter feedAdapter;
    private NativeMiniPlayer miniPlayer;
    private FrameLayout legacyContent;
    private ViewPager2 primaryPager;
    private MainPagerAdapter primaryPagerAdapter;
    private AppUpdater appUpdater;

    private Screen screen = Screen.HOME;
    private final Runnable ratingPromptCheck = () -> {
        if (screen != Screen.HOME || primaryPager == null
                || primaryPager.getCurrentItem() != MainPagerAdapter.PAGE_HOME
                || (miniPlayer != null && miniPlayer.isVisible())) return;
        RatingFeedbackPrompt.maybeShow(this);
    };
    private String feedBaseUrl = CrazyShitRepository.HOME;
    private String feedTitle = "Home";
    private int currentPage;
    private boolean loading;
    private boolean endReached;
    private int generation;
    private int restoredPrimaryPage = -1;
    private int portraitInsetLeft = -1;
    private int portraitInsetTop = -1;
    private int portraitInsetRight = -1;
    private int portraitInsetBottom = -1;
    private boolean restoringPortraitFromFullscreen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) restoredPrimaryPage = state.getInt("primary_page", -1);
        ZeroChillUi.applySystemBars(this);
        FeedViewStyleController.prepareVisualRefresh(this);
        buildUi();
        appUpdater = new AppUpdater(this);
        configureBack();

        if (!AccessNoticeDialog.isAccepted(this)) {
            AccessNoticeDialog.show(this, this::startAppContent);
        } else {
            startAppContent();
        }
    }

    private void startAppContent() {
        if (restoredPrimaryPage >= 0 && primaryPagerAdapter != null && restoredPrimaryPage < primaryPagerAdapter.getItemCount())
            showPrimaryPage(restoredPrimaryPage, false);
        else showHome();
        dispatchLauncherShortcut();
        NotificationCoordinator.maybeOfferPermission(this);
        scheduleRatingPromptCheck();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!AccessNoticeDialog.isAccepted(this)) {
            return;
        }
        showHome();
        dispatchLauncherShortcut();
    }

    private void buildUi() {
        overlayRoot = new FrameLayout(this);
        overlayRoot.setBackgroundColor(ZeroChillUi.background(this));

        shell = new FrostedNavigationLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(ZeroChillUi.background(this));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            boolean landscape = getResources().getConfiguration().orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            if (!(restoringPortraitFromFullscreen && landscape)) {
                applyShellInsets(view, insets, false);
            }
            return insets;
        });
        overlayRoot.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        shell.addView(buildTopBar(), new LinearLayout.LayoutParams(
                -1,
                ZeroChillUi.dimension(this, R.dimen.zc_top_bar_height)
        ));

        FrameLayout content = new FrameLayout(this);
        legacyContent = content;
        legacyContent.setVisibility(View.GONE);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 0f));

        primaryPagerAdapter = new MainPagerAdapter(this, new MainPagerAdapter.Host() {
            @Override
            public void onOpenItem(NativeContentItem item) {
                haptic(primaryPager);
                openNativeItem(item);
            }

            @Override
            public void onLongPressItem(NativeContentItem item, View anchor) {
                haptic(anchor);
                showItemMenu(item, anchor);
            }

            @Override
            public void onOpenComments(NativeContentItem item) {
                openComments(item);
            }
        });

        primaryPager = new ViewPager2(this);
        primaryPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        primaryPager.setOffscreenPageLimit(MainPagerAdapter.PAGE_COUNT - 1);
        primaryPager.setAdapter(primaryPagerAdapter);
        primaryPager.setPageTransformer((page, position) -> {
            if (ZeroChillMotion.animationsEnabled(page.getContext())) {
                float distance = Math.min(1f, Math.abs(position));
                page.setAlpha(1f - (distance * 0.10f));
                float scale = 1f - (distance * 0.012f);
                page.setScaleX(scale);
                page.setScaleY(scale);
            } else {
                page.setAlpha(1f);
                page.setScaleX(1f);
                page.setScaleY(1f);
            }
        });
        primaryPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showPagerChrome(position);
            }
        });
        shell.addView(primaryPager, new LinearLayout.LayoutParams(-1, 0, 1f));

        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setColorSchemeColors(UiPalette.PRIMARY);
        swipeRefresh.setOnRefreshListener(this::refreshCurrentScreen);
        content.addView(swipeRefresh, new FrameLayout.LayoutParams(-1, -1));

        recycler = new RecyclerView(this);
        recycler.setBackgroundColor(ZeroChillUi.background(this));
        recycler.setClipToPadding(false);
        recycler.setPadding(0, dp(5), 0, dp(18));
        recycler.setItemAnimator(null);
        swipeRefresh.addView(recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        ZeroChillUi.styleProgress(progress);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        content.addView(progress, progressParams);

        emptyView = new TextView(this);
        ZeroChillUi.styleEmpty(emptyView);
        emptyView.setVisibility(View.GONE);
        emptyView.setOnClickListener(v -> openFallback(feedBaseUrl));
        content.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        feedAdapter = new NativeFeedAdapter(this, new NativeFeedAdapter.Listener() {
            @Override
            public void onOpen(NativeContentItem item) {
                haptic(recycler);
                openNativeItem(item);
            }

            @Override
            public void onLongPress(NativeContentItem item, View anchor) {
                haptic(anchor);
                showItemMenu(item, anchor);
            }

            @Override
            public void onComments(NativeContentItem item) {
                haptic(recycler);
                openComments(item);
            }
        });

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView view, int dx, int dy) {
                if (!isFeedScreen()) return;
                RecyclerView.LayoutManager lm = view.getLayoutManager();
                if (!(lm instanceof LinearLayoutManager)) return;
                int first = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
                int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                feedAdapter.preloadVisible(first, last);
                if (dy > 0 && !loading && !endReached &&
                        last >= Math.max(0, feedAdapter.getItemCount() - 5)) {
                    loadFeed(true);
                }
            }
        });

        bottomNavigation = new ZeroChillBottomNavigationView(this);
        bottomNavigation.setBackground(ZeroChillUi.navigationGlass(this));
        bottomNavigation.setElevation(ZeroChillUi.dimension(this, R.dimen.zc_elevation_navigation));
        bottomNavigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        Menu menu = bottomNavigation.getMenu();
        menu.add(Menu.NONE, NAV_HOME, 0, "Home").setIcon(R.drawable.ic_nav_home);
        menu.add(Menu.NONE, NAV_SERIES, 1, "Shows").setIcon(R.drawable.ic_nav_series);
        menu.add(Menu.NONE, NAV_CHAOS, 2, "ShitTok").setIcon(R.drawable.ic_nav_chaos);
        menu.add(Menu.NONE, NAV_ONLYFAP, 3, "OnlyFap").setIcon(R.drawable.ic_nav_onlyfap);
        menu.add(Menu.NONE, NAV_MORE, 4, "More").setIcon(R.drawable.ic_nav_more);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (bottomNavigation instanceof ZeroChillBottomNavigationView &&
                    ((ZeroChillBottomNavigationView) bottomNavigation).isUserInteractionActive()) {
                View touched = bottomNavigation.findViewById(id);
                ZeroChillMotion.performSelectionHaptic(touched == null ? bottomNavigation : touched);
            }
            if (id == NAV_HOME) {
                showHome();
                return true;
            }
            if (id == NAV_SERIES) {
                showSeries();
                return true;
            }
            if (id == NAV_ONLYFAP) {
                showOnlyFapPage();
                return true;
            }
            if (id == NAV_CHAOS) {
                showPrimaryPage(MainPagerAdapter.PAGE_CHAOS, true);
                return true;
            }
            if (id == NAV_MORE) {
                showMoreSheet();
                return false;
            }
            return false;
        });
        shell.addView(bottomNavigation, new LinearLayout.LayoutParams(
                -1,
                ZeroChillUi.dimension(this, R.dimen.zc_bottom_nav_height)
        ));
        bottomNavigation.post(() -> {
            View chaosItem = bottomNavigation.findViewById(NAV_CHAOS);
            if (chaosItem != null) {
                chaosItem.setContentDescription("ShitTok featured tab");
            }
            if (bottomNavigation instanceof ZeroChillBottomNavigationView && primaryPager != null) {
                ((ZeroChillBottomNavigationView) bottomNavigation)
                        .setSettledPage(primaryPager.getCurrentItem());
            }
        });

        miniPlayer = new NativeMiniPlayer(this, overlayRoot, this);
        setContentView(overlayRoot);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), 0, dp(12), 0);
        bar.setBackgroundColor(ZeroChillUi.background(this));
        bar.setElevation(0f);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(0, 0, dp(8), 0);

        headerTitle = new TextView(this);
        ZeroChillUi.styleTitle(headerTitle);
        headerTitle.setTextSize(28f);
        headerTitle.setSingleLine(true);
        labels.addView(headerTitle);

        headerSubtitle = new TextView(this);
        headerSubtitle.setText(R.string.zerochill_tagline);
        ZeroChillUi.styleSecondary(headerSubtitle);
        headerSubtitle.setTextSize(11);
        labels.addView(headerSubtitle);
        headerSubtitle.setVisibility(View.GONE);
        bar.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView search = new ImageView(this);
        search.setImageResource(R.drawable.ic_nav_search);
        search.setPadding(dp(12), dp(12), dp(12), dp(12));
        search.setContentDescription("Search");
        search.setColorFilter(ZeroChillUi.color(this, R.color.zc_cyan));
        search.setBackground(ZeroChillUi.rounded(
                this,
                ZeroChillUi.color(this, R.color.zc_cyan_container),
                Color.TRANSPARENT,
                R.dimen.zc_radius_pill
        ));
        search.setClickable(true);
        search.setFocusable(true);
        ZeroChillMotion.installPressFeedback(search);
        search.setOnClickListener(v -> {
            haptic(v);
            openContextualSearch();
        });
        bar.addView(search, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return bar;
    }

    private void showHome() {
        showPrimaryPage(MainPagerAdapter.PAGE_HOME, true);
    }

    private void showSeries() {
        showPrimaryPage(MainPagerAdapter.PAGE_SERIES, true);
    }

    private void showOnlyFapPage() {
        showPrimaryPage(MainPagerAdapter.PAGE_ONLYFAP, true);
    }

    boolean isOnlyFapSearchContext() {
        return primaryPager != null &&
                primaryPager.getVisibility() == View.VISIBLE &&
                primaryPager.getCurrentItem() == MainPagerAdapter.PAGE_ONLYFAP;
    }

    void openContextualSearch() {
        Intent intent = isOnlyFapSearchContext()
                ? SearchActivity.createBunkrSearch(this)
                : new Intent(this, SearchActivity.class);
        startActivity(intent);
    }

    private void showPrimaryPage(int position, boolean smooth) {
        if (primaryPager == null) return;
        showPagerChrome(position);
        primaryPager.setCurrentItem(position, smooth && ZeroChillMotion.animationsEnabled(this));
    }

    private void showPagerChrome(int position) {
        if (legacyContent != null) {
            legacyContent.setVisibility(View.GONE);
            LinearLayout.LayoutParams old = (LinearLayout.LayoutParams) legacyContent.getLayoutParams();
            old.weight = 0f;
            old.height = 0;
            legacyContent.setLayoutParams(old);
        }
        if (primaryPager != null) {
            primaryPager.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams pp = (LinearLayout.LayoutParams) primaryPager.getLayoutParams();
            pp.weight = 1f;
            pp.height = 0;
            primaryPager.setLayoutParams(pp);
        }

        if (position == MainPagerAdapter.PAGE_SERIES) {
            screen = Screen.SERIES;
            feedBaseUrl = CrazyShitRepository.HOME;
            feedTitle = "Shows";
            selectNavSilently(NAV_SERIES);
        } else if (position == MainPagerAdapter.PAGE_ONLYFAP) {
            screen = Screen.ONLYFAP;
            feedBaseUrl = CrazyShitRepository.HOME;
            feedTitle = "OnlyFap";
            selectNavSilently(NAV_ONLYFAP);
        } else if (position == MainPagerAdapter.PAGE_CHAOS) {
            screen = Screen.CHAOS;
            feedBaseUrl = CrazyShitRepository.HOME;
            feedTitle = "ShitTok";
            selectNavSilently(NAV_CHAOS);
        } else {
            screen = Screen.HOME;
            feedBaseUrl = CrazyShitRepository.HOME;
            feedTitle = "Home";
            selectNavSilently(NAV_HOME);
        }

        if (primaryPagerAdapter != null) primaryPagerAdapter.setPrimaryActive(position);
        if (headerTitle != null) {
            if (position == MainPagerAdapter.PAGE_HOME) {
                setZeroChillWordmark();
            } else {
                headerTitle.setText(feedTitle);
                headerTitle.setTextColor(ZeroChillUi.color(this, R.color.zc_text_primary));
            }
        }
        if (headerSubtitle != null) headerSubtitle.setVisibility(View.GONE);
        applyChaosFullscreenChrome();
        if (position == MainPagerAdapter.PAGE_HOME) scheduleRatingPromptCheck();
    }

    private void setZeroChillWordmark() {
        SpannableString wordmark = new SpannableString("ZEROCHILL");
        wordmark.setSpan(
                new ForegroundColorSpan(ZeroChillUi.color(this, R.color.zc_text_primary)),
                0,
                4,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        wordmark.setSpan(
                new ForegroundColorSpan(ZeroChillUi.color(this, R.color.zc_cyan)),
                4,
                wordmark.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        headerTitle.setText(wordmark);
    }

    private void scheduleRatingPromptCheck() {
        if (overlayRoot == null) return;
        overlayRoot.removeCallbacks(ratingPromptCheck);
        overlayRoot.postDelayed(ratingPromptCheck, 1400L);
    }

    private void applyChaosFullscreenChrome() {
        boolean landscape = getResources().getConfiguration().orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        setChaosFullscreenChrome(screen == Screen.CHAOS && landscape);
    }

    private void setChaosFullscreenChrome(boolean fullscreen) {
        View topBar = null;
        if (headerTitle != null && headerTitle.getParent() instanceof View) {
            View labels = (View) headerTitle.getParent();
            if (labels.getParent() instanceof View) topBar = (View) labels.getParent();
            else topBar = labels;
        }
        if (topBar != null) topBar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int types = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
                if (fullscreen) {
                    controller.hide(types);
                    controller.setSystemBarsBehavior(
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    controller.show(types);
                    restoreShellInsetsAfterFullscreen();
                }
            }
        } else {
            if (fullscreen) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                restoreShellInsetsAfterFullscreen();
            }
        }
    }

    private void applyShellInsets(View view, WindowInsets insets, boolean includeHiddenSystemBars) {
        if (view == null || insets == null) return;
        int left;
        int top;
        int right;
        int bottom;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets safe = safeShellInsets(insets, includeHiddenSystemBars);
            left = safe.left;
            top = safe.top;
            right = safe.right;
            bottom = safe.bottom;
        } else {
            left = insets.getSystemWindowInsetLeft();
            top = insets.getSystemWindowInsetTop();
            right = insets.getSystemWindowInsetRight();
            bottom = insets.getSystemWindowInsetBottom();
        }
        view.setPadding(left, top, right, bottom);
        cachePortraitShellInsets(left, top, right, bottom);
    }

    private void cachePortraitShellInsets(int left, int top, int right, int bottom) {
        boolean portrait = getResources().getConfiguration().orientation !=
                android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (!portrait || top <= 0) return;
        portraitInsetLeft = left;
        portraitInsetTop = top;
        portraitInsetRight = right;
        portraitInsetBottom = bottom;
    }

    int cachedPortraitInsetTop() {
        return portraitInsetTop;
    }

    private boolean restoreCachedPortraitInsets() {
        if (shell == null || portraitInsetTop < 0) return false;
        shell.setPadding(
                portraitInsetLeft,
                portraitInsetTop,
                portraitInsetRight,
                portraitInsetBottom
        );
        return true;
    }

    static android.graphics.Insets safeShellInsets(
            WindowInsets insets,
            boolean includeHiddenSystemBars
    ) {
        int types = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
        return includeHiddenSystemBars
                ? insets.getInsetsIgnoringVisibility(types)
                : insets.getInsets(types);
    }

    private void restoreShellInsetsAfterFullscreen() {
        if (shell == null) return;
        restoringPortraitFromFullscreen = true;
        boolean restored = restoreCachedPortraitInsets();
        if (!restored && Build.VERSION.SDK_INT >= 30) {
            WindowInsets current = shell.getRootWindowInsets();
            if (current != null) applyShellInsets(shell, current, true);
        }
        shell.requestApplyInsets();
    }

    private void exitChaosFullscreenChrome() {
        setChaosFullscreenChrome(false);
    }

    private void showLegacyContent() {
        exitChaosFullscreenChrome();
        if (primaryPager != null) {
            primaryPager.setVisibility(View.GONE);
            LinearLayout.LayoutParams pp = (LinearLayout.LayoutParams) primaryPager.getLayoutParams();
            pp.weight = 0f;
            pp.height = 0;
            primaryPager.setLayoutParams(pp);
        }
        if (legacyContent != null) {
            legacyContent.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams old = (LinearLayout.LayoutParams) legacyContent.getLayoutParams();
            old.weight = 1f;
            old.height = 0;
            legacyContent.setLayoutParams(old);
        }
    }

    private void showSearch(String query) {
        showLegacyContent();
        screen = Screen.SEARCH;
        feedBaseUrl = repository.searchUrl(query);
        feedTitle = "Search: " + query;
        prepareFeed();
        loadFeed(false);
    }

    private void prepareFeed() {
        generation++;
        currentPage = 0;
        endReached = false;
        loading = false;
        headerTitle.setText(feedTitle);
        headerSubtitle.setText("Search results  •  " + viewModeLabel(currentViewMode()));
        applyFeedLayout();
        recycler.setAdapter(feedAdapter);
        feedAdapter.replace(new ArrayList<>());
        emptyView.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        recycler.scrollToPosition(0);
    }

    private void loadFeed(boolean append) {
        if (loading || endReached || !isLegacyFeedScreen()) return;
        loading = true;
        int requestPage = append ? currentPage + 1 : 1;
        String requestBase = feedBaseUrl;
        int requestGeneration = generation;
        if (!append && feedAdapter.getItemCount() == 0) progress.setVisibility(View.VISIBLE);

        io.execute(() -> {
            try {
                List<NativeContentItem> result = repository.fetchFeed(this, requestBase, requestPage);
                runOnUiThread(() -> {
                    if (requestGeneration != generation || !requestBase.equals(feedBaseUrl)) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (append) feedAdapter.append(result); else feedAdapter.replace(result);
                    if (!result.isEmpty()) currentPage = requestPage;
                    if (result.isEmpty()) endReached = true;
                    emptyView.setVisibility(View.GONE);
                    if (feedAdapter.getItemCount() == 0) {
                        showNativeEmpty("This feed couldn't be rendered natively.\nTap to open the website fallback.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (feedAdapter.getItemCount() == 0) {
                        showNativeEmpty("Couldn't load this feed.\nTap to open the website fallback.");
                    } else {
                        Toast.makeText(this, "Couldn't load more right now.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void refreshCurrentScreen() {
        if (!isLegacyFeedScreen()) return;
        generation++;
        currentPage = 0;
        endReached = false;
        loading = false;
        loadFeed(false);
    }

    private boolean isLegacyFeedScreen() {
        return screen == Screen.SEARCH && legacyContent != null && legacyContent.getVisibility() == View.VISIBLE;
    }

    private boolean isFeedScreen() {
        if (primaryPager != null && primaryPager.getVisibility() == View.VISIBLE) {
            return primaryPager.getCurrentItem() == MainPagerAdapter.PAGE_HOME;
        }
        return isLegacyFeedScreen();
    }

    private void openNativeItem(NativeContentItem item) {
        if (item == null || item.url.isEmpty()) return;
        progress.setVisibility(View.VISIBLE);
        final int requestGeneration = generation;
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = PlayableSourceRouter.resolve(this, item);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            runOnUiThread(() -> {
                if (requestGeneration != generation) return;
                progress.setVisibility(View.GONE);
                if (resolved != null && resolved.mediaUrl != null && !resolved.mediaUrl.isEmpty()) {
                    openVideoDetail(resolved, item);
                } else {
                    openFallback(item.url);
                }
            });
        });
    }

    private void openVideoDetail(CrazyShitRepository.StreamInfo stream, NativeContentItem item) {
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, stream.mediaUrl);
        intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, stream.pageUrl);
        intent.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, stream.requestReferer);
        intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, EfuktRepository.isEfuktUrl(stream.pageUrl)
                ? "efukt" : FapelloRepository.isFapelloUrl(stream.pageUrl) ? "bunkr" : "crazyshit");
        intent.putExtra(PlayerActivity.EXTRA_TITLE,
                item != null && item.title != null && !item.title.trim().isEmpty()
                        ? item.title : stream.title);
        if (item != null) {
            intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);
            intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);
            intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);
            if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
                intent.putExtra(VideoDetailActivity.EXTRA_POSTER_URL, item.imageUrl);
            }
        }
        try {
            intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, WebSettings.getDefaultUserAgent(this));
        } catch (Exception ignored) {
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(stream.mediaUrl);
            if ((cookies == null || cookies.isEmpty()) && stream.pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(stream.pageUrl);
            }
            if (cookies != null) intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
        } catch (Exception ignored) {
        }
        miniPlayer.stop();
        startActivityForResult(intent, PLAYER_REQUEST);
    }

    private void openFallback(String url) {
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(
                WebFallbackActivity.EXTRA_URL,
                url == null || url.isEmpty() ? CrazyShitRepository.HOME : url
        );
        startActivity(intent);
    }

    private void openComments(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        new InlineCommentsDialog(
                this,
                item.url,
                item.title,
                item.comments,
                null
        ).show();
    }

    private String viewPreferenceKey() {
        if (screen == Screen.SEARCH) return "native_view_search";
        return "native_view_home";
    }

    private int currentViewMode() {
        if (primaryPager != null && primaryPager.getVisibility() == View.VISIBLE && primaryPagerAdapter != null) {
            return primaryPagerAdapter.viewMode(primaryPager.getCurrentItem());
        }
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getInt(viewPreferenceKey(), NativeFeedAdapter.VIEW_LIST);
    }

    private String viewModeLabel(int mode) {
        if (mode == NativeFeedAdapter.VIEW_LIST) return "List";
        if (mode == NativeFeedAdapter.VIEW_GRID) return "Grid";
        if (mode == NativeFeedAdapter.VIEW_POSTERS) return "Posters";
        return "Cards";
    }

    private void applyFeedLayout() {
        int mode = currentViewMode();
        if (primaryPager != null && primaryPager.getVisibility() == View.VISIBLE && primaryPagerAdapter != null) {
            primaryPagerAdapter.setViewMode(primaryPager.getCurrentItem(), mode);
            return;
        }
        feedAdapter.setViewMode(mode);
        if (mode == NativeFeedAdapter.VIEW_GRID || mode == NativeFeedAdapter.VIEW_POSTERS) {
            recycler.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            recycler.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private void showViewStyleDialog() {
        if (!isFeedScreen()) {
            Toast.makeText(this, "View styles apply to feeds.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] choices = {"Cards", "List", "Grid", "Posters"};
        int selected = currentViewMode();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("View style")
                .setSingleChoiceItems(choices, selected, null)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checked = dialog.getListView().getCheckedItemPosition();
            if (checked < NativeFeedAdapter.VIEW_CARDS || checked > NativeFeedAdapter.VIEW_POSTERS) {
                checked = NativeFeedAdapter.VIEW_LIST;
            }
            if (primaryPager != null && primaryPager.getVisibility() == View.VISIBLE && primaryPagerAdapter != null) {
                primaryPagerAdapter.setViewMode(primaryPager.getCurrentItem(), checked);
            } else {
                getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit()
                        .putInt(viewPreferenceKey(), checked)
                        .apply();
                feedAdapter.setViewMode(checked);
                applyFeedLayout();
            }
            headerSubtitle.setText("Home  •  " + viewModeLabel(checked));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showItemMenu(NativeContentItem item, View anchor) {
        String saveTitle = FavoriteStore.contains(this, item.url)
                ? "Remove from Watch Later"
                : "Save to Watch Later";
        ArrayList<VideoActionSheet.Action> actions = new ArrayList<>();
        if (item.comments != null && !item.comments.isEmpty()) {
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_comments,
                    "Comments",
                    item.comments + " ready to view",
                    () -> openComments(item)
            ));
        }
        actions.add(VideoActionSheet.action(
                R.drawable.ic_action_share,
                "Share",
                "Send the source page",
                () -> shareItem(item)
        ));
        actions.add(VideoActionSheet.action(
                R.drawable.ic_more_website,
                "Open website page",
                "Use the compatibility browser",
                () -> openFallback(item.url)
        ));

        VideoActionSheet.show(
                this,
                item.title,
                VideoActionSheet.section(
                        "SAVE",
                        VideoActionSheet.action(
                                R.drawable.ic_action_download,
                                "Download",
                                "Save this video for offline playback",
                                () -> VideoDownloadStore.downloadPage(this, item)
                        ),
                        VideoActionSheet.action(
                                R.drawable.ic_more_library,
                                saveTitle,
                                "Keep this video in your library",
                                () -> toggleWatchLater(item)
                        )
                ),
                VideoActionSheet.section(
                        "ACTIONS",
                        actions.toArray(new VideoActionSheet.Action[0])
                )
        );
    }

    private void toggleWatchLater(NativeContentItem item) {
        if (FavoriteStore.contains(this, item.url)) {
            FavoriteStore.remove(this, item.url);
            Toast.makeText(this, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(this, item.title, item.url);
            Toast.makeText(this, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareItem(NativeContentItem item) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, item.url);
        share.putExtra(Intent.EXTRA_SUBJECT, item.title);
        startActivity(Intent.createChooser(share, "Share"));
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("Search ZeroChill");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad = dp(18);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Search")
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search", (d, which) -> {
                    String query = input.getText().toString().trim();
                    if (!query.isEmpty()) showSearch(query);
                })
                .create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                );
            }
        });
        dialog.show();
    }

    private void showMoreSheet() {
        LandscapeMoreDialog.show(this);
    }

    private void dispatchLauncherShortcut() {
        Intent launchIntent = getIntent();
        String action = launchIntent == null ? null : launchIntent.getAction();
        if (!AppShortcuts.isShortcutAction(action)) return;
        if (launchIntent.getBooleanExtra(AppShortcuts.EXTRA_SHORTCUT_ROUTED, false)) return;

        launchIntent.putExtra(AppShortcuts.EXTRA_SHORTCUT_ROUTED, true);
        AppShortcuts.reportUsed(this, action);
        if (AppShortcuts.ACTION_CHAOS.equals(action)) {
            showPrimaryPage(MainPagerAdapter.PAGE_CHAOS, false);
            return;
        }
        if (AppShortcuts.ACTION_SEARCH.equals(action)) {
            overlayRoot.post(this::openContextualSearch);
            return;
        }

        int startTab = AppShortcuts.ACTION_WATCH_LATER.equals(action)
                ? FavoritesActivity.START_WATCH_LATER
                : FavoritesActivity.START_CONTINUE;
        Intent library = new Intent(this, FavoritesActivity.class);
        library.putExtra(FavoritesActivity.EXTRA_START_TAB, startTab);
        overlayRoot.post(() -> startActivityForResult(library, FAVORITES_REQUEST));
    }

    private void showNativeEmpty(String text) {
        emptyView.setText(text);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void checkForUpdates(boolean manual) {
        if (appUpdater != null) appUpdater.check(manual);
    }

    private void configureBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackNavigation
            );
        }
    }

    private void handleBackNavigation() {
        if (screen == Screen.CHAOS && primaryPagerAdapter != null
                && primaryPagerAdapter.exitChaosFullscreenForBack()) {
            return;
        }
        if (legacyContent != null && legacyContent.getVisibility() == View.VISIBLE) {
            showHome();
            return;
        }
        if (primaryPager != null && primaryPager.getCurrentItem() != MainPagerAdapter.PAGE_HOME) {
            showHome();
            return;
        }
        finish();
    }

    private void selectNavSilently(int id) {
        if (bottomNavigation == null || bottomNavigation.getSelectedItemId() == id) return;
        android.view.MenuItem item = bottomNavigation.getMenu().findItem(id);
        if (item != null) item.setChecked(true);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (primaryPagerAdapter != null) primaryPagerAdapter.onConfigurationChanged();
        if (newConfig.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && restoringPortraitFromFullscreen) {
            restoreCachedPortraitInsets();
            restoringPortraitFromFullscreen = false;
            if (shell != null) {
                shell.requestApplyInsets();
                shell.post(shell::requestApplyInsets);
            }
        }
        applyChaosFullscreenChrome();
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PLAYER_REQUEST) {
            if (resultCode == RESULT_OK && data != null &&
                    data.getBooleanExtra(PlayerActivity.EXTRA_MINIMIZED, false)) {
                miniPlayer.start(data);
                return;
            }
            if (data != null) {
                String fallback = data.getStringExtra(PlayerActivity.EXTRA_FALLBACK_PAGE);
                if (fallback != null && !fallback.isEmpty()) openFallback(fallback);
            }
            return;
        }

        if (requestCode == FAVORITES_REQUEST && resultCode == RESULT_OK && data != null) {
            String selected = data.getStringExtra(FavoritesActivity.EXTRA_SELECTED_URL);
            if (selected != null && !selected.isEmpty()) {
                openNativeItem(new NativeContentItem(
                        NativeContentItem.KIND_MEDIA,
                        "Saved video",
                        selected,
                        "",
                        "",
                        "",
                        ""
                ));
            }
        }
    }

    @Override
    public void reopenMiniPlayer(Intent intent) {
        startActivityForResult(intent, PLAYER_REQUEST);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (primaryPager != null) state.putInt("primary_page", primaryPager.getCurrentItem());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (overlayRoot != null) overlayRoot.removeCallbacks(ratingPromptCheck);
        if (miniPlayer != null) miniPlayer.onPause();
        if (primaryPagerAdapter != null) primaryPagerAdapter.onHostPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (miniPlayer != null) miniPlayer.onResume();
        if (primaryPagerAdapter != null) primaryPagerAdapter.onHostResume();
        if (appUpdater != null) {
            appUpdater.onHostResume();
        }
        applyChaosFullscreenChrome();
        scheduleRatingPromptCheck();
    }

    @Override
    protected void onDestroy() {
        if (miniPlayer != null) miniPlayer.stop();
        if (primaryPagerAdapter != null) primaryPagerAdapter.close();
        if (feedAdapter != null) feedAdapter.close();
        if (appUpdater != null) appUpdater.close();
        io.shutdownNow();
        super.onDestroy();
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
}
