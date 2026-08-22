package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
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
 * Keeps Home, Trending, Memes and Chaos alive for true horizontal paging.
 * Chaos itself owns a nested vertical ViewPager2 for Shorts/Reels-style playback.
 */
public final class MainPagerAdapter extends RecyclerView.Adapter<MainPagerAdapter.Holder> {
    public static final int PAGE_HOME = 0;
    public static final int PAGE_TRENDING = 1;
    public static final int PAGE_MEMES = 2;
    public static final int PAGE_CHAOS = 3;
    public static final int PAGE_COUNT = 4;
    private static final int FEED_PAGE_COUNT = 3;

    public interface Host {
        void onOpenItem(NativeContentItem item, boolean meme);
        void onLongPressItem(NativeContentItem item, View anchor, boolean meme);
        void onOpenComments(NativeContentItem item);
    }

    private final Activity activity;
    private final Host host;
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final MemeRepository memeRepository = new MemeRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Page[] pages = new Page[FEED_PAGE_COUNT];
    private final ChaosFeedView chaosView;

    public MainPagerAdapter(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        setHasStableIds(true);
        pages[PAGE_HOME] = buildFeedPage(PAGE_HOME, "native_view_home", CrazyShitRepository.HOME, false);
        pages[PAGE_TRENDING] = buildFeedPage(PAGE_TRENDING, "native_view_trending", CrazyShitRepository.TRENDING, false);
        pages[PAGE_MEMES] = buildFeedPage(PAGE_MEMES, "native_view_memes", MemeRepository.MEMES, true);

        chaosView = new ChaosFeedView(activity, new ChaosFeedView.Host() {
            @Override
            public void openDetails(NativeContentItem item) {
                host.onOpenItem(item, false);
            }

            @Override
            public void openComments(NativeContentItem item) {
                host.onOpenComments(item);
            }
        });
        chaosView.setActive(false);

        for (Page page : pages) load(page, false);
    }

    public String titleFor(int position) {
        if (position == PAGE_TRENDING) return "Trending";
        if (position == PAGE_MEMES) return "Memes";
        if (position == PAGE_CHAOS) return "Chaos";
        return "Home";
    }

    public int viewMode(int position) {
        if (position == PAGE_CHAOS) return NativeFeedAdapter.VIEW_LARGE;
        Page page = pageAt(position);
        return page == null ? NativeFeedAdapter.VIEW_LARGE : page.viewMode;
    }

    public void setViewMode(int position, int mode) {
        if (position == PAGE_CHAOS) return;
        Page page = pageAt(position);
        if (page == null) return;
        int safe = mode;
        if (safe < NativeFeedAdapter.VIEW_LARGE || safe > NativeFeedAdapter.VIEW_GRID) {
            safe = NativeFeedAdapter.VIEW_LARGE;
        }
        page.viewMode = safe;
        activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .edit()
                .putInt(page.preferenceKey, safe)
                .apply();
        applyLayout(page);
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

    public void onHostResume() {
        chaosView.onHostResume();
    }

    public void onHostPause() {
        chaosView.onHostPause();
    }

    public void close() {
        chaosView.close();
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

    private Page buildFeedPage(int index, String prefKey, String baseUrl, boolean meme) {
        Page page = new Page(index, prefKey, baseUrl, meme);
        page.root = new FrameLayout(activity);
        page.root.setBackgroundColor(Color.rgb(13, 13, 15));

        page.refresh = new SwipeRefreshLayout(activity);
        page.refresh.setColorSchemeColors(Color.rgb(255, 90, 31));
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

        page.adapter = new NativeFeedAdapter(activity, new NativeFeedAdapter.Listener() {
            @Override
            public void onOpen(NativeContentItem item) {
                if (page.meme || item.isMeme()) openMeme(item);
                else host.onOpenItem(item, false);
            }

            @Override
            public void onLongPress(NativeContentItem item, View anchor) {
                if (page.meme || item.isMeme()) showMemeMenu(item, anchor);
                else host.onLongPressItem(item, anchor, false);
            }

            @Override
            public void onComments(NativeContentItem item) {
                if (!page.meme && !item.isMeme()) host.onOpenComments(item);
            }
        });
        page.recycler.setAdapter(page.adapter);
        page.viewMode = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getInt(prefKey, NativeFeedAdapter.VIEW_LARGE);
        applyLayout(page);

        page.refresh.setOnRefreshListener(() -> refresh(page.index));
        page.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                RecyclerView.LayoutManager manager = view.getLayoutManager();
                if (!(manager instanceof LinearLayoutManager)) return;
                LinearLayoutManager lm = (LinearLayoutManager) manager;
                int first = lm.findFirstVisibleItemPosition();
                int last = lm.findLastVisibleItemPosition();
                page.adapter.preloadVisible(first, last);
                if (dy > 0 && !page.loading && !page.endReached &&
                        last >= Math.max(0, page.adapter.getItemCount() - 5)) {
                    load(page, true);
                }
            }
        });

        return page;
    }

    private void openMeme(NativeContentItem item) {
        if (item == null) return;
        Intent intent = new Intent(activity, MemeViewerActivity.class);
        intent.putExtra(MemeViewerActivity.EXTRA_TITLE, item.title);
        intent.putExtra(MemeViewerActivity.EXTRA_PAGE_URL, item.url);
        intent.putExtra(MemeViewerActivity.EXTRA_IMAGE_URL, item.imageUrl);
        activity.startActivity(intent);
    }

    private void showMemeMenu(NativeContentItem item, View anchor) {
        if (item == null || anchor == null) return;
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, "View image");
        menu.getMenu().add(0, 2, 1, "Share");
        menu.getMenu().add(0, 3, 2, "Open meme page");
        menu.setOnMenuItemClickListener(clicked -> {
            if (clicked.getItemId() == 1) {
                openMeme(item);
                return true;
            }
            if (clicked.getItemId() == 2) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, item.url);
                share.putExtra(Intent.EXTRA_SUBJECT, item.title);
                activity.startActivity(Intent.createChooser(share, "Share meme"));
                return true;
            }
            if (clicked.getItemId() == 3) {
                Intent web = new Intent(activity, WebFallbackActivity.class);
                web.putExtra(WebFallbackActivity.EXTRA_URL, item.url);
                activity.startActivity(web);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void applyLayout(Page page) {
        if (page == null || page.recycler == null || page.adapter == null) return;
        page.adapter.setViewMode(page.viewMode);
        RecyclerView.LayoutManager old = page.recycler.getLayoutManager();
        int position = 0;
        if (old instanceof LinearLayoutManager) {
            position = Math.max(0, ((LinearLayoutManager) old).findFirstVisibleItemPosition());
        }
        if (page.viewMode == NativeFeedAdapter.VIEW_GRID) {
            page.recycler.setLayoutManager(new GridLayoutManager(activity, 2));
        } else {
            page.recycler.setLayoutManager(new LinearLayoutManager(activity));
        }
        if (page.adapter.getItemCount() > 0) {
            page.recycler.scrollToPosition(Math.min(position, page.adapter.getItemCount() - 1));
        }
    }

    private void load(Page page, boolean append) {
        if (page == null || page.loading || page.endReached) return;
        page.loading = true;
        final int generation = page.generation;
        final int requestPage = append ? page.currentPage + 1 : 1;
        if (!append && page.adapter.getItemCount() == 0) page.progress.setVisibility(View.VISIBLE);

        io.execute(() -> {
            try {
                List<NativeContentItem> result = page.meme
                        ? memeRepository.fetch(activity, requestPage)
                        : repository.fetchFeed(activity, page.baseUrl, requestPage);
                activity.runOnUiThread(() -> {
                    if (generation != page.generation) return;
                    page.loading = false;
                    page.progress.setVisibility(View.GONE);
                    page.refresh.setRefreshing(false);
                    if (append) page.adapter.append(result); else page.adapter.replace(result);
                    if (!result.isEmpty()) page.currentPage = requestPage;
                    if (result.isEmpty()) page.endReached = true;
                    page.empty.setVisibility(View.GONE);
                    if (page.adapter.getItemCount() == 0) {
                        page.empty.setText(page.meme
                                ? "No memes could be loaded right now."
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
                    if (page.adapter.getItemCount() == 0) {
                        page.empty.setText("Couldn't load this tab right now.");
                        page.empty.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
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
        final String preferenceKey;
        final String baseUrl;
        final boolean meme;

        FrameLayout root;
        SwipeRefreshLayout refresh;
        RecyclerView recycler;
        ProgressBar progress;
        TextView empty;
        NativeFeedAdapter adapter;
        int viewMode;
        int currentPage;
        boolean loading;
        boolean endReached;
        int generation;

        Page(int index, String preferenceKey, String baseUrl, boolean meme) {
            this.index = index;
            this.preferenceKey = preferenceKey;
            this.baseUrl = baseUrl;
            this.meme = meme;
        }
    }
}
