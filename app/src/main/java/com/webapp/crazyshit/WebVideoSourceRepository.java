package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared native feed and playback parser for lightweight public video sources. */
final class WebVideoSourceRepository {
    enum Source {
        KAOTIC("Kaotic"),
        THEYNC("TheYNC"),
        ITEMFIX("ItemFix");

        final String label;

        Source(String label) {
            this.label = label;
        }
    }

    private static final Pattern NUMBER = Pattern.compile(
            "(?i)(?:\\b\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?[KMB]?\\b|\\b\\d+(?:\\.\\d+)?[KMB]\\b)"
    );
    private static final Pattern KAOTIC_SECTION = Pattern.compile(
            "(?i)^(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s+"
                    + "(?:january|february|march|april|may|june|july|august|september|october|november|december)\\s+"
                    + "\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?$"
    );

    List<NativeContentItem> fetchFeed(Context context, Source source, int page) throws IOException {
        SourceConfig.WebVideo config = config(source);
        if (!config.enabled) throw new IOException(source.label + " is temporarily unavailable");
        String route = page <= 1 ? config.feedFirstRoute : config.feedPageRoute;
        route = route.replace("{page}", String.valueOf(Math.max(1, page)));
        String requested = config.baseUrl + route;

        Document document = null;
        IOException directFailure = null;
        try {
            document = fetchConfigured(context, config, requested);
        } catch (IOException error) {
            directFailure = error;
        }

        List<NativeContentItem> result = document == null
                ? new ArrayList<>()
                : parseFeed(document, source, page, config);

        if (source == Source.THEYNC && result.isEmpty()) {
            try {
                Document rendered = RenderedSourcePageFetcher.fetch(
                        context,
                        requested,
                        config.userAgent,
                        config.requestHeaders,
                        config.refererOverride.isEmpty() ? config.baseUrl : config.refererOverride,
                        "/video/"
                );
                result = parseFeed(rendered, source, page, config);
            } catch (IOException browserFailure) {
                if (directFailure == null) directFailure = browserFailure;
            }
        }

        if (page <= 1 && result.isEmpty()) {
            throw directFailure == null
                    ? new IOException(source.label + " returned no playable feed items")
                    : new IOException(source.label + " could not load its feed", directFailure);
        }
        return result;
    }

    List<NativeContentItem> parseFeed(Document document, Source source, int page) {
        return parseFeed(document, source, page, config(source));
    }

    private List<NativeContentItem> parseFeed(
            Document document,
            Source source,
            int page,
            SourceConfig.WebVideo config
    ) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        LinkedHashMap<String, Integer> positions = new LinkedHashMap<>();
        String pendingHeader = "";
        int sectionOrdinal = 0;
        int mediaCount = 0;

        for (Element node : document.getAllElements()) {
            String tag = node.tagName();
            if (!"a".equalsIgnoreCase(tag)) {
                if (source == Source.KAOTIC) {
                    String section = kaoticSection(node);
                    if (!section.isEmpty()) pendingHeader = section;
                } else if (source == Source.ITEMFIX && mediaCount > 0 &&
                        "popular fixes".equalsIgnoreCase(clean(node.ownText()))) {
                    break;
                }
                continue;
            }

            if (!node.is(config.cardLinksSelector)) continue;
            String pageUrl = absolute(node, "href", document.location());
            if (pageUrl.isEmpty() || !config.pageUrlPattern.matcher(pageUrl).find()) continue;

            Element scope = cardScope(node);
            String title = title(node, scope, pageUrl);
            if (title.length() < 2) continue;
            String image = image(node, scope, document.location());
            String views = views(scope);
            NativeContentItem candidate = new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    title,
                    pageUrl,
                    image,
                    views,
                    source.label,
                    ""
            );

            Integer oldPosition = positions.get(pageUrl);
            if (oldPosition != null) {
                result.set(oldPosition, result.get(oldPosition).merge(candidate));
                continue;
            }

            if (source == Source.KAOTIC && !pendingHeader.isEmpty()) {
                result.add(new NativeContentItem(
                        NativeContentItem.KIND_SECTION,
                        pendingHeader,
                        "section:kaotic:" + Math.max(1, page) + ":" + (++sectionOrdinal)
                                + ":" + slug(pendingHeader),
                        "",
                        "",
                        "",
                        ""
                ));
                pendingHeader = "";
            }

            positions.put(pageUrl, result.size());
            result.add(candidate);
            mediaCount++;
            if (mediaCount >= 60) break;
        }
        return result;
    }

    private String kaoticSection(Element element) {
        if (element == null) return "";
        String own = clean(element.ownText());
        if (KAOTIC_SECTION.matcher(own).matches()) return own;
        String tag = element.tagName().toLowerCase(Locale.US);
        if (tag.matches("h[1-6]")) {
            String text = clean(element.text());
            if (KAOTIC_SECTION.matcher(text).matches()) return text;
        }
        return "";
    }

    private String slug(String value) {
        String text = clean(value).toLowerCase(Locale.US).replace('’', '\'');
        text = text.replaceAll("[^a-z0-9]+", "-");
        return text.replaceAll("(^-+|-+$)", "");
    }

    CrazyShitRepository.StreamInfo resolvePlayable(
            Context context,
            Source source,
            String pageUrl
    ) throws IOException {
        if (isDirectMedia(pageUrl)) {
            return new CrazyShitRepository.StreamInfo(pageUrl, pageUrl, source.label);
        }
        SourceConfig.WebVideo config = config(source);
        if (!config.enabled) throw new IOException(source.label + " is temporarily unavailable");
        Document page;
        try {
            page = fetchConfigured(context, config, pageUrl);
        } catch (IOException error) {
            if (source != Source.THEYNC) throw error;
            page = RenderedSourcePageFetcher.fetch(
                    context,
                    pageUrl,
                    config.userAgent,
                    config.requestHeaders,
                    config.refererOverride.isEmpty() ? config.baseUrl : config.refererOverride,
                    "/video/"
            );
        }
        String title = clean(page.title());
        String media = playableFromDocument(page, config);
        if (media.isEmpty() && source == Source.THEYNC) {
            try {
                Document rendered = RenderedSourcePageFetcher.fetch(
                        context,
                        pageUrl,
                        config.userAgent,
                        config.requestHeaders,
                        config.refererOverride.isEmpty() ? config.baseUrl : config.refererOverride,
                        "/video/"
                );
                media = playableFromDocument(rendered, config);
                if (title.isEmpty()) title = clean(rendered.title());
            } catch (IOException ignored) {
            }
        }
        if (media.isEmpty()) {
            int checked = 0;
            for (Element frame : page.select("iframe[src]")) {
                if (++checked > 4) break;
                String frameUrl = absolute(frame, "src", page.location());
                if (!frameUrl.startsWith("https://")) continue;
                try {
                    Document framePage = fetchDocument(context, config, frameUrl, pageUrl);
                    media = playableFromDocument(framePage, config);
                    if (!media.isEmpty()) break;
                } catch (IOException ignored) {
                }
            }
        }
        return media.isEmpty()
                ? null
                : new CrazyShitRepository.StreamInfo(media, pageUrl, title.isEmpty() ? source.label : title);
    }

    private String playableFromDocument(Document document, SourceConfig.WebVideo config) {
        for (Element element : document.select(config.playableVideoSelector)) {
            String attr = element.hasAttr("src") ? "src" : "content";
            String candidate = absolute(element, attr, document.location());
            if (isDirectMedia(candidate)) return candidate;
        }
        for (Element script : document.select("script")) {
            String body = script.data();
            if (body == null || body.isEmpty()) body = script.html();
            if (body == null || body.isEmpty()) continue;
            body = body.replace("\\/", "/").replace("&amp;", "&");
            Matcher matcher = config.scriptMediaUrlPattern.matcher(body);
            while (matcher.find()) {
                String candidate = cleanUrl(matcher.group());
                if (isDirectMedia(candidate)) return candidate;
            }
        }
        return "";
    }

    private Document fetchConfigured(
            Context context,
            SourceConfig.WebVideo config,
            String requested
    ) throws IOException {
        IOException failure = null;
        for (String candidate : candidates(config, requested)) {
            for (int attempt = 0; attempt <= config.retryCount; attempt++) {
                try {
                    return fetchDocument(context, config, candidate, config.baseUrl);
                } catch (IOException error) {
                    failure = error;
                }
            }
        }
        throw failure == null ? new IOException("Source request failed") : failure;
    }

    private Document fetchDocument(
            Context context,
            SourceConfig.WebVideo config,
            String url,
            String referer
    ) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(config.userAgent)
                .referrer(config.refererOverride.isEmpty() ? referer : config.refererOverride)
                .timeout(config.requestTimeoutMs)
                .maxBodySize(8 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(false);
        for (Map.Entry<String, String> header : config.requestHeaders.entrySet()) {
            connection.header(header.getKey(), header.getValue());
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return connection.get();
    }

    private List<String> candidates(SourceConfig.WebVideo config, String requested) {
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

    private SourceConfig.WebVideo config(Source source) {
        SourceConfig current = RemoteSourceConfigManager.snapshot();
        if (source == Source.KAOTIC) return current.kaotic;
        if (source == Source.THEYNC) return current.theYnc;
        return current.itemFix;
    }

    private Element cardScope(Element link) {
        Element best = link;
        Element current = link;
        for (int depth = 0; depth < 7 && current != null; depth++) {
            current = current.parent();
            if (current == null) break;
            String text = clean(current.text());
            boolean visual = !current.select("img,video[poster],source,[data-src],[data-lazy-src]").isEmpty();
            boolean heading = !current.select("h1,h2,h3,h4,h5,.title,.post-title,.video-title").isEmpty();
            if ((visual || heading) && text.length() > 2 && text.length() < 1500) {
                best = current;
                if (visual && heading) break;
            }
        }
        return best;
    }

    private String title(Element link, Element scope, String pageUrl) {
        String[] direct = {link.attr("title"), link.attr("aria-label"), link.ownText(), link.text()};
        for (String value : direct) {
            String candidate = clean(value);
            if (usefulTitle(candidate)) return candidate;
        }
        if (scope != null) {
            for (Element heading : scope.select("h1,h2,h3,h4,h5,.title,.post-title,.video-title")) {
                String candidate = clean(heading.text());
                if (usefulTitle(candidate)) return candidate;
            }
            Element image = scope.selectFirst("img[alt]");
            if (image != null && usefulTitle(clean(image.attr("alt")))) return clean(image.attr("alt"));
        }
        try {
            String path = new URI(pageUrl).getPath();
            if (path != null) {
                String[] pieces = path.split("/");
                for (int index = pieces.length - 1; index >= 0; index--) {
                    String part = pieces[index].replace('-', ' ').replace('_', ' ').trim();
                    if (part.length() >= 3 && !part.matches("\\d+")) return part;
                }
            }
        } catch (Exception ignored) {
        }
        return "Video";
    }

    private String image(Element link, Element scope, String base) {
        Element[] roots = scope == null ? new Element[]{link} : new Element[]{link, scope};
        for (Element root : roots) {
            for (Element visual : root.select("img,video[poster],source,[data-src],[data-lazy-src],[data-original]")) {
                String[] attrs = {"poster", "data-src", "data-lazy-src", "data-original", "src"};
                for (String attr : attrs) {
                    String candidate = absolute(visual, attr, base);
                    if (looksImage(candidate)) return candidate;
                }
                String srcset = visual.attr("srcset");
                if (!srcset.isEmpty()) {
                    String first = srcset.split(",")[0].trim().split("\\s+")[0];
                    String candidate = absolute(base, first);
                    if (looksImage(candidate)) return candidate;
                }
            }
        }
        return "";
    }

    private String views(Element scope) {
        if (scope == null) return "";
        String text = clean(scope.text());
        Matcher matcher = Pattern.compile("(?i)([0-9][0-9,.]*[KMB]?)\\s+views?").matcher(text);
        if (matcher.find()) return matcher.group(1);
        matcher = NUMBER.matcher(text);
        return matcher.find() && text.toLowerCase(Locale.US).contains("view") ? matcher.group() : "";
    }

    private boolean usefulTitle(String value) {
        if (value == null || value.length() < 2 || value.length() > 240) return false;
        String lower = value.toLowerCase(Locale.US);
        return !(lower.equals("video") || lower.equals("watch") || lower.equals("play") ||
                lower.equals("read more") || lower.equals("view"));
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

    private boolean looksImage(String url) {
        String value = cleanUrl(url).toLowerCase(Locale.US);
        return value.matches(".*\\.(?:jpg|jpeg|png|webp|gif|avif)(?:\\?.*)?$");
    }

    private boolean isDirectMedia(String url) {
        String value = cleanUrl(url).toLowerCase(Locale.US);
        return value.matches(".*\\.(?:mp4|m3u8|mpd|webm|m4v)(?:\\?.*)?$");
    }

    static boolean isKaoticUrl(String url) {
        return hostMatches(url, "kaotic.com");
    }

    static boolean isTheYncUrl(String url) {
        return hostMatches(url, "theync.com");
    }

    static boolean isItemFixUrl(String url) {
        return hostMatches(url, "itemfix.com");
    }

    private static boolean hostMatches(String url, String host) {
        try {
            String actual = new URI(url == null ? "" : url).getHost();
            return actual != null && (actual.equalsIgnoreCase(host) ||
                    actual.toLowerCase(Locale.US).endsWith("." + host));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String cleanUrl(String value) {
        if (value == null) return "";
        return value.trim().replace("&amp;", "&").replace("\\/", "/");
    }
}
