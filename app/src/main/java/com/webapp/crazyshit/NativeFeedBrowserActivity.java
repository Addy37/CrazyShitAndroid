package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native media feed opened from a Series or Category card. */
public final class NativeFeedBrowserActivity extends Activity {
    private static final int CREATOR_TAB_ALL = 0;
    private static final int CREATOR_TAB_PICTURES = 1;
    private static final int CREATOR_TAB_VIDEOS = 2;
    private static final int CREATOR_TAB_COUNT = 3;

    public static final String EXTRA_TITLE = "browser_title";
    public static final String EXTRA_BASE_URL = "browser_base_url";
    public static final String EXTRA_MEME_MODE = "browser_meme_mode";
    public static final String EXTRA_SOURCE = "browser_source";
    public static final String EXTRA_BUNKR_CREATOR_QUERY = "browser_bunkr_creator_query";
    public static final String EXTRA_FAPELLO_PROFILE_URL = "browser_fapello_profile_url";
    public static final String SOURCE_CRAZYSHIT = "crazyshit";
    public static final String SOURCE_EFUKT = "efukt";
    public static final String SOURCE_BUNKR = "bunkr";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final BunkrCreatorGalleryRepository creatorGalleryRepository =
            new BunkrCreatorGalleryRepository();
    private final MemeRepository memeRepository = new MemeRepository();

    private NativeFeedAdapter adapter;
    private BunkrGalleryAdapter bunkrGalleryAdapter;
    private final BunkrGalleryAdapter[] creatorTabAdapters =
            new BunkrGalleryAdapter[CREATOR_TAB_COUNT];
    private final RecyclerView[] creatorTabRecyclers =
            new RecyclerView[CREATOR_TAB_COUNT];
    private String bunkrGallerySessionId;
    private String browserSnapshot = ScreenSnapshotStore.newId();
    private Bundle restoredBrowserState;
    private boolean restoringBrowser;

    private RecyclerView recycler;
    private ViewPager2 creatorTabsPager;
    private TabLayout creatorTabs;
    private CreatorProfileHeader creatorProfile;
    private TabLayoutMediator creatorTabsMediator;
    private SwipeRefreshLayout refresh;
    private ProgressBar progress;
    private TextView empty;
    private String title;
    private String baseUrl;
    private String source;
    private String creatorQuery;
    private String fapelloProfileUrl;
    private boolean memeMode;
    private boolean loading;
    private boolean endReached;
    private boolean fapelloFailureShown;
    private int currentPage;
    private int generation;
    private int creatorGalleryColumns;

    public static Intent create(Activity activity, String title, String baseUrl, boolean memeMode) {
        return create(activity, title, baseUrl, memeMode, SOURCE_CRAZYSHIT);
    }

    public static Intent create(
            Activity activity,
            String title,
            String baseUrl,
            boolean memeMode,
            String source
    ) {
        Intent intent = new Intent(activity, NativeFeedBrowserActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_BASE_URL, baseUrl);
        intent.putExtra(EXTRA_MEME_MODE, memeMode);
        intent.putExtra(EXTRA_SOURCE, source);
        return intent;
    }

    public static Intent createCreatorGallery(Activity activity, String title, String query) {
        return createCreatorGallery(activity, title, query, "");
    }

    public static Intent createCreatorGallery(
            Activity activity,
            String title,
            String query,
            String fapelloProfileUrl
    ) {
        String cleanQuery = query == null ? "" : query.trim();
        Intent intent = create(
                activity,
                title,
                BunkrRepository.searchUrl(cleanQuery),
                false,
                SOURCE_BUNKR
        );
        intent.putExtra(EXTRA_BUNKR_CREATOR_QUERY, cleanQuery);
        if (FapelloRepository.isModelUrl(fapelloProfileUrl)) {
            intent.putExtra(EXTRA_FAPELLO_PROFILE_URL, fapelloProfileUrl);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title = value(getIntent().getStringExtra(EXTRA_TITLE), "Browse");
        baseUrl = value(getIntent().getStringExtra(EXTRA_BASE_URL), CrazyShitRepository.HOME);
        memeMode = getIntent().getBooleanExtra(EXTRA_MEME_MODE, false);
        source = value(getIntent().getStringExtra(EXTRA_SOURCE), SOURCE_CRAZYSHIT);
        creatorQuery = value(getIntent().getStringExtra(EXTRA_BUNKR_CREATOR_QUERY), "");
        fapelloProfileUrl = value(getIntent().getStringExtra(EXTRA_FAPELLO_PROFILE_URL), "");
        if (!creatorQuery.isEmpty()) {
            source = SOURCE_BUNKR;
            baseUrl = BunkrRepository.searchUrl(creatorQuery);
        } else if (BunkrRepository.isAlbumUrl(baseUrl)) source = SOURCE_BUNKR;
        else if (EfuktRepository.isEfuktUrl(baseUrl)) source = SOURCE_EFUKT;
        restoredBrowserState = state;
        if (state != null) {
            browserSnapshot = state.getString("browser_snapshot", browserSnapshot);
            bunkrGallerySessionId = state.getString("gallery_session");
        }
        buildUi();
        if (state == null) load(false);
        else restoreBrowser();
    }

    private void buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.BLACK);

        TextView back = text("‹", 34, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView heading = text(isCreatorGallery() ? "OnlyFap" : title, 20, Color.WHITE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView options = text("⋮", 28, Color.rgb(220, 220, 226));
        options.setGravity(Gravity.CENTER);
        options.setContentDescription("Feed options");
        options.setOnClickListener(this::showOptions);
        top.addView(options, new LinearLayout.LayoutParams(dp(48), dp(52)));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        if (isCreatorGallery()) {
            creatorProfile = new CreatorProfileHeader(this, title, creatorQuery, baseUrl);
            shell.addView(creatorProfile);
            creatorTabs = new TabLayout(this);
            creatorTabs.setBackgroundColor(Color.BLACK);
            creatorTabs.setSelectedTabIndicatorColor(UiPalette.PRIMARY);
            creatorTabs.setTabTextColors(Color.rgb(174, 174, 182), UiPalette.PRIMARY);
            creatorTabs.setTabMode(TabLayout.MODE_FIXED);
            creatorTabs.setTabGravity(TabLayout.GRAVITY_FILL);
            shell.addView(creatorTabs, new LinearLayout.LayoutParams(-1, dp(48)));
        }

        FrameLayout body = new FrameLayout(this);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        refresh = new SwipeRefreshLayout(this);
        refresh.setColorSchemeColors(UiPalette.PRIMARY);
        refresh.setOnRefreshListener(this::reload);
        body.addView(refresh, new FrameLayout.LayoutParams(-1, -1));

        if (isBunkr()) {
            if (bunkrGallerySessionId == null || bunkrGallerySessionId.isEmpty()) {
                bunkrGallerySessionId = isCreatorGallery()
                        ? BunkrGallerySessionStore.createCreator(title, baseUrl, creatorQuery)
                        : BunkrGallerySessionStore.create(title, baseUrl);
                if (isCreatorGallery()) creatorGalleryRepository.reset(
                        bunkrGallerySessionId, creatorQuery, fapelloProfileUrl, title);
            }
            if (isCreatorGallery()) buildCreatorTabs();
            else {
                recycler = createRecycler();
                bunkrGalleryAdapter = createBunkrGalleryAdapter(false);
                recycler.setAdapter(bunkrGalleryAdapter);
                refresh.addView(recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));
                attachGalleryScrollListener(recycler, bunkrGalleryAdapter);
            }
        } else {
            recycler = createRecycler();
            adapter = new NativeFeedAdapter(this, new NativeFeedAdapter.Listener() {
                @Override
                public void onOpen(NativeContentItem item) {
                    if (item == null || item.isSection()) return;
                    if (memeMode || item.isMeme()) openMeme(item);
                    else openVideo(item);
                }

                @Override
                public void onLongPress(NativeContentItem item, View anchor) {
                    if (item == null || item.isSection()) return;
                    showItemMenu(item, anchor);
                }

                @Override
                public void onComments(NativeContentItem item) {
                    if (item == null || item.isSection() || memeMode || !supportsComments()) return;
                    new InlineCommentsDialog(
                            NativeFeedBrowserActivity.this,
                            item.url,
                            item.title,
                            item.comments,
                            null
                    ).show();
                }
            });
            recycler.setAdapter(adapter);
            refresh.addView(recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));
            attachFeedScrollListener(recycler);
        }
        applyLayout();

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        body.addView(progress, progressParams);

        empty = text("", 15, Color.rgb(190, 190, 198));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(28), dp(28), dp(28), dp(28));
        empty.setVisibility(View.GONE);
        empty.setOnClickListener(v -> {
            if (isCreatorGallery()) {
                if (itemCount() == 0) reload();
                else if (!endReached) load(true);
            }
            else openWebsite(baseUrl);
        });
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        setContentView(shell);
    }

    private RecyclerView createRecycler() {
        RecyclerView next = new RecyclerView(this);
        next.setBackgroundColor(Color.BLACK);
        next.setClipToPadding(false);
        next.setPadding(0, dp(5), 0, dp(18));
        next.setItemAnimator(null);
        return next;
    }

    private BunkrGalleryAdapter createBunkrGalleryAdapter(boolean adaptiveAspectRatios) {
        return new BunkrGalleryAdapter(
                this,
                new BunkrGalleryAdapter.Listener() {
                    @Override
                    public void onOpen(int position, NativeContentItem item) {
                        openBunkrGallery(position, item);
                    }

                    @Override
                    public void onLongPress(NativeContentItem item, View anchor) {
                        showItemMenu(item, anchor);
                    }
                },
                adaptiveAspectRatios
        );
    }

    private void buildCreatorTabs() {
        creatorGalleryColumns = savedCreatorGalleryColumnCount();
        for (int index = 0; index < CREATOR_TAB_COUNT; index++) {
            creatorTabAdapters[index] = createBunkrGalleryAdapter(true);
        }
        bunkrGalleryAdapter = creatorTabAdapters[CREATOR_TAB_ALL];

        creatorTabsPager = new ViewPager2(this);
        creatorTabsPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        creatorTabsPager.setOffscreenPageLimit(CREATOR_TAB_COUNT - 1);
        creatorTabsPager.setAdapter(new CreatorTabsPagerAdapter());
        refresh.addView(creatorTabsPager, new SwipeRefreshLayout.LayoutParams(-1, -1));
        refresh.setOnChildScrollUpCallback((parent, child) -> {
            RecyclerView active = activeCreatorRecycler();
            return active != null && active.canScrollVertically(-1);
        });

        creatorTabsMediator = new TabLayoutMediator(
                creatorTabs,
                creatorTabsPager,
                (tab, position) -> tab.setText(position == CREATOR_TAB_PICTURES
                        ? "Pictures"
                        : position == CREATOR_TAB_VIDEOS ? "Videos" : "All")
        );
        creatorTabsMediator.attach();
        updateCreatorTabLabels();
        creatorTabsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                RecyclerView active = activeCreatorRecycler();
                if (active != null) recycler = active;
                updateCreatorEmptyState();
                creatorTabsPager.postDelayed(() -> {
                    if (isFinishing()) return;
                    RecyclerView current = activeCreatorRecycler();
                    if (current != null) recycler = current;
                    OledImmersiveUiController.attachBrowser(NativeFeedBrowserActivity.this);
                    FeedMotionController.attach(NativeFeedBrowserActivity.this);
                }, 80L);
            }
        });
    }

    private void attachFeedScrollListener(RecyclerView list) {
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                int[] range = visibleRange(view.getLayoutManager());
                adapter.preloadVisible(range[0], range[1]);
                if (dy > 0 && !loading && !endReached &&
                        range[1] >= Math.max(0, adapter.getItemCount() - 5)) {
                    load(true);
                }
            }
        });
    }

    private void attachGalleryScrollListener(
            RecyclerView list,
            BunkrGalleryAdapter galleryAdapter
    ) {
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                int[] range = visibleRange(view.getLayoutManager());
                galleryAdapter.preloadVisible(range[0], range[1]);
                if (dy > 0 && !loading && !endReached &&
                        range[1] >= Math.max(0, galleryAdapter.getItemCount() - 5)) {
                    load(true);
                }
            }
        });
    }

    private int[] visibleRange(RecyclerView.LayoutManager manager) {
        if (manager instanceof StaggeredGridLayoutManager) {
            StaggeredGridLayoutManager staggered = (StaggeredGridLayoutManager) manager;
            int[] first = staggered.findFirstVisibleItemPositions(null);
            int[] last = staggered.findLastVisibleItemPositions(null);
            return new int[]{minimumPosition(first), maximumPosition(last)};
        }
        if (manager instanceof LinearLayoutManager) {
            LinearLayoutManager linear = (LinearLayoutManager) manager;
            return new int[]{
                    Math.max(0, linear.findFirstVisibleItemPosition()),
                    Math.max(0, linear.findLastVisibleItemPosition())
            };
        }
        return new int[]{0, 0};
    }

    private int minimumPosition(int[] positions) {
        int result = Integer.MAX_VALUE;
        if (positions != null) {
            for (int position : positions) {
                if (position != RecyclerView.NO_POSITION) result = Math.min(result, position);
            }
        }
        return result == Integer.MAX_VALUE ? 0 : result;
    }

    private int maximumPosition(int[] positions) {
        int result = 0;
        if (positions != null) {
            for (int position : positions) result = Math.max(result, position);
        }
        return result;
    }

    private void reload() {
        generation++;
        fapelloFailureShown = false;
        currentPage = 0;
        loading = false;
        endReached = false;
        empty.setVisibility(View.GONE);
        if (isBunkr()) {
            replaceBunkrItems(new ArrayList<>());
            bunkrGallerySessionId = isCreatorGallery()
                    ? BunkrGallerySessionStore.createCreator(title, baseUrl, creatorQuery)
                    : BunkrGallerySessionStore.create(title, baseUrl);
            if (isCreatorGallery()) {
                creatorGalleryRepository.reset(
                        bunkrGallerySessionId, creatorQuery, fapelloProfileUrl, title);
            }
        } else {
            adapter.replace(new ArrayList<>());
        }
        load(false);
    }

    private void load(boolean append) {
        if (restoringBrowser || loading || endReached) return;
        loading = true;
        int requestPage = append ? currentPage + 1 : 1;
        int requestGeneration = generation;
        String requestSession = bunkrGallerySessionId;
        if (!append && itemCount() == 0) progress.setVisibility(View.VISIBLE);

        io.execute(() -> {
            try {
                List<NativeContentItem> result;
                BunkrCreatorGalleryRepository.Batch creatorBatch = null;
                if (memeMode) {
                    result = memeRepository.fetch(this, requestPage);
                } else if (isCreatorGallery()) {
                    creatorBatch = creatorGalleryRepository.fetchNext(
                            this,
                            requestSession,
                            creatorQuery,
                            fapelloProfileUrl,
                            title
                    );
                    result = creatorBatch.items;
                } else if (isBunkr()) {
                    result = bunkrRepository.fetchAlbum(this, baseUrl, requestPage);
                } else if (isEfukt()) {
                    result = efuktRepository.fetchSeriesFeed(this, baseUrl, requestPage);
                } else {
                    result = repository.fetchFeed(this, baseUrl, requestPage);
                }
                BunkrCreatorGalleryRepository.Batch completedCreatorBatch = creatorBatch;
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing() || isDestroyed()) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    refresh.setRefreshing(false);
                    int before = itemCount();
                    BunkrGallerySessionStore.Snapshot currentCreator = isCreatorGallery()
                            ? BunkrGallerySessionStore.snapshot(requestSession) : null;
                    if (currentCreator != null) {
                        replaceBunkrItems(currentCreator.items);
                        currentPage = currentCreator.currentPage;
                    } else if (isBunkr()) {
                        if (append) appendBunkrItems(result);
                        else replaceBunkrItems(result);
                    } else if (append) {
                        adapter.append(result);
                    } else {
                        adapter.replace(result);
                    }
                    int added = itemCount() - before;
                    if (currentCreator == null && !result.isEmpty() && (!append || added > 0)) currentPage = requestPage;
                    if (isCreatorGallery()) {
                        endReached = currentCreator != null ? currentCreator.endReached
                                : completedCreatorBatch == null || completedCreatorBatch.endReached;
                    } else if (result.isEmpty() || (append && added == 0) || isEfukt()) {
                        endReached = true;
                    }
                    if (isBunkr() && !isCreatorGallery()) {
                        if (append) {
                            BunkrGallerySessionStore.append(
                                    bunkrGallerySessionId,
                                    result,
                                    currentPage,
                                    endReached
                            );
                        } else {
                            BunkrGallerySessionStore.replace(
                                    bunkrGallerySessionId,
                                    result,
                                    currentPage,
                                    endReached
                            );
                        }
                    }
                    persistBrowser();
                    restoreScrollPositions();
                    empty.setVisibility(View.GONE);
                    if (isCreatorGallery()) {
                        updateCreatorEmptyState();
                        showFapelloFailure(completedCreatorBatch == null
                                ? null
                                : completedCreatorBatch.fapelloFailure);
                    } else if (itemCount() == 0) {
                        empty.setText(isCreatorGallery()
                                ? "No matching pictures or videos loaded.\nTap to try again."
                                : isBunkr()
                                ? "No supported pictures or videos were found.\nTap to open the album."
                                : "Couldn't render this feed natively.\nTap to open the website.");
                        empty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing() || isDestroyed()) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    refresh.setRefreshing(false);
                    if (isCreatorGallery()) {
                        updateCreatorEmptyState();
                        FapelloSourceException failure = fapelloFailure(e);
                        if (failure != null && itemCount() == 0) {
                            empty.setText(failure.userMessage() + "\nTap to try again.");
                            empty.setVisibility(View.VISIBLE);
                        }
                        if (itemCount() > 0) {
                            Toast.makeText(
                                    this,
                                    "Couldn't load more right now.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    } else if (itemCount() == 0) {
                        empty.setText(isCreatorGallery()
                                ? "Couldn't build this creator gallery.\nTap to try again."
                                : "Couldn't load this feed.\nTap to open the website.");
                        empty.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(this, "Couldn't load more right now.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showFapelloFailure(FapelloSourceException failure) {
        if (failure == null || fapelloFailureShown) return;
        fapelloFailureShown = true;
        if (itemCount() == 0) {
            empty.setText(failure.userMessage() + "\nTap to try again.");
            empty.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, failure.userMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private FapelloSourceException fapelloFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof FapelloSourceException) {
                return (FapelloSourceException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private void replaceBunkrItems(List<NativeContentItem> items) {
        if (!isCreatorGallery()) {
            if (bunkrGalleryAdapter != null) bunkrGalleryAdapter.replace(items);
            return;
        }
        for (int tab = 0; tab < CREATOR_TAB_COUNT; tab++) {
            if (creatorTabAdapters[tab] != null) {
                creatorTabAdapters[tab].replace(filterCreatorItems(items, tab));
            }
        }
        updateCreatorTabLabels();
    }

    private void appendBunkrItems(List<NativeContentItem> items) {
        if (!isCreatorGallery()) {
            if (bunkrGalleryAdapter != null) bunkrGalleryAdapter.append(items);
            return;
        }
        for (int tab = 0; tab < CREATOR_TAB_COUNT; tab++) {
            if (creatorTabAdapters[tab] != null) {
                creatorTabAdapters[tab].append(filterCreatorItems(items, tab));
            }
        }
        updateCreatorTabLabels();
    }

    private ArrayList<NativeContentItem> filterCreatorItems(
            List<NativeContentItem> items,
            int tab
    ) {
        ArrayList<NativeContentItem> filtered = new ArrayList<>();
        if (items == null) return filtered;
        for (NativeContentItem item : items) {
            if (item == null) continue;
            if (tab == CREATOR_TAB_PICTURES && !item.isImage()) continue;
            if (tab == CREATOR_TAB_VIDEOS && !item.isVideo()) continue;
            filtered.add(item);
        }
        return filtered;
    }

    private int activeCreatorTab() {
        if (creatorTabsPager == null) return CREATOR_TAB_ALL;
        return Math.max(
                CREATOR_TAB_ALL,
                Math.min(CREATOR_TAB_VIDEOS, creatorTabsPager.getCurrentItem())
        );
    }

    private RecyclerView activeCreatorRecycler() {
        return creatorTabRecyclers[activeCreatorTab()];
    }

    private BunkrGalleryAdapter activeCreatorAdapter() {
        return creatorTabAdapters[activeCreatorTab()];
    }

    private void updateCreatorTabLabels() {
        if (creatorTabs == null) return;
        String[] labels = {"All", "Pictures", "Videos"};
        for (int tab = 0; tab < CREATOR_TAB_COUNT; tab++) {
            TabLayout.Tab target = creatorTabs.getTabAt(tab);
            if (target == null) continue;
            BunkrGalleryAdapter galleryAdapter = creatorTabAdapters[tab];
            int count = galleryAdapter == null ? 0 : galleryAdapter.getItemCount();
            target.setText(count > 0 ? labels[tab] + "  " + count : labels[tab]);
        }
    }

    private void updateCreatorEmptyState() {
        if (!isCreatorGallery() || empty == null) return;
        BunkrGalleryAdapter active = activeCreatorAdapter();
        if (active != null && active.getItemCount() > 0) {
            empty.setVisibility(View.GONE);
            return;
        }

        int tab = activeCreatorTab();
        if (loading) {
            empty.setText(tab == CREATOR_TAB_PICTURES
                    ? "Loading pictures..."
                    : tab == CREATOR_TAB_VIDEOS ? "Loading videos..." : "Loading gallery...");
        } else if (itemCount() == 0) {
            empty.setText(endReached
                    ? "No matching pictures or videos were found.\nTap to try again."
                    : "No matching pictures or videos loaded.\nTap to try again.");
        } else if (tab == CREATOR_TAB_PICTURES) {
            empty.setText(endReached
                    ? "No pictures were found for this creator."
                    : "No pictures loaded yet.\nTap to load more.");
        } else if (tab == CREATOR_TAB_VIDEOS) {
            empty.setText(endReached
                    ? "No videos were found for this creator."
                    : "No videos loaded yet.\nTap to load more.");
        } else {
            empty.setText("No media loaded yet.\nTap to try again.");
        }
        empty.setVisibility(View.VISIBLE);
    }

    private void openVideo(NativeContentItem item) {
        progress.setVisibility(View.VISIBLE);
        final int requestGeneration = generation;
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = PlayableSourceRouter.resolve(this, item.url);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            runOnUiThread(() -> {
                if (requestGeneration != generation || isFinishing()) return;
                progress.setVisibility(View.GONE);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    openWebsite(item.url);
                    return;
                }
                Intent intent = new Intent(this, VideoDetailActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, resolved.mediaUrl);
                intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, resolved.pageUrl);
                intent.putExtra(PlayerActivity.EXTRA_TITLE, item.title);
                intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);
                intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);
                intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);
                intent.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, baseUrl);
                intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, source);
                intent.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, resolved.requestReferer);
                try {
                    intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, WebSettings.getDefaultUserAgent(this));
                } catch (Exception ignored) {
                }
                try {
                    String cookies = CookieManager.getInstance().getCookie(resolved.mediaUrl);
                    if ((cookies == null || cookies.isEmpty()) && resolved.pageUrl != null) {
                        cookies = CookieManager.getInstance().getCookie(resolved.pageUrl);
                    }
                    if (cookies != null) intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
                } catch (Exception ignored) {
                }
                startActivity(intent);
            });
        });
    }

    private void openMeme(NativeContentItem item) {
        Intent intent = new Intent(this, MemeViewerActivity.class);
        intent.putExtra(MemeViewerActivity.EXTRA_TITLE, item.title);
        intent.putExtra(MemeViewerActivity.EXTRA_PAGE_URL, item.url);
        intent.putExtra(MemeViewerActivity.EXTRA_IMAGE_URL, item.imageUrl);
        startActivity(intent);
    }

    private void openBunkrGallery(int position, NativeContentItem item) {
        if (item == null || bunkrGalleryAdapter == null) return;
        if (!isCreatorGallery()) BunkrGallerySessionStore.replace(
                bunkrGallerySessionId,
                bunkrGalleryAdapter.snapshot(),
                currentPage,
                endReached
        );
        Intent intent = new Intent(this, BunkrGalleryActivity.class);
        intent.putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, bunkrGallerySessionId);
        intent.putExtra(BunkrGalleryActivity.EXTRA_TITLE, title);
        intent.putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, baseUrl);
        intent.putExtra(BunkrGalleryActivity.EXTRA_CREATOR_QUERY, creatorQuery);
        intent.putExtra(BunkrGalleryActivity.EXTRA_FAPELLO_PROFILE_URL, fapelloProfileUrl);
        intent.putExtra(
                BunkrGalleryActivity.EXTRA_MEDIA_FILTER,
                !isCreatorGallery() || activeCreatorTab() == CREATOR_TAB_ALL
                        ? BunkrGalleryActivity.FILTER_ALL
                        : activeCreatorTab() == CREATOR_TAB_PICTURES
                        ? BunkrGalleryActivity.FILTER_PICTURES
                        : BunkrGalleryActivity.FILTER_VIDEOS
        );
        intent.putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, item.url);
        intent.putExtra(BunkrGalleryActivity.EXTRA_INITIAL_POSITION, position);
        startActivity(intent);
    }

    private void showItemMenu(NativeContentItem item, View anchor) {
        if (isBunkr()) {
            PopupMenu menu = new PopupMenu(this, anchor);
            if (item.isVideo()) {
                menu.getMenu().add(Menu.NONE, 1, 0, "Download video");
            }
            menu.getMenu().add(Menu.NONE, 2, 1, "Share");
            menu.getMenu().add(Menu.NONE, 3, 2, "Open item page");
            menu.setOnMenuItemClickListener(clicked -> {
                if (clicked.getItemId() == 1) {
                    VideoDownloadStore.downloadPage(this, item);
                    return true;
                }
                if (clicked.getItemId() == 2) {
                    shareItem(item);
                    return true;
                }
                if (clicked.getItemId() == 3) {
                    openWebsite(item.url);
                    return true;
                }
                return false;
            });
            menu.show();
            return;
        }
        if (!memeMode) {
            showVideoItemMenu(item);
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(Menu.NONE, 2, 1, "Share");
        menu.getMenu().add(Menu.NONE, 3, 2, "Open website page");
        menu.setOnMenuItemClickListener(clicked -> {
            if (clicked.getItemId() == 2) {
                shareItem(item);
                return true;
            }
            if (clicked.getItemId() == 3) {
                openWebsite(item.url);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showVideoItemMenu(NativeContentItem item) {
        String saveTitle = FavoriteStore.contains(this, item.url)
                ? "Remove from Watch Later"
                : "Save to Watch Later";
        ArrayList<VideoActionSheet.Action> actions = new ArrayList<>();
        if (item.comments != null && !item.comments.isEmpty()) {
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_comments,
                    "Comments",
                    item.comments + " ready to view",
                    () -> new InlineCommentsDialog(
                            this,
                            item.url,
                            item.title,
                            item.comments,
                            null
                    ).show()
            ));
        }
        actions.add(VideoActionSheet.action(
                R.drawable.ic_action_share,
                "Share",
                "Send the CrazyShit page",
                () -> shareItem(item)
        ));
        actions.add(VideoActionSheet.action(
                R.drawable.ic_more_website,
                "Open website page",
                "Use the compatibility browser",
                () -> openWebsite(item.url)
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

    private void showOptions(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (!isBunkr()) menu.getMenu().add(Menu.NONE, 1, 0, "View style");
        menu.getMenu().add(Menu.NONE, 2, 1, "Open website");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showViewStyleDialog();
                return true;
            }
            if (item.getItemId() == 2) {
                openWebsite(baseUrl);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showViewStyleDialog() {
        String[] choices = {"Cards", "List", "Grid", "Posters"};
        int selected = viewMode();
        new AlertDialog.Builder(this)
                .setTitle("View style")
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .edit()
                            .putInt("native_view_collection", which)
                            .apply();
                    applyLayout();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int viewMode() {
        int mode = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getInt("native_view_collection", NativeFeedAdapter.VIEW_LIST);
        if (mode < NativeFeedAdapter.VIEW_CARDS || mode > NativeFeedAdapter.VIEW_POSTERS) {
            return NativeFeedAdapter.VIEW_LIST;
        }
        return mode;
    }

    private void applyLayout() {
        if (isCreatorGallery()) {
            for (RecyclerView creatorRecycler : creatorTabRecyclers) {
                if (creatorRecycler != null) applyCreatorGalleryLayout(creatorRecycler);
            }
            RecyclerView active = activeCreatorRecycler();
            if (active != null) recycler = active;
            return;
        }
        if (recycler == null || (adapter == null && bunkrGalleryAdapter == null)) return;
        RecyclerView.LayoutManager old = recycler.getLayoutManager();
        int position = 0;
        int offset = 0;
        if (old instanceof LinearLayoutManager) {
            LinearLayoutManager lm = (LinearLayoutManager) old;
            position = Math.max(0, lm.findFirstVisibleItemPosition());
            View anchor = lm.findViewByPosition(position);
            if (anchor != null) offset = anchor.getTop() - recycler.getPaddingTop();
        }

        if (isBunkr()) {
            Configuration config = getResources().getConfiguration();
            boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
            int columns;
            if (landscape) columns = config.screenWidthDp >= 900 ? 7 : 5;
            else columns = config.screenWidthDp >= 600 ? 5 : 3;
            GridLayoutManager gallery = new GridLayoutManager(this, columns);
            recycler.setLayoutManager(gallery);
            if (bunkrGalleryAdapter.getItemCount() > 0) {
                int safe = Math.min(position, bunkrGalleryAdapter.getItemCount() - 1);
                gallery.scrollToPositionWithOffset(safe, offset);
            }
            return;
        }

        int mode = viewMode();
        adapter.setViewMode(mode);
        LinearLayoutManager next;
        if (mode == NativeFeedAdapter.VIEW_GRID || mode == NativeFeedAdapter.VIEW_POSTERS) {
            Configuration config = getResources().getConfiguration();
            boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
            int columns = landscape && config.screenWidthDp >= 900 ? 3 : 2;
            GridLayoutManager grid = new GridLayoutManager(this, columns);
            grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int adapterPosition) {
                    return adapter.isSectionAt(adapterPosition) ? columns : 1;
                }
            });
            next = grid;
        } else {
            next = new LinearLayoutManager(this);
        }
        recycler.setLayoutManager(next);
        if (adapter.getItemCount() > 0) {
            int safe = Math.min(position, adapter.getItemCount() - 1);
            next.scrollToPositionWithOffset(safe, offset);
        }
    }

    private void applyCreatorGalleryLayout(RecyclerView list) {
        if (list == null) return;
        RecyclerView.LayoutManager old = list.getLayoutManager();
        int position = 0;
        int offset = 0;
        if (old != null) {
            int[] range = visibleRange(old);
            position = Math.max(0, range[0]);
            View anchor = old.findViewByPosition(position);
            if (anchor != null) offset = anchor.getTop() - list.getPaddingTop();
        }

        StaggeredGridLayoutManager gallery = new StaggeredGridLayoutManager(
                creatorGalleryColumnCount(),
                StaggeredGridLayoutManager.VERTICAL
        );
        gallery.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        list.setLayoutManager(gallery);
        RecyclerView.Adapter<?> listAdapter = list.getAdapter();
        if (listAdapter != null && listAdapter.getItemCount() > 0) {
            int safe = Math.min(position, listAdapter.getItemCount() - 1);
            gallery.scrollToPositionWithOffset(safe, offset);
        }
    }

    private int creatorGalleryColumnCount() {
        if (creatorGalleryColumns > 0) {
            return clamp(
                    creatorGalleryColumns,
                    creatorGalleryMinimumColumns(),
                    creatorGalleryMaximumColumns()
            );
        }
        creatorGalleryColumns = savedCreatorGalleryColumnCount();
        return creatorGalleryColumns;
    }

    private int savedCreatorGalleryColumnCount() {
        int fallback = defaultCreatorGalleryColumnCount();
        int saved = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getInt(creatorGalleryPreferenceKey(), fallback);
        return clamp(saved, creatorGalleryMinimumColumns(), creatorGalleryMaximumColumns());
    }

    private int defaultCreatorGalleryColumnCount() {
        Configuration config = getResources().getConfiguration();
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) return config.screenWidthDp >= 900 ? 7 : 5;
        return config.screenWidthDp >= 600 ? 4 : 2;
    }

    private int creatorGalleryMinimumColumns() {
        return getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
    }

    private int creatorGalleryMaximumColumns() {
        Configuration config = getResources().getConfiguration();
        if (config.screenWidthDp >= 900) return 8;
        if (config.screenWidthDp >= 600) return 7;
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE ? 7 : 5;
    }

    private String creatorGalleryPreferenceKey() {
        Configuration config = getResources().getConfiguration();
        String size = config.screenWidthDp >= 600 ? "tablet" : "phone";
        String orientation = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "wide"
                : "tall";
        return "creator_gallery_columns_" + size + "_" + orientation;
    }

    private void changeCreatorGalleryColumns(int delta) {
        int next = clamp(
                creatorGalleryColumnCount() + delta,
                creatorGalleryMinimumColumns(),
                creatorGalleryMaximumColumns()
        );
        if (next == creatorGalleryColumns) return;
        creatorGalleryColumns = next;
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putInt(creatorGalleryPreferenceKey(), next)
                .apply();

        for (RecyclerView creatorRecycler : creatorTabRecyclers) {
            if (creatorRecycler == null) continue;
            RecyclerView.LayoutManager manager = creatorRecycler.getLayoutManager();
            if (manager instanceof StaggeredGridLayoutManager) {
                StaggeredGridLayoutManager grid = (StaggeredGridLayoutManager) manager;
                grid.setSpanCount(next);
                grid.invalidateSpanAssignments();
            } else {
                applyCreatorGalleryLayout(creatorRecycler);
            }
        }
    }

    private void attachCreatorGalleryPinch(RecyclerView list) {
        final float[] accumulatedScale = {1f};
        ScaleGestureDetector detector = new ScaleGestureDetector(
                this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector scaleDetector) {
                        accumulatedScale[0] = 1f;
                        list.requestDisallowInterceptTouchEvent(true);
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector scaleDetector) {
                        accumulatedScale[0] *= scaleDetector.getScaleFactor();
                        if (accumulatedScale[0] >= 1.16f) {
                            changeCreatorGalleryColumns(-1);
                            accumulatedScale[0] = 1f;
                        } else if (accumulatedScale[0] <= 0.86f) {
                            changeCreatorGalleryColumns(1);
                            accumulatedScale[0] = 1f;
                        }
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector scaleDetector) {
                        accumulatedScale[0] = 1f;
                        list.requestDisallowInterceptTouchEvent(false);
                    }
                }
        );

        list.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private boolean scaling;

            @Override
            public boolean onInterceptTouchEvent(
                    @NonNull RecyclerView view,
                    @NonNull MotionEvent event
            ) {
                detector.onTouchEvent(event);
                if (event.getPointerCount() > 1 || detector.isInProgress()) {
                    scaling = true;
                    view.requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                        event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    scaling = false;
                    view.requestDisallowInterceptTouchEvent(false);
                }
                return scaling;
            }

            @Override
            public void onTouchEvent(
                    @NonNull RecyclerView view,
                    @NonNull MotionEvent event
            ) {
                detector.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                        event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    scaling = false;
                    view.requestDisallowInterceptTouchEvent(false);
                }
            }
        });
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void openWebsite(String url) {
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, url);
        startActivity(intent);
    }

    private boolean isEfukt() {
        return SOURCE_EFUKT.equals(source) || EfuktRepository.isEfuktUrl(baseUrl);
    }

    private boolean isBunkr() {
        return SOURCE_BUNKR.equals(source) || BunkrRepository.isAlbumUrl(baseUrl);
    }

    private boolean isCreatorGallery() {
        return creatorQuery != null && !creatorQuery.isEmpty();
    }

    private boolean supportsComments() {
        return !isEfukt() && !isBunkr();
    }

    private int itemCount() {
        return isBunkr()
                ? (bunkrGalleryAdapter == null ? 0 : bunkrGalleryAdapter.getItemCount())
                : (adapter == null ? 0 : adapter.getItemCount());
    }

    private void persistBrowser() {
        if (restoringBrowser) return;
        if (isBunkr()) { BunkrGallerySessionStore.persist(this, bunkrGallerySessionId); return; }
        try {
            org.json.JSONObject value = new org.json.JSONObject().put("page", currentPage).put("end", endReached)
                    .put("items", ContentItemCodec.encodeList(adapter.snapshot(), 2000));
            ScreenSnapshotStore.save(this, browserSnapshot, value);
        } catch (Exception ignored) { }
    }

    private void restoreBrowser() {
        restoringBrowser = true;
        io.execute(() -> {
            BunkrGallerySessionStore.Snapshot gallery = isBunkr()
                    ? BunkrGallerySessionStore.restore(this, bunkrGallerySessionId) : null;
            org.json.JSONObject feed = isBunkr() ? null : ScreenSnapshotStore.read(this, browserSnapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                restoringBrowser = false;
                if (creatorTabsPager != null) creatorTabsPager.setCurrentItem(restoredBrowserState.getInt("tab", 0), false);
                if (gallery != null) {
                    replaceBunkrItems(gallery.items);
                    currentPage = gallery.currentPage; endReached = gallery.endReached;
                    if (gallery.items.isEmpty() && !endReached) { load(false); return; }
                } else if (feed != null) {
                    adapter.replace(ContentItemCodec.decodeList(feed.optJSONArray("items"), 2000));
                    currentPage = feed.optInt("page"); endReached = feed.optBoolean("end");
                } else {
                    if (isBunkr()) {
                        bunkrGallerySessionId = isCreatorGallery()
                                ? BunkrGallerySessionStore.createCreator(title, baseUrl, creatorQuery)
                                : BunkrGallerySessionStore.create(title, baseUrl);
                        if (isCreatorGallery()) creatorGalleryRepository.reset(
                                bunkrGallerySessionId, creatorQuery, fapelloProfileUrl, title);
                    }
                    load(false); return;
                }
                progress.setVisibility(View.GONE);
                if (creatorTabsPager != null) creatorTabsPager.setCurrentItem(restoredBrowserState.getInt("tab", 0), false);
                restoreScrollPositions();
                updateCreatorEmptyState();
            });
        });
    }

    private void restoreScrollPositions() {
        if (restoredBrowserState == null) return;
        if (isCreatorGallery()) {
            for (int i = 0; i < CREATOR_TAB_COUNT; i++) restoreScroll(creatorTabRecyclers[i], "scroll_" + i);
        } else restoreScroll(recycler, "scroll");
    }

    private void restoreScroll(RecyclerView view, String key) {
        if (view == null || view.getLayoutManager() == null || restoredBrowserState == null) return;
        android.os.Parcelable scroll = restoredBrowserState.getParcelable(key);
        if (scroll != null) {
            view.getLayoutManager().onRestoreInstanceState(scroll);
            restoredBrowserState.remove(key);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("browser_snapshot", browserSnapshot);
        state.putString("gallery_session", bunkrGallerySessionId);
        state.putInt("tab", activeCreatorTab());
        if (isCreatorGallery()) {
            for (int i = 0; i < CREATOR_TAB_COUNT; i++) {
                RecyclerView view = creatorTabRecyclers[i];
                if (view != null && view.getLayoutManager() != null)
                    state.putParcelable("scroll_" + i, view.getLayoutManager().onSaveInstanceState());
            }
        } else if (recycler != null && recycler.getLayoutManager() != null)
            state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        persistBrowser();
        super.onSaveInstanceState(state);
    }

    @Override protected void onPause() { persistBrowser(); super.onPause(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (creatorProfile != null) creatorProfile.refresh();
        if (isBunkr() && bunkrGalleryAdapter != null) {
            BunkrGallerySessionStore.Snapshot snapshot =
                    BunkrGallerySessionStore.snapshot(bunkrGallerySessionId);
            if (snapshot != null) {
                if (snapshot.items.size() > bunkrGalleryAdapter.size()) {
                    replaceBunkrItems(snapshot.items);
                }
                currentPage = snapshot.currentPage;
                endReached = snapshot.endReached;
            }
            updateCreatorEmptyState();
        } else if (adapter != null) {
            adapter.refreshPlaybackState();
        }
        applyLayout();
    }

    @Override
    protected void onDestroy() {
        generation++;
        if (adapter != null) adapter.close();
        if (creatorTabsMediator != null) creatorTabsMediator.detach();
        io.shutdownNow();
        super.onDestroy();
    }

    private final class CreatorTabsPagerAdapter
            extends RecyclerView.Adapter<CreatorTabHolder> {
        CreatorTabsPagerAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public CreatorTabHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            RecyclerView page = createRecycler();
            page.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            page.setAdapter(creatorTabAdapters[viewType]);
            creatorTabRecyclers[viewType] = page;
            applyCreatorGalleryLayout(page);
            attachCreatorGalleryPinch(page);
            attachGalleryScrollListener(page, creatorTabAdapters[viewType]);
            if (viewType == activeCreatorTab()) recycler = page;
            return new CreatorTabHolder(page, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull CreatorTabHolder holder, int position) {
            creatorTabRecyclers[position] = holder.recycler;
            if (!restoringBrowser) restoreScroll(holder.recycler, "scroll_" + position);
            if (position == activeCreatorTab()) recycler = holder.recycler;
        }

        @Override
        public void onViewRecycled(@NonNull CreatorTabHolder holder) {
            if (creatorTabRecyclers[holder.tab] == holder.recycler) {
                creatorTabRecyclers[holder.tab] = null;
            }
            holder.recycler.clearOnScrollListeners();
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return CREATOR_TAB_COUNT;
        }
    }

    private static final class CreatorTabHolder extends RecyclerView.ViewHolder {
        final RecyclerView recycler;
        final int tab;

        CreatorTabHolder(RecyclerView recycler, int tab) {
            super(recycler);
            this.recycler = recycler;
            this.tab = tab;
        }
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
