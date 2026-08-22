package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Lightweight native parser for CrazyShit's separate /memes feed. */
public final class MemeRepository {
    public static final String MEMES = CrazyShitRepository.BASE + "memes";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    public List<NativeContentItem> fetch(Context context, int page) throws IOException {
        // The current memes landing page is a single Recent feed. Avoid guessing a
        // pagination route that could collide with individual /memes/<id>-<slug> pages.
        if (page > 1) return new ArrayList<>();

        Document doc = fetchDocument(context, MEMES);
        LinkedHashMap<String, NativeContentItem> found = new LinkedHashMap<>();

        for (Element link : doc.select("a[href]")) {
            String url = absolute(link.absUrl("href"));
            if (!isMemePage(url)) continue;

            Element scope = findScope(link);
            String image = firstImage(link, scope);
            String title = firstTitle(link, scope);
            String uploader = firstUploader(scope);

            if (title.isEmpty() && image.isEmpty()) continue;
            if (title.isEmpty()) title = "Meme";

            found.putIfAbsent(url, new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    title,
                    url,
                    image,
                    "",
                    uploader,
                    ""
            ));
            if (found.size() >= 60) break;
        }

        return new ArrayList<>(found.values());
    }

    private Document fetchDocument(Context context, String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(CrazyShitRepository.BASE)
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

    private boolean isMemePage(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("https://crazyshit.com/memes/")) return false;
        String tail = lower.substring("https://crazyshit.com/memes/".length());
        if (tail.startsWith("tag/") || tail.startsWith("recent") || tail.startsWith("top") || tail.startsWith("saved")) {
            return false;
        }
        return tail.matches("\\d+[-/].*") || tail.matches("\\d+.*");
    }

    private Element findScope(Element link) {
        Element best = link;
        Element current = link;
        for (int i = 0; i < 6 && current != null; i++) {
            current = current.parent();
            if (current == null) break;
            String text = clean(current.text());
            boolean visual = !current.select("img,source,[data-src],[data-original]").isEmpty();
            boolean profile = !current.select("a[href*=/profile/]").isEmpty();
            if (visual && text.length() < 900) {
                best = current;
                if (profile) break;
            }
        }
        return best;
    }

    private String firstTitle(Element link, Element scope) {
        String title = clean(link.attr("title"));
        if (goodTitle(title)) return title;

        Element image = link.selectFirst("img[alt]");
        if (image == null && scope != null) image = scope.selectFirst("img[alt]");
        if (image != null) {
            title = clean(image.attr("alt"));
            if (goodTitle(title)) return title;
        }

        title = clean(link.text());
        if (goodTitle(title)) return title;

        if (scope != null) {
            Element heading = scope.selectFirst("h1,h2,h3,h4,.title,.meme-title");
            if (heading != null && goodTitle(heading.text())) return clean(heading.text());
        }
        return "";
    }

    private String firstImage(Element link, Element scope) {
        String result = imageFrom(link);
        if (!result.isEmpty()) return result;
        if (scope != null) return imageFrom(scope);
        return "";
    }

    private String imageFrom(Element root) {
        if (root == null) return "";
        for (Element image : root.select("img,source")) {
            String[] attrs = {"data-src", "data-original", "src"};
            for (String attr : attrs) {
                if (!image.hasAttr(attr)) continue;
                String value = absolute(image.absUrl(attr));
                if (value.isEmpty()) value = absolute(image.attr(attr));
                if (goodImage(value)) return value;
            }
            String srcset = image.attr("srcset");
            if (!srcset.trim().isEmpty()) {
                String[] pieces = srcset.split(",");
                for (int i = pieces.length - 1; i >= 0; i--) {
                    String value = absolute(pieces[i].trim().split("\\s+")[0]);
                    if (goodImage(value)) return value;
                }
            }
        }
        return "";
    }

    private String firstUploader(Element scope) {
        if (scope == null) return "";
        Element user = scope.selectFirst("a[href*=/profile/]");
        return user == null ? "" : clean(user.text());
    }

    private boolean goodTitle(String value) {
        String text = clean(value);
        if (text.length() < 2 || text.length() > 180) return false;
        String lower = text.toLowerCase(Locale.US);
        return !lower.equals("memes") && !lower.equals("share") && !lower.equals("recent") && !lower.equals("top");
    }

    private boolean goodImage(String value) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        return !lower.contains("logo") && !lower.contains("avatar") && !lower.contains("sprite") && !lower.contains("placeholder");
    }

    private String absolute(String value) {
        if (value == null) return "";
        String url = value.trim().replace("&amp;", "&");
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return "https://crazyshit.com" + url;
        return url;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
