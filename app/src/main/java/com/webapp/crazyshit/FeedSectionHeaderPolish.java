package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Adds the date/group headers from the CrazyShit home page to the native Home feed.
 *
 * This stays outside NativeFeedAdapter so Large, Compact, Grid and watch-state behavior remain
 * unchanged. Section rows are injected into the adapter data only for the Home feed, then styled
 * as a lightweight full-width native header. GridLayoutManager gives section rows both columns.
 */
final class FeedSectionHeaderPolish {
    private static final String HOME = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final int ACCENT = Color.rgb(244, 183, 28);
    private static final int APP_BG = Color.rgb(13, 13, 15);

    private static final Pattern DATE_HEADER = Pattern.compile(
            "(?i)^(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s+" +
            "(january|february|march|april|may|june|july|august|september|october|november|december)\\s+" +
            "\\d{1,2}(?:st|nd|rd|th)?$"
    );

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<NativeMainActivity, Boolean> ACTIVITIES = new WeakHashMap<>();
    private static final WeakHashMap<RecyclerView, RecyclerView.OnChildAttachStateChangeListener>
            RECYCLERS = new WeakHashMap<>();
    private static final WeakHashMap<View, OriginalCardState> ORIGINAL_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, View> HEADER_OVERLAYS = new WeakHashMap<>();
    private static final WeakHashMap<GridLayoutManager, Boolean> GRID_MANAGERS = new WeakHashMap<>();

    private static volatile Catalog catalog;
    private static volatile boolean catalogLoading;
    private static Field itemsField;
    private static Field thumbnailsField;

    private FeedSectionHeaderPolish() {
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        synchronized (ACTIVITIES) {
            ACTIVITIES.put(activity, Boolean.TRUE);
        }
        loadCatalog();
        apply(activity);
        View decor = activity.getWindow().getDecorView();
        decor.postDelayed(() -> apply(activity), 180L);
        decor.postDelayed(() -> apply(activity), 650L);
        decor.postDelayed(() -> apply(activity), 1400L);
    }

    private static void loadCatalog() {
        if (catalog != null || catalogLoading) return;
        synchronized (FeedSectionHeaderPolish.class) {
            if (catalog != null || catalogLoading) return;
            catalogLoading = true;
        }
        IO.execute(() -> {
            Catalog loaded = null;
            try {
                Document doc = Jsoup.connect(HOME)
                        .userAgent(USER_AGENT)
                        .referrer(HOME)
                        .timeout(18000)
                        .maxBodySize(5 * 1024 * 1024)
                        .followRedirects(true)
                        .get();
                loaded = parseCatalog(doc);
            } catch (Exception ignored) {
            }
            Catalog result = loaded;
            MAIN.post(() -> {
                catalogLoading = false;
                if (result != null && !result.boundaries.isEmpty()) catalog = result;
                synchronized (ACTIVITIES) {
                    for (NativeMainActivity activity : new ArrayList<>(ACTIVITIES.keySet())) {
                        if (activity != null && !activity.isFinishing()) apply(activity);
                    }
                }
            });
        });
    }

    private static Catalog parseCatalog(Document doc) {
        if (doc == null) return null;
        LinkedHashCatalog data = new LinkedHashCatalog();
        String pendingHeader = "";
        Set<String> seenMedia = new HashSet<>();

        for (Element node : doc.select("h1,h2,h3,h4,h5,a[href*=/cnt/medias/]")) {
            String tag = node.tagName().toLowerCase(Locale.US);
            if (tag.startsWith("h")) {
                String heading = clean(node.text());
                if (isSectionHeading(heading)) pendingHeader = heading;
                continue;
            }
            if (!"a".equals(tag)) continue;
            String url = canonical(node.absUrl("href"));
            if (!url.contains("crazyshit.com/cnt/medias/")) continue;
            if (!seenMedia.add(url)) continue;
            if (data.firstMediaUrl.isEmpty()) data.firstMediaUrl = url;
            if (!pendingHeader.isEmpty()) {
                data.boundaries.put(url, pendingHeader);
                pendingHeader = "";
            }
        }
        return new Catalog(data.firstMediaUrl, data.boundaries);
    }

    private static void apply(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        walk(root);
    }

    private static void walk(View view) {
        if (view instanceof RecyclerView) setupRecycler((RecyclerView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i));
    }

    private static void setupRecycler(RecyclerView recycler) {
        if (!(recycler.getAdapter() instanceof NativeFeedAdapter)) return;
        NativeFeedAdapter adapter = (NativeFeedAdapter) recycler.getAdapter();
        injectSectionsIfHome(adapter);
        ensureGridSpan(recycler);
        styleVisibleChildren(recycler);

        synchronized (RECYCLERS) {
            if (!RECYCLERS.containsKey(recycler)) {
                RecyclerView.OnChildAttachStateChangeListener listener =
                        new RecyclerView.OnChildAttachStateChangeListener() {
                            @Override
                            public void onChildViewAttachedToWindow(@NonNull View child) {
                                recycler.post(() -> {
                                    injectSectionsIfHome(adapter);
                                    ensureGridSpan(recycler);
                                    styleChild(recycler, child);
                                });
                            }

                            @Override
                            public void onChildViewDetachedFromWindow(@NonNull View child) {
                            }
                        };
                recycler.addOnChildAttachStateChangeListener(listener);
                recycler.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                    ensureGridSpan(recycler);
                    styleVisibleChildren(recycler);
                });
                RECYCLERS.put(recycler, listener);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectSectionsIfHome(NativeFeedAdapter adapter) {
        Catalog current = catalog;
        if (adapter == null || current == null || current.firstMediaUrl.isEmpty()) return;
        try {
            Field field = itemsField();
            List<NativeContentItem> items = (List<NativeContentItem>) field.get(adapter);
            if (items == null || items.isEmpty()) return;
            for (NativeContentItem item : items) {
                if (item != null && item.isSection()) return;
            }

            String first = "";
            for (NativeContentItem item : items) {
                if (item != null && !item.isMeme() && !item.isCategory()) {
                    first = canonical(item.url);
                    if (!first.isEmpty()) break;
                }
            }
            if (!current.firstMediaUrl.equals(first)) return;

            ArrayList<NativeContentItem> decorated = new ArrayList<>();
            ArrayList<String> sectionUrls = new ArrayList<>();
            for (NativeContentItem item : items) {
                String header = item == null ? null : current.boundaries.get(canonical(item.url));
                if (header != null && !header.isEmpty()) {
                    String sectionUrl = "section:" + header.toLowerCase(Locale.US)
                            .replaceAll("[^a-z0-9]+", "-")
                            .replaceAll("(^-+|-+$)", "");
                    decorated.add(new NativeContentItem(
                            NativeContentItem.KIND_SECTION,
                            header,
                            sectionUrl,
                            "",
                            "",
                            "",
                            ""
                    ));
                    sectionUrls.add(sectionUrl);
                }
                decorated.add(item);
            }
            if (sectionUrls.isEmpty()) return;

            items.clear();
            items.addAll(decorated);
            try {
                Field thumbField = thumbnailsField();
                Map<String, String> thumbnails = (Map<String, String>) thumbField.get(adapter);
                if (thumbnails != null) {
                    for (String sectionUrl : sectionUrls) thumbnails.put(sectionUrl, "");
                }
            } catch (Exception ignored) {
            }
            adapter.notifyDataSetChanged();
        } catch (Exception ignored) {
        }
    }

    private static void styleVisibleChildren(RecyclerView recycler) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            styleChild(recycler, recycler.getChildAt(i));
        }
    }

    private static void styleChild(RecyclerView recycler, View child) {
        int position = recycler.getChildAdapterPosition(child);
        if (position == RecyclerView.NO_POSITION) return;
        NativeContentItem item = itemAt(recycler, position);
        if (item != null && item.isSection()) styleAsHeader(child, item.title);
        else restoreNormalCard(child);
    }

    private static void styleAsHeader(View child, String rawTitle) {
        if (!(child instanceof MaterialCardView)) return;
        MaterialCardView card = (MaterialCardView) child;
        if (!ORIGINAL_STATES.containsKey(child)) {
            ORIGINAL_STATES.put(child, OriginalCardState.capture(card));
        }

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(card, 52));
        params.setMargins(dp(card, 12), dp(card, 10), dp(card, 12), dp(card, 2));
        card.setLayoutParams(params);
        card.setCardBackgroundColor(APP_BG);
        card.setStrokeWidth(0);
        card.setCardElevation(0f);
        card.setRadius(0f);
        card.setClickable(false);
        card.setLongClickable(false);

        View oldOverlay = HEADER_OVERLAYS.get(child);
        if (oldOverlay instanceof TextView) {
            ((TextView) oldOverlay).setText(styledHeader(rawTitle));
            return;
        }

        TextView header = new TextView(card.getContext());
        header.setText(styledHeader(rawTitle));
        header.setTextSize(17f);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setSingleLine(true);
        header.setBackgroundColor(APP_BG);
        header.setPadding(dp(card, 4), 0, dp(card, 4), 0);
        card.addView(header, new MaterialCardView.LayoutParams(-1, -1));
        HEADER_OVERLAYS.put(child, header);
    }

    private static SpannableString styledHeader(String rawTitle) {
        String title = clean(rawTitle).toUpperCase(Locale.US);
        SpannableString text = new SpannableString(title);
        int split = title.indexOf(' ');
        int accentEnd = split > 0 ? split : title.length();
        text.setSpan(new ForegroundColorSpan(ACCENT), 0, accentEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (accentEnd < title.length()) {
            text.setSpan(new ForegroundColorSpan(Color.WHITE), accentEnd, title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private static void restoreNormalCard(View child) {
        OriginalCardState state = ORIGINAL_STATES.remove(child);
        View overlay = HEADER_OVERLAYS.remove(child);
        if (overlay != null && child instanceof ViewGroup) ((ViewGroup) child).removeView(overlay);
        if (state != null && child instanceof MaterialCardView) state.restore((MaterialCardView) child);
    }

    private static void ensureGridSpan(RecyclerView recycler) {
        if (!(recycler.getLayoutManager() instanceof GridLayoutManager)) return;
        GridLayoutManager grid = (GridLayoutManager) recycler.getLayoutManager();
        synchronized (GRID_MANAGERS) {
            if (GRID_MANAGERS.containsKey(grid)) return;
            int columns = grid.getSpanCount();
            grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    NativeContentItem item = itemAt(recycler, position);
                    return item != null && item.isSection() ? columns : 1;
                }
            });
            GRID_MANAGERS.put(grid, Boolean.TRUE);
        }
    }

    @SuppressWarnings("unchecked")
    private static NativeContentItem itemAt(RecyclerView recycler, int position) {
        if (!(recycler.getAdapter() instanceof NativeFeedAdapter)) return null;
        try {
            List<NativeContentItem> items =
                    (List<NativeContentItem>) itemsField().get(recycler.getAdapter());
            if (items == null || position < 0 || position >= items.size()) return null;
            return items.get(position);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field itemsField() throws NoSuchFieldException {
        if (itemsField == null) {
            itemsField = NativeFeedAdapter.class.getDeclaredField("items");
            itemsField.setAccessible(true);
        }
        return itemsField;
    }

    private static Field thumbnailsField() throws NoSuchFieldException {
        if (thumbnailsField == null) {
            thumbnailsField = NativeFeedAdapter.class.getDeclaredField("resolvedThumbnails");
            thumbnailsField.setAccessible(true);
        }
        return thumbnailsField;
    }

    private static boolean isSectionHeading(String value) {
        String text = clean(value).replace('’', '\'');
        String lower = text.toLowerCase(Locale.US);
        if ("today's crazy shit".equals(lower) || "todays crazy shit".equals(lower)) return true;
        return DATE_HEADER.matcher(text).matches();
    }

    private static String canonical(String value) {
        if (value == null) return "";
        String url = value.trim().replace("&amp;", "&").replace("\\/", "/");
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        while (url.endsWith("/") && url.length() > "https://x/".length()) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static final class LinkedHashCatalog {
        String firstMediaUrl = "";
        final Map<String, String> boundaries = new java.util.LinkedHashMap<>();
    }

    private static final class Catalog {
        final String firstMediaUrl;
        final Map<String, String> boundaries;

        Catalog(String firstMediaUrl, Map<String, String> boundaries) {
            this.firstMediaUrl = firstMediaUrl == null ? "" : firstMediaUrl;
            this.boundaries = new HashMap<>(boundaries);
        }
    }

    private static final class OriginalCardState {
        final RecyclerView.LayoutParams params;
        final int backgroundColor;
        final int strokeColor;
        final int strokeWidth;
        final float radius;
        final float elevation;

        OriginalCardState(
                RecyclerView.LayoutParams params,
                int backgroundColor,
                int strokeColor,
                int strokeWidth,
                float radius,
                float elevation
        ) {
            this.params = params;
            this.backgroundColor = backgroundColor;
            this.strokeColor = strokeColor;
            this.strokeWidth = strokeWidth;
            this.radius = radius;
            this.elevation = elevation;
        }

        static OriginalCardState capture(MaterialCardView card) {
            RecyclerView.LayoutParams source = (RecyclerView.LayoutParams) card.getLayoutParams();
            RecyclerView.LayoutParams copy = new RecyclerView.LayoutParams(source.width, source.height);
            copy.setMargins(source.leftMargin, source.topMargin, source.rightMargin, source.bottomMargin);
            int background = card.getCardBackgroundColor() == null
                    ? Color.rgb(25, 25, 28)
                    : card.getCardBackgroundColor().getDefaultColor();
            return new OriginalCardState(
                    copy,
                    background,
                    card.getStrokeColor(),
                    card.getStrokeWidth(),
                    card.getRadius(),
                    card.getCardElevation()
            );
        }

        void restore(MaterialCardView card) {
            card.setLayoutParams(params);
            card.setCardBackgroundColor(backgroundColor);
            card.setStrokeColor(strokeColor);
            card.setStrokeWidth(strokeWidth);
            card.setRadius(radius);
            card.setCardElevation(elevation);
        }
    }
}
