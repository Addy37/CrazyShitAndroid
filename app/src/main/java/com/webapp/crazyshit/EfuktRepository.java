package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native catalog, series-feed and playback resolver for EFukt's public pages. */
public final class EfuktRepository {
    public static final String BASE = "https://efukt.com/";
    public static final String SERIES = BASE + "series/";
    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final Pattern VIDEO_PAGE = Pattern.compile(
            "(?i)^https?://(?:www\\.)?efukt\\.com/(?:[^/?#]+/)*\\d+_[^/?#]+\\.html(?:[?#].*)?$"
    );
    private static final Pattern MEDIA_IN_SCRIPT = Pattern.compile(
            "(?i)(?:file|src|video_url|contentUrl)\\s*[:=]\\s*['\\\"]([^'\\\"]+?\\.(?:mp4|m3u8|mpd|webm|m4v)(?:\\?[^'\\\"]*)?)['\\\"]"
    );
    private static final Pattern ABSOLUTE_MEDIA = Pattern.compile(
            "(?i)https?://[^\\s\\\"'<>]+?\\.(?:mp4|m3u8|mpd|webm|m4v)(?:\\?[^\\s\\\"'<>]*)?"
    );
    private static final Pattern CSS_URL = Pattern.compile(
            "(?i)url\\(\\s*['\\\"]?([^'\\\")]+)['\\\"]?\\s*\\)"
    );

    public List<NativeContentItem> fetchSeries(Context context) throws IOException {
        IOException listingError = null;
        try {
            List<NativeContentItem> result = parseSeries(fetchDocument(context, SERIES));
            if (!result.isEmpty()) return result;
        } catch (IOException e) {
            listingError = e;
        }

        // EFukt also links current series from its home feed. This gives the beta a useful,
        // smaller fallback if the full directory is unavailable to the current region/session.
        try {
            List<NativeContentItem> result = parseSeries(fetchDocument(context, BASE));
            if (!result.isEmpty()) return result;
        } catch (IOException fallbackError) {
            if (listingError != null) throw listingError;
            throw fallbackError;
        }

        if (listingError != null) throw listingError;
        throw new IOException("EFukt Series is unavailable in this region or session");
    }

    /** Returns the newest public EFukt uploads for background alerts and the native latest feed. */
    public List<NativeContentItem> fetchLatest(Context context) throws IOException {
        return parseVideoFeed(fetchDocument(context, BASE));
    }

    public List<NativeContentItem> fetchSeriesFeed(Context context, String seriesUrl, int page)
            throws IOException {
        if (page > 1) return new ArrayList<>();
        return parseVideoFeed(fetchDocument(context, seriesUrl));
    }

    public List<NativeContentItem> search(Context context, String query) throws IOException {
        String value = query == null ? "" : query.trim();
        if (value.length() < 2) return new ArrayList<>();
        String encoded = URLEncoder.encode(value, "UTF-8");
        return parseVideoFeed(fetchDocument(context, BASE + "search/" + encoded + "/"));
    }

    private List<NativeContentItem> parseVideoFeed(Document doc) {
        LinkedHashMap<String, NativeContentItem> items = new LinkedHashMap<>();

        for (Element link : doc.select("a[href]")) {
            String url = absolute(link, "href", doc.location());
            if (!isVideoPage(url)) continue;

            Element scope = findCardScope(link);
            String title = firstUseful(
                    clean(link.attr("title")),
                    clean(link.ownText()),
                    clean(link.text()),
                    heading(scope),
                    titleFromVideoUrl(url)
            );
            if (title.length() < 2) continue;
            String image = findImage(link, scope, doc.location());
            String description = findDescription(scope, title);
            NativeContentItem candidate = new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    title,
                    url,
                    image,
                    "",
                    "EFukt",
                    "",
                    description
            );
            NativeContentItem old = items.get(url);
            items.put(url, old == null ? candidate : old.merge(candidate));
        }
        return new ArrayList<>(items.values());
    }

    public CrazyShitRepository.StreamInfo resolvePlayable(Context context, String pageUrl)
            throws IOException {
        String normalized = normalizeUrl(pageUrl, BASE);
        if (isDirectMedia(normalized)) {
            return new CrazyShitRepository.StreamInfo(normalized, pageUrl, "EFukt video");
        }

        Document page = fetchDocument(context, normalized);
        String stream = findStream(page, normalized);
        if (stream.isEmpty()) {
            int checked = 0;
            for (Element iframe : page.select("iframe[src]")) {
                if (++checked > 4) break;
                String frameUrl = absolute(iframe, "src", normalized);
                if (!frameUrl.startsWith("http://") && !frameUrl.startsWith("https://")) continue;
                try {
                    Document frame = fetchDocument(context, frameUrl);
                    stream = findStream(frame, frameUrl);
                    if (!stream.isEmpty()) break;
                } catch (IOException ignored) {
                }
            }
        }

        if (stream.isEmpty()) return null;
        String title = clean(page.selectFirst("h1") == null ? "" : page.selectFirst("h1").text());
        if (title.isEmpty()) title = clean(page.title());
        return new CrazyShitRepository.StreamInfo(stream, normalized, title);
    }

    public static boolean isEfuktUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.US);
        return lower.startsWith("https://efukt.com/") || lower.startsWith("https://www.efukt.com/") ||
                lower.startsWith("http://efukt.com/") || lower.startsWith("http://www.efukt.com/");
    }

    private List<NativeContentItem> parseSeries(Document doc) {
        LinkedHashMap<String, Draft> items = new LinkedHashMap<>();
        for (Element link : doc.select("a[href*=/series/]")) {
            String url = absolute(link, "href", doc.location());
            if (!isSeriesPage(url)) continue;
            Element scope = findCardScope(link);
            String title = firstUseful(
                    clean(link.attr("title")),
                    clean(link.ownText()),
                    clean(link.text()),
                    heading(scope),
                    titleFromSeriesUrl(url)
            );
            String image = findImage(link, scope, doc.location());
            String description = findDescription(scope, title);
            Draft draft = items.get(url);
            if (draft == null) {
                draft = new Draft(url);
                items.put(url, draft);
            }
            draft.absorb(title, image, description);
        }

        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (Map.Entry<String, Draft> entry : items.entrySet()) {
            Draft item = entry.getValue();
            if (item.title.length() < 2 || looksLikeNavigation(item.title)) continue;
            result.add(new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    item.title,
                    item.url,
                    item.image,
                    "",
                    "EFukt",
                    "",
                    item.description
            ));
        }
        return result;
    }

    private String findStream(Document doc, String documentUrl) {
        String[] selectors = {
                "source[src][type*=video]",
                "video source[src]",
                "video[src]",
                "meta[property=og:video][content]",
                "meta[property=og:video:url][content]",
                "meta[property=og:video:secure_url][content]",
                "meta[itemprop=contentUrl][content]"
        };
        for (String selector : selectors) {
            for (Element element : doc.select(selector)) {
                String attr = element.hasAttr("src") ? "src" : "content";
                String candidate = absolute(element, attr, documentUrl);
                if (isDirectMedia(candidate)) return candidate;
            }
        }

        for (Element script : doc.select("script")) {
            String body = script.data();
            if (body == null || body.isEmpty()) body = script.html();
            if (body == null || body.isEmpty()) continue;
            body = Parser.unescapeEntities(body, false)
                    .replace("\\/", "/")
                    .replace("\\u0026", "&");

            Matcher named = MEDIA_IN_SCRIPT.matcher(body);
            while (named.find()) {
                String candidate = normalizeUrl(named.group(1), documentUrl);
                if (isDirectMedia(candidate)) return candidate;
            }

            Matcher absolute = ABSOLUTE_MEDIA.matcher(body);
            while (absolute.find()) {
                String candidate = normalizeUrl(absolute.group(), documentUrl);
                if (isDirectMedia(candidate)) return candidate;
            }
        }
        return "";
    }

    private Element findCardScope(Element link) {
        Element best = link;
        Element current = link;
        for (int i = 0; i < 6 && current != null; i++) {
            current = current.parent();
            if (current == null) break;
            String text = clean(current.text());
            boolean visual = !current.select("img,source,video[poster],[style*=url],[data-src]").isEmpty();
            boolean label = !current.select("h1,h2,h3,h4,h5,.title").isEmpty();
            if ((visual || label) && text.length() < 900) {
                best = current;
                if (visual && label) break;
            }
        }
        return best;
    }

    private String heading(Element scope) {
        if (scope == null) return "";
        Element heading = scope.selectFirst("h1,h2,h3,h4,h5,.title");
        return heading == null ? "" : clean(heading.text());
    }

    private String findDescription(Element scope, String title) {
        if (scope == null) return "";
        String normalizedTitle = clean(title).toLowerCase(Locale.US);
        for (Element element : scope.select(
                "[itemprop=description],.description,.excerpt,.summary,.caption,p"
        )) {
            String value = clean(element.text());
            if (value.length() < 12 || value.length() > 900) continue;
            String lower = value.toLowerCase(Locale.US);
            if (lower.equals(normalizedTitle) || looksLikeNavigation(value)) continue;
            if (lower.matches("^(views?|comments?|posted|added|date)\\b.*")) continue;
            if (!normalizedTitle.isEmpty() && lower.startsWith(normalizedTitle)) {
                value = clean(value.substring(Math.min(value.length(), title.length())));
            }
            if (value.length() >= 12) return value;
        }
        return "";
    }

    private String findImage(Element link, Element scope, String documentUrl) {
        String image = imageFromElements(link.select(
                "img,source,video[poster],[data-src],[data-original],[data-lazy-src]," +
                        "[data-bg],[data-background],[data-thumb],[data-thumbnail],[style*=url]"
        ), documentUrl);
        if (!image.isEmpty()) return image;
        image = imageFromElement(link, documentUrl);
        if (!image.isEmpty()) return image;
        if (scope == null) return "";
        image = imageFromElements(scope.select(
                "img,source,video[poster],[data-src],[data-original],[data-lazy-src]," +
                        "[data-bg],[data-background],[data-thumb],[data-thumbnail],[style*=url]"
        ), documentUrl);
        return image.isEmpty() ? imageFromElement(scope, documentUrl) : image;
    }

    private String imageFromElements(Elements elements, String documentUrl) {
        for (Element element : elements) {
            String value = imageFromElement(element, documentUrl);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String imageFromElement(Element element, String documentUrl) {
        if (element == null) return "";
        for (String attr : new String[] {
                "data-src", "data-original", "data-lazy-src", "data-poster", "data-thumb",
                "data-thumbnail", "data-bg", "data-background", "poster", "src"
        }) {
            if (!element.hasAttr(attr)) continue;
            String value = absolute(element, attr, documentUrl);
            if (goodImage(value)) return value;
        }

        for (String attr : new String[] {"data-srcset", "srcset"}) {
            String srcset = element.attr(attr);
            if (srcset == null || srcset.trim().isEmpty()) continue;
            String[] candidates = srcset.split(",");
            for (int i = candidates.length - 1; i >= 0; i--) {
                String[] parts = candidates[i].trim().split("\\s+");
                if (parts.length == 0) continue;
                String value = normalizeUrl(parts[0], documentUrl);
                if (goodImage(value)) return value;
            }
        }

        Matcher css = CSS_URL.matcher(element.attr("style"));
        while (css.find()) {
            String value = normalizeUrl(css.group(1), documentUrl);
            if (goodImage(value)) return value;
        }
        return "";
    }

    private Document fetchDocument(Context context, String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(BASE)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .timeout(18000)
                .maxBodySize(6 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(false);
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) {
        }
        Document doc = connection.get();
        String text = clean(doc.text()).toLowerCase(Locale.US);
        if (text.contains("access to this website is not available in your area")) {
            throw new IOException("EFukt is unavailable in this region");
        }
        return doc;
    }

    private boolean isSeriesPage(String url) {
        if (!isEfuktUrl(url)) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!lower.contains("/series/")) return false;
        String plain = lower.replace("http://", "https://");
        return !plain.equals(SERIES) && !plain.equals(SERIES.substring(0, SERIES.length() - 1));
    }

    private boolean isVideoPage(String url) {
        return url != null && VIDEO_PAGE.matcher(url).matches();
    }

    private boolean isDirectMedia(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mpd") ||
                lower.contains(".webm") || lower.contains(".m4v");
    }

    private boolean goodImage(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        return !lower.contains("logo") && !lower.contains("sprite") &&
                !lower.contains("spacer") && !lower.contains("placeholder");
    }

    private boolean looksLikeNavigation(String title) {
        String lower = clean(title).toLowerCase(Locale.US);
        return lower.equals("series") || lower.equals("video") || lower.equals("videos") ||
                lower.equals("categories") || lower.equals("home") || lower.equals("efukt");
    }

    private String titleFromSeriesUrl(String url) {
        String slug = lastPath(url).replaceAll("-\\d+$", "");
        return titleFromSlug(slug);
    }

    private String titleFromVideoUrl(String url) {
        String slug = lastPath(url).replaceFirst("^\\d+_", "").replaceFirst("(?i)\\.html$", "");
        return titleFromSlug(slug);
    }

    private String titleFromSlug(String slug) {
        StringBuilder result = new StringBuilder();
        for (String part : slug.replace('-', ' ').replace('_', ' ').split("\\s+")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) result.append(part.substring(1));
        }
        return result.toString();
    }

    private String lastPath(String url) {
        if (url == null) return "";
        String cleanUrl = url.split("[?#]", 2)[0];
        while (cleanUrl.endsWith("/")) cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        int slash = cleanUrl.lastIndexOf('/');
        return slash < 0 ? cleanUrl : cleanUrl.substring(slash + 1);
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned.length() >= 2 && cleaned.length() <= 220 && !looksLikeNavigation(cleaned)) {
                return cleaned;
            }
        }
        return "";
    }

    private String absolute(Element element, String attr, String baseUrl) {
        String value = element.absUrl(attr);
        if (value == null || value.isEmpty()) value = element.attr(attr);
        return normalizeUrl(value, baseUrl);
    }

    private String normalizeUrl(String value, String baseUrl) {
        if (value == null) return "";
        String url = Parser.unescapeEntities(value.trim(), false)
                .replace("\\/", "/")
                .replace("\\u0026", "&");
        if (url.isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        try {
            return URI.create(baseUrl == null || baseUrl.isEmpty() ? BASE : baseUrl)
                    .resolve(url)
                    .toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String clean(String value) {
        return value == null ? "" : Parser.unescapeEntities(value, false)
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final class Draft {
        final String url;
        String title = "";
        String image = "";
        String description = "";

        Draft(String url) {
            this.url = url;
        }

        void absorb(String nextTitle, String nextImage, String nextDescription) {
            if (title.isEmpty() && nextTitle != null && !nextTitle.isEmpty()) title = nextTitle;
            if (image.isEmpty() && nextImage != null && !nextImage.isEmpty()) image = nextImage;
            if (description.isEmpty() && nextDescription != null && !nextDescription.isEmpty()) {
                description = nextDescription;
            }
        }
    }
}
