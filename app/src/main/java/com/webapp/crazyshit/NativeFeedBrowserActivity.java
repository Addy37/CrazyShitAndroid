package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native media feed opened from a Series or Category card. */
public final class NativeFeedBrowserActivity extends Activity {
    public static final String EXTRA_TITLE = "browser_title";
    public static final String EXTRA_BASE_URL = "browser_base_url";
    public static final String EXTRA_MEME_MODE = "browser_meme_mode";
    public static final String EXTRA_SOURCE = "browser_source";
    public static final String SOURCE_CRAZYSHIT = "crazyshit";
    public static final String SOURCE_EFUKT = "efukt";
    public static final String SOURCE_BUNKR = "bunkr";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final MemeRepository memeRepository = new MemeRepository();

    private NativeFeedAdapter adapter;
    private BunkrGalleryAdapter bunkrGalleryAdapter;
    private String bunkrGallerySessionId;
    private RecyclerView recycler;
    private SwipeRefreshLayout refresh;
    private ProgressBar progress;
    private TextView empty;
    private String title;
    private String baseUrl;
    private String source;
    private boolean memeMode;
    private boolean loading;
    private boolean endReached;
    private int currentPage;
    private int generation;

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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title = value(getIntent().getStringExtra(EXTRA_TITLE), "Browse");
        baseUrl = value(getIntent().getStringExtra(EXTRA_BASE_URL), CrazyShitRepository.HOME);
        memeMode = getIntent().getBooleanExtra(EXTRA_MEME_MODE, false);
        source = value(getIntent().getStringExtra(EXTRA_SOURCE), SOURCE_CRAZYSHIT);
        if (BunkrRepository.isAlbumUrl(baseUrl)) source = SOURCE_BUNKR;
        else if (EfuktRepository.isEfuktUrl(baseUrl)) source = SOURCE_EFUKT;
        buildUi();
        load(false);
    }

    private void buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(13, 13, 15));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.rgb(17, 17, 20));

        TextView back = text("‹", 34, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView heading = text(title, 20, Color.WHITE);
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

        FrameLayout body = new FrameLayout(this);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        refresh = new SwipeRefreshLayout(this);
        refresh.setColorSchemeColors(UiPalette.PRIMARY);
        refresh.setOnRefreshListener(this::reload);
        body.addView(refresh, new FrameLayout.LayoutParams(-1, -1));

        recycler = new RecyclerView(this);
        recycler.setBackgroundColor(Color.rgb(13, 13, 15));
        recycler.setClipToPadding(false);
        recycler.setPadding(0, dp(5), 0, dp(18));
        recycler.setItemAnimator(null);
        refresh.addView(recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));

        if (isBunkr()) {
            bunkrGallerySessionId = BunkrGallerySessionStore.create(title, baseUrl);
            bunkrGalleryAdapter = new BunkrGalleryAdapter(
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
                    }
            );
            recycler.setAdapter(bunkrGalleryAdapter);
        } else {
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
        }
        applyLayout();

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                RecyclerView.LayoutManager manager = view.getLayoutManager();
                if (!(manager instanceof LinearLayoutManager)) return;
                LinearLayoutManager lm = (LinearLayoutManager) manager;
                int first = lm.findFirstVisibleItemPosition();
                int last = lm.findLastVisibleItemPosition();
                if (isBunkr()) bunkrGalleryAdapter.preloadVisible(first, last);
                else adapter.preloadVisible(first, last);
                if (dy > 0 && !loading && !endReached &&
                        last >= Math.max(0, itemCount() - 5)) {
                    load(true);
                }
            }
        });

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        body.addView(progress, progressParams);

        empty = text("", 15, Color.rgb(190, 190, 198));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(28), dp(28), dp(28), dp(28));
        empty.setVisibility(View.GONE);
        empty.setOnClickListener(v -> openWebsite(baseUrl));
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        setContentView(shell);
    }

    private void reload() {
        generation++;
        currentPage = 0;
        loading = false;
        endReached = false;
        if (isBunkr()) {
            bunkrGalleryAdapter.replace(new ArrayList<>());
            BunkrGallerySessionStore.replace(
                    bunkrGallerySessionId,
                    new ArrayList<>(),
                    0,
                    false
            );
        } else {
            adapter.replace(new ArrayList<>());
        }
        load(false);
    }

    private void load(boolean append) {
        if (loading || endReached) return;
        loading = true;
        int requestPage = append ? currentPage + 1 : 1;
        int requestGeneration = generation;
        if (!append && itemCount() == 0) progress.setVisibility(View.VISIBLE);

        io.execute(() -> {
            try {
                List<NativeContentItem> result;
                if (memeMode) {
                    result = memeRepository.fetch(this, requestPage);
                } else if (isBunkr()) {
                    result = bunkrRepository.fetchAlbum(this, baseUrl, requestPage);
                } else if (isEfukt()) {
                    result = efuktRepository.fetchSeriesFeed(this, baseUrl, requestPage);
                } else {
                    result = repository.fetchFeed(this, baseUrl, requestPage);
                }
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    refresh.setRefreshing(false);
                    int before = itemCount();
                    if (isBunkr()) {
                        if (append) bunkrGalleryAdapter.append(result);
                        else bunkrGalleryAdapter.replace(result);
                    } else if (append) {
                        adapter.append(result);
                    } else {
                        adapter.replace(result);
                    }
                    int added = itemCount() - before;
                    if (!result.isEmpty() && (!append || added > 0)) currentPage = requestPage;
                    if (result.isEmpty() || (append && added == 0) || isEfukt()) {
                        endReached = true;
                    }
                    if (isBunkr()) {
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
                    empty.setVisibility(View.GONE);
                    if (itemCount() == 0) {
                        empty.setText(isBunkr()
                                ? "No supported pictures or videos were found.\nTap to open the album."
                                : "Couldn't render this feed natively.\nTap to open the website.");
                        empty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    refresh.setRefreshing(false);
                    if (itemCount() == 0) {
                        empty.setText("Couldn't load this feed.\nTap to open the website.");
                        empty.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(this, "Couldn't load more right now.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
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
        BunkrGallerySessionStore.replace(
                bunkrGallerySessionId,
                bunkrGalleryAdapter.snapshot(),
                currentPage,
                endReached
        );
        Intent intent = new Intent(this, BunkrGalleryActivity.class);
        intent.putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, bunkrGallerySessionId);
        intent.putExtra(BunkrGalleryActivity.EXTRA_TITLE, title);
        intent.putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, baseUrl);
        intent.putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, item.url);
        intent.putExtra(BunkrGalleryActivity.EXTRA_INITIAL_POSITION, position);
        startActivity(intent);
    }

    private void showItemMenu(NativeContentItem item, View anchor) {
        if (isBunkr()) {
            PopupMenu menu = new PopupMenu(this, anchor);
            menu.getMenu().add(Menu.NONE, 2, 1, "Share");
            menu.getMenu().add(Menu.NONE, 3, 2, "Open item page");
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

    private boolean supportsComments() {
        return !isEfukt() && !isBunkr();
    }

    private int itemCount() {
        return isBunkr()
                ? (bunkrGalleryAdapter == null ? 0 : bunkrGalleryAdapter.getItemCount())
                : (adapter == null ? 0 : adapter.getItemCount());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isBunkr() && bunkrGalleryAdapter != null) {
            BunkrGallerySessionStore.Snapshot snapshot =
                    BunkrGallerySessionStore.snapshot(bunkrGallerySessionId);
            if (snapshot != null) {
                if (snapshot.items.size() > bunkrGalleryAdapter.size()) {
                    bunkrGalleryAdapter.replace(snapshot.items);
                }
                currentPage = snapshot.currentPage;
                endReached = snapshot.endReached;
            }
        } else if (adapter != null) {
            adapter.refreshPlaybackState();
        }
        applyLayout();
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) adapter.close();
        io.shutdownNow();
        super.onDestroy();
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
