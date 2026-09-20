package com.webapp.crazyshit;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps Home, Shows, ShitTok and OnlyFap alive for true horizontal paging.
 * Chaos itself owns a nested vertical ViewPager2 for Shorts/Reels-style playback.
 */
public final class MainPagerAdapter extends RecyclerView.Adapter<MainPagerAdapter.Holder> {
    public static final int PAGE_HOME = 0;
    public static final int PAGE_SERIES = 1;
    public static final int PAGE_CHAOS = 2;
    /** Stable slot 3 is preserved for upgrades; it now hosts the public OnlyFap tab. */
    public static final int PAGE_ONLYFAP = 3;
    @Deprecated public static final int PAGE_LIBRARY = PAGE_ONLYFAP;
    @Deprecated public static final int PAGE_CATEGORIES = PAGE_ONLYFAP;
    public static final int PAGE_COUNT = 4;
    private static final int PAGE_ARRAY_COUNT = 4;
    private static final int SERIES_SOURCE_CRAZYSHIT = 0;
    private static final int SERIES_SOURCE_EFUKT = 1;
    private static final int SERIES_SOURCE_BUNKR = 2;
    private static final int SERIES_SOURCE_CATEGORIES = 3;
    private static final String PREF_SERIES_SOURCE = "native_series_source";
    private static final String PREF_FAPZONE_MODE = "native_fapzone_mode";

    public interface Host {
        void onOpenItem(NativeContentItem item);
        void onLongPressItem(NativeContentItem item, View anchor);
        void onOpenComments(NativeContentItem item);
    }

    private enum PageKind {
        FEED,
        SERIES,
        CATEGORIES,
        ONLYFAP
    }

    private final Activity activity;
    private final Host host;
    private HomeSourceRepository homeRepository = new HomeSourceRepository();
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final BrowseRepository browseRepository = new BrowseRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final FapzoneCreatorRepository fapzoneCreatorRepository =
            new FapzoneCreatorRepository();
    private final BrowseArtworkResolver browseArtworkResolver;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Page[] pages = new Page[PAGE_ARRAY_COUNT];
    private final ChaosFeedView chaosView;

    public MainPagerAdapter(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.browseArtworkResolver = new BrowseArtworkResolver(activity);
        setHasStableIds(true);

        pages[PAGE_HOME] = buildFeedPage(PAGE_HOME, "native_view_home", CrazyShitRepository.HOME);
        pages[PAGE_SERIES] = buildBrowsePage(PAGE_SERIES, PageKind.SERIES);
        pages[PAGE_ONLYFAP] = buildBrowsePage(PAGE_ONLYFAP, PageKind.ONLYFAP);

        chaosView = new ChaosFeedView(activity, new ChaosFeedView.Host() {
            @Override
            public void openDetails(NativeContentItem item) {
                host.onOpenItem(item);
            }
        });
        chaosView.setActive(false);

    }

    public String titleFor(int position) {
        if (position == PAGE_SERIES) return "Shows";
        if (position == PAGE_ONLYFAP) return "OnlyFap";
        if (position == PAGE_CHAOS) return "ShitTok";
        return "Home";
    }

    public int viewMode(int position) {
        if (position == PAGE_CHAOS) return NativeFeedAdapter.VIEW_CARDS;
        Page page = pageAt(position);
        if (page == null) return NativeFeedAdapter.VIEW_LIST;
        if (page.kind != PageKind.FEED) return NativeFeedAdapter.VIEW_GRID;
        return page.viewMode;
    }

    public void setViewMode(int position, int mode) {
        if (position == PAGE_CHAOS) return;
        Page page = pageAt(position);
        if (page == null || page.kind != PageKind.FEED) return;

        int safe = mode;
        if (safe < NativeFeedAdapter.VIEW_CARDS || safe > NativeFeedAdapter.VIEW_POSTERS) {
            safe = NativeFeedAdapter.VIEW_LIST;
        }
        page.viewMode = safe;
        activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .edit()
                .putInt(page.preferenceKey, safe)
                .apply();
        applyFeedLayout(page);
    }

    public void refresh(int position) {
        if (position == PAGE_CHAOS) {
            chaosView.refresh();
            return;
        }
        Page page = pageAt(position);
        if (page == null) return;
        page.generation++;
        if (page.loadTask != null) page.loadTask.cancel(true);
        page.empty.setVisibility(View.GONE);
        page.currentPage = 0;
        page.endReached = false;
        page.loading = false;
        load(page, false);
    }

    public void setPrimaryActive(int position) {
        chaosView.setActive(position == PAGE_CHAOS);
        if (position == PAGE_CHAOS) return;
        Page page = pageAt(position);
        if (page != null && page.itemCount() == 0 && !page.loading && !page.endReached) {
            load(page, false);
        }
    }

    public void onHostResume() {
        chaosView.onHostResume();
        Page onlyFap = pageAt(PAGE_ONLYFAP);
        if (onlyFap != null && onlyFap.browseAdapter != null) onlyFap.browseAdapter.notifyDataSetChanged();
        Page home = pageAt(PAGE_HOME);
        if (home != null && home.feedAdapter != null) home.feedAdapter.refreshPlaybackState();
    }

    public void onHostPause() {
        chaosView.onHostPause();
    }

    public void onConfigurationChanged() {
        chaosView.onConfigurationChanged();
    }

    public boolean exitChaosFullscreenForBack() {
        return chaosView.exitSensorFullscreenForBack();
    }

    public void close() {
        chaosView.close();
        browseArtworkResolver.close();
        for (Page page : pages) {
            if (page == null) continue;
            page.generation++;
            if (page.loadTask != null) page.loadTask.cancel(true);
            if (page.browseAdapter != null) page.browseAdapter.close();
            if (page.feedAdapter != null) page.feedAdapter.close();
        }
        io.shutdownNow();
    }

    @Override
    public long getItemId(int position) {
        return 10_000L + position;
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(parent.getContext());
        container.setBackgroundColor(ZeroChillUi.background(activity));
        container.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
        return new Holder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        View pageView;
        if (position == PAGE_CHAOS) {
            pageView = chaosView;
        } else {
            Page page = pageAt(position);
            if (page == null) return;
            pageView = page.root;
        }
        if (pageView.getParent() instanceof ViewGroup) {
            ((ViewGroup) pageView.getParent()).removeView(pageView);
        }
        holder.container.removeAllViews();
        holder.container.addView(pageView, new FrameLayout.LayoutParams(-1, -1));
    }

    private Page buildFeedPage(int index, String prefKey, String baseUrl) {
        Page page = createPageShell(index, PageKind.FEED, prefKey, baseUrl);
        page.feedAdapter = new NativeFeedAdapter(activity, new NativeFeedAdapter.Listener() {
            @Override
            public void onOpen(NativeContentItem item) {
                if (item == null || item.isSection()) return;
                host.onOpenItem(item);
            }

            @Override
            public void onLongPress(NativeContentItem item, View anchor) {
                if (item == null || item.isSection()) return;
                host.onLongPressItem(item, anchor);
            }

            @Override
            public void onComments(NativeContentItem item) {
                if (item == null || item.isSection()) return;
                host.onOpenComments(item);
            }
        });
        page.recycler.setAdapter(page.feedAdapter);
        android.content.SharedPreferences homePrefs =
                activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        int savedHomeSource = homePrefs.getInt("home_source", 1);
        page.homeSource = savedHomeSource == 2 ? 2 : 1;
        if (savedHomeSource != page.homeSource) {
            homePrefs.edit().putInt("home_source", page.homeSource).apply();
        }
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setBackground(ZeroChillUi.panelGlass(activity));
        scroll.setElevation(ZeroChillUi.dimension(activity, R.dimen.zc_elevation_low));
        LinearLayout sources = new LinearLayout(activity);
        sources.setClipChildren(false);
        sources.setClipToPadding(false);
        sources.setGravity(Gravity.CENTER_VERTICAL);
        sources.setPadding(dp(12), dp(6), dp(12), dp(6));
        String[] names = {"CrazyShit", "EFukt"};
        int[] sourceIds = {1, 2};
        for (int chipIndex = 0; chipIndex < names.length; chipIndex++) {
            final int selected = sourceIds[chipIndex];
            TextView chip = BrowseUi.action(activity, names[chipIndex], names[chipIndex] + " Home feed", v -> {
                if (page.homeSource == selected) {
                    if (!page.loading && page.itemCount() == 0) refresh(page.index);
                    return;
                }
                page.homeSource = selected;
                homePrefs.edit().putInt("home_source", selected).apply();
                styleHomeSources(page);
                page.feedAdapter.replace(java.util.Collections.emptyList());
                page.recycler.scrollToPosition(0);
                refresh(page.index);
            });
            chip.setTextSize(13);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
            params.setMarginEnd(dp(6));
            sources.addView(chip, params);
            page.homeChips.add(chip);
        }
        scroll.addView(sources);
        FrameLayout.LayoutParams feedParams = (FrameLayout.LayoutParams) page.refresh.getLayoutParams();
        feedParams.topMargin = dp(60);
        page.refresh.setLayoutParams(feedParams);
        FrameLayout.LayoutParams sourceParams = new FrameLayout.LayoutParams(-1, dp(56));
        sourceParams.setMargins(dp(8), dp(2), dp(8), dp(2));
        page.root.addView(scroll, sourceParams);
        FrameLayout.LayoutParams emptyParams = (FrameLayout.LayoutParams) page.empty.getLayoutParams();
        emptyParams.topMargin = dp(60);
        page.empty.setLayoutParams(emptyParams);
        page.empty.setOnClickListener(v -> refresh(page.index));
        page.empty.setContentDescription("Retry Home feed");
        styleHomeSources(page);
        page.viewMode = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getInt(prefKey, NativeFeedAdapter.VIEW_CARDS);
        if (page.viewMode < NativeFeedAdapter.VIEW_CARDS || page.viewMode > NativeFeedAdapter.VIEW_POSTERS) {
            page.viewMode = NativeFeedAdapter.VIEW_LIST;
        }
        applyFeedLayout(page);

        page.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                RecyclerView.LayoutManager manager = view.getLayoutManager();
                if (!(manager instanceof LinearLayoutManager)) return;
                LinearLayoutManager lm = (LinearLayoutManager) manager;
                int first = lm.findFirstVisibleItemPosition();
                int last = lm.findLastVisibleItemPosition();
                page.feedAdapter.preloadVisible(first, last);
                if (dy > 0 && !page.loading && !page.endReached &&
                        last >= Math.max(0, page.feedAdapter.getItemCount() - 5)) {
                    load(page, true);
                }
            }
        });
        return page;
    }

    private Page buildBrowsePage(int index, PageKind kind) {
        Page page = createPageShell(index, kind, "", "");
        page.browseAdapter = new NativeCategoryAdapter(activity, item -> {
            if (item == null) return;
            if (item.isCreator()) {
                String query = item.searchQuery == null || item.searchQuery.trim().isEmpty()
                        ? item.title
                        : item.searchQuery.trim();
                activity.startActivity(NativeFeedBrowserActivity.createCreatorGallery(
                        activity,
                        item.title,
                        query,
                        FapelloRepository.isModelUrl(item.url) ? item.url : ""
                ));
                return;
            }
            if (item.url == null || item.url.isEmpty()) return;
            String source = BunkrRepository.isAlbumUrl(item.url)
                    ? NativeFeedBrowserActivity.SOURCE_BUNKR
                    : EfuktRepository.isEfuktUrl(item.url)
                    ? NativeFeedBrowserActivity.SOURCE_EFUKT
                    : NativeFeedBrowserActivity.SOURCE_CRAZYSHIT;
            activity.startActivity(NativeFeedBrowserActivity.create(
                    activity,
                    item.title,
                    item.url,
                    false,
                    source
            ));
        });
        page.recycler.setAdapter(page.browseAdapter);
        page.recycler.setLayoutManager(new GridLayoutManager(activity, 2));
        if (kind == PageKind.SERIES) {
            addSeriesSourceSelector(page);
            page.empty.setOnClickListener(v -> {
                String url = page.seriesSource == SERIES_SOURCE_EFUKT
                        ? EfuktRepository.SERIES
                        : page.seriesSource == SERIES_SOURCE_CATEGORIES
                        ? BrowseRepository.CATEGORIES
                        : BrowseRepository.SERIES;
                android.content.Intent intent = new android.content.Intent(activity, WebFallbackActivity.class);
                intent.putExtra(WebFallbackActivity.EXTRA_URL, url);
                activity.startActivity(intent);
            });
        } else if (kind == PageKind.ONLYFAP) {
            addOnlyFapControls(page);
            page.empty.setOnClickListener(v -> refresh(PAGE_ONLYFAP));
        }
        return page;
    }

    private void addSeriesSourceSelector(Page page) {
        android.content.SharedPreferences prefs =
                activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        int storedSource = prefs.getInt(PREF_SERIES_SOURCE, SERIES_SOURCE_CRAZYSHIT);
        if (storedSource == SERIES_SOURCE_CRAZYSHIT ||
                storedSource == SERIES_SOURCE_EFUKT ||
                storedSource == SERIES_SOURCE_CATEGORIES) {
            page.seriesSource = storedSource;
        } else {
            page.seriesSource = SERIES_SOURCE_CRAZYSHIT;
            prefs.edit().putInt(PREF_SERIES_SOURCE, SERIES_SOURCE_CRAZYSHIT).apply();
        }

        LinearLayout selector = new LinearLayout(activity);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setClipChildren(false);
        selector.setClipToPadding(false);
        selector.setGravity(Gravity.CENTER);
        selector.setPadding(dp(12), dp(8), dp(12), dp(8));
        selector.setBackground(ZeroChillUi.panelGlass(activity));
        selector.setElevation(ZeroChillUi.dimension(activity, R.dimen.zc_elevation_low));

        page.crazyShitSource = seriesSourceButton("CrazyShit");
        page.efuktSource = seriesSourceButton("EFukt");
        page.categoriesSource = seriesSourceButton("Categories");
        TextView[] sourceButtons = {
                page.crazyShitSource,
                page.efuktSource,
                page.categoriesSource
        };
        for (int index = 0; index < sourceButtons.length; index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
            if (index > 0) params.setMarginStart(dp(4));
            if (index + 1 < sourceButtons.length) params.setMarginEnd(dp(4));
            selector.addView(sourceButtons[index], params);
        }

        page.crazyShitSource.setOnClickListener(v -> switchSeriesSource(page, SERIES_SOURCE_CRAZYSHIT));
        page.efuktSource.setOnClickListener(v -> switchSeriesSource(page, SERIES_SOURCE_EFUKT));
        page.categoriesSource.setOnClickListener(v -> switchSeriesSource(page, SERIES_SOURCE_CATEGORIES));

        FrameLayout.LayoutParams selectorParams = new FrameLayout.LayoutParams(-1, dp(56));
        selectorParams.gravity = Gravity.TOP;
        selectorParams.setMargins(dp(8), 0, dp(8), 0);
        page.root.addView(selector, selectorParams);
        updateSeriesSourceButtons(page);
    }

    private void addOnlyFapControls(Page page) {
        android.content.SharedPreferences prefs =
                activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        page.fapzoneMode = prefs.getInt(PREF_FAPZONE_MODE, FapzoneCreatorRepository.MODE_TOP_50);
        if (page.fapzoneMode < FapzoneCreatorRepository.MODE_TOP_50 ||
                page.fapzoneMode > FapzoneCreatorRepository.MODE_POPULAR) {
            page.fapzoneMode = FapzoneCreatorRepository.MODE_TOP_50;
            prefs.edit().putInt(PREF_FAPZONE_MODE, page.fapzoneMode).apply();
        }

        LinearLayout modes = new LinearLayout(activity);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        modes.setPadding(dp(12), dp(6), dp(12), dp(6));
        modes.setBackground(ZeroChillUi.panelGlass(activity));

        page.fapzoneTop = fapzoneModeButton("Top 50");
        page.fapzoneNew = fapzoneModeButton("New");
        page.fapzoneHot = fapzoneModeButton("Hot");
        page.fapzonePopular = fapzoneModeButton("Popular");
        TextView[] modeButtons = {
                page.fapzoneTop,
                page.fapzoneNew,
                page.fapzoneHot,
                page.fapzonePopular
        };
        for (int index = 0; index < modeButtons.length; index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1f);
            if (index > 0) params.setMarginStart(dp(3));
            if (index + 1 < modeButtons.length) params.setMarginEnd(dp(3));
            modes.addView(modeButtons[index], params);
        }

        page.fapzoneTop.setOnClickListener(v -> switchFapzoneMode(
                page, FapzoneCreatorRepository.MODE_TOP_50));
        page.fapzoneNew.setOnClickListener(v -> switchFapzoneMode(
                page, FapzoneCreatorRepository.MODE_NEW));
        page.fapzoneHot.setOnClickListener(v -> switchFapzoneMode(
                page, FapzoneCreatorRepository.MODE_HOT));
        page.fapzonePopular.setOnClickListener(v -> switchFapzoneMode(
                page, FapzoneCreatorRepository.MODE_POPULAR));
        page.fapzoneModes = modes;

        FrameLayout.LayoutParams modeParams = new FrameLayout.LayoutParams(-1, dp(52));
        modeParams.gravity = Gravity.TOP;
        modeParams.setMargins(dp(8), 0, dp(8), 0);
        page.root.addView(modes, modeParams);

        LinearLayout caption = new LinearLayout(activity);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(17), dp(7), dp(17), dp(9));
        caption.setBackground(ZeroChillUi.panelGlass(activity));

        LinearLayout captionCopy = new LinearLayout(activity);
        captionCopy.setOrientation(LinearLayout.VERTICAL);
        captionCopy.setGravity(Gravity.CENTER_VERTICAL);

        TextView captionTitle = new TextView(activity);
        captionTitle.setText(FapzoneCreatorRepository.titleFor(page.fapzoneMode));
        captionTitle.setTextColor(Color.WHITE);
        captionTitle.setTextSize(17);
        captionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        captionTitle.setMaxLines(1);
        captionTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        captionCopy.addView(captionTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView captionHint = new TextView(activity);
        captionHint.setText(FapzoneCreatorRepository.hintFor(page.fapzoneMode));
        ZeroChillUi.styleSecondary(captionHint);
        captionHint.setTextSize(11.5f);
        captionHint.setMaxLines(1);
        captionHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(1);
        captionCopy.addView(captionHint, hintParams);
        caption.addView(captionCopy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView countBadge = new TextView(activity);
        countBadge.setText(FapzoneCreatorRepository.badgeFor(page.fapzoneMode));
        countBadge.setTextColor(Color.BLACK);
        countBadge.setTextSize(13);
        countBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        countBadge.setGravity(Gravity.CENTER);
        countBadge.setContentDescription("Creator list mode");
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(UiPalette.PRIMARY);
        badgeBackground.setCornerRadius(dp(17));
        countBadge.setBackground(badgeBackground);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(50), dp(30));
        badgeParams.setMarginStart(dp(12));
        caption.addView(countBadge, badgeParams);

        page.seriesCaptionTitle = captionTitle;
        page.seriesCaptionHint = captionHint;
        page.seriesCaptionBadge = countBadge;
        page.seriesCaption = caption;

        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(-1, dp(64));
        captionParams.gravity = Gravity.TOP;
        captionParams.setMargins(dp(8), dp(56), dp(8), 0);
        page.root.addView(caption, captionParams);

        FrameLayout.LayoutParams refreshParams =
                (FrameLayout.LayoutParams) page.refresh.getLayoutParams();
        refreshParams.topMargin = dp(124);
        page.refresh.setLayoutParams(refreshParams);
        page.recycler.setPadding(dp(4), dp(3), dp(4), dp(26));
        page.browseAdapter.setWideCreatorCards(true);
        applyBrowseLayout(page, true);
        updateFapzoneModeButtons(page);
        updateFapzoneCaption(page);
    }

    private TextView fapzoneModeButton(String label) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(12.5f);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("Show " + label + " OnlyFap creators");
        return button;
    }

    private TextView seriesSourceButton(String label) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("Show " + label + " collections");
        return button;
    }

    private void switchSeriesSource(Page page, int source) {
        if (page == null || page.kind != PageKind.SERIES || page.seriesSource == source) return;
        page.seriesSource = source;
        activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .edit()
                .putInt(PREF_SERIES_SOURCE, source)
                .apply();
        updateSeriesSourceButtons(page);
        page.generation++;
        page.loading = false;
        page.endReached = false;
        page.currentPage = 0;
        page.browseAdapter.replace(java.util.Collections.emptyList());
        page.recycler.scrollToPosition(0);
        page.empty.setVisibility(View.GONE);
        load(page, false);
    }

    private void switchFapzoneMode(Page page, int mode) {
        if (page == null || page.kind != PageKind.ONLYFAP || page.fapzoneMode == mode) return;
        page.fapzoneMode = mode;
        activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .edit()
                .putInt(PREF_FAPZONE_MODE, mode)
                .apply();
        updateFapzoneModeButtons(page);
        updateFapzoneCaption(page);
        page.generation++;
        page.loading = false;
        page.endReached = false;
        page.currentPage = 0;
        page.browseAdapter.replace(java.util.Collections.emptyList());
        page.recycler.scrollToPosition(0);
        page.empty.setVisibility(View.GONE);
        load(page, false);
    }

    private void updateSeriesSourceButtons(Page page) {
        styleSeriesSourceButton(page.crazyShitSource, page.seriesSource == SERIES_SOURCE_CRAZYSHIT);
        styleSeriesSourceButton(page.efuktSource, page.seriesSource == SERIES_SOURCE_EFUKT);
        styleSeriesSourceButton(page.categoriesSource, page.seriesSource == SERIES_SOURCE_CATEGORIES);
        if (page.refresh != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) page.refresh.getLayoutParams();
            params.topMargin = dp(56);
            page.refresh.setLayoutParams(params);
        }
        if (page.recycler != null) {
            page.recycler.setPadding(0, dp(5), 0, dp(18));
        }
        if (page.browseAdapter != null) {
            page.browseAdapter.setWideCreatorCards(false);
        }
        applyBrowseLayout(page, false);
    }

    private void updateFapzoneModeButtons(Page page) {
        if (page == null) return;
        styleFapzoneModeButton(
                page.fapzoneTop,
                page.fapzoneMode == FapzoneCreatorRepository.MODE_TOP_50
        );
        styleFapzoneModeButton(
                page.fapzoneNew,
                page.fapzoneMode == FapzoneCreatorRepository.MODE_NEW
        );
        styleFapzoneModeButton(
                page.fapzoneHot,
                page.fapzoneMode == FapzoneCreatorRepository.MODE_HOT
        );
        styleFapzoneModeButton(
                page.fapzonePopular,
                page.fapzoneMode == FapzoneCreatorRepository.MODE_POPULAR
        );
    }

    private void updateFapzoneCaption(Page page) {
        if (page == null) return;
        if (page.seriesCaptionTitle != null) {
            page.seriesCaptionTitle.setText(FapzoneCreatorRepository.titleFor(page.fapzoneMode));
        }
        if (page.seriesCaptionHint != null) {
            page.seriesCaptionHint.setText(FapzoneCreatorRepository.hintFor(page.fapzoneMode));
        }
        if (page.seriesCaptionBadge != null) {
            page.seriesCaptionBadge.setText(FapzoneCreatorRepository.badgeFor(page.fapzoneMode));
        }
    }

    private void applyBrowseLayout(Page page, boolean wideCards) {
        if (page == null || page.recycler == null) return;
        if (!wideCards) {
            RecyclerView.LayoutManager current = page.recycler.getLayoutManager();
            if (current instanceof GridLayoutManager &&
                    ((GridLayoutManager) current).getSpanCount() == 2) return;
            page.recycler.setLayoutManager(new GridLayoutManager(activity, 2));
            return;
        }

        int columns = creatorGridColumnCount();
        GridLayoutManager editorialGrid = new GridLayoutManager(activity, columns);
        editorialGrid.setInitialPrefetchItemCount(Math.max(8, columns * 3));
        GridLayoutManager.SpanSizeLookup spanLookup = new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int adapterPosition) {
                return page.browseAdapter.creatorSpanSize(adapterPosition, columns);
            }
        };
        spanLookup.setSpanIndexCacheEnabled(true);
        editorialGrid.setSpanSizeLookup(spanLookup);
        editorialGrid.setUsingSpansToEstimateScrollbarDimensions(true);
        page.recycler.setLayoutManager(editorialGrid);
    }

    private int creatorGridColumnCount() {
        Configuration config = activity.getResources().getConfiguration();
        if (config.screenWidthDp >= 720) return 4;
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                config.screenWidthDp >= 600 ? 4 : 2;
    }

    private void styleSeriesSourceButton(TextView button, boolean selected) {
        ZeroChillUi.styleChip(button, selected);
    }

    private void styleFapzoneModeButton(TextView button, boolean selected) {
        ZeroChillUi.styleChip(button, selected);
    }

    private Page createPageShell(int index, PageKind kind, String prefKey, String baseUrl) {
        Page page = new Page(index, kind, prefKey, baseUrl);
        page.root = new FrameLayout(activity);
        page.root.setBackgroundColor(ZeroChillUi.background(activity));

        page.refresh = new SwipeRefreshLayout(activity);
        page.refresh.setColorSchemeColors(UiPalette.PRIMARY);
        page.root.addView(page.refresh, new FrameLayout.LayoutParams(-1, -1));

        page.recycler = new RecyclerView(activity);
        page.recycler.setBackgroundColor(ZeroChillUi.background(activity));
        page.recycler.setClipToPadding(false);
        page.recycler.setPadding(0, dp(5), 0, dp(18));
        page.recycler.setItemAnimator(null);
        page.refresh.addView(page.recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));

        page.progress = new ProgressBar(activity);
        page.progress.setIndeterminate(true);
        ZeroChillUi.styleProgress(page.progress);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        page.root.addView(page.progress, progressParams);

        page.empty = new TextView(activity);
        ZeroChillUi.styleEmpty(page.empty);
        page.empty.setVisibility(View.GONE);
        page.root.addView(page.empty, new FrameLayout.LayoutParams(-1, -1));

        page.refresh.setOnRefreshListener(() -> refresh(page.index));
        return page;
    }

    private void applyFeedLayout(Page page) {
        if (page == null || page.feedAdapter == null) return;
        page.feedAdapter.setViewMode(page.viewMode);
        RecyclerView.LayoutManager old = page.recycler.getLayoutManager();
        int position = 0;
        int offset = 0;
        if (old instanceof LinearLayoutManager) {
            LinearLayoutManager lm = (LinearLayoutManager) old;
            position = Math.max(0, lm.findFirstVisibleItemPosition());
            View anchor = lm.findViewByPosition(position);
            if (anchor != null) offset = anchor.getTop() - page.recycler.getPaddingTop();
        }

        LinearLayoutManager next;
        if (page.viewMode == NativeFeedAdapter.VIEW_GRID ||
                page.viewMode == NativeFeedAdapter.VIEW_POSTERS) {
            GridLayoutManager grid = new GridLayoutManager(activity, 2);
            grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int adapterPosition) {
                    return page.feedAdapter.isSectionAt(adapterPosition) ? 2 : 1;
                }
            });
            next = grid;
        } else {
            next = new LinearLayoutManager(activity);
        }
        page.recycler.setLayoutManager(next);

        if (page.feedAdapter.getItemCount() > 0) {
            int safePosition = Math.min(position, page.feedAdapter.getItemCount() - 1);
            next.scrollToPositionWithOffset(safePosition, offset);
        }
    }

    private void styleHomeSources(Page page) {
        for (int i = 0; i < page.homeChips.size(); i++) {
            TextView chip = page.homeChips.get(i);
            boolean selected = (i + 1) == page.homeSource;
            ZeroChillUi.styleChip(chip, selected);
        }
    }

    private void load(Page page, boolean append) {
        if (page == null || page.loading || page.endReached) return;
        if (page.kind != PageKind.FEED) append = false;
        page.loading = true;
        final int generation = page.generation;
        final int selectedHomeSource = page.homeSource;
        final boolean appendRequest = append;
        final int requestPage = append ? page.currentPage + 1 : 1;
        if (!append && page.itemCount() == 0) page.progress.setVisibility(View.VISIBLE);

        page.loadTask = io.submit(() -> {
            try {
                List<NativeContentItem> result;
                if (page.kind == PageKind.ONLYFAP) {
                    result = fapzoneCreatorRepository.fetch(
                            activity,
                            page.fapzoneMode,
                            items -> showPopularCreatorProgress(page, generation, items)
                    );
                } else if (page.kind == PageKind.SERIES) {
                    result = page.seriesSource == SERIES_SOURCE_EFUKT
                            ? efuktRepository.fetchSeries(activity)
                            : page.seriesSource == SERIES_SOURCE_CATEGORIES
                            ? browseRepository.fetchCategories(activity)
                            : browseRepository.fetchSeries(activity);
                } else if (page.kind == PageKind.CATEGORIES) {
                    result = browseRepository.fetchCategories(activity);
                } else {
                    result = homeRepository.fetch(activity, selectedHomeSource, requestPage,
                            items -> {
                                if (appendRequest) return;
                                activity.runOnUiThread(() -> {
                                    if (generation != page.generation || activity.isFinishing()) return;
                                    page.feedAdapter.replace(items);
                                    page.progress.setVisibility(View.GONE);
                                    page.refresh.setRefreshing(false);
                                    page.empty.setVisibility(View.GONE);
                                });
                            });
                }

                activity.runOnUiThread(() -> {
                    if (generation != page.generation) return;
                    page.loading = false;
                    page.progress.setVisibility(View.GONE);
                    page.refresh.setRefreshing(false);
                    page.empty.setVisibility(View.GONE);

                    if (page.kind == PageKind.FEED) {
                        if (appendRequest) page.feedAdapter.append(result);
                        else page.feedAdapter.replace(result);
                        if (!result.isEmpty()) page.currentPage = requestPage;
                        if (result.isEmpty()) page.endReached = true;
                    } else {
                        page.browseAdapter.replace(result);
                        page.endReached = true;
                        requestBrowseArtwork(page, generation);
                    }

                    if (page.itemCount() == 0) {
                        page.empty.setText(page.kind == PageKind.ONLYFAP
                                ? "Couldn't load this OnlyFap list right now.\nPull down to try again."
                                : page.kind == PageKind.SERIES && page.seriesSource == SERIES_SOURCE_EFUKT
                                ? "Couldn't load EFukt Series here.\nIt may be unavailable in your region.\nTap to open the website."
                                : page.seriesSource == SERIES_SOURCE_CATEGORIES
                                ? "Couldn't load Categories right now."
                                : page.kind == PageKind.SERIES
                                ? "Couldn't load CrazyShit right now."
                                : "No videos returned for this source.\nTap to retry or choose another source.");
                        page.empty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (generation != page.generation) return;
                    page.loading = false;
                    page.progress.setVisibility(View.GONE);
                    page.refresh.setRefreshing(false);
                    if (page.itemCount() == 0) {
                        page.empty.setText(page.kind == PageKind.ONLYFAP
                                ? "Couldn't load this OnlyFap list right now.\nPull down to try again."
                                : page.kind == PageKind.SERIES && page.seriesSource == SERIES_SOURCE_EFUKT
                                ? "Couldn't load EFukt Series here.\nIt may be unavailable in your region.\nTap to open the website."
                                : page.seriesSource == SERIES_SOURCE_CATEGORIES
                                ? "Couldn't load Categories right now."
                                : page.kind == PageKind.SERIES
                                ? "Couldn't load CrazyShit right now."
                                : "Couldn't load this source.\nTap to retry or choose another source.");
                        page.empty.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void showPopularCreatorProgress(
            Page page,
            int generation,
            List<NativeContentItem> items
    ) {
        if (items == null || items.isEmpty()) return;
        activity.runOnUiThread(() -> {
            if (generation != page.generation ||
                    page.kind != PageKind.ONLYFAP ||
                    page.browseAdapter == null) return;
            page.progress.setVisibility(View.GONE);
            page.refresh.setRefreshing(false);
            page.empty.setVisibility(View.GONE);
            page.browseAdapter.replace(items);
        });
    }

    private void requestBrowseArtwork(Page page, int generation) {
        if (page == null || page.browseAdapter == null || !page.browseAdapter.hasMissingArtwork()) return;

        if (page.kind == PageKind.CATEGORIES ||
                (page.kind == PageKind.SERIES && page.seriesSource == SERIES_SOURCE_CATEGORIES)) {
            browseArtworkResolver.request(BrowseRepository.CATEGORIES, "/category/", (source, artwork) -> {
                if (generation != page.generation) return;
                page.browseAdapter.applyArtwork(artwork);
            });
            return;
        }

        if (page.kind == PageKind.SERIES && page.seriesSource == SERIES_SOURCE_CRAZYSHIT) {
            browseArtworkResolver.request(BrowseRepository.SERIES, "/series/", (source, artwork) -> {
                if (generation != page.generation) return;
                page.browseAdapter.applyArtwork(artwork);
                if (!page.browseAdapter.hasMissingArtwork()) return;
                browseArtworkResolver.request(CrazyShitRepository.HOME, "/series/", (home, fallback) -> {
                    if (generation != page.generation) return;
                    page.browseAdapter.applyArtwork(fallback);
                });
            });
        }
    }

    private Page pageAt(int position) {
        if (position < 0 || position >= pages.length) return null;
        return pages[position];
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout container;

        Holder(FrameLayout container) {
            super(container);
            this.container = container;
        }
    }

    private static final class Page {
        final int index;
        final PageKind kind;
        final String preferenceKey;
        final String baseUrl;

        FrameLayout root;
        SwipeRefreshLayout refresh;
        RecyclerView recycler;
        ProgressBar progress;
        TextView empty;
        NativeFeedAdapter feedAdapter;
        NativeCategoryAdapter browseAdapter;
        TextView crazyShitSource;
        TextView efuktSource;
        TextView bunkrSource;
        TextView categoriesSource;
        View fapzoneModes;
        TextView fapzoneTop;
        TextView fapzoneNew;
        TextView fapzoneHot;
        TextView fapzonePopular;
        View seriesCaption;
        TextView seriesCaptionTitle;
        TextView seriesCaptionHint;
        TextView seriesCaptionBadge;
        int viewMode = NativeFeedAdapter.VIEW_LIST;
        int seriesSource = SERIES_SOURCE_CRAZYSHIT;
        int fapzoneMode = FapzoneCreatorRepository.MODE_TOP_50;
        int homeSource;
        java.util.concurrent.Future<?> loadTask;
        final java.util.List<TextView> homeChips = new java.util.ArrayList<>();
        int currentPage;
        boolean loading;
        boolean endReached;
        int generation;

        Page(int index, PageKind kind, String preferenceKey, String baseUrl) {
            this.index = index;
            this.kind = kind;
            this.preferenceKey = preferenceKey;
            this.baseUrl = baseUrl;
        }

        int itemCount() {
            if (feedAdapter != null) return feedAdapter.getItemCount();
            return browseAdapter == null ? 0 : browseAdapter.getItemCount();
        }
    }
}
