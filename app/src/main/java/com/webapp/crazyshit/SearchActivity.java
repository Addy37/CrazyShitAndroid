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
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.Map;
import org.json.JSONObject;
import android.os.Parcelable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native search across site videos, collections, categories and the local Library. */
public final class SearchActivity extends Activity {
    public static final String EXTRA_QUERY = "query";
    private static final String EXTRA_SCOPE = "scope";
    private static final String SCOPE_BUNKR = "bunkr";

    private enum Filter {
        ALL,
        VIDEOS,
        EFUKT,
        BUNKR,
        COLLECTIONS,
        CATEGORIES,
        LIBRARY
    }

    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final BrowseRepository browseRepository = new BrowseRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(8);

    private EditText input;
    private ProgressBar progress;
    private TextView status;
    private RecyclerView recycler;
    private GlobalSearchAdapter adapter;
    private final List<TextView> filterViews = new ArrayList<>();

    private List<NativeContentItem> videos = new ArrayList<>();
    private List<NativeContentItem> crazyVideos = new ArrayList<>();
    private List<NativeContentItem> efuktVideos = new ArrayList<>();
    private List<NativeContentItem> series = new ArrayList<>();
    private List<NativeContentItem> efuktSeries = new ArrayList<>();
    private List<NativeContentItem> bunkrAlbums = new ArrayList<>();
    private List<NativeContentItem> fapzoneCreators = new ArrayList<>();
    private List<NativeContentItem> categories = new ArrayList<>();
    private List<NativeContentItem> library = new ArrayList<>();
    private Filter filter = Filter.ALL;
    private String activeQuery = "";
    private int generation;
    private boolean bunkrOnly;
    private CreatorSuggestionsController suggestions;
    private TextView searchState;
    private View filterBar;
    private final List<Future<?>> requests = new ArrayList<>();
    private final Map<Integer, String> errors = new LinkedHashMap<>();
    private List<NativeContentItem> crazySeries = new ArrayList<>();
    private int pendingSources;
    private boolean destroyed;
    private String snapshotId = ScreenSnapshotStore.newId();
    private Parcelable restoredScroll;
    private long searchStarted;
    private boolean firstResultsRecorded;


    static Intent createBunkrSearch(Activity activity) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra(EXTRA_SCOPE, SCOPE_BUNKR);
        return intent;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        bunkrOnly = SCOPE_BUNKR.equals(getIntent().getStringExtra(EXTRA_SCOPE));
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (state != null) {
            snapshotId = state.getString("snapshot", snapshotId);
            try { filter = Filter.valueOf(state.getString("filter", "ALL")); } catch (Exception ignored) { }
            refreshFilterStyles();
            input.setText(state.getString("typed", ""));
            input.setSelection(input.length());
            restoredScroll = state.getParcelable("scroll");
            activeQuery = state.getString("active", "");
            boolean showingSuggestions = state.getBoolean("suggestions", true);
            if (!showingSuggestions) { suggestions.hide(); input.clearFocus(); }
            if (!bunkrOnly && !activeQuery.isEmpty()) {
                final String savedQuery = activeQuery;
                final int token = ++generation;
                io.execute(() -> {
                    JSONObject snapshot = ScreenSnapshotStore.read(this, snapshotId);
                    runOnUiThread(() -> {
                        if (destroyed || token != generation) return;
                        restoreSnapshot(snapshot);
                        startGlobalSearch(savedQuery, false);
                    });
                });
            }
        } else {
            String supplied = getIntent().getStringExtra(EXTRA_QUERY);
            if (supplied != null && !supplied.trim().isEmpty()) {
                input.setText(supplied.trim());
                input.setSelection(input.length());
                runSearch();
            } else {
                input.requestFocus();
                getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                            | androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        shell.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(56)));
        shell.addView(buildSearchRow(), new LinearLayout.LayoutParams(-1, dp(62)));
        if (!bunkrOnly) {
            filterBar = buildFilters();
            shell.addView(filterBar, new LinearLayout.LayoutParams(-1, dp(52)));
        }

        searchState = BrowseUi.text(this, "", 12, BrowseUi.MUTED);
        searchState.setPadding(dp(16), dp(4), dp(16), dp(4));
        searchState.setVisibility(View.GONE);
        searchState.setOnClickListener(v -> { if (!activeQuery.isEmpty()) startGlobalSearch(activeQuery, false); });
        shell.addView(searchState);
        FrameLayout content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setBackgroundColor(Color.BLACK);
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
        status.setText(bunkrOnly
                ? "Search Fapzone\nBunkr, Fapello, WikiFeet and WikiFeet X open in one gallery"
                : "Search CrazyShit, EFukt, Fapzone, Collections, Categories and your Library");
        content.addView(status, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        content.addView(progress, progressParams);

        LinearLayout suggestionPanel = new LinearLayout(this);
        content.addView(suggestionPanel, new FrameLayout.LayoutParams(-1, -1));
        suggestions = new CreatorSuggestionsController(this, input, suggestionPanel, this::openCreator);
        suggestions.setSearchAction(this::runSearch);
        suggestions.setVisibilityListener(showing -> {
            if (filterBar != null) filterBar.setVisibility(showing ? View.GONE : View.VISIBLE);
        });
        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(7), 0, dp(14), 0);
        bar.setBackgroundColor(Color.BLACK);

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
        title.setText(bunkrOnly ? "Search Fapzone" : "Search");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(bunkrOnly ? "One combined media gallery" : "Everything in one place");
        subtitle.setTextColor(Color.rgb(165, 165, 176));
        subtitle.setTextSize(12f);
        subtitle.setVisibility(View.GONE);
        labels.addView(subtitle);
        bar.addView(labels, new LinearLayout.LayoutParams(0, -1, 1f));
        bar.addView(BrowseUi.action(this, "★", "Favorite creators", v ->
                startActivity(new Intent(this, CreatorsActivity.class))),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        return bar;
    }

    private View buildSearchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(6));

        input = new EditText(this);
        input.setHint("Search creators or videos");
        input.setHintTextColor(Color.rgb(145, 145, 155));
        input.setTextColor(Color.WHITE);
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setContentDescription("Search creators, albums and videos");
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
        row.addView(BrowseUi.action(this, "×", "Clear search", v -> {
            input.setText(""); input.requestFocus();
        }), new LinearLayout.LayoutParams(dp(48), dp(48)));

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
        addFilter(row, "EFukt", Filter.EFUKT);
        addFilter(row, "Fapzone", Filter.BUNKR);
        addFilter(row, "Collections", Filter.COLLECTIONS);
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
            recycler.scrollToPosition(0);
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
            input.setError("Type at least 2 characters");
            return;
        }
        BrowseUi.hideKeyboard(this, input);
        input.clearFocus();
        if (bunkrOnly) {
            startActivity(NativeFeedBrowserActivity.createCreatorGallery(this, query, query));
            return;
        }
        suggestions.hide();
        startGlobalSearch(query, true);
    }

    private void openCreator(NativeContentItem item) {
        BrowseUi.hideKeyboard(this, input);
        startActivity(NativeFeedBrowserActivity.createCreatorGallery(this, item.title,
                item.searchQuery.isEmpty() ? item.title : item.searchQuery));
    }

    private void startGlobalSearch(String query, boolean clear) {
        for (Future<?> request : requests) request.cancel(true);
        requests.clear();
        if (io instanceof java.util.concurrent.ThreadPoolExecutor)
            ((java.util.concurrent.ThreadPoolExecutor) io).purge();
        activeQuery = query;
        int token = ++generation;
        errors.clear();
        pendingSources = 8;
        searchStarted = android.os.SystemClock.elapsedRealtime();
        firstResultsRecorded = false;
        if (clear) {
            crazyVideos.clear(); efuktVideos.clear(); crazySeries.clear(); efuktSeries.clear();
            bunkrAlbums.clear(); fapzoneCreators.clear(); categories.clear(); library.clear();
            videos.clear(); series.clear();
            restoredScroll = null;
            recycler.scrollToPosition(0);
        }
        renderResults();
        source(token, 0, "CrazyShit videos", () -> repository.fetchFeed(this, repository.searchUrl(query), 1));
        source(token, 1, "EFukt videos", () -> efuktRepository.search(this, query));
        source(token, 2, "CrazyShit series", () -> matchCatalog(browseRepository.fetchSeries(this), query));
        source(token, 3, "EFukt series", () -> matchCatalog(efuktRepository.fetchSeries(this), query));
        source(token, 4, "Fapzone albums", () -> bunkrRepository.searchAlbums(this, query, 1));
        source(token, 5, "Categories", () -> matchCatalog(browseRepository.fetchCategories(this), query));
        source(token, 6, "Your Library", () -> searchLibrary(query));
        source(token, 7, "Fapzone creators", () ->
                new FapzoneCreatorSearchRepository().search(this, query, 20));
    }

    private void source(int token, int id, String title, Callable<List<NativeContentItem>> fetch) {
        requests.add(io.submit(() -> {
            List<NativeContentItem> items = null;
            try { items = fetch.call(); } catch (Exception ignored) { }
            List<NativeContentItem> result = items;
            runOnUiThread(() -> {
                if (destroyed || isFinishing() || token != generation) return;
                pendingSources--;
                if (result == null) errors.put(id, title);
                else switch (id) {
                    case 0: crazyVideos = result; break;
                    case 1: efuktVideos = result; break;
                    case 2: crazySeries = result; break;
                    case 3: efuktSeries = result; break;
                    case 4: bunkrAlbums = result; break;
                    case 5: categories = result; break;
                    case 6: library = result; break;
                    case 7:
                        fapzoneCreators = result;
                        CreatorCatalog.remember(this, result);
                        break;
                }
                videos = interleave(crazyVideos, efuktVideos);
                series = interleave(crazySeries, efuktSeries);
                renderResults();
                saveSnapshot();
            });
        }));
    }

    private void saveSnapshot() {
        try {
            JSONObject data = new JSONObject().put("query", activeQuery);
            data.put("crazyVideos", ContentItemCodec.encodeList(crazyVideos, 100));
            data.put("efuktVideos", ContentItemCodec.encodeList(efuktVideos, 100));
            data.put("crazySeries", ContentItemCodec.encodeList(crazySeries, 100));
            data.put("efuktSeries", ContentItemCodec.encodeList(efuktSeries, 100));
            data.put("albums", ContentItemCodec.encodeList(bunkrAlbums, 100));
            data.put("creators", ContentItemCodec.encodeList(fapzoneCreators, 100));
            data.put("categories", ContentItemCodec.encodeList(categories, 100));
            data.put("library", ContentItemCodec.encodeList(library, 100));
            ScreenSnapshotStore.save(this, snapshotId, data);
        } catch (Exception ignored) { }
    }

    private void restoreSnapshot(JSONObject data) {
        if (data == null || !activeQuery.equals(data.optString("query"))) return;
        crazyVideos = ContentItemCodec.decodeList(data.optJSONArray("crazyVideos"), 100);
        efuktVideos = ContentItemCodec.decodeList(data.optJSONArray("efuktVideos"), 100);
        crazySeries = ContentItemCodec.decodeList(data.optJSONArray("crazySeries"), 100);
        efuktSeries = ContentItemCodec.decodeList(data.optJSONArray("efuktSeries"), 100);
        bunkrAlbums = ContentItemCodec.decodeList(data.optJSONArray("albums"), 100);
        fapzoneCreators = ContentItemCodec.decodeList(data.optJSONArray("creators"), 100);
        categories = ContentItemCodec.decodeList(data.optJSONArray("categories"), 100);
        library = ContentItemCodec.decodeList(data.optJSONArray("library"), 100);
        videos = interleave(crazyVideos, efuktVideos);
        series = interleave(crazySeries, efuktSeries);
        renderResults();
    }

    private List<NativeContentItem> interleave(
            List<NativeContentItem> first,
            List<NativeContentItem> second
    ) {
        LinkedHashMap<String, NativeContentItem> result = new LinkedHashMap<>();
        int firstSize = first == null ? 0 : first.size();
        int secondSize = second == null ? 0 : second.size();
        int count = Math.max(firstSize, secondSize);
        for (int i = 0; i < count; i++) {
            if (i < firstSize) addUnique(result, first.get(i));
            if (i < secondSize) addUnique(result, second.get(i));
        }
        return new ArrayList<>(result.values());
    }

    private void addUnique(
            LinkedHashMap<String, NativeContentItem> result,
            NativeContentItem item
    ) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        NativeContentItem old = result.get(item.url);
        result.put(item.url, old == null ? item : old.merge(item));
    }

    private List<NativeContentItem> matchCatalog(List<NativeContentItem> source, String query) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (source == null) return result;
        String[] terms = normalized(query).split("\\s+");
        for (NativeContentItem item : source) {
            if (item == null) continue;
            String haystack = normalized(item.title + " " + item.url + " " + item.description);
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
        if (filter == Filter.ALL || filter == Filter.VIDEOS) appendSection(output, "Videos  •  CrazyShit + EFukt", videos, GlobalSearchAdapter.SOURCE_REMOTE, 40);
        if (filter == Filter.EFUKT) appendSection(output, "EFukt Videos", efuktVideos, GlobalSearchAdapter.SOURCE_REMOTE, 40);
        if (filter == Filter.ALL || filter == Filter.BUNKR || filter == Filter.COLLECTIONS) {
            appendSection(output, "Fapzone Creators", fapzoneCreators,
                    GlobalSearchAdapter.SOURCE_REMOTE, 20);
            appendSection(output, "Fapzone Albums", bunkrAlbums, GlobalSearchAdapter.SOURCE_REMOTE, 30);
        }
        if (filter == Filter.ALL || filter == Filter.COLLECTIONS) appendSection(output, "Series", series, GlobalSearchAdapter.SOURCE_REMOTE, 20);
        if (filter == Filter.EFUKT) appendSection(output, "EFukt Series", efuktSeries, GlobalSearchAdapter.SOURCE_REMOTE, 20);
        if (filter == Filter.ALL || filter == Filter.CATEGORIES) appendSection(output, "Categories", categories, GlobalSearchAdapter.SOURCE_REMOTE, 20);
        if (filter == Filter.ALL || filter == Filter.LIBRARY) appendSection(output, "Your Library", library, GlobalSearchAdapter.SOURCE_LIBRARY, 30);
        if (!output.isEmpty() && !firstResultsRecorded && searchStarted > 0) {
            firstResultsRecorded = true;
            AppPerformance.record("Search first results", android.os.SystemClock.elapsedRealtime() - searchStarted);
        }
        adapter.replace(output);

        progress.setVisibility(output.isEmpty() && pendingSources > 0 ? View.VISIBLE : View.GONE);
        String unavailable = String.join(", ", errors.values());
        searchState.setVisibility(View.VISIBLE);
        searchState.setText(pendingSources > 0
                ? "Searching · " + pendingSources + " sources remaining"
                : unavailable.isEmpty() ? "Search complete"
                : "Unavailable: " + unavailable + " · Tap to retry");
        searchState.setContentDescription(searchState.getText());
        if (output.isEmpty()) {
            status.setText(pendingSources > 0 ? "" : !unavailable.isEmpty()
                    ? "Some sources could not be reached.\nTap the message above to retry."
                    : "No matches for “" + activeQuery + "”\n\nTry a shorter or more general search.");
            status.setVisibility(pendingSources > 0 ? View.GONE : View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
            if (restoredScroll != null) {
                recycler.getLayoutManager().onRestoreInstanceState(restoredScroll);
                restoredScroll = null;
            }
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
        if (item.isCreator()) {
            openCreator(item);
            return;
        }
        if (item.isSeries() || item.isCategory()) {
            String source = BunkrRepository.isAlbumUrl(item.url)
                    ? NativeFeedBrowserActivity.SOURCE_BUNKR
                    : EfuktRepository.isEfuktUrl(item.url)
                    ? NativeFeedBrowserActivity.SOURCE_EFUKT
                    : NativeFeedBrowserActivity.SOURCE_CRAZYSHIT;
            startActivity(NativeFeedBrowserActivity.create(
                    this, item.title, item.url, false, source
            ));
            return;
        }

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
                if (destroyed || isFinishing() || requestGeneration != generation) return;
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
                intent.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, resolved.requestReferer);
                if (BunkrRepository.isBunkrUrl(item.url)) {
                    intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_BUNKR);
                } else if (EfuktRepository.isEfuktUrl(item.url)) {
                    intent.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, EfuktRepository.SERIES);
                    intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_EFUKT);
                }
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

    @Override protected void onResume() {
        super.onResume();
        if (suggestions != null) suggestions.refreshLocal();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("typed", input.getText().toString());
        state.putString("active", activeQuery);
        state.putString("filter", filter.name());
        state.putString("snapshot", snapshotId);
        state.putBoolean("suggestions", suggestions.isShowing());
        state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        saveSnapshot();
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        generation++;
        if (suggestions != null) suggestions.close();
        if (adapter != null) adapter.close();
        io.shutdownNow();
        super.onDestroy();
    }
}
