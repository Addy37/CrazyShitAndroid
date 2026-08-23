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

/** Parses CrazyShit's visual Series and Categories browser pages into native cards. */
public final class BrowseRepository {
    public static final String BASE = CrazyShitRepository.BASE;
    public static final String SERIES = BASE + "series/";
    public static final String CATEGORIES = CrazyShitRepository.CATEGORIES;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final Pattern CSS_URL = Pattern.compile(
            "(?i)url\\(\\s*['\\\"]?([^'\\\")]+)['\\\"]?\\s*\\)"
    );

    public List<NativeContentItem> fetchCategories(Context context) throws IOException {
        Document doc = fetchDocument(context, CATEGORIES);
        return parseBrowse(doc, "/category/", NativeContentItem.KIND_CATEGORY);
    }

    public List<NativeContentItem> fetchSeries(Context context) throws IOException {
        IOException directError = null;
        try {
            List<NativeContentItem> direct = parseBrowse(
                    fetchDocument(context, SERIES),
                    "/series/",
                    NativeContentItem.KIND_SERIES
            );
            if (!direct.isEmpty()) return direct;
        } catch (IOException e) {
            directError = e;
        }

        // The home page also exposes the site's Popular Series block. This keeps Series useful
        // even if the standalone listing changes its route or markup.
        try {
            return parseBrowse(
                    fetchDocument(context, CrazyShitRepository.HOME),
                    "/series/",
                    NativeContentItem.KIND_SERIES
            );
        } catch (IOException fallbackError) {
            if (directError != null) throw directError;
            throw fallbackError;
        }
    }

    private List<NativeContentItem> parseBrowse(Document doc, String pathMarker, String kind) {
        LinkedHashMap<String, Draft> drafts = new LinkedHashMap<>();
        String selector = "a[href*=" + pathMarker + "]";

        for (Element link : doc.select(selector)) {
            String url = normalizeUrl(link.absUrl("href"));
            if (url.isEmpty()) url = normalizeUrl(link.attr("href"));
            if (!isCollectionUrl(url, pathMarker)) continue;

            Draft draft = drafts.get(url);
            if (draft == null) {
                draft = new Draft(url);
                drafts.put(url, draft);
            }

            Element scope = findBrowseScope(link);
            String title = findBrowseTitle(link, scope);
            String image = findImage(link, scope);
            draft.absorb(title, image);
        }

        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (Map.Entry<String, Draft> entry : drafts.entrySet()) {
            Draft draft = entry.getValue();
            String title = draft.title;
            if (title.isEmpty()) title = titleFromUrl(draft.url, pathMarker);
            if (title.length() < 2 || looksLikeNavigation(title)) continue;

            result.add(new NativeContentItem(
                    kind,
                    title,
                    draft.url,
                    draft.image,
                    "",
                    "",
                    ""
            ));
        }
        return result;
    }

    private Element findBrowseScope(Element link) {
        Element best = link;
        Element current = link;
        for (int i = 0; i < 5 && current != null; i++) {
            current = current.parent();
            if (current == null) break;
            String text = clean(current.text());
            boolean hasImage = !current.select(
                    "img,source,video[poster],[data-src],[data-bg],[data-background],[style*=url]"
            ).isEmpty();
            boolean hasLabel = !current.select("h1,h2,h3,h4,h5,.title,.name,.category-title,.series-title").isEmpty();
            if ((hasImage || hasLabel) && text.length() < 420) {
                best = current;
                if (hasImage && (hasLabel || text.length() > 1)) break;
            }
        }
        return best;
    }

    private String findBrowseTitle(Element link, Element scope) {
        String[] direct = {
                clean(link.attr("title")),
                clean(link.ownText()),
                clean(link.text())
        };
        for (String value : direct) if (goodBrowseTitle(value)) return value;

        Element image = link.selectFirst("img[alt]");
        if (image != null) {
            String alt = clean(image.attr("alt"));
            if (goodBrowseTitle(alt)) return alt;
        }

        if (scope != null) {
            for (Element heading : scope.select("h1,h2,h3,h4,h5,.title,.name,.category-title,.series-title")) {
                String value = clean(heading.text());
                if (goodBrowseTitle(value)) return value;
            }

            for (Element candidate : scope.select("a[href]")) {
                String candidateUrl = normalizeUrl(candidate.absUrl("href"));
                if (!candidateUrl.equals(normalizeUrl(link.absUrl("href")))) continue;
                String value = clean(candidate.text());
                if (goodBrowseTitle(value)) return value;
            }
        }
        return "";
    }

    private boolean goodBrowseTitle(String value) {
        if (value == null) return false;
        String text = clean(value);
        if (text.length() < 2 || text.length() > 90) return false;
        if (text.matches("\\d+")) return false;
        return !looksLikeNavigation(text);
    }

    private String findImage(Element link, Element scope) {
        String value = imageFromElements(link.select(
                "img,source,video[poster],[data-src],[data-original],[data-lazy-src]," +
                        "[data-bg],[data-background],[data-poster],[data-thumb],[data-thumbnail],[style*=url]"
        ));
        if (!value.isEmpty()) return value;

        value = imageFromElement(link);
        if (!value.isEmpty()) return value;

        if (scope != null) {
            value = imageFromElements(scope.select(
                    "img,source,video[poster],[data-src],[data-original],[data-lazy-src]," +
                            "[data-bg],[data-background],[data-poster],[data-thumb],[data-thumbnail],[style*=url]"
            ));
            if (!value.isEmpty()) return value;
            value = imageFromElement(scope);
        }
        return value;
    }

    private String imageFromElements(Elements elements) {
        for (Element element : elements) {
            String value = imageFromElement(element);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String imageFromElement(Element element) {
        if (element == null) return "";
        String[] attrs = {
                "data-src", "data-original", "data-lazy-src", "data-image", "data-poster",
                "data-thumb", "data-thumbnail", "data-bg", "data-background",
                "data-background-image", "poster", "src"
        };

        for (String attr : attrs) {
            if (!element.hasAttr(attr)) continue;
            String value = normalizeUrl(element.absUrl(attr));
            if (value.isEmpty()) value = normalizeUrl(element.attr(attr));
            if (goodImage(value)) return value;
        }

        for (String attr : new String[] {"data-srcset", "srcset"}) {
            String srcset = element.attr(attr);
            if (srcset == null || srcset.trim().isEmpty()) continue;
            String[] candidates = srcset.split(",");
            for (int i = candidates.length - 1; i >= 0; i--) {
                String[] parts = candidates[i].trim().split("\\s+");
                if (parts.length == 0) continue;
                String value = normalizeUrl(parts[0]);
                if (goodImage(value)) return value;
            }
        }

        String style = element.attr("style");
        if (style != null && !style.isEmpty()) {
            Matcher matcher = CSS_URL.matcher(style.replace("&amp;", "&"));
            while (matcher.find()) {
                String value = normalizeUrl(matcher.group(1));
                if (goodImage(value)) return value;
            }
        }
        return "";
    }

    private boolean isCollectionUrl(String url, String pathMarker) {
        if (url == null || url.isEmpty() || !url.contains("crazyshit.com" + pathMarker)) return false;
        String normalized = ensureTrailingSlash(url);
        String root = ensureTrailingSlash(BASE.substring(0, BASE.length() - 1) + pathMarker);
        return !normalized.equals(root);
    }

    private String titleFromUrl(String url, String pathMarker) {
        if (url == null) return "";
        String cleanUrl = url;
        int query = cleanUrl.indexOf('?');
        if (query >= 0) cleanUrl = cleanUrl.substring(0, query);
        while (cleanUrl.endsWith("/")) cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        int slash = cleanUrl.lastIndexOf('/');
        String slug = slash >= 0 ? cleanUrl.substring(slash + 1) : cleanUrl;
        slug = slug.replaceAll("_\\d+$", "").replace('-', ' ').replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String part : slug.split("\\s+")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) result.append(part.substring(1));
        }
        return result.toString();
    }

    private boolean looksLikeNavigation(String value) {
        String text = clean(value).toLowerCase(Locale.US);
        return text.equals("home") || text.equals("categories") || text.equals("series") ||
                text.equals("trending") || text.equals("memes") || text.equals("more") ||
                text.equals("login") || text.equals("register") || text.equals("crazyshit");
    }

    private boolean goodImage(String value) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.contains("spacer") || lower.contains("blank.gif") || lower.contains("placeholder")) return false;
        return !lower.contains("logo") && !lower.contains("sprite") && !lower.contains("avatar");
    }

    private String normalizeUrl(String value) {
        if (value == null) return "";
        String url = value.trim()
                .replace("&amp;", "&")
                .replace("\\/", "/")
                .replace("\\u0026", "&");
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

    private Document fetchDocument(Context context, String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(BASE)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .timeout(18000)
                .maxBodySize(5 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(false);

        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return connection.get();
    }

    private static final class Draft {
        final String url;
        String title = "";
        String image = "";

        Draft(String url) {
            this.url = url;
        }

        void absorb(String nextTitle, String nextImage) {
            if (title.isEmpty() && nextTitle != null && !nextTitle.trim().isEmpty()) title = nextTitle.trim();
            if (image.isEmpty() && nextImage != null && !nextImage.trim().isEmpty()) image = nextImage.trim();
        }
    }
}