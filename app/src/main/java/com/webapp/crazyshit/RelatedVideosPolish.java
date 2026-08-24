package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Replaces VideoDetailActivity's old Home + Trending recommendation fallback with results that
 * come from the currently playing video's own page and collection context.
 */
final class RelatedVideosPolish {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final WeakHashMap<VideoDetailActivity, State> STATES = new WeakHashMap<>();

    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private RelatedVideosPolish() {
    }

    static void attach(VideoDetailActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State state = STATES.get(activity);
        if (state == null) {
            state = new State(activity);
            STATES.put(activity, state);
        }
        state.start();
    }

    static void detach(VideoDetailActivity activity) {
        State state = STATES.remove(activity);
        if (state != null) state.stop();
    }

    private static final class State {
        final WeakReference<VideoDetailActivity> activityRef;
        final Runnable watcher;
        String pageUrl = "";
        int generation;
        boolean running;

        State(VideoDetailActivity activity) {
            activityRef = new WeakReference<>(activity);
            watcher = this::checkPage;
        }

        void start() {
            if (running) return;
            running = true;
            MAIN.postDelayed(watcher, 120L);
        }

        void stop() {
            running = false;
            generation++;
            MAIN.removeCallbacks(watcher);
        }

        void checkPage() {
            if (!running) return;
            VideoDetailActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                stop();
                return;
            }

            String current = stringField(activity, "pageUrl");
            if (!current.isEmpty() && !sameUrl(current, pageUrl)) {
                pageUrl = current;
                int requestGeneration = ++generation;
                IO.execute(() -> {
                    List<NativeContentItem> related = fetchRelated(activity, current, 12);
                    MAIN.post(() -> {
                        VideoDetailActivity host = activityRef.get();
                        if (!running || host == null || host.isFinishing() || host.isDestroyed()) return;
                        if (requestGeneration != generation || !sameUrl(current, stringField(host, "pageUrl"))) return;
                        apply(host, related);

                        // VideoDetailActivity's legacy Home/Trending request can finish after ours.
                        // Re-apply the context-aware set briefly so the old request cannot win a race.
                        MAIN.postDelayed(() -> reapply(host, current, requestGeneration, related), 700L);
                        MAIN.postDelayed(() -> reapply(host, current, requestGeneration, related), 1800L);
                    });
                });
            }
            MAIN.postDelayed(watcher, 450L);
        }

        void reapply(
                VideoDetailActivity activity,
                String expectedPage,
                int expectedGeneration,
                List<NativeContentItem> related
        ) {
            if (!running || activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            if (expectedGeneration != generation) return;
            if (!sameUrl(expectedPage, stringField(activity, "pageUrl"))) return;
            apply(activity, related);
        }
    }

    private static List<NativeContentItem> fetchRelated(Context context, String pageUrl, int limit) {
        LinkedHashMap<String, NativeContentItem> results = new LinkedHashMap<>();
        try {
            Document doc = fetchDocument(context, pageUrl);
            String current = normalize(pageUrl);

            // First choice: a real Related / Recommended / Similar block on the media page.
            for (Element root : relatedRoots(doc)) {
                collectMediaLinks(root, current, results, limit);
                if (results.size() >= limit) break;
            }

            // Second choice: use the video's own category or series, which is still genuinely
            // connected to the current video rather than an unrelated app-wide Home feed.
            if (results.size() < 6) {
                ArrayList<String> collectionUrls = new ArrayList<>();
                for (Element link : doc.select("a[href*=/category/],a[href*=/series/]")) {
                    String url = absolute(link, "href");
                    String lower = url.toLowerCase(Locale.US);
                    if (url.isEmpty()) continue;
                    if (!(lower.contains("crazyshit.com/category/") || lower.contains("crazyshit.com/series/"))) continue;
                    if (lower.endsWith("/category/") || lower.endsWith("/series/")) continue;
                    if (!collectionUrls.contains(url)) collectionUrls.add(url);
                    if (collectionUrls.size() >= 3) break;
                }

                CrazyShitRepository repository = new CrazyShitRepository();
                for (String collection : collectionUrls) {
                    try {
                        for (NativeContentItem item : repository.fetchFeed(context, collection, 1)) {
                            if (item == null || item.isSection()) continue;
                            if (sameUrl(current, item.url)) continue;
                            if (item.url == null || item.url.isEmpty()) continue;
                            results.putIfAbsent(normalize(item.url), item);
                            if (results.size() >= limit) break;
                        }
                    } catch (Exception ignored) {
                    }
                    if (results.size() >= limit) break;
                }
            }

            // Last page-local fallback: media links already present on the current video page.
            // This can include neighboring recommendations, but never fetches Home or Trending.
            if (results.size() < 4) {
                collectMediaLinks(doc, current, results, limit);
            }
        } catch (Exception ignored) {
        }

        ArrayList<NativeContentItem> out = new ArrayList<>();
        for (NativeContentItem item : results.values()) {
            if (item == null || item.url == null || item.url.isEmpty()) continue;
            out.add(item);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static List<Element> relatedRoots(Document doc) {
        ArrayList<Element> roots = new ArrayList<>();
        if (doc == null) return roots;

        for (Element element : doc.select(
                "[id*=related],[class*=related],[id*=recommend],[class*=recommend]," +
                        "[id*=similar],[class*=similar],[id*=suggest],[class*=suggest]"
        )) {
            if (element.select("a[href*=/cnt/medias/]").size() > 0 && !roots.contains(element)) {
                roots.add(element);
            }
        }

        for (Element heading : doc.select("h1,h2,h3,h4,h5,h6,.title,.heading,.section-title,.widget-title")) {
            String text = clean(heading.text()).toLowerCase(Locale.US);
            if (!(text.contains("related") || text.contains("recommended") ||
                    text.contains("similar") || text.contains("more video") ||
                    text.contains("you may also") || text.contains("suggested"))) {
                continue;
            }

            Element current = heading;
            for (int i = 0; i < 5 && current != null; i++) {
                if (current.select("a[href*=/cnt/medias/]").size() >= 2) {
                    if (!roots.contains(current)) roots.add(current);
                    break;
                }
                current = current.parent();
            }

            Element sibling = heading.nextElementSibling();
            if (sibling != null && sibling.select("a[href*=/cnt/medias/]").size() > 0 && !roots.contains(sibling)) {
                roots.add(sibling);
            }
        }
        return roots;
    }

    private static void collectMediaLinks(
            Element root,
            String currentUrl,
            LinkedHashMap<String, NativeContentItem> out,
            int limit
    ) {
        if (root == null || out.size() >= limit) return;
        for (Element link : root.select("a[href*=/cnt/medias/]")) {
            String url = absolute(link, "href");
            if (url.isEmpty() || sameUrl(url, currentUrl)) continue;

            String label = clean(link.text()).toLowerCase(Locale.US);
            String classes = (link.className() + " " + link.parent() + "").toLowerCase(Locale.US);
            if (label.equals("prev") || label.equals("previous") || label.equals("next") ||
                    classes.contains("pagination") || classes.contains("pager")) {
                continue;
            }

            NativeContentItem item = buildItem(link, url);
            if (item.title.length() < 2) continue;
            out.putIfAbsent(normalize(url), item);
            if (out.size() >= limit) return;
        }
    }

    private static NativeContentItem buildItem(Element link, String url) {
        Element scope = cardScope(link);
        String title = clean(link.attr("title"));
        if (!goodTitle(title)) title = clean(link.text());
        if (!goodTitle(title) && scope != null) {
            for (Element heading : scope.select("h1,h2,h3,h4,h5,.title,.post-title,.media-title")) {
                String candidate = clean(heading.text());
                if (goodTitle(candidate)) {
                    title = candidate;
                    break;
                }
            }
        }
        if (!goodTitle(title) && scope != null) {
            Element image = scope.selectFirst("img[alt]");
            if (image != null && goodTitle(clean(image.attr("alt")))) title = clean(image.attr("alt"));
        }
        if (!goodTitle(title)) title = titleFromUrl(url);

        String image = imageFrom(link);
        if (image.isEmpty() && scope != null) image = imageFrom(scope);

        String text = scope == null ? "" : clean(scope.text());
        String views = firstCountBefore(text, "view");
        String comments = firstCountBefore(text, "comment");

        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                url,
                image,
                views,
                "",
                comments
        );
    }

    private static Element cardScope(Element link) {
        Element best = link;
        Element current = link;
        for (int i = 0; i < 6 && current != null; i++) {
            current = current.parent();
            if (current == null) break;
            String text = clean(current.text());
            boolean visual = !current.select("img,video[poster],[data-src],[data-thumb],[data-thumbnail]").isEmpty();
            if (visual && text.length() < 900) {
                best = current;
                if (text.length() > 2) break;
            }
        }
        return best;
    }

    private static String imageFrom(Element root) {
        if (root == null) return "";
        for (Element element : root.select("img,video[poster],[data-src],[data-original],[data-lazy-src],[data-thumb],[data-thumbnail]")) {
            for (String attr : new String[]{"data-src", "data-original", "data-lazy-src", "data-thumb", "data-thumbnail", "poster", "src"}) {
                if (!element.hasAttr(attr)) continue;
                String value = absolute(element, attr);
                if (looksLikeImage(value)) return value;
            }
        }
        return "";
    }

    private static String firstCountBefore(String text, String word) {
        if (text == null || text.isEmpty()) return "";
        String lower = text.toLowerCase(Locale.US);
        int at = lower.indexOf(word);
        if (at < 0) return "";
        int start = at - 1;
        while (start >= 0 && Character.isWhitespace(text.charAt(start))) start--;
        int end = start + 1;
        while (start >= 0) {
            char c = text.charAt(start);
            if (!(Character.isDigit(c) || c == ',' || c == '.' || c == 'k' || c == 'K' || c == 'm' || c == 'M' || c == 'b' || c == 'B')) break;
            start--;
        }
        String value = text.substring(start + 1, end).trim();
        return value.matches("(?i)[0-9][0-9,.]*[kmb]?") ? value : "";
    }

    private static Document fetchDocument(Context context, String url) throws Exception {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(SITE)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .timeout(18000)
                .maxBodySize(5 * 1024 * 1024)
                .followRedirects(true);
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return connection.get();
    }

    private static void apply(VideoDetailActivity activity, List<NativeContentItem> items) {
        try {
            Method method = VideoDetailActivity.class.getDeclaredMethod("renderRelated", List.class);
            method.setAccessible(true);
            method.invoke(activity, items == null ? new ArrayList<>() : items);
        } catch (Exception ignored) {
        }
    }

    private static String stringField(VideoDetailActivity activity, String name) {
        try {
            Field field = VideoDetailActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value == null ? "" : value.toString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String absolute(Element element, String attr) {
        if (element == null) return "";
        String value = element.absUrl(attr);
        if (value == null || value.trim().isEmpty()) value = element.attr(attr);
        if (value == null) return "";
        value = value.trim().replace("&amp;", "&");
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return "https://crazyshit.com" + value;
        return value;
    }

    private static boolean looksLikeImage(String value) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://");
    }

    private static boolean goodTitle(String value) {
        if (value == null) return false;
        String text = clean(value);
        if (text.length() < 2 || text.length() > 120) return false;
        String lower = text.toLowerCase(Locale.US);
        return !lower.equals("prev") && !lower.equals("previous") && !lower.equals("next") &&
                !lower.equals("home") && !lower.equals("video");
    }

    private static String titleFromUrl(String url) {
        String value = normalize(url);
        int slash = value.lastIndexOf('/');
        String slug = slash >= 0 ? value.substring(slash + 1) : value;
        slug = slug.replaceFirst("^[0-9]+-", "").replace('-', ' ').replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : slug.split("\\s+")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.length() == 0 ? "Video" : out.toString();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String url = value.trim();
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        while (url.endsWith("/") && url.length() > 8) url = url.substring(0, url.length() - 1);
        return url;
    }

    private static boolean sameUrl(String a, String b) {
        return normalize(a).equalsIgnoreCase(normalize(b));
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
