package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** v2.6 native search across site videos, Series, Categories and the local Library. */
public final class SearchActivity extends Activity {
    public static final String EXTRA_QUERY = "query";

    private enum Filter {
        ALL,
        VIDEOS,
        SERIES,
        CATEGORIES,
        LIBRARY
    }

    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final BrowseRepository browseRepository = new BrowseRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(3);

    private EditText input;
    private ProgressBar progress;
    private TextView status;
    private RecyclerView recycler;
    private GlobalSearchAdapter adapter;
    private final List<TextView> filterViews = new ArrayList<>();

    private List<NativeContentItem> videos = new ArrayList<>();
    private List<NativeContentItem> series = new ArrayList<>();
    private List<NativeContentItem> categories = new ArrayList<>();
    private List<NativeContentItem> library = new ArrayList<>();
    private Filter filter = Filter.ALL;
    private String activeQuery = "";
    private int generation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();

        String supplied = getIntent().getStringExtra(EXTRA_QUERY);
        if (supplied != null && !supplied.trim().isEmpty()) {
            input.setText(supplied.trim());
            input.setSelection(input.length());
            runSearch();
        } else {
            input.requestFocus();
            if (getWindow() != null) {
                getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 13, 15));

        LinearLayout shell = new LinearLayout(this);
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
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        shell.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(66)));
        shell.addView(buildSearchRow(), new LinearLayout.LayoutParams(-1, dp(62)));
        shell.addView(buildFilters(), new LinearLayout.LayoutParams(-1, dp(52)));

        FrameLayout content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setBackgroundColor(Color.rgb(13, 13, 15));
        recycler.setClipToPadding(false);
        recycler.setPadding(0, dp(4), 0, dp(22));
        recycler.setItemAnimator(null);
        adapter = new GlobalSearchAdapter(this::openResult);
        recycler.setAdapter(adapter);
        content.addView(recycler, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(this);
        status.setTextColor(Color.rgb(180, 180, 190));
        status.setTextSize(15f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(26), dp(26), dp(26), dp(26));
        status.setText("Search videos, Series, Categories and your Library");
        content.addView(status, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        content.addView(progress, progressParams);

        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(7), 0, dp(14), 0);
        bar.setBackgroundColor(Color.rgb(17, 17, 20));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(38f);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), -1));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Search");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Everything in one place");
        subtitle.setTextColor(Color.rgb(165, 165, 176));
        subtitle.setTextSize(12f);
        labels.addView(subtitle);
        bar.addView(labels, new LinearLayout.LayoutParams(0, -1, 1f));
        return bar;
    }

    private View buildSearchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(6));

        input = new EditText(this);
        input.setHint("Search CrazyShit");
        input.setHintTextColor(Color.rgb(145, 145, 155));
        input.setTextColor(Color.WHITE);
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(Color.rgb(30, 30, 35), dp(14)));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        row.addView(input, new LinearLayout.LayoutParams(0, -1, 1f));

        Button go = new Button(this);
        go.setText("Search");
        go.setTextColor(UiPalette.ON_PRIMARY);
        go.setTextSize(13f);
        go.setAllCaps(false);
        go.setBackground(rounded(UiPalette.PRIMARY, dp(14)));
        go.setOnClickListener(v -> {
            haptic(v);
            runSearch();
        });
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(dp(92), -1);
        goParams.leftMargin = dp(8);
        row.addView(go, goParams);
        return row;
    }

    private View buildFilters() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(5), dp(10), dp(7));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));

        addFilter(row, "All", Filter.ALL);
        addFilter(row, "Videos", Filter.VIDEOS);
        addFilter(row, "Series", Filter.SERIES);
        addFilter(row, "Categories", Filter.CATEGORIES);
        addFilter(row, "Library", Filter.LIBRARY);
        refreshFilterStyles();
        return scroll;
    }

    private void addFilter(LinearLayout row, String label, Filter value) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTag(value);
        chip.setTextSize(13.5f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(15), 0, dp(15), 0);
        chip.setOnClickListener(v -> {
            haptic(v);
            filter = (Filter) v.getTag();
            refreshFilterStyles();
            renderResults();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(chip, params);
        filterViews.add(chip);
    }

    private void refreshFilterStyles() {
        for (TextView chip : filterViews) {
            boolean selected = chip.getTag() == filter;
            chip.setTextColor(selected ? UiPalette.ON_PRIMARY : Color.rgb(188, 188, 198));
            chip.setBackground(rounded(
                    selected ? UiPalette.PRIMARY : Color.rgb(31, 31, 36),
                    dp(18)
            ));
        }
    }

    private void runSearch() {
        String query = input.getText().toString().trim();
        if (query.length() < 2) {
            Toast.makeText(this, "Type at least 2 characters.", Toast.LENGTH_SHORT).show();
            return;
        }
        activeQuery = query;
        int requestGeneration = ++generation;
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        adapter.replace(new ArrayList<>());

        io.execute(() -> {
            List<NativeContentItem> foundVideos = new ArrayList<>();
            List<NativeContentItem> foundSeries = new ArrayList<>();
            List<NativeContentItem> foundCategories = new ArrayList<>();

            try {
                foundVideos = repository.fetchFeed(this, repository.searchUrl(query), 1);
            } catch (Exception ignored) {
            }
            try {
                foundSeries = matchCatalog(browseRepository.fetchSeries(this), query);
            } catch (Exception ignored) {
            }
            try {
                foundCategories = matchCatalog(browseRepository.fetchCategories(this), query);
            } catch (Exception ignored) {
            }
            List<NativeContentItem> foundLibrary = searchLibrary(query);

            final List<NativeContentItem> videoResult = foundVideos;
            final List<NativeContentItem> seriesResult = foundSeries;
            final List<NativeContentItem> categoryResult = foundCategories;
            final List<NativeContentItem> libraryResult = foundLibrary;
            runOnUiThread(() -> {
                if (requestGeneration != generation) return;
                videos = videoResult;
                series = seriesResult;
                categories = categoryResult;
                library = libraryResult;
                progress.setVisibility(View.GONE);
                renderResults();
            });
        });
    }

    private List<NativeContentItem> matchCatalog(List<NativeContentItem> source, String query) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (source == null) return result;
        String[] terms = normalized(query).split("\\s+");
        for (NativeContentItem item : source) {
            if (item == null) continue;
            String haystack = normalized(item.title + " " + item.url);
            boolean match = true;
            for (String term : terms) {
                if (!term.isEmpty() && !haystack.contains(term)) {
                    match = false;
                    break;
                }
            }
            if (match) result.add(item);
        }
        return result;
    }

    private List<NativeContentItem> searchLibrary(String query) {
        LinkedHashMap<String, NativeContentItem> result = new LinkedHashMap<>();
        String needle = normalized(query);

        for (FavoriteStore.Item saved : FavoriteStore.load(this)) {
            if (!normalized(saved.title).contains(needle)) continue;
            result.put(saved.url, new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    saved.title,
                    saved.url,
                    "",
                    "",
                    "",
                    ""
            ));
        }
        for (PlaybackHistoryStore.Item history : PlaybackHistoryStore.load(this)) {
            if (!normalized(history.title).contains(needle)) continue;
            result.putIfAbsent(history.pageUrl, new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    history.title,
                    history.pageUrl,
                    "",
                    history.progressPercent() > 0 ? history.progressPercent() + "% watched" : "",
                    "",
                    ""
            ));
        }
        return new ArrayList<>(result.values());
    }

    private void renderResults() {
        if (activeQuery.isEmpty()) return;
        ArrayList<GlobalSearchAdapter.Entry> output = new ArrayList<>();
        if (filter == Filter.ALL || filter == Filter.VIDEOS) appendSection(output, "Videos", videos, GlobalSearchAdapter.SOURCE_REMOTE, 30);
        if (filter == Filter.ALL || filter == Filter.SERIES) appendSection(output, "Series", series, GlobalSearchAdapter.SOURCE_REMOTE, 20);
        if (filter == Filter.ALL || filter == Filter.CATEGORIES) appendSection(output, "Categories", categories, GlobalSearchAdapter.SOURCE_REMOTE, 20);
        if (filter == Filter.ALL || filter == Filter.LIBRARY) appendSection(output, "Your Library", library, GlobalSearchAdapter.SOURCE_LIBRARY, 30);
        adapter.replace(output);

        if (output.isEmpty()) {
            status.setText("No matches for “" + activeQuery + "”\n\nTry a shorter or more general search.");
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
            recycler.scrollToPosition(0);
        }
    }

    private void appendSection(
            List<GlobalSearchAdapter.Entry> out,
            String title,
            List<NativeContentItem> items,
            int source,
            int limit
    ) {
        if (items == null || items.isEmpty()) return;
        out.add(GlobalSearchAdapter.Entry.section(title + "  •  " + items.size()));
        int count = Math.min(limit, items.size());
        for (int i = 0; i < count; i++) {
            out.add(GlobalSearchAdapter.Entry.item(items.get(i), source));
        }
    }

    private void openResult(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        haptic(recycler);
        if (item.isSeries() || item.isCategory()) {
            startActivity(NativeFeedBrowserActivity.create(this, item.title, item.url, false));
            return;
        }

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
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    Intent fallback = new Intent(this, WebFallbackActivity.class);
                    fallback.putExtra(WebFallbackActivity.EXTRA_URL, item.url);
                    startActivity(fallback);
                    return;
                }
                Intent intent = new Intent(this, VideoDetailActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, resolved.mediaUrl);
                intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, resolved.pageUrl);
                intent.putExtra(PlayerActivity.EXTRA_TITLE,
                        item.title == null || item.title.trim().isEmpty() ? resolved.title : item.title);
                intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);
                intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);
                intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);
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

    private String normalized(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US)
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("[^a-z0-9 ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void haptic(View view) {
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
