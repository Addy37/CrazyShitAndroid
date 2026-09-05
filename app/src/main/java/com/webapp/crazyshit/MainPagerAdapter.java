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
 * Keeps Home, Collections, Chaos and Categories alive for true horizontal paging.
 * Chaos itself owns a nested vertical ViewPager2 for Shorts/Reels-style playback.
 */
public final class MainPagerAdapter extends RecyclerView.Adapter<MainPagerAdapter.Holder> {
    public static final int PAGE_HOME = 0;
    public static final int PAGE_SERIES = 1;
    public static final int PAGE_CHAOS = 2;
    public static final int PAGE_CATEGORIES = 3;
    public static final int PAGE_COUNT = 4;
    private static final int PAGE_ARRAY_COUNT = 4;
    private static final int SERIES_SOURCE_CRAZYSHIT = 0;
    private static final int SERIES_SOURCE_EFUKT = 1;
    private static final int SERIES_SOURCE_BUNKR = 2;
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
        CATEGORIES
    }

    private final Activity activity;
    private final Host host;
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
        pages[PAGE_CATEGORIES] = buildBrowsePage(PAGE_CATEGORIES, PageKind.CATEGORIES);

        chaosView = new ChaosFeedView(activity, new ChaosFeedView.Host() {
            @Override
            public void openDetails(NativeContentItem item) {
                host.onOpenItem(item);
            }
        });
        chaosView.setActive(false);

        for (Page page : pages) if (page != null) load(page, false);
    }

    public String titleFor(int position) {
        if (position == PAGE_SERIES) return "Collections";
        if (position == PAGE_CATEGORIES) return "Categories";
        if (position == PAGE_CHAOS) return "Chaos";
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
        page.currentPage = 0;
        page.endReached = false;
        page.loading = false;
        load(page, false);
    }

    public void setPrimaryActive(int position) {
        chaosView.setActive(position == PAGE_CHAOS);
    }

    public boolean isBunkrCollectionsSelected() {
        Page page = pageAt(PAGE_SERIES);
        return page != null && page.seriesSource == SERIES_SOURCE_BUNKR;
    }

    public void onHostResume() {
        chaosView.onHostResume();
        Page collections = pageAt(PAGE_SERIES);
        if (collections != null && collections.seriesSource == SERIES_SOURCE_BUNKR
                && collections.browseAdapter != null) collections.browseAdapter.notifyDataSetChanged();
        Page home = pageAt(PAGE_HOME);
        if (home != null && home.feedAdapter != null) home.feedAdapter.refreshPlaybackState();
    }

    public void onHostPause() {
        chaosView.onHostPause();
    }

    public void onConfigurationChanged() {
        chaosView.onConfigurationChanged();
    }

    public void close() {
        chaosView.close();
        browseArtworkResolver.close();
        for (Page page : pages) {
            if (page != null && page.browseAdapter != null) page.browseAdapter.close();
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
        container.setBackgroundColor(Color.rgb(13, 13, 15));
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
        page.viewMode = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getInt(prefKey, NativeFeedAdapter.VIEW_LIST);
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
                        query
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
                if (page.seriesSource == SERIES_SOURCE_BUNKR) {
                    refresh(PAGE_SERIES);
                    return;
                }
                String url = page.seriesSource == SERIES_SOURCE_BUNKR
                        ? BunkrRepository.MOST_FILES_ALBUMS
                        : page.seriesSource == SERIES_SOURCE_EFUKT
                        ? EfuktRepository.SERIES
                        : BrowseRepository.SERIES;
                android.content.Intent intent = new android.content.Intent(activity, WebFallbackActivity.class);
                intent.putExtra(WebFallbackActivity.EXTRA_URL, url);
                activity.startActivity(intent);
            });
        }
        return page;
    }

    private void addSeriesSourceSelector(Page page) {
        page.seriesSource = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getInt(PREF_SERIES_SOURCE, SERIES_SOURCE_CRAZYSHIT);
        page.fapzoneMode = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getInt(PREF_FAPZONE_MODE, FapzoneCreatorRepository.MODE_TOP_50);
        if (page.seriesSource != SERIES_SOURCE_EFUKT && page.seriesSource != SERIES_SOURCE_BUNKR) {
            page.seriesSource = SERIES_SOURCE_CRAZYSHIT;
        }
        if (page.fapzoneMode < FapzoneCreatorRepository.MODE_TOP_50 ||
                page.fapzoneMode > FapzoneCreatorRepository.MODE_POPULAR) {
            page.fapzoneMode = FapzoneCreatorRepository.MODE_TOP_50;
        }

        LinearLayout selector = new LinearLayout(activity);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setGravity(Gravity.CENTER);
        selector.setPadding(dp(12), dp(8), dp(12), dp(8));
        selector.setBackgroundColor(Color.rgb(17, 17, 20));

        page.crazyShitSource = seriesSourceButton("CrazyShit");
        page.efuktSource = seriesSourceButton("EFukt");
        page.bunkrSource = seriesSourceButton("Fapzone");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        buttonParams.setMarginEnd(dp(4));
        selector.addView(page.crazyShitSource, buttonParams);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        secondParams.setMarginStart(dp(4));
        secondParams.setMarginEnd(dp(4));
        selector.addView(page.efuktSource, secondParams);
        LinearLayout.LayoutParams thirdParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        thirdParams.setMarginStart(dp(4));
        selector.addView(page.bunkrSource, thirdParams);

        page.crazyShitSource.setOnClickListener(v -> switchSeriesSource(page, SERIES_SOURCE_CRAZYSHIT));
        page.efuktSource.setOnClickListener(v -> switchSeriesSource(page, SERIES_SOURCE_EFUKT));
        page.bunkrSource.setOnClickListener(v -> switchSeriesSource(page, SERIES_SOURCE_BUNKR));
        FrameLayout.LayoutParams selectorParams = new FrameLayout.LayoutParams(-1, dp(56));
        selectorParams.gravity = Gravity.TOP;
        page.root.addView(selector, selectorParams);

        LinearLayout fapzoneModes = new LinearLayout(activity);
        fapzoneModes.setOrientation(LinearLayout.HORIZONTAL);
        fapzoneModes.setGravity(Gravity.CENTER);
        fapzoneModes.setPadding(dp(12), dp(6), dp(12), dp(6));
        fapzoneModes.setBackgroundColor(Color.rgb(17, 17, 20));
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
            fapzoneModes.addView(modeButtons[index], params);
        }
        page.fapzoneTop.setOnClickListener(v -> switchFapzoneMode(
                page,
                FapzoneCreatorRepository.MODE_TOP_50
        ));
        page.fapzoneNew.setOnClickListener(v -> switchFapzoneMode(
                page,
                FapzoneCreatorRepository.MODE_NEW
        ));
        page.fapzoneHot.setOnClickListener(v -> switchFapzoneMode(
                page,
                FapzoneCreatorRepository.MODE_HOT
        ));
        page.fapzonePopular.setOnClickListener(v -> switchFapzoneMode(
                page,
                FapzoneCreatorRepository.MODE_POPULAR
        ));
        page.fapzoneModes = fapzoneModes;
        FrameLayout.LayoutParams modesParams = new FrameLayout.LayoutParams(-1, dp(52));
        modesParams.gravity = Gravity.TOP;
        modesParams.topMargin = dp(56);
        page.root.addView(fapzoneModes, modesParams);

        LinearLayout caption = new LinearLayout(activity);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(17), dp(7), dp(17), dp(9));
        caption.setBackgroundColor(Color.rgb(17, 17, 20));

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
        captionHint.setTextColor(Color.rgb(164, 164, 174));
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
        captionParams.topMargin = dp(108);
        page.root.addView(caption, captionParams);
        updateSeriesSourceButtons(page);
    }

    private TextView fapzoneModeButton(String label) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(12.5f);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("Show " + label + " Fapzone creators");
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
        if (page == null || page.kind != PageKind.SERIES ||
                page.seriesSource != SERIES_SOURCE_BUNKR || page.fapzoneMode == mode) return;
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
        styleSeriesSourceButton(page.bunkrSource, page.seriesSource == SERIES_SOURCE_BUNKR);
        boolean showFapzoneChrome = page.seriesSource == SERIES_SOURCE_BUNKR;
        if (page.fapzoneModes != null) {
            page.fapzoneModes.setVisibility(showFapzoneChrome ? View.VISIBLE : View.GONE);
        }
        if (page.seriesCaption != null) {
            page.seriesCaption.setVisibility(showFapzoneChrome ? View.VISIBLE : View.GONE);
        }
        updateFapzoneModeButtons(page);
        updateFapzoneCaption(page);
        if (page.refresh != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) page.refresh.getLayoutParams();
            params.topMargin = dp(showFapzoneChrome ? 172 : 56);
            page.refresh.setLayoutParams(params);
        }
        if (page.recycler != null) {
            page.recycler.setPadding(
                    showFapzoneChrome ? dp(4) : 0,
                    showFapzoneChrome ? dp(3) : dp(5),
                    showFapzoneChrome ? dp(4) : 0,
                    showFapzoneChrome ? dp(26) : dp(18)
            );
        }
        if (page.browseAdapter != null) {
            page.browseAdapter.setWideCreatorCards(showFapzoneChrome);
        }
        applyBrowseLayout(page, showFapzoneChrome);
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
        if (button == null) return;
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(20));
        background.setColor(selected ? UiPalette.PRIMARY : Color.rgb(27, 27, 31));
        background.setStroke(dp(1), selected ? UiPalette.PRIMARY : Color.rgb(57, 57, 64));
        button.setBackground(background);
        button.setTextColor(selected ? Color.BLACK : Color.rgb(220, 220, 226));
        button.setSelected(selected);
    }

    private void styleFapzoneModeButton(TextView button, boolean selected) {
        if (button == null) return;
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(11));
        background.setColor(selected ? Color.rgb(46, 46, 18) : Color.rgb(24, 24, 28));
        background.setStroke(
                dp(1),
                selected ? UiPalette.PRIMARY : Color.rgb(52, 52, 59)
        );
        button.setBackground(background);
        button.setTextColor(selected ? UiPalette.PRIMARY : Color.rgb(188, 188, 198));
        button.setSelected(selected);
    }

    private Page createPageShell(int index, PageKind kind, String prefKey, String baseUrl) {
        Page page = new Page(index, kind, prefKey, baseUrl);
        page.root = new FrameLayout(activity);
        page.root.setBackgroundColor(Color.rgb(13, 13, 15));

        page.refresh = new SwipeRefreshLayout(activity);
        page.refresh.setColorSchemeColors(UiPalette.PRIMARY);
        page.root.addView(page.refresh, new FrameLayout.LayoutParams(-1, -1));

        page.recycler = new RecyclerView(activity);
        page.recycler.setBackgroundColor(Color.rgb(13, 13, 15));
        page.recycler.setClipToPadding(false);
        page.recycler.setPadding(0, dp(5), 0, dp(18));
        page.recycler.setItemAnimator(null);
        page.refresh.addView(page.recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));

        page.progress = new ProgressBar(activity);
        page.progress.setIndeterminate(true);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        page.root.addView(page.progress, progressParams);

        page.empty = new TextView(activity);
        page.empty.setTextColor(Color.rgb(190, 190, 198));
        page.empty.setTextSize(15);
        page.empty.setGravity(Gravity.CENTER);
        page.empty.setPadding(dp(28), dp(28), dp(28), dp(28));
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

    private void load(Page page, boolean append) {
        if (page == null || page.loading || page.endReached) return;
        if (page.kind != PageKind.FEED) append = false;
        page.loading = true;
        final int generation = page.generation;
        final boolean appendRequest = append;
        final int requestPage = append ? page.currentPage + 1 : 1;
        if (!append && page.itemCount() == 0) page.progress.setVisibility(View.VISIBLE);

        io.execute(() -> {
            try {
                List<NativeContentItem> result;
                if (page.kind == PageKind.SERIES) {
                    result = page.seriesSource == SERIES_SOURCE_BUNKR
                            ? fapzoneCreatorRepository.fetch(
                                    activity,
                                    page.fapzoneMode,
                                    items -> showPopularCreatorProgress(page, generation, items)
                            )
                            : page.seriesSource == SERIES_SOURCE_EFUKT
                            ? efuktRepository.fetchSeries(activity)
                            : browseRepository.fetchSeries(activity);
                } else if (page.kind == PageKind.CATEGORIES) {
                    result = browseRepository.fetchCategories(activity);
                } else {
                    result = repository.fetchFeed(activity, page.baseUrl, requestPage);
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
                        page.empty.setText(page.kind == PageKind.SERIES && page.seriesSource == SERIES_SOURCE_BUNKR
                                ? "Couldn't load this Fapzone list right now.\nPull down to try again."
                                : page.kind == PageKind.SERIES && page.seriesSource == SERIES_SOURCE_EFUKT
                                ? "Couldn't load EFukt Series here.\nIt may be unavailable in your region.\nTap to open the website."
                                : page.kind == PageKind.SERIES
                                ? "Couldn't load CrazyShit Series right now."
                                : page.kind == PageKind.CATEGORIES
                                ? "Couldn't load Categories right now."
                                : "This feed couldn't be rendered right now.");
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
                        page.empty.setText(page.kind == PageKind.SERIES && page.seriesSource == SERIES_SOURCE_BUNKR
                                ? "Couldn't load this Fapzone list right now.\nPull down to try again."
                                : page.kind == PageKind.SERIES && page.seriesSource == SERIES_SOURCE_EFUKT
                                ? "Couldn't load EFukt Series here.\nIt may be unavailable in your region.\nTap to open the website."
                                : page.kind == PageKind.SERIES
                                ? "Couldn't load CrazyShit Series right now."
                                : page.kind == PageKind.CATEGORIES
                                ? "Couldn't load Categories right now."
                                : "Couldn't load this tab right now.");
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
                    page.seriesSource != SERIES_SOURCE_BUNKR ||
                    page.browseAdapter == null) return;
            page.progress.setVisibility(View.GONE);
            page.refresh.setRefreshing(false);
            page.empty.setVisibility(View.GONE);
            page.browseAdapter.replace(items);
        });
    }

    private void requestBrowseArtwork(Page page, int generation) {
        if (page == null || page.browseAdapter == null || !page.browseAdapter.hasMissingArtwork()) return;

        if (page.kind == PageKind.CATEGORIES) {
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
