package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ThumbnailResolver {
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final Pattern JSON_IMAGE = Pattern.compile(
            "(?i)[\\\"'](?:thumbnailUrl|thumbnail|poster|image|imageUrl)[\\\"']\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"']"
    );

    private static final Pattern IMAGE_URL = Pattern.compile(
            "(?i)https?://[^\\s\\\"'<>]+?\\.(?:jpe?g|png|webp|avif|gif)(?:\\?[^\\s\\\"'<>]*)?"
    );

    private ThumbnailResolver() {}

    static String resolve(Context context, String pageUrl) {
        if (pageUrl == null || pageUrl.trim().isEmpty()) return "";
        try {
            Document doc = fetch(context, pageUrl);

            String[] metaSelectors = {
                    "meta[property=og:image]",
                    "meta[property=og:image:url]",
                    "meta[property=og:image:secure_url]",
                    "meta[name=twitter:image]",
                    "meta[name=twitter:image:src]",
                    "meta[itemprop=thumbnailUrl]",
                    "meta[itemprop=image]"
            };
            for (String selector : metaSelectors) {
                Element meta = doc.selectFirst(selector);
                if (meta == null) continue;
                String value = absolute(pageUrl, meta.attr("content"));
                if (good(value)) return value;
            }

            String[] elementSelectors = {
                    "video[poster]",
                    "link[rel=image_src]",
                    "img[itemprop=thumbnailUrl]",
                    "img[itemprop=image]"
            };
            for (String selector : elementSelectors) {
                for (Element element : doc.select(selector)) {
                    String raw;
                    if (element.hasAttr("poster")) raw = element.attr("poster");
                    else if (element.hasAttr("href")) raw = element.attr("href");
                    else raw = firstNonEmpty(
                            element.attr("src"),
                            element.attr("data-src"),
                            element.attr("data-original"),
                            element.attr("data-lazy-src")
                    );
                    String value = absolute(pageUrl, raw);
                    if (good(value)) return value;
                }
            }

            for (Element script : doc.select("script")) {
                String body = script.data();
                if (body == null || body.isEmpty()) body = script.html();
                if (body == null || body.isEmpty()) continue;
                body = body.replace("\\/", "/")
                        .replace("\\u0026", "&")
                        .replace("&amp;", "&");

                Matcher keyed = JSON_IMAGE.matcher(body);
                while (keyed.find()) {
                    String value = absolute(pageUrl, keyed.group(1));
                    if (good(value)) return value;
                }

                Matcher generic = IMAGE_URL.matcher(body);
                while (generic.find()) {
                    String value = absolute(pageUrl, generic.group());
                    if (good(value)) return value;
                }
            }

            for (Element image : doc.select("img")) {
                String raw = firstNonEmpty(
                        image.attr("data-src"),
                        image.attr("data-original"),
                        image.attr("data-lazy-src"),
                        image.attr("src")
                );
                String value = absolute(pageUrl, raw);
                if (good(value)) return value;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static Document fetch(Context context, String url) throws Exception {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(SITE)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .timeout(15000)
                .maxBodySize(4 * 1024 * 1024)
                .followRedirects(true);

        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) {
                connection.header("Cookie", cookies);
            }
        } catch (Exception ignored) {
        }
        return connection.get();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String absolute(String pageUrl, String raw) {
        if (raw == null) return "";
        String value = raw.trim()
                .replace("&amp;", "&")
                .replace("\\/", "/")
                .replace("\\u0026", "&");
        if (value.isEmpty() || value.startsWith("data:")) return "";
        try {
            if (value.startsWith("//")) return "https:" + value;
            URI base = URI.create(pageUrl);
            return base.resolve(value).toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static boolean good(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.contains("logo") || lower.contains("sprite") || lower.contains("avatar")) return false;
        if (lower.contains("favicon") || lower.contains("placeholder") || lower.contains("blank.gif")) return false;
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
                lower.contains(".webp") || lower.contains(".avif") || lower.contains(".gif") ||
                lower.contains("image") || lower.contains("thumb") || lower.contains("poster");
    }
}
