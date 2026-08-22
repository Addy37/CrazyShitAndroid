package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrazyShitRepository {
    public static final String BASE = "https://crazyshit.com/";
    public static final String HOME = BASE;
    public static final String TRENDING = BASE + "trending/";
    public static final String CATEGORIES = BASE + "categories/";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final Pattern NUMBER = Pattern.compile(
            "(?i)(?:\\b\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?[KMB]?\\b|" +
            "\\b\\d+(?:\\.\\d+)?[KMB]\\b|\\b\\d{1,3}\\b)"
    );

    private static final Pattern MEDIA_IN_SCRIPT = Pattern.compile(
            "(?i)https?://[^\\s\\\"'<>]+?\\.(?:m3u8|mpd|mp4|webm|m4v)(?:\\?[^\\s\\\"'<>]*)?"
    );

    public List<NativeContentItem> fetchFeed(Context context, String baseUrl, int page)
            throws IOException {
        Document doc = fetchDocument(context, pageUrl(baseUrl, page));
        LinkedHashMap<String, NativeContentItem> items = new LinkedHashMap<>();

        for (Element link : doc.select("a[href*=/cnt/medias/]")) {
            String url = normalizeUrl(link.absUrl("href"));
            if (!isMediaPage(url)) continue;

            Element scope = findCardScope(link);
            String title = findTitle(link, scope, url);
            String image = findImage(link, scope);
            Meta meta = findMeta(scope, title);

            NativeContentItem candidate = new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    title,
                    url,
                    image,
                    meta.views,
                    meta.uploader,
                    meta.comments
            );

            NativeContentItem old = items.get(url);
            items.put(url, old == null ? candidate : old.merge(candidate));
        }

        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (NativeContentItem item : items.values()) {
            if (item.title.trim().length() < 3) continue;
            if (looksLikeNavigation(item.title)) continue;
            result.add(item);
            if (result.size() >= 60) break;
        }
        return result;
    }

    public List<NativeContentItem> fetchCategories(Context context) throws IOException {
        Document doc = fetchDocument(context, CATEGORIES);
        LinkedHashMap<String, NativeContentItem> categories = new LinkedHashMap<>();

        for (Element link : doc.select("a[href*=/category/]")) {
            String url = normalizeUrl(link.absUrl("href"));
            if (url.isEmpty() || !url.contains("crazyshit.com/category/")) continue;
            String title = clean(link.text());
            if (title.length() < 2) {
                Element parent = link.parent();
                if (parent != null) title = clean(parent.text());
            }
            if (title.length() < 2 || looksLikeNavigation(title)) continue;

            NativeContentItem item = new NativeContentItem(
                    NativeContentItem.KIND_CATEGORY,
                    title,
                    url,
                    findImage(link, link.parent()),
                    "",
                    "",
                    ""
            );
            categories.putIfAbsent(url, item);
        }
        return new ArrayList<>(categories.values());
    }

    public StreamInfo resolvePlayable(Context context, String pageUrl) throws IOException {
        Document doc = fetchDocument(context, pageUrl);
        String title = clean(doc.title());

        String[] selectors = {
                "video[src]",
                "video source[src]",
                "source[type*=video][src]"
        };
        for (String selector : selectors) {
            for (Element el : doc.select(selector)) {
                String candidate = normalizeUrl(el.absUrl("src"));
                if (isDirectMedia(candidate)) {
                    return new StreamInfo(candidate, pageUrl, title);
                }
            }
        }

        String[] metas = {
                "meta[property=og:video]",
                "meta[property=og:video:url]",
                "meta[property=og:video:secure_url]",
                "meta[name=twitter:player:stream]"
        };
        for (String selector : metas) {
            Element meta = doc.selectFirst(selector);
            if (meta == null) continue;
            String candidate = normalizeUrl(meta.absUrl("content"));
            if (candidate.isEmpty()) candidate = normalizeUrl(meta.attr("content"));
            if (isDirectMedia(candidate)) {
                return new StreamInfo(candidate, pageUrl, title);
            }
        }

        for (Element script : doc.select("script")) {
            String body = script.data();
            if (body == null || body.isEmpty()) body = script.html();
            if (body == null || body.isEmpty()) continue;
            body = body.replace("\\/", "/").replace("&amp;", "&");
            Matcher matcher = MEDIA_IN_SCRIPT.matcher(body);
            while (matcher.find()) {
                String candidate = normalizeUrl(matcher.group());
                if (isDirectMedia(candidate)) {
                    return new StreamInfo(candidate, pageUrl, title);
                }
            }
        }

        return null;
    }

    public String searchUrl(String query) {
        String slug = query == null ? "" : query.toLowerCase(Locale.US).trim();
        slug = slug.replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) return BASE + "search/";
        return BASE + "search/" + slug + "/";
    }

    public String pageUrl(String baseUrl, int page) {
        String base = baseUrl == null || baseUrl.trim().isEmpty() ? HOME : baseUrl.trim();
        if (page <= 1) return ensureTrailingSlash(base);
        base = ensureTrailingSlash(base);
        return base + page + "/";
    }

    private Document fetchDocument(Context context, String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(BASE)
                .timeout(18000)
                .maxBodySize(5 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(false);

        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) {
                connection.header("Cookie", cookies);
            }
        } catch (Exception ignored) {
        }

        return connection.get();
    }

    private Element findCardScope(Element link) {
        Element best = link;
        Element current = link;
        for (int i = 0; i < 6 && current != null; i++) {
            current = current.parent();
            if (current == null) break;
            String text = clean(current.text());
            boolean hasImage = !current.select("img").isEmpty();
            boolean hasHeading = !current.select("h1,h2,h3,h4,h5").isEmpty();
            if ((hasImage || hasHeading) && text.length() > 3 && text.length() < 900) {
                best = current;
                if (hasImage && hasHeading) break;
            }
        }
        return best;
    }

    private String findTitle(Element link, Element scope, String targetUrl) {
        String title = clean(link.attr("title"));
        if (goodTitle(title)) return title;

        title = clean(link.text());
        if (goodTitle(title)) return title;

        if (scope != null) {
            for (Element candidate : scope.select("a[href]")) {
                String href = normalizeUrl(candidate.absUrl("href"));
                if (!targetUrl.equals(href)) continue;
                String text = clean(candidate.text());
                if (goodTitle(text)) return text;
            }

            for (Element heading : scope.select("h1,h2,h3,h4,h5,.title,.post-title,.media-title")) {
                String text = clean(heading.text());
                if (goodTitle(text)) return text;
            }

            Element image = scope.selectFirst("img[alt]");
            if (image != null) {
                String alt = clean(image.attr("alt"));
                if (goodTitle(alt)) return alt;
            }
        }
        return "Video";
    }

    private String findImage(Element link, Element scope) {
        String direct = imageFromElements(link == null ? new Elements() : link.select("img"));
        if (!direct.isEmpty()) return direct;
        if (scope != null) {
            direct = imageFromElements(scope.select("img"));
            if (!direct.isEmpty()) return direct;
        }
        return "";
    }

    private String imageFromElements(Elements images) {
        for (Element image : images) {
            String[] attrs = {"data-src", "data-original", "data-lazy-src", "src"};
            for (String attr : attrs) {
                if (!image.hasAttr(attr)) continue;
                String value = normalizeUrl(image.absUrl(attr));
                if (value.isEmpty()) value = normalizeUrl(image.attr(attr));
                if (goodImage(value)) return value;
            }

            String srcset = image.attr("data-srcset");
            if (srcset.isEmpty()) srcset = image.attr("srcset");
            if (!srcset.isEmpty()) {
                String first = srcset.split(",")[0].trim().split("\\s+")[0];
                String value = normalizeUrl(first);
                if (goodImage(value)) return value;
            }
        }
        return "";
    }

    private Meta findMeta(Element scope, String title) {
        if (scope == null) return new Meta("", "", "");
        String text = clean(scope.text());
        if (title != null && !title.isEmpty()) text = text.replace(title, " ");

        ArrayList<String> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (!numbers.contains(value)) numbers.add(value);
        }

        String views = numbers.isEmpty() ? "" : numbers.get(0);
        String comments = numbers.size() < 2 ? "" : numbers.get(numbers.size() - 1);
        if (views.equals(comments)) comments = "";

        String uploader = "";
        for (Element user : scope.select("a[href*=user],a[href*=member],a[href*=profile]")) {
            String value = clean(user.text());
            if (value.length() >= 2 && value.length() <= 40) {
                uploader = value;
                break;
            }
        }
        if (uploader.isEmpty() && text.toLowerCase(Locale.US).contains("crazyshit")) {
            uploader = "crazyshit";
        }
        return new Meta(views, uploader, comments);
    }

    private boolean isMediaPage(String url) {
        return url != null && url.contains("crazyshit.com/cnt/medias/");
    }

    private boolean isDirectMedia(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".m3u8") || lower.contains(".mpd") ||
                lower.contains(".mp4") || lower.contains(".webm") ||
                lower.contains(".m4v");
    }

    private boolean goodTitle(String value) {
        if (value == null) return false;
        String text = clean(value);
        if (text.length() < 3 || text.length() > 220) return false;
        if (text.matches("\\d+")) return false;
        return !looksLikeNavigation(text);
    }

    private boolean looksLikeNavigation(String value) {
        if (value == null) return true;
        String text = clean(value).toLowerCase(Locale.US);
        return text.equals("crazyshit") || text.equals("home") || text.equals("videos") ||
                text.equals("pictures") || text.equals("pics") || text.equals("series") ||
                text.equals("memes") || text.equals("categories") || text.equals("trending") ||
                text.equals("login") || text.equals("register") || text.equals("more") ||
                text.equals("top categories") || text.equals("all categories");
    }

    private boolean goodImage(String value) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.startsWith("data:")) return false;
        return !lower.contains("logo") && !lower.contains("sprite") && !lower.contains("avatar");
    }

    private String normalizeUrl(String value) {
        if (value == null) return "";
        String url = value.trim().replace("&amp;", "&").replace("\\/", "/");
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return BASE.substring(0, BASE.length() - 1) + url;
        return url;
    }

    private String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static final class Meta {
        final String views;
        final String uploader;
        final String comments;

        Meta(String views, String uploader, String comments) {
            this.views = views;
            this.uploader = uploader;
            this.comments = comments;
        }
    }

    public static final class StreamInfo {
        public final String mediaUrl;
        public final String pageUrl;
        public final String title;

        StreamInfo(String mediaUrl, String pageUrl, String title) {
            this.mediaUrl = mediaUrl;
            this.pageUrl = pageUrl;
            this.title = title == null || title.trim().isEmpty() ? "Video" : title;
        }
    }
}
