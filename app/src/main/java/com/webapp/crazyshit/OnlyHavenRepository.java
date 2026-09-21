package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/** Creator search and mixed-media extraction for OnlyHaven (cum.st). */
final class OnlyHavenRepository {
    static final String BASE = "https://cum.st/";

    static final class Creator {
        final String service;
        final String id;
        final String name;
        final String url;
        final String imageUrl;

        Creator(String service, String id, String name, String url, String imageUrl) {
            this.service = cleanStatic(service);
            this.id = cleanStatic(id);
            this.name = cleanStatic(name);
            this.url = cleanStatic(url);
            this.imageUrl = cleanStatic(imageUrl);
        }
    }

    List<Creator> searchCreators(Context context, String query, int limit) throws IOException {
        SourceConfig.OnlyHaven config = config();
        if (!config.enabled) throw new IOException("OnlyHaven is temporarily unavailable");
        String value = query == null ? "" : query.trim();
        if (value.length() < 2) return new ArrayList<>();
        String encoded = URLEncoder.encode(value, "UTF-8").replace("+", "%20");

        ArrayList<String> routes = new ArrayList<>();
        routes.add(config.creatorSearchRoute.replace("{query}", encoded));
        routes.add("creators?q=" + encoded);
        routes.add("creators?query=" + encoded);

        IOException lastError = null;
        LinkedHashMap<String, Creator> creators = new LinkedHashMap<>();
        for (String route : routes) {
            try {
                Document document = fetchConfigured(context, config, config.baseUrl + route);
                parseCreators(document, config, creators, Math.max(1, limit));
                if (!creators.isEmpty()) break;
            } catch (IOException error) {
                lastError = error;
            }
        }
        if (creators.isEmpty() && lastError != null) throw lastError;
        return new ArrayList<>(creators.values());
    }

    List<NativeContentItem> fetchCreatorMedia(
            Context context,
            Creator creator,
            int page,
            int limit
    ) throws IOException {
        if (creator == null || creator.service.isEmpty() || creator.id.isEmpty()) {
            return new ArrayList<>();
        }
        SourceConfig.OnlyHaven config = config();
        if (!config.enabled) throw new IOException("OnlyHaven is temporarily unavailable");
        String route = config.creatorPageRoute
                .replace("{service}", urlToken(creator.service))
                .replace("{id}", urlToken(creator.id))
                .replace("{page}", String.valueOf(Math.max(1, page)));
        Document document = fetchConfigured(context, config, config.baseUrl + route);
        return parseMedia(document, config, creator, Math.max(1, limit));
    }

    CrazyShitRepository.StreamInfo resolvePlayable(Context context, String pageUrl)
            throws IOException {
        if (isDirectVideo(pageUrl)) {
            return new CrazyShitRepository.StreamInfo(pageUrl, BASE, "OnlyHaven");
        }
        SourceConfig.OnlyHaven config = config();
        if (!config.enabled) throw new IOException("OnlyHaven is temporarily unavailable");
        Document page = fetchConfigured(context, config, pageUrl);
        for (Element element : page.select(config.playableVideoSelector)) {
            String attr = element.hasAttr("src") ? "src" : "content";
            String candidate = absolute(element, attr, page.location());
            if (isDirectVideo(candidate)) {
                return new CrazyShitRepository.StreamInfo(candidate, pageUrl, clean(page.title()));
            }
        }
        String scriptMedia = scriptMedia(page, config, true);
        return scriptMedia.isEmpty()
                ? null
                : new CrazyShitRepository.StreamInfo(scriptMedia, pageUrl, clean(page.title()));
    }

    private void parseCreators(
            Document document,
            SourceConfig.OnlyHaven config,
            LinkedHashMap<String, Creator> output,
            int limit
    ) {
        for (Element link : document.select(config.creatorLinksSelector)) {
            String url = absolute(link, "href", document.location());
            Matcher matcher = config.creatorUrlPattern.matcher(url);
            if (!matcher.find() || matcher.groupCount() < 2) continue;
            String service = matcher.group(1);
            String id = matcher.group(2);
            Element scope = cardScope(link);
            String name = firstUseful(
                    link.attr("title"),
                    link.attr("aria-label"),
                    link.ownText(),
                    link.text(),
                    heading(scope),
                    id
            );
            String image = image(scope == null ? link : scope, document.location());
            String key = service.toLowerCase(Locale.US) + ":" + id;
            output.putIfAbsent(key, new Creator(service, id, name, url, image));
            if (output.size() >= limit) break;
        }
    }

    private List<NativeContentItem> parseMedia(
            Document document,
            SourceConfig.OnlyHaven config,
            Creator creator,
            int limit
    ) {
        LinkedHashMap<String, NativeContentItem> items = new LinkedHashMap<>();
        for (Element element : document.select(config.mediaLinksSelector)) {
            String candidate = "";
            if (element.hasAttr("href")) candidate = absolute(element, "href", document.location());
            if (candidate.isEmpty() && element.hasAttr("src")) {
                candidate = absolute(element, "src", document.location());
            }
            addDirect(items, candidate, element, creator, document.location());
            if (items.size() >= limit) break;

            if (candidate.contains("/posts/")) {
                Element scope = cardScope(element);
                if (scope != null) {
                    for (Element media : scope.select("video[src],video source[src],a[href],img[src]")) {
                        String direct = media.hasAttr("href")
                                ? absolute(media, "href", document.location())
                                : absolute(media, "src", document.location());
                        addDirect(items, direct, media, creator, document.location());
                        if (items.size() >= limit) break;
                    }
                }
            }
        }

        if (items.size() < limit) {
            for (Element script : document.select("script")) {
                String body = script.data();
                if (body == null || body.isEmpty()) body = script.html();
                if (body == null || body.isEmpty()) continue;
                body = body.replace("\\/", "/").replace("&amp;", "&");
                Matcher matcher = config.scriptMediaUrlPattern.matcher(body);
                while (matcher.find() && items.size() < limit) {
                    addDirect(items, matcher.group(), null, creator, document.location());
                }
            }
        }
        return new ArrayList<>(items.values());
    }

    private void addDirect(
            LinkedHashMap<String, NativeContentItem> items,
            String candidate,
            Element element,
            Creator creator,
            String base
    ) {
        String url = cleanUrl(candidate);
        boolean video = isDirectVideo(url);
        boolean image = isDirectImage(url);
        if (!video && !image) return;
        String preview = image ? url : "";
        if (video && element != null) {
            Element scope = cardScope(element);
            preview = image(scope == null ? element : scope, base);
        }
        String title = creator.name.isEmpty() ? "OnlyHaven media" : creator.name;
        NativeContentItem item = new NativeContentItem(
                video ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                title,
                url,
                preview,
                "OnlyHaven",
                creator.url,
                "",
                creator.service + " · OnlyHaven"
        );
        items.putIfAbsent(url, item);
    }

    private String scriptMedia(Document document, SourceConfig.OnlyHaven config, boolean videoOnly) {
        for (Element script : document.select("script")) {
            String body = script.data();
            if (body == null || body.isEmpty()) body = script.html();
            if (body == null || body.isEmpty()) continue;
            body = body.replace("\\/", "/").replace("&amp;", "&");
            Matcher matcher = config.scriptMediaUrlPattern.matcher(body);
            while (matcher.find()) {
                String candidate = cleanUrl(matcher.group());
                if (videoOnly ? isDirectVideo(candidate) : (isDirectVideo(candidate) || isDirectImage(candidate))) {
                    return candidate;
                }
            }
        }
        return "";
    }

    private Document fetchConfigured(
            Context context,
            SourceConfig.OnlyHaven config,
            String requested
    ) throws IOException {
        IOException failure = null;
        for (String candidate : candidates(config, requested)) {
            for (int attempt = 0; attempt <= config.retryCount; attempt++) {
                try {
                    Connection connection = Jsoup.connect(candidate)
                            .userAgent(config.userAgent)
                            .referrer(config.refererOverride.isEmpty() ? config.baseUrl : config.refererOverride)
                            .timeout(config.requestTimeoutMs)
                            .maxBodySize(12 * 1024 * 1024)
                            .followRedirects(true)
                            .ignoreHttpErrors(false);
                    for (Map.Entry<String, String> header : config.requestHeaders.entrySet()) {
                        connection.header(header.getKey(), header.getValue());
                    }
                    try {
                        String cookies = CookieManager.getInstance().getCookie(candidate);
                        if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
                    } catch (Exception ignored) {
                    }
                    return connection.get();
                } catch (IOException error) {
                    failure = error;
                }
            }
        }
        throw failure == null ? new IOException("OnlyHaven request failed") : failure;
    }

    private List<String> candidates(SourceConfig.OnlyHaven config, String requested) {
        ArrayList<String> values = new ArrayList<>();
        values.add(requested);
        if (!RemoteSourceConfigManager.snapshot().fallbacksEnabled) return values;
        try {
            URI requestedUri = new URI(requested);
            URI baseUri = new URI(config.baseUrl);
            if (!baseUri.getHost().equalsIgnoreCase(requestedUri.getHost())) return values;
            String suffix = requestedUri.getRawPath();
            if (requestedUri.getRawQuery() != null) suffix += "?" + requestedUri.getRawQuery();
            for (String fallback : config.fallbackDomains) {
                values.add(fallback + (suffix.startsWith("/") ? suffix.substring(1) : suffix));
            }
        } catch (Exception ignored) {
        }
        return values;
    }

    private SourceConfig.OnlyHaven config() {
        return RemoteSourceConfigManager.snapshot().onlyHaven;
    }

    private Element cardScope(Element element) {
        Element best = element;
        Element current = element;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            current = current.parent();
            if (current == null) break;
            String text = clean(current.text());
            boolean visual = !current.select("img,video,source").isEmpty();
            boolean heading = !current.select("h1,h2,h3,h4,h5,.title,.name,.username").isEmpty();
            if ((visual || heading) && text.length() < 1800) {
                best = current;
                if (visual && heading) break;
            }
        }
        return best;
    }

    private String heading(Element scope) {
        if (scope == null) return "";
        Element heading = scope.selectFirst("h1,h2,h3,h4,h5,.title,.name,.username");
        return heading == null ? "" : clean(heading.text());
    }

    private String image(Element root, String base) {
        if (root == null) return "";
        for (Element element : root.select("img[src],img[data-src],video[poster],meta[property=og:image][content]")) {
            String attr = element.hasAttr("poster") ? "poster"
                    : element.hasAttr("data-src") ? "data-src"
                    : element.hasAttr("src") ? "src" : "content";
            String candidate = absolute(element, attr, base);
            if (isDirectImage(candidate)) return candidate;
        }
        return "";
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            String candidate = clean(value);
            if (!candidate.isEmpty() && candidate.length() <= 180) return candidate;
        }
        return "";
    }

    private String absolute(Element element, String attr, String base) {
        if (element == null || !element.hasAttr(attr)) return "";
        String value = element.absUrl(attr);
        if (!value.isEmpty()) return cleanUrl(value);
        return absolute(base, element.attr(attr));
    }

    private String absolute(String base, String value) {
        String clean = cleanUrl(value);
        if (clean.isEmpty()) return "";
        try {
            return new URI(base).resolve(clean).toASCIIString();
        } catch (Exception ignored) {
            return clean;
        }
    }

    private String urlToken(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception ignored) {
            return value;
        }
    }

    static boolean isOnlyHavenUrl(String url) {
        try {
            String host = new URI(url == null ? "" : url).getHost();
            return host != null && (host.equalsIgnoreCase("cum.st") ||
                    host.toLowerCase(Locale.US).endsWith(".cum.st"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isDirectVideo(String url) {
        return cleanStatic(url).toLowerCase(Locale.US)
                .matches(".*\\.(?:mp4|m3u8|mpd|webm|m4v)(?:\\?.*)?$");
    }

    private static boolean isDirectImage(String url) {
        return cleanStatic(url).toLowerCase(Locale.US)
                .matches(".*\\.(?:jpg|jpeg|png|webp|gif|avif)(?:\\?.*)?$");
    }

    private String clean(String value) {
        return cleanStatic(value);
    }

    private String cleanUrl(String value) {
        return cleanStatic(value).replace("&amp;", "&").replace("\\/", "/");
    }

    private static String cleanStatic(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
