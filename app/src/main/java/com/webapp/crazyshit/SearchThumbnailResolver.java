package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves missing thumbnails for Search/Library media results from the individual media page.
 * Results are cached locally so a page only needs to be inspected once.
 */
final class SearchThumbnailResolver {
    interface Callback {
        void onResolved(String pageUrl, String thumbnailUrl);
    }

    private static final String PREFS = "search_thumbnail_cache_v1";
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final Pattern JSON_THUMB = Pattern.compile(
            "(?i)\\\"(?:thumbnailUrl|thumbnail|poster|image)\\\"\\s*:\\s*(?:\\[\\s*)?\\\"([^\\\"]+)\\\""
    );

    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, String> MEMORY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    private SearchThumbnailResolver() {
    }

    static void resolve(Context context, String pageUrl, Callback callback) {
        if (context == null || pageUrl == null || pageUrl.trim().isEmpty() || callback == null) return;
        final Context app = context.getApplicationContext();
        final String key = normalizePage(pageUrl);
        if (key.isEmpty()) return;

        String cached = MEMORY.get(key);
        if (cached == null) {
            cached = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "");
            if (cached != null && !cached.isEmpty()) MEMORY.put(key, cached);
        }
        if (cached != null && !cached.isEmpty()) {
            final String value = cached;
            MAIN.post(() -> callback.onResolved(pageUrl, value));
            return;
        }

        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) return;
        IO.execute(() -> {
            String thumb = "";
            try {
                thumb = fetch(app, pageUrl);
            } catch (Exception ignored) {
            }
            IN_FLIGHT.remove(key);
            if (!thumb.isEmpty()) {
                MEMORY.put(key, thumb);
                try {
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(key, thumb)
                            .apply();
                } catch (Exception ignored) {
                }
            }
            final String value = thumb;
            MAIN.post(() -> callback.onResolved(pageUrl, value));
        });
    }

    private static String fetch(Context context, String pageUrl) throws Exception {
        Connection connection = Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .referrer(SITE)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .timeout(14000)
                .maxBodySize(4 * 1024 * 1024)
                .followRedirects(true);
        try {
            String cookies = CookieManager.getInstance().getCookie(pageUrl);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) {
        }

        Document doc = connection.get();
        String[] selectors = {
                "meta[property=og:image]",
                "meta[property=og:image:url]",
                "meta[property=og:image:secure_url]",
                "meta[name=twitter:image]",
                "meta[name=twitter:image:src]",
                "meta[itemprop=thumbnailUrl]",
                "link[rel=image_src]",
                "video[poster]"
        };
        for (String selector : selectors) {
            for (Element element : doc.select(selector)) {
                String candidate;
                if (element.hasAttr("content")) {
                    candidate = absolute(element, "content");
                } else if (element.hasAttr("href")) {
                    candidate = absolute(element, "href");
                } else {
                    candidate = absolute(element, "poster");
                }
                candidate = cleanUrl(candidate);
                if (good(candidate)) return candidate;
            }
        }

        for (Element script : doc.select("script[type=application/ld+json],script")) {
            String body = script.data();
            if (body == null || body.isEmpty()) body = script.html();
            if (body == null || body.isEmpty()) continue;
            body = body.replace("\\/", "/").replace("&amp;", "&");
            Matcher matcher = JSON_THUMB.matcher(body);
            while (matcher.find()) {
                String candidate = cleanUrl(matcher.group(1));
                if (good(candidate)) return candidate;
            }
        }

        // Last HTML fallback. Prefer obvious thumbnail/poster images rather than avatars/icons.
        for (Element image : doc.select("img[src],img[data-src],img[data-original],img[data-lazy-src]")) {
            String[] attrs = {"data-src", "data-original", "data-lazy-src", "src"};
            for (String attr : attrs) {
                if (!image.hasAttr(attr)) continue;
                String candidate = cleanUrl(absolute(image, attr));
                if (good(candidate) && looksLikeMediaArt(candidate)) return candidate;
            }
        }
        return "";
    }

    private static String absolute(Element element, String attr) {
        String value = element.absUrl(attr);
        if (value == null || value.trim().isEmpty()) value = element.attr(attr);
        if (value == null) return "";
        value = value.trim();
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return "https://crazyshit.com" + value;
        return value;
    }

    private static boolean good(String value) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.US);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return false;
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
                lower.contains(".webp") || lower.contains("/thumb") || lower.contains("/poster");
    }

    private static boolean looksLikeMediaArt(String value) {
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("media.crazyshit.com") || lower.contains("/thumb") ||
                lower.contains("/poster") || lower.contains("/media/");
    }

    private static String cleanUrl(String value) {
        if (value == null) return "";
        return value.trim().replace("&amp;", "&").replace("\\u0026", "&");
    }

    private static String normalizePage(String value) {
        String url = value == null ? "" : value.trim().toLowerCase(Locale.US);
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }
}
