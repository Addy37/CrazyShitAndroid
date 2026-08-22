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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationBarView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class NativeMainActivity extends Activity implements NativeMiniPlayer.Host {
    private static final int NAV_HOME = 1;
    private static final int NAV_TRENDING = 2;
    private static final int NAV_CATEGORIES = 3;
    private static final int NAV_SAVED = 4;
    private static final int NAV_MORE = 5;
    private static final int PLAYER_REQUEST = 3001;
    private static final int FAVORITES_REQUEST = 3002;
    private static final long UPDATE_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String RELEASE_API =
            "https://api.github.com/repos/Addy37/CrazyShitAndroid/releases/latest";

    private enum Screen {
        HOME,
        TRENDING,
        CATEGORIES,
        CATEGORY,
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
    private NativeCategoryAdapter categoryAdapter;
    private NativeMiniPlayer miniPlayer;

    private Screen screen = Screen.HOME;
    private String feedBaseUrl = CrazyShitRepository.HOME;
    private String feedTitle = "Home";
    private int currentPage;
    private boolean loading;
    private boolean endReached;
    private int generation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        configureBack();

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("age_warning_accepted", false)) {
            showAgeWarning();
        } else {
            showHome();
        }
        checkForUpdates(false);
    }

    private void buildUi() {
        overlayRoot = new FrameLayout(this);
        overlayRoot.setBackgroundColor(Color.rgb(13, 13, 15));

        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(13, 13, 15));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
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
            return insets;
        });
        overlayRoot.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        shell.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(70)));

        FrameLayout content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setColorSchemeColors(Color.rgb(255, 90, 31));
        swipeRefresh.setOnRefreshListener(this::refreshCurrentScreen);
        content.addView(swipeRefresh, new FrameLayout.LayoutParams(-1, -1));

        recycler = new RecyclerView(this);
        recycler.setBackgroundColor(Color.rgb(13, 13, 15));
        recycler.setClipToPadding(false);
        recycler.setPadding(0, dp(5), 0, dp(18));
        recycler.setItemAnimator(null);
        swipeRefresh.addView(recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        content.addView(progress, progressParams);

        emptyView = new TextView(this);
        emptyView.setTextColor(Color.rgb(190, 190, 198));
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(28), dp(28), dp(28), dp(28));
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

        categoryAdapter = new NativeCategoryAdapter(item -> {
            haptic(recycler);
            showCategory(item);
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

        bottomNavigation = new BottomNavigationView(this);
        bottomNavigation.setBackgroundColor(Color.rgb(21, 21, 24));
        bottomNavigation.setElevation(dp(12));
        bottomNavigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        Menu menu = bottomNavigation.getMenu();
        menu.add(Menu.NONE, NAV_HOME, 0, "Home").setIcon(R.drawable.ic_nav_home);
        menu.add(Menu.NONE, NAV_TRENDING, 1, "Trending").setIcon(R.drawable.ic_nav_trending);
        menu.add(Menu.NONE, NAV_CATEGORIES, 2, "Categories").setIcon(R.drawable.ic_nav_categories);
        menu.add(Menu.NONE, NAV_SAVED, 3, "Saved").setIcon(R.drawable.ic_nav_saved);
        menu.add(Menu.NONE, NAV_MORE, 4, "More").setIcon(R.drawable.ic_nav_more);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == NAV_HOME) {
                showHome();
                return true;
            }
            if (id == NAV_TRENDING) {
                showTrending();
                return true;
            }
            if (id == NAV_CATEGORIES) {
                showCategories();
                return true;
            }
            if (id == NAV_SAVED) {
                startActivityForResult(new Intent(this, FavoritesActivity.class), FAVORITES_REQUEST);
                return false;
            }
            if (id == NAV_MORE) {
                showMoreSheet();
                return false;
            }
            return false;
        });
        shell.addView(bottomNavigation, new LinearLayout.LayoutParams(-1, dp(74)));

        miniPlayer = new NativeMiniPlayer(this, overlayRoot, this);
        setContentView(overlayRoot);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(7), dp(10), dp(7));
        bar.setBackgroundColor(Color.rgb(17, 17, 20));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bar.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(8), 0);

        headerTitle = new TextView(this);
        headerTitle.setTextColor(Color.WHITE);
        headerTitle.setTextSize(19);
        headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        headerTitle.setSingleLine(true);
        labels.addView(headerTitle);

        headerSubtitle = new TextView(this);
        headerSubtitle.setText("Jeremy Edition  •  Native v2");
        headerSubtitle.setTextColor(Color.rgb(168, 168, 178));
        headerSubtitle.setTextSize(12);
        labels.addView(headerSubtitle);
        bar.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView search = new ImageView(this);
        search.setImageResource(R.drawable.ic_nav_search);
        search.setPadding(dp(12), dp(12), dp(12), dp(12));
        search.setContentDescription("Search");
        search.setClickable(true);
        search.setFocusable(true);
        search.setOnClickListener(v -> {
            haptic(v);
            showSearchDialog();
        });
        bar.addView(search, new LinearLayout.LayoutParams(dp(52), dp(52)));
        return bar;
    }

    private void showHome() {
        selectNavSilently(NAV_HOME);
        screen = Screen.HOME;
        feedBaseUrl = CrazyShitRepository.HOME;
        feedTitle = "Home";
        prepareFeed();
        loadFeed(false);
    }

    private void showTrending() {
        screen = Screen.TRENDING;
        feedBaseUrl = CrazyShitRepository.TRENDING;
        feedTitle = "Trending";
        prepareFeed();
        loadFeed(false);
    }

    private void showCategory(NativeContentItem category) {
        screen = Screen.CATEGORY;
        feedBaseUrl = category.url;
        feedTitle = category.title;
        prepareFeed();
        loadFeed(false);
    }

    private void showSearch(String query) {
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
        headerSubtitle.setText("Jeremy Edition  •  Native v2  •  " + viewModeLabel(currentViewMode()));
        applyFeedLayout();
        recycler.setAdapter(feedAdapter);
        feedAdapter.replace(new ArrayList<>());
        emptyView.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        recycler.scrollToPosition(0);
    }

    private void showCategories() {
        screen = Screen.CATEGORIES;
        generation++;
        loading = false;
        endReached = true;
        headerTitle.setText("Categories");
        headerSubtitle.setText("Jeremy Edition  •  Native v2");
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setAdapter(categoryAdapter);
        categoryAdapter.replace(new ArrayList<>());
        emptyView.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(false);
        recycler.scrollToPosition(0);

        final int requestGeneration = generation;
        io.execute(() -> {
            try {
                List<NativeContentItem> result = repository.fetchCategories(this);
                runOnUiThread(() -> {
                    if (requestGeneration != generation || screen != Screen.CATEGORIES) return;
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    categoryAdapter.replace(result);
                    if (result.isEmpty()) {
                        showNativeEmpty("Couldn't build the category list natively.\nTap to open the website fallback.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation) return;
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    showNativeEmpty("Couldn't load categories.\nTap to open the website fallback.");
                });
            }
        });
    }

    private void loadFeed(boolean append) {
        if (loading || endReached || !isFeedScreen()) return;
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
        if (screen == Screen.CATEGORIES) {
            showCategories();
            return;
        }
        if (!isFeedScreen()) return;
        generation++;
        currentPage = 0;
        endReached = false;
        loading = false;
        loadFeed(false);
    }

    private boolean isFeedScreen() {
        return screen == Screen.HOME || screen == Screen.TRENDING ||
                screen == Screen.CATEGORY || screen == Screen.SEARCH;
    }

    private void openNativeItem(NativeContentItem item) {
        if (item == null || item.url.isEmpty()) return;
        progress.setVisibility(View.VISIBLE);
        final int requestGeneration = generation;
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = repository.resolvePlayable(this, item.url);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            runOnUiThread(() -> {
                if (requestGeneration != generation) return;
                progress.setVisibility(View.GONE);
                if (resolved != null && resolved.mediaUrl != null && !resolved.mediaUrl.isEmpty()) {
                    openPlayer(resolved);
                } else {
                    openFallback(item.url);
                }
            });
        });
    }

    private void openPlayer(CrazyShitRepository.StreamInfo stream) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, stream.mediaUrl);
        intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, stream.pageUrl);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, stream.title);
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
        Intent intent = new Intent(this, CommentsActivity.class);
        intent.putExtra(CommentsActivity.EXTRA_PAGE_URL, item.url);
        intent.putExtra(CommentsActivity.EXTRA_TITLE, item.title);
        intent.putExtra(CommentsActivity.EXTRA_COUNT, item.comments);
        startActivity(intent);
    }

    private String viewPreferenceKey() {
        if (screen == Screen.TRENDING) return "native_view_trending";
        if (screen == Screen.CATEGORY) return "native_view_category";
        if (screen == Screen.SEARCH) return "native_view_search";
        return "native_view_home";
    }

    private int currentViewMode() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getInt(viewPreferenceKey(), NativeFeedAdapter.VIEW_LARGE);
    }

    private String viewModeLabel(int mode) {
        if (mode == NativeFeedAdapter.VIEW_COMPACT) return "Compact";
        if (mode == NativeFeedAdapter.VIEW_GRID) return "Grid";
        return "Large";
    }

    private void applyFeedLayout() {
        int mode = currentViewMode();
        feedAdapter.setViewMode(mode);
        if (mode == NativeFeedAdapter.VIEW_GRID) {
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
        String[] choices = {"Large cards", "Compact list", "2-column grid"};
        int selected = currentViewMode();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("View style")
                .setSingleChoiceItems(choices, selected, null)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checked = dialog.getListView().getCheckedItemPosition();
            if (checked < 0) checked = NativeFeedAdapter.VIEW_LARGE;
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt(viewPreferenceKey(), checked)
                    .apply();
            applyFeedLayout();
            headerSubtitle.setText("Jeremy Edition  •  Native v2  •  " + viewModeLabel(checked));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showItemMenu(NativeContentItem item, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        boolean saved = FavoriteStore.contains(this, item.url);
        menu.getMenu().add(0, 1, 0, saved ? "Remove from Watch Later" : "Save to Watch Later");
        if (item.comments != null && !item.comments.isEmpty()) {
            menu.getMenu().add(0, 4, 1, "View comments");
        }
        menu.getMenu().add(0, 2, 2, "Share");
        menu.getMenu().add(0, 3, 3, "Open website page");
        menu.setOnMenuItemClickListener(clicked -> {
            if (clicked.getItemId() == 1) {
                if (FavoriteStore.contains(this, item.url)) {
                    FavoriteStore.remove(this, item.url);
                    Toast.makeText(this, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
                } else {
                    FavoriteStore.add(this, item.title, item.url);
                    Toast.makeText(this, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if (clicked.getItemId() == 4) {
                openComments(item);
                return true;
            }
            if (clicked.getItemId() == 2) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, item.url);
                share.putExtra(Intent.EXTRA_SUBJECT, item.title);
                startActivity(Intent.createChooser(share, "Share"));
                return true;
            }
            if (clicked.getItemId() == 3) {
                openFallback(item.url);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("Search CrazyShit");
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
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(28));
        content.setBackgroundColor(Color.rgb(18, 18, 21));

        TextView title = sheetText("CrazyShit Jeremy Edition", 22, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);
        TextView subtitle = sheetText("Native v2 controls", 13, Color.rgb(170, 170, 180));
        subtitle.setPadding(0, 0, 0, dp(10));
        content.addView(subtitle);

        if (isFeedScreen()) {
            int mode = currentViewMode();
            String label = mode == NativeFeedAdapter.VIEW_COMPACT ? "Compact list" :
                    mode == NativeFeedAdapter.VIEW_GRID ? "2-column grid" : "Large cards";
            addSheetAction(content, "View style", label + " • change how posts are displayed", () -> {
                sheet.dismiss();
                showViewStyleDialog();
            });
        }
        addSheetAction(content, "Settings", "Playback, privacy, haptics and app options", () -> {
            startActivity(new Intent(this, SettingsActivity.class));
            sheet.dismiss();
        });
        addSheetAction(content, "Login / account", "Open the website account flow", () -> {
            openFallback(CrazyShitRepository.BASE + "login/");
            sheet.dismiss();
        });
        addSheetAction(content, "Open full website", "Use the compatibility browser", () -> {
            openFallback(CrazyShitRepository.HOME);
            sheet.dismiss();
        });
        addSheetAction(content, "Check for updates", "Check the latest GitHub release", () -> {
            checkForUpdates(true);
            sheet.dismiss();
        });

        sheet.setContentView(content);
        sheet.show();
    }

    private void addSheetAction(LinearLayout root, String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackgroundColor(Color.rgb(28, 28, 32));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });
        TextView titleView = sheetText(title, 16, Color.WHITE);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(titleView);
        TextView sub = sheetText(subtitle, 12, Color.rgb(170, 170, 180));
        sub.setPadding(0, dp(3), 0, 0);
        row.addView(sub);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        root.addView(row, params);
    }

    private TextView sheetText(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void showNativeEmpty(String text) {
        emptyView.setText(text);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void showAgeWarning() {
        new AlertDialog.Builder(this)
                .setTitle("18+ / Graphic Content")
                .setMessage(
                        "CrazyShit Jeremy Edition connects to CrazyShit.com, which contains adult and graphic material. " +
                        "Continue only if you are 18 or older and want to view that type of content.\n\n" +
                        "This community app is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com."
                )
                .setCancelable(false)
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .setPositiveButton("Continue", (dialog, which) -> {
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("age_warning_accepted", true)
                            .apply();
                    showHome();
                })
                .show();
    }

    private void checkForUpdates(boolean manual) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong("native_last_update_check", 0L);
        if (!manual && now - last < UPDATE_INTERVAL_MS) return;
        prefs.edit().putLong("native_last_update_check", now).apply();

        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "CrazyShit-Jeremy-Edition-Android");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
                reader.close();

                JSONObject release = new JSONObject(json.toString());
                String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                String page = release.optString(
                        "html_url",
                        "https://github.com/Addy37/CrazyShitAndroid/releases/latest"
                );
                boolean newer = compareVersions(tag, currentVersion()) > 0;
                runOnUiThread(() -> {
                    if (newer) showUpdateDialog(tag, page);
                    else if (manual) {
                        Toast.makeText(this, "You're up to date.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (manual) {
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            "Couldn't check for updates right now.",
                            Toast.LENGTH_SHORT
                    ).show());
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String currentVersion() {
        try {
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "0.0.0" : info.versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    private void showUpdateDialog(String version, String page) {
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("CrazyShit Jeremy Edition " + version + " is available on GitHub.")
                .setNegativeButton("Later", null)
                .setPositiveButton("View release", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page)));
                    } catch (Exception ignored) {
                    }
                })
                .show();
    }

    private int compareVersions(String a, String b) {
        String[] left = (a == null ? "" : a).split("[^0-9]+");
        String[] right = (b == null ? "" : b).split("[^0-9]+");
        int count = Math.max(left.length, right.length);
        for (int i = 0; i < count; i++) {
            int lv = numberAt(left, i);
            int rv = numberAt(right, i);
            if (lv != rv) return Integer.compare(lv, rv);
        }
        return 0;
    }

    private int numberAt(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (Exception e) {
            return 0;
        }
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
        if (screen == Screen.CATEGORY) {
            showCategories();
            selectNavSilently(NAV_CATEGORIES);
            return;
        }
        if (screen != Screen.HOME) {
            showHome();
            selectNavSilently(NAV_HOME);
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

    @Override
    protected void onPause() {
        super.onPause();
        if (miniPlayer != null) miniPlayer.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (miniPlayer != null) miniPlayer.onResume();
    }

    @Override
    protected void onDestroy() {
        if (miniPlayer != null) miniPlayer.stop();
        io.shutdownNow();
        super.onDestroy();
    }

    private void haptic(View view) {
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
