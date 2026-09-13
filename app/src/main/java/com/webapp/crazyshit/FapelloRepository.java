package com.webapp.crazyshit;

import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fapello model search, paged mixed-media feeds and original media resolution. */
final class FapelloRepository {
    static final String BASE = "https://fapello.com/";
    static final String LIST_NEW = "new";
    static final String LIST_HOT = "hot";
    static final String LIST_POPULAR = "popular";
    private static final int MODEL_PAGE_SIZE = 30;

    static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final Pattern POST_PATH = Pattern.compile(
            "(?i)^/(?:video/(?:[^/]+/)?[0-9]+|[^/]+/[0-9]+)/?$"
    );
    private static final Pattern POST_ID = Pattern.compile("(?i)/(?:new/)?([0-9]+)/?$");
    private static final Pattern CONTENT_URL = Pattern.compile(
            "(?is)[\\\"']contentUrl[\\\"']\\s*:\\s*[\\\"'](https?://[^\\\"']+)[\\\"']"
    );
    private static final Pattern DIRECT_MEDIA = Pattern.compile(
            "(?i)\\.(?:jpe?g|png|webp|avif|gif|bmp|heic|heif|mp4|webm|m4v|mov|m3u8|mpd)(?:$|[?#])"
    );
    private static final Pattern VIDEO_MEDIA = Pattern.compile(
            "(?i)\\.(?:mp4|webm|m4v|mov|m3u8|mpd)(?:$|[?#])"
    );
    private static final Pattern CREATOR_LINK_TEXT = Pattern.compile(
            "(?i)^(?:see|view)\\s+(?:all\\s+)?content\\s+(?:of|from)\\s+(.+)$"
    );
    private static final Pattern MEDIA_COUNT = Pattern.compile("(?i)\\b([0-9][0-9,]*)\\s+media\\b");
    private static final Pattern NEXT_PAGE = Pattern.compile("(?i)(?:^|/)page-([0-9]+)/?$");
    private static final Pattern SCRIPT_MEDIA_URL = Pattern.compile(
            "(?is)(?:https?:)?(?:\\\\/|/){2}[^\\s\\\"'<>]+?\\.(?:jpe?g|png|webp|avif|gif|mp4|webm|m4v|mov|m3u8|mpd)(?:\\?[^\\s\\\"'<>]*)?"
    );
    private static final Pattern BLOCKED_PAGE = Pattern.compile(
            "(?is)(?:cdn-cgi/challenge-platform|cf-chl-|checking your browser|verify you are human|"
                    + "attention required[^<]{0,80}cloudflare|just a moment[^<]{0,120}cloudflare|"
                    + "cloudflare[^<]{0,120}(?:blocked|forbidden|challenge))"
    );
    private static final Pattern RESERVED_MODEL_SLUG = Pattern.compile(
            "(?i)^(?:search|search_v2|s|new|hot|videos|trending|popular|ajax|video|welcome|"
                    + "login|signup|sign-up|tags|random|forum|report|dmca|contacts|language|"
                    + "privacy|terms|add-model|upload|posts|comments|recent-comments|2257|"
                    + "what-is-fapello|daily-search-ranking|popular-videos|popular_videos|"
                    + "video-player|forgot-password)$"
    );

    List<Model> searchModels(Context context, String query, int limit) throws IOException {
        String cleanQuery = clean(query);
        if (cleanQuery.isEmpty()) return new ArrayList<>();
        int safeLimit = Math.max(1, Math.min(8, limit));
        String endpoint = searchUrl(cleanQuery, safeLimit);
        return limitModels(parseSearchResponse(fetchBody(context, endpoint, BASE, true)), safeLimit);
    }

    List<Model> searchConfirmedModels(Context context, String query, int limit) throws IOException {
        return searchModels(context, query, limit);
    }

    static String searchUrl(String query, int limit) throws IOException {
        String cleanQuery = cleanStatic(query);
        int safeLimit = Math.max(1, Math.min(8, limit));
        if (cleanQuery.isEmpty()) throw new IOException("Fapello creator name was missing");
        return BASE + "search_v2/?ajax=1&q=" + encodeStatic(cleanQuery) +
                "&type=models&limit=" + safeLimit + "&offset=0";
    }

    List<Model> parseSearchResponse(String body) throws IOException {
        String value = body == null ? "" : body.trim();
        if (value.isEmpty()) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.MALFORMED,
                    "Fapello search returned an empty response"
            );
        }
        if (looksBlocked(value)) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.BLOCKED,
                    403,
                    "Fapello search returned a Cloudflare page"
            );
        }
        try {
            Object root = new org.json.JSONTokener(value).nextValue();
            if (!(root instanceof JSONObject) && !(root instanceof JSONArray)) {
                throw new FapelloSourceException(
                        FapelloSourceException.Reason.MALFORMED,
                        "Fapello search did not return a JSON object or array"
                );
            }
            LinkedHashMap<String, Model> models = new LinkedHashMap<>();
            collectSearchModels(root, models, 0);
            return new ArrayList<>(models.values());
        } catch (FapelloSourceException error) {
            throw error;
        } catch (Exception error) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.MALFORMED,
                    "Fapello search response was invalid",
                    error
            );
        }
    }

    private void collectSearchModels(Object value, LinkedHashMap<String, Model> models, int depth)
            throws IOException {
        if (value == null || depth > 5 || models.size() >= 40) return;
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            for (int index = 0; index < values.length(); index++) {
                collectSearchModels(values.opt(index), models, depth + 1);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        String url = normalizeUrl(first(object, "url", "profile_url", "profileUrl", "href"), BASE);
        String name = clean(first(object, "name", "title", "username", "model"));
        if (isModelUrl(url) && !name.isEmpty()) {
            models.putIfAbsent(canonicalKey(url), new Model(name, canonicalModelUrl(url), firstJsonImage(object)));
        }
        for (String key : new String[]{"results", "models", "items", "data", "suggestions"}) {
            collectSearchModels(object.opt(key), models, depth + 1);
        }
    }

    private List<Model> limitModels(List<Model> models, int limit) {
        if (models == null || models.size() <= limit) return models == null ? new ArrayList<>() : models;
        return new ArrayList<>(models.subList(0, limit));
    }

    List<Model> fetchModelListing(Context context, String listing, int page) throws IOException {
        String safeListing = normalizeListing(listing);
        int safePage = Math.max(1, page);
        String listingRoot = LIST_NEW.equals(safeListing) ? BASE : BASE + safeListing + "/";
        String endpoint = listingUrl(safeListing, safePage);
        Document document = fetchDocument(context, endpoint, listingRoot);
        List<Model> models = parseModelListing(document, endpoint);
        if (models.isEmpty() && looksLikeListingWithCreators(document)) {
            document = fetchRenderedDocument(context, endpoint, listingRoot);
            models = parseModelListing(document, endpoint);
            if (models.isEmpty() && looksLikeListingWithCreators(document)) {
                throw new FapelloSourceException(
                        FapelloSourceException.Reason.PARSER,
                        "Fapello creator cards no longer match the parser"
                );
            }
        }
        return models;
    }

    List<Model> parseModelListing(Document document, String endpoint) {
        LinkedHashMap<String, Model> models = new LinkedHashMap<>();
        if (document == null) return new ArrayList<>();

        for (Element link : document.select("a[href]")) {
            String text = clean(link.text());
            Matcher creatorText = CREATOR_LINK_TEXT.matcher(text);
            if (!creatorText.matches()) continue;
            String name = clean(creatorText.group(1));
            String url = normalizeUrl(link.attr("href"), endpoint);
            if (name.isEmpty() || !isModelUrl(url)) continue;
            models.putIfAbsent(canonicalKey(url),
                    new Model(name, canonicalModelUrl(url), listingImage(link, endpoint)));
        }

        // Current listing pages include navigation and legal links alongside creator cards.
        // Only use the broad legacy fallback when no explicit creator card was found. Running
        // it as a supplement admits routes such as /upload/, /posts/ and /2257/ as creators.
        if (models.isEmpty()) {
            LinkedHashMap<String, List<Element>> profileLinks = new LinkedHashMap<>();
            for (Element link : document.select("a[href]")) {
                String url = normalizeUrl(link.attr("href"), endpoint);
                if (!isModelUrl(url)) continue;
                profileLinks.computeIfAbsent(canonicalKey(url), ignored -> new ArrayList<>()).add(link);
            }
            for (Map.Entry<String, List<Element>> entry : profileLinks.entrySet()) {
                List<Element> links = entry.getValue();
                String name = fallbackCreatorName(links);
                String image = fallbackCreatorImage(links, endpoint);
                if (name.isEmpty() || (links.size() < 2 && image.isEmpty())) continue;
                String url = canonicalModelUrl(normalizeUrl(links.get(0).attr("href"), endpoint));
                models.put(entry.getKey(), new Model(name, url, image));
            }
        }
        return new ArrayList<>(models.values());
    }

    static String listingUrl(String listing, int page) throws IOException {
        String safeListing = normalizeListingValue(listing);
        int safePage = Math.max(1, page);
        if (LIST_NEW.equals(safeListing)) {
            return BASE + (safePage > 1 ? "page-" + safePage + "/" : "");
        }
        return BASE + safeListing + (safePage > 1 ? "-" + safePage : "") + "/";
    }

    List<NativeContentItem> fetchModelMedia(Context context, Model model, int page)
            throws IOException {
        return fetchModelMediaPage(context, model, page).items;
    }

    MediaPage fetchModelMediaPage(Context context, Model model, int page) throws IOException {
        if (model == null || !isModelUrl(model.url)) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.MALFORMED,
                    "Fapello model page was missing"
            );
        }
        String slug = modelSlug(model.url);
        if (slug.isEmpty()) throw new FapelloSourceException(
                FapelloSourceException.Reason.MALFORMED,
                "Fapello model name was missing"
        );
        int safePage = Math.max(1, page);
        String endpoint = modelMediaUrl(model.url, safePage);
        Document document;
        try {
            document = fetchDocument(context, endpoint, model.url);
        } catch (FapelloSourceException error) {
            if (safePage != 1 || (error.reason != FapelloSourceException.Reason.NOT_FOUND &&
                    error.reason != FapelloSourceException.Reason.MALFORMED)) throw error;
            endpoint = modelProfilePageUrl(model.url, safePage);
            document = fetchDocument(context, endpoint, model.url);
        }
        MediaPage parsed = parseModelMedia(document, model, safePage, endpoint);
        if (safePage == 1 && parsed.items.isEmpty() && endpoint.contains("/ajax/model/")) {
            endpoint = modelProfilePageUrl(model.url, safePage);
            document = fetchDocument(context, endpoint, model.url);
            parsed = parseModelMedia(document, model, safePage, endpoint);
        }
        if (parsed.items.isEmpty() && hasMediaEvidence(document)) {
            document = fetchRenderedDocument(context, endpoint, model.url);
            parsed = parseModelMedia(document, model, safePage, endpoint);
            if (parsed.items.isEmpty() && hasMediaEvidence(document)) {
                throw new FapelloSourceException(
                        FapelloSourceException.Reason.PARSER,
                        "Fapello creator page reported media but no supported entries were parsed"
                );
            }
        }
        validateParsedModelPage(document, parsed, safePage);
        return parsed;
    }

    void validateParsedModelPage(Document document, MediaPage page, int pageNumber)
            throws FapelloSourceException {
        if (page != null && !page.items.isEmpty()) return;
        if (Math.max(1, pageNumber) > 1) return;
        String text = clean(document == null ? "" : document.text()).toLowerCase(Locale.US);
        boolean explicitEmpty = text.matches("(?s).*\\b0\\s+media\\b.*") ||
                text.contains("no media") || text.contains("no content") ||
                text.contains("nothing was found");
        if (!explicitEmpty) throw new FapelloSourceException(
                FapelloSourceException.Reason.PARSER,
                "Fapello creator page did not contain readable media or an empty result"
        );
    }

    static String modelMediaUrl(String modelUrl, int page) throws IOException {
        String canonical = canonicalModelUrl(modelUrl);
        if (!isModelUrl(canonical)) throw new IOException("Fapello model page was invalid");
        return BASE + "ajax/model/" + modelSlug(canonical) + "/page-" +
                Math.max(1, page) + "/";
    }

    static String modelProfilePageUrl(String modelUrl, int page) throws IOException {
        String canonical = canonicalModelUrl(modelUrl);
        if (!isModelUrl(canonical)) throw new IOException("Fapello model page was invalid");
        int safePage = Math.max(1, page);
        return safePage == 1 ? canonical : canonical + "page-" + safePage + "/";
    }

    MediaPage parseModelMedia(Document document, Model model, int page, String endpoint) {
        LinkedHashMap<String, NativeContentItem> items = new LinkedHashMap<>();
        if (document == null) return new MediaPage(new ArrayList<>(), false, "");
        String resolvedEndpoint = clean(document.location()).isEmpty() ? endpoint : document.location();
        int postCardCount = 0;

        for (Element element : document.select("a[href],a[data-href],a[data-url],[data-post],[data-permalink]")) {
            String pageUrl = firstNormalizedUrl(
                    element,
                    resolvedEndpoint,
                    "href",
                    "data-href",
                    "data-url",
                    "data-post",
                    "data-permalink"
            );
            if (!isPostUrl(pageUrl) && !isDirectMedia(pageUrl)) continue;
            if (isPostUrl(pageUrl)) postCardCount++;
            String thumbnail = imageFrom(element, resolvedEndpoint);
            if (thumbnail.isEmpty() && isImageUrl(pageUrl)) thumbnail = pageUrl;
            putMedia(items, mediaItem(model, pageUrl, thumbnail,
                    isVideoCard(element, pageUrl), modelSlug(model.url)));
        }

        for (Element video : document.select("video,source")) {
            String mediaUrl = firstNormalizedUrl(video, resolvedEndpoint, "src", "data-src", "data-url");
            if (!isDirectMedia(mediaUrl)) continue;
            Element owner = "source".equalsIgnoreCase(video.tagName()) ? video.parent() : video;
            String poster = owner == null ? "" : normalizeUrl(owner.attr("poster"), resolvedEndpoint);
            if (poster.isEmpty() && owner != null) poster = imageFrom(owner, resolvedEndpoint);
            putMedia(items, mediaItem(model, maximizeMediaUrl(mediaUrl), poster, true, modelSlug(model.url)));
        }

        for (Element image : document.select("img")) {
            boolean explicitMedia = image.hasAttr("data-full") || image.hasAttr("data-original") ||
                    image.hasAttr("data-url") || image.hasAttr("data-src") ||
                    image.hasAttr("data-lazy-src");
            if (!explicitMedia) continue;
            String preview = imageAttribute(image, resolvedEndpoint);
            if (!isImageUrl(preview) || isPlaceholder(preview)) continue;
            String full = firstNormalizedUrl(image, resolvedEndpoint,
                    "data-full", "data-original", "data-url", "data-src", "src");
            full = maximizeMediaUrl(full);
            if (!isImageUrl(full)) continue;
            Element link = image.closest("a");
            String ownerUrl = link == null ? "" : firstNormalizedUrl(
                    link, resolvedEndpoint, "href", "data-href", "data-url");
            if (isPostUrl(ownerUrl)) continue;
            putMedia(items, mediaItem(model, full, preview, false, modelSlug(model.url)));
        }

        collectStructuredMedia(document, model, resolvedEndpoint, items);
        collectScriptMedia(document, model, resolvedEndpoint, items);

        String next = nextPageUrl(document, model.url, page, resolvedEndpoint);
        boolean ajaxHasNext = resolvedEndpoint.contains("/ajax/model/") &&
                postCardCount >= MODEL_PAGE_SIZE;
        if (next.isEmpty() && ajaxHasNext) next = modelMediaUrlUnchecked(model.url, page + 1);
        return new MediaPage(new ArrayList<>(items.values()), !next.isEmpty(), next);
    }

    private NativeContentItem mediaItem(
            Model model,
            String pageUrl,
            String thumbnail,
            boolean video,
            String slug
    ) {
        String id = postId(pageUrl);
        String title = clean(model == null ? "" : model.name);
        if (title.isEmpty()) title = humanizeSlug(slug);
        if (!id.isEmpty()) title += " #" + id;
        return new NativeContentItem(
                video ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                title,
                pageUrl,
                thumbnail,
                "Fapello",
                model == null ? "" : model.url,
                "",
                "Fapello"
        );
    }

    private void putMedia(
            LinkedHashMap<String, NativeContentItem> items,
            NativeContentItem candidate
    ) {
        if (candidate == null || clean(candidate.url).isEmpty()) return;
        String key = canonicalKey(candidate.url);
        NativeContentItem current = items.get(key);
        if (current == null ||
                (candidate.isVideo() && !current.isVideo()) ||
                (clean(current.imageUrl).isEmpty() && !clean(candidate.imageUrl).isEmpty())) {
            items.put(key, candidate);
        }
    }

    private void collectStructuredMedia(
            Document document,
            Model model,
            String endpoint,
            LinkedHashMap<String, NativeContentItem> items
    ) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                Object value = new org.json.JSONTokener(script.data()).nextValue();
                collectStructuredMediaValue(value, model, endpoint, items, 0);
            } catch (Exception ignored) {
            }
        }
    }

    private void collectStructuredMediaValue(
            Object value,
            Model model,
            String endpoint,
            LinkedHashMap<String, NativeContentItem> items,
            int depth
    ) {
        if (value == null || depth > 7 || items.size() >= 500) return;
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            for (int index = 0; index < values.length(); index++) {
                collectStructuredMediaValue(values.opt(index), model, endpoint, items, depth + 1);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        String type = clean(object.optString("@type")).toLowerCase(Locale.US);
        String content = normalizeUrl(first(object, "contentUrl", "content_url", "embedUrl"), endpoint);
        String page = normalizeUrl(first(object, "url", "mainEntityOfPage"), endpoint);
        String thumbnail = jsonUrl(object.opt("thumbnailUrl"), endpoint);
        if (thumbnail.isEmpty()) thumbnail = jsonUrl(object.opt("thumbnail"), endpoint);
        String target = isPostUrl(page) ? page : isDirectMedia(content) ? content : "";
        if (!target.isEmpty()) {
            boolean video = type.contains("video") || isVideoUrl(content) || isVideoUrl(target);
            putMedia(items, mediaItem(model, target, thumbnail, video, modelSlug(model.url)));
        }
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            collectStructuredMediaValue(
                    object.opt(keys.next()), model, endpoint, items, depth + 1);
        }
    }

    private void collectScriptMedia(
            Document document,
            Model model,
            String endpoint,
            LinkedHashMap<String, NativeContentItem> items
    ) {
        for (Element script : document.select("script:not([type=application/ld+json])")) {
            String data = script.data();
            if (data.isEmpty() || data.length() > 2_000_000) continue;
            Matcher matcher = SCRIPT_MEDIA_URL.matcher(data);
            int found = 0;
            while (matcher.find() && found++ < 500) {
                String url = normalizeUrl(matcher.group(), endpoint);
                if (!isDirectMedia(url) || isPlaceholder(url)) continue;
                boolean video = isVideoUrl(url);
                putMedia(items, mediaItem(model, maximizeMediaUrl(url),
                        video ? "" : url, video, modelSlug(model.url)));
            }
        }
    }

    List<NativeContentItem> fetchPopularVideos(Context context, int page) throws IOException {
        int safePage = Math.max(1, page);
        String endpoint = popularVideosUrl(safePage);
        Document document = fetchDocument(context, endpoint, BASE + "popular_videos/week/");
        return parsePopularVideos(document, endpoint);
    }

    static String popularVideosUrl(int page) {
        return BASE + "popular_videos/week/" + (page > 1 ? "page-" + page + "/" : "");
    }

    List<NativeContentItem> parsePopularVideos(Document document, String endpoint) {
        LinkedHashMap<String, NativeContentItem> items = new LinkedHashMap<>();
        boolean hasVideoRoutes = document.select("a[href]").stream().anyMatch(link -> {
            String url = normalizeUrl(link.attr("href"), endpoint);
            return isPostUrl(url) && url.contains("/video/");
        });
        for (Element link : document.select("a[href]")) {
            String pageUrl = normalizeUrl(link.attr("href"), endpoint);
            if (!isPostUrl(pageUrl)) continue;
            if (hasVideoRoutes && !pageUrl.contains("/video/")) continue;
            String thumbnail = imageFrom(link, endpoint);
            String id = postId(pageUrl);
            String title = clean(link.attr("title"));
            if (title.isEmpty()) title = id.isEmpty() ? "Fapzone video" : "Fapzone video #" + id;
            NativeContentItem candidate = new NativeContentItem(
                    NativeContentItem.KIND_MEDIA, title, pageUrl, thumbnail,
                    "", "", "", "Fapzone");
            NativeContentItem existing = items.get(pageUrl);
            if (existing == null || (existing.imageUrl.isEmpty() && !thumbnail.isEmpty())) {
                items.put(pageUrl, candidate);
            }
        }
        return new ArrayList<>(items.values());
    }

    CrazyShitRepository.StreamInfo resolvePlayable(Context context, String pageUrl)
            throws IOException {
        String canonical = normalizeUrl(pageUrl, BASE);
        if (isDirectMedia(canonical)) {
            return new CrazyShitRepository.StreamInfo(
                    maximizeMediaUrl(canonical),
                    pageUrl,
                    fileTitle(canonical),
                    BASE
            );
        }
        if (!isPostUrl(canonical)) throw new IOException("Not a Fapello media page");

        Document document = fetchDocument(context, canonical, BASE);
        try {
            return parsePlayable(document, canonical);
        } catch (FapelloSourceException parserError) {
            if (parserError.reason != FapelloSourceException.Reason.PARSER) throw parserError;
            document = fetchRenderedDocument(context, canonical, BASE);
            return parsePlayable(document, canonical);
        }
    }

    CrazyShitRepository.StreamInfo parsePlayable(Document document, String canonical)
            throws IOException {
        if (document == null) throw new FapelloSourceException(
                FapelloSourceException.Reason.MALFORMED,
                "Fapello returned no media page"
        );
        String resolvedPage = document.location().isEmpty() ? canonical : document.location();
        String title = clean(document.selectFirst("h1,h2") == null
                ? document.title()
                : document.selectFirst("h1,h2").text());

        String mediaUrl = attribute(document, "meta[property=og:video][content]", "content");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "meta[property=og:video:url][content]", "content");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "meta[property=og:video:secure_url][content]", "content");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "meta[name=twitter:player:stream][content]", "content");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "video source[src]", "src");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "video source[data-src]", "data-src");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "video[src]", "src");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "video[data-src]", "data-src");
        if (mediaUrl.isEmpty()) {
            Matcher content = CONTENT_URL.matcher(document.html());
            if (content.find()) mediaUrl = content.group(1);
        }
        mediaUrl = maximizeMediaUrl(normalizeUrl(mediaUrl, resolvedPage));

        if (mediaUrl.isEmpty()) {
            Element centered = document.selectFirst(".uk-align-center");
            if (centered != null) {
                mediaUrl = "img".equalsIgnoreCase(centered.tagName())
                        ? imageAttribute(centered, resolvedPage)
                        : imageFrom(centered, resolvedPage);
            }
        }
        if (mediaUrl.isEmpty()) {
            mediaUrl = normalizeUrl(
                    attribute(document, "meta[property=og:image][content]", "content"),
                    resolvedPage
            );
        }
        mediaUrl = maximizeMediaUrl(mediaUrl);
        if (!isDirectMedia(mediaUrl)) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.PARSER,
                    "Fapello did not return a supported media URL"
            );
        }
        return new CrazyShitRepository.StreamInfo(
                mediaUrl,
                resolvedPage,
                title,
                resolvedPage
        );
    }

    static boolean isFapelloUrl(String value) {
        String host = host(value);
        return host.equals("fapello.com") || host.endsWith(".fapello.com");
    }

    static boolean isModelUrl(String value) {
        if (!isFapelloUrl(value)) return false;
        try {
            String path = new URI(value).getPath();
            if (path == null) return false;
            String[] parts = path.replaceAll("^/+|/+$", "").split("/");
            if (parts.length != 1 || parts[0].isEmpty()) return false;
            String lower = parts[0].toLowerCase(Locale.US);
            return !RESERVED_MODEL_SLUG.matcher(lower).matches() && !lower.startsWith("top-");
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isPostUrl(String value) {
        if (!isFapelloUrl(value)) return false;
        try {
            String path = new URI(value).getPath();
            return path != null && POST_PATH.matcher(path).matches();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String fetchBody(Context context, String url, String referer, boolean json)
            throws IOException {
        try {
            Connection connection = connection(context, url, referer)
                    .ignoreContentType(true)
                    .header("Accept", json
                            ? "application/json,text/javascript,*/*;q=0.8"
                            : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            if (json) {
                connection.timeout(8000)
                        .maxBodySize(1024 * 1024)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://fapello.com")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors");
            }
            Connection.Response response = execute(connection, url);
            String body = response.body();
            validateResponse(
                    response.statusCode(),
                    response.contentType(),
                    response.header("Server"),
                    response.header("CF-Ray"),
                    body,
                    json
            );
            persistCookies(response);
            String finalUrl = response.url() == null ? url : response.url().toString();
            if (!isFapelloUrl(finalUrl)) throw new FapelloSourceException(
                    FapelloSourceException.Reason.MALFORMED,
                    "Fapello search redirected to an unrelated host"
            );
            return body;
        } catch (FapelloSourceException error) {
            if (!browserRetryAllowed(error)) throw error;
            FapelloWebViewFetcher.Page rendered = FapelloWebViewFetcher.fetch(context, url, referer);
            String body = rendered.bodyText.trim();
            validateResponse(200, json ? "application/json" : "text/html", "WebView", "", body, json);
            return body;
        }
    }

    private Document fetchDocument(Context context, String url, String referer) throws IOException {
        try {
            Connection.Response response = execute(
                    connection(context, url, referer)
                            .header(
                                    "Accept",
                                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                            )
                            .header("Upgrade-Insecure-Requests", "1")
                            .header("Sec-Fetch-Dest", "document")
                            .header("Sec-Fetch-Mode", "navigate"),
                    url
            );
            String body = response.body();
            validateResponse(
                    response.statusCode(),
                    response.contentType(),
                    response.header("Server"),
                    response.header("CF-Ray"),
                    body,
                    false
            );
            persistCookies(response);
            String finalUrl = response.url() == null ? url : response.url().toString();
            if (!isFapelloUrl(finalUrl)) throw new FapelloSourceException(
                    FapelloSourceException.Reason.MALFORMED,
                    "Fapello redirected to an unrelated host"
            );
            return Jsoup.parse(body, finalUrl);
        } catch (FapelloSourceException error) {
            if (!browserRetryAllowed(error)) throw error;
            return fetchRenderedDocument(context, url, referer);
        }
    }

    private Document fetchRenderedDocument(Context context, String url, String referer)
            throws IOException {
        FapelloWebViewFetcher.Page rendered = FapelloWebViewFetcher.fetch(context, url, referer);
        validateResponse(200, "text/html", "WebView", "", rendered.bodyText, false);
        if (!isFapelloUrl(rendered.finalUrl)) throw new FapelloSourceException(
                FapelloSourceException.Reason.MALFORMED,
                "Fapello browser redirected to an unrelated host"
        );
        return rendered.document;
    }

    private Connection.Response execute(Connection connection, String url) throws IOException {
        try {
            Connection.Response response = connection.execute();
            if (BuildConfig.DEBUG) {
                String path = "/";
                try {
                    URI uri = new URI(url);
                    path = uri.getPath() == null ? "/" : uri.getPath();
                } catch (Exception ignored) {
                }
                Log.d(
                        "FapelloSource",
                        "status=" + response.statusCode() + " path=" + path +
                                " type=" + clean(response.contentType()) +
                                " bytes=" + response.bodyAsBytes().length
                );
            }
            return response;
        } catch (FapelloSourceException error) {
            throw error;
        } catch (IOException error) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.NETWORK,
                    "Fapello network request failed",
                    error
            );
        }
    }

    static void validateResponse(
            int status,
            String contentType,
            String server,
            String cfRay,
            String body,
            boolean expectJson
    ) throws FapelloSourceException {
        String value = body == null ? "" : body.trim();
        boolean blocked = looksBlocked(value) ||
                !cleanStatic(cfRay).isEmpty() && status == 403 ||
                cleanStatic(server).toLowerCase(Locale.US).contains("cloudflare") && status == 403;
        if (status == 429) throw new FapelloSourceException(
                FapelloSourceException.Reason.RATE_LIMITED,
                status,
                "Fapello returned HTTP 429"
        );
        if (status == 404) throw new FapelloSourceException(
                FapelloSourceException.Reason.NOT_FOUND,
                status,
                "Fapello returned HTTP 404"
        );
        if (blocked) throw new FapelloSourceException(
                FapelloSourceException.Reason.BLOCKED,
                status == 0 ? 403 : status,
                "Fapello returned a Cloudflare block"
        );
        if (status >= 400) throw new FapelloSourceException(
                FapelloSourceException.Reason.HTTP,
                status,
                "Fapello returned HTTP " + status
        );
        if (value.isEmpty()) throw new FapelloSourceException(
                FapelloSourceException.Reason.MALFORMED,
                "Fapello returned an empty response"
        );
        String type = cleanStatic(contentType).toLowerCase(Locale.US);
        if (expectJson && (type.contains("html") ||
                !(value.startsWith("{") || value.startsWith("[")))) {
            throw new FapelloSourceException(
                    FapelloSourceException.Reason.MALFORMED,
                    "Fapello returned HTML instead of search data"
            );
        }
    }

    static boolean looksBlocked(String value) {
        return value != null && BLOCKED_PAGE.matcher(value).find();
    }

    private boolean browserRetryAllowed(FapelloSourceException error) {
        return error != null && (error.reason == FapelloSourceException.Reason.BLOCKED ||
                error.reason == FapelloSourceException.Reason.NETWORK ||
                error.reason == FapelloSourceException.Reason.MALFORMED ||
                error.reason == FapelloSourceException.Reason.HTTP);
    }

    private void persistCookies(Connection.Response response) {
        if (response == null || response.cookies().isEmpty()) return;
        try {
            CookieManager manager = CookieManager.getInstance();
            String url = response.url() == null ? BASE : response.url().toString();
            for (Map.Entry<String, String> cookie : response.cookies().entrySet()) {
                manager.setCookie(url, cookie.getKey() + "=" + cookie.getValue() + "; Path=/; Secure");
            }
            manager.flush();
        } catch (Exception ignored) {
        }
    }

    private Connection connection(Context context, String url, String referer) {
        Connection connection = Jsoup.connect(url)
                .userAgent(browserUserAgent(context))
                .referrer(referer == null || referer.isEmpty() ? BASE : referer)
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("DNT", "1")
                .header("Sec-Fetch-Site", "same-origin")
                .timeout(15000)
                .maxBodySize(10 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(true);
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if ((cookies == null || cookies.isEmpty()) && referer != null) {
                cookies = CookieManager.getInstance().getCookie(referer);
            }
            if (cookies != null && !cookies.trim().isEmpty()) {
                connection.header("Cookie", cookies);
            }
        } catch (Exception ignored) {
        }
        return connection;
    }

    static String browserUserAgent(Context context) {
        if (context != null) {
            try {
                String current = WebSettings.getDefaultUserAgent(context);
                if (!cleanStatic(current).isEmpty()) return current;
            } catch (Exception ignored) {
            }
        }
        return USER_AGENT;
    }

    private String firstJsonImage(JSONObject value) {
        String[] keys = {"image", "thumbnail", "thumb", "avatar", "photo"};
        for (String key : keys) {
            Object candidate = value.opt(key);
            String image = candidate instanceof JSONObject
                    ? ((JSONObject) candidate).optString("url", "")
                    : candidate instanceof String ? (String) candidate : "";
            image = normalizeUrl(image, BASE);
            if (isImageUrl(image)) return image;
        }
        return "";
    }

    private String first(JSONObject value, String... keys) {
        if (value == null || keys == null) return "";
        for (String key : keys) {
            Object candidate = value.opt(key);
            if (candidate instanceof String && !clean((String) candidate).isEmpty()) {
                return clean((String) candidate);
            }
            if (candidate instanceof JSONObject) {
                String nested = clean(((JSONObject) candidate).optString("url"));
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private String jsonUrl(Object value, String baseUrl) {
        if (value instanceof String) return normalizeUrl((String) value, baseUrl);
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            for (int index = values.length() - 1; index >= 0; index--) {
                String url = jsonUrl(values.opt(index), baseUrl);
                if (!url.isEmpty()) return url;
            }
        }
        if (value instanceof JSONObject) {
            return normalizeUrl(first((JSONObject) value, "url", "contentUrl"), baseUrl);
        }
        return "";
    }

    private String fallbackCreatorName(List<Element> links) {
        if (links == null) return "";
        for (Element link : links) {
            String[] values = {link.text(), link.attr("title"), link.attr("aria-label")};
            for (String value : values) {
                String candidate = clean(value);
                Matcher creatorText = CREATOR_LINK_TEXT.matcher(candidate);
                if (creatorText.matches()) candidate = clean(creatorText.group(1));
                String lower = candidate.toLowerCase(Locale.US);
                if (candidate.isEmpty() || candidate.length() > 80 || lower.equals("image") ||
                        lower.equals("post") || lower.startsWith("follow") ||
                        lower.startsWith("+") || lower.matches("[0-9]+\\s+likes?")) continue;
                return candidate;
            }
        }
        return "";
    }

    private String fallbackCreatorImage(List<Element> links, String endpoint) {
        if (links == null) return "";
        for (Element link : links) {
            String image = imageFrom(link, endpoint);
            if (!image.isEmpty()) return image;
            Element parent = link.parent();
            for (int depth = 0; parent != null && depth < 3; depth++) {
                image = imageFrom(parent, endpoint);
                if (!image.isEmpty()) return image;
                parent = parent.parent();
            }
        }
        return "";
    }

    private boolean looksLikeListingWithCreators(Document document) {
        if (document == null) return false;
        String text = clean(document.text()).toLowerCase(Locale.US);
        return text.contains("see all content of ") || text.contains("view all content from ") ||
                text.matches("(?s).*\\+\\s*[0-9]+\\s+(?:photos?|videos?).*");
    }

    private boolean hasMediaEvidence(Document document) {
        if (document == null) return false;
        Matcher count = MEDIA_COUNT.matcher(document.text());
        if (count.find()) {
            try {
                if (Long.parseLong(count.group(1).replace(",", "")) > 0L) return true;
            } catch (Exception ignored) {
            }
        }
        if (!document.select("video,source,[data-post],[data-permalink]").isEmpty()) return true;
        return SCRIPT_MEDIA_URL.matcher(document.html()).find();
    }

    private String imageFrom(Element root, String baseUrl) {
        if (root == null) return "";
        for (Element image : root.select("img")) {
            String value = imageAttribute(image, baseUrl);
            String lower = value.toLowerCase(Locale.US);
            if (value.isEmpty() || lower.contains("icon-play") ||
                    lower.endsWith("/assets/images/load.svg") || lower.contains("/banners/")) {
                continue;
            }
            return value;
        }
        return "";
    }

    private String listingImage(Element creatorLink, String baseUrl) {
        Element container = creatorLink == null ? null : creatorLink.parent();
        for (int depth = 0; container != null && depth < 6; depth++) {
            if (listingCreatorLinkCount(container) > 1) break;
            String best = bestListingImage(container, baseUrl);
            if (!best.isEmpty()) return best;
            container = container.parent();
        }
        return "";
    }

    private int listingCreatorLinkCount(Element root) {
        int count = 0;
        for (Element link : root.select("a[href]")) {
            String text = clean(link.text());
            if (CREATOR_LINK_TEXT.matcher(text).matches()) count++;
            if (count > 1) break;
        }
        return count;
    }

    private String bestListingImage(Element root, String baseUrl) {
        String best = "";
        int bestScore = Integer.MIN_VALUE;
        for (Element image : root.select("img")) {
            String value = imageAttribute(image, baseUrl);
            if (value.isEmpty()) continue;
            String lower = value.toLowerCase(Locale.US);
            if (lower.contains("/data/avatars/default/") || lower.contains("load.svg") ||
                    lower.contains("/banners/") || lower.contains("logo")) continue;
            int score = 10;
            if (lower.contains("/content/")) score += 80;
            if (lower.contains("_300px")) score += 35;
            if (lower.contains("thumb") || lower.contains("poster")) score += 20;
            String width = image.attr("width");
            String height = image.attr("height");
            try {
                score += Math.min(40, (Integer.parseInt(width) + Integer.parseInt(height)) / 40);
            } catch (Exception ignored) {
            }
            if (score > bestScore) {
                bestScore = score;
                best = value;
            }
        }
        return best;
    }

    private String imageAttribute(Element image, String baseUrl) {
        String[] attrs = {
                "data-full", "data-original", "data-src", "data-lazy-src",
                "data-thumbnail", "data-thumb", "src"
        };
        for (String attr : attrs) {
            String value = normalizeUrl(image.attr(attr), baseUrl);
            if (isImageUrl(value)) return value;
        }
        String srcset = image.attr("srcset");
        if (!srcset.isEmpty()) {
            String[] candidates = srcset.split(",");
            for (int i = candidates.length - 1; i >= 0; i--) {
                String candidate = candidates[i].trim().split("\\s+")[0];
                String value = normalizeUrl(candidate, baseUrl);
                if (isImageUrl(value)) return value;
            }
        }
        return "";
    }

    private boolean isVideoCard(Element link, String pageUrl) {
        String html = link.outerHtml().toLowerCase(Locale.US);
        return html.contains("icon-play") || html.contains("type=\"video\"") ||
                html.contains("data-type=\"video\"") || html.contains("class=\"video") ||
                pageUrl.toLowerCase(Locale.US).contains("/video/") || isVideoUrl(pageUrl);
    }

    private String firstNormalizedUrl(Element element, String baseUrl, String... attributes) {
        if (element == null || attributes == null) return "";
        for (String attribute : attributes) {
            String value = normalizeUrl(element.attr(attribute), baseUrl);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String nextPageUrl(Document document, String modelUrl, int page, String endpoint) {
        String canonicalModel = canonicalModelUrl(modelUrl);
        String expected = "";
        try {
            expected = modelProfilePageUrl(canonicalModel, page + 1);
        } catch (Exception ignored) {
        }
        Set<String> candidates = new java.util.LinkedHashSet<>();
        for (Element link : document.select("link[rel=next][href],a[rel=next][href],a[href]")) {
            String rel = clean(link.attr("rel"));
            String text = clean(link.text());
            String value = normalizeUrl(link.attr("href"), endpoint);
            if (!isFapelloUrl(value) || !value.startsWith(canonicalModel)) continue;
            Matcher matcher;
            try {
                matcher = NEXT_PAGE.matcher(new URI(value).getPath());
            } catch (Exception ignored) {
                continue;
            }
            int target = 1;
            if (matcher.find()) {
                try {
                    target = Integer.parseInt(matcher.group(1));
                } catch (Exception ignored) {
                    continue;
                }
            }
            if (target <= page) continue;
            if (rel.toLowerCase(Locale.US).contains("next") ||
                    text.equalsIgnoreCase("Next Page") || target == page + 1) {
                candidates.add(value);
            }
        }
        if (!expected.isEmpty()) {
            for (String candidate : candidates) {
                if (canonicalKey(candidate).equals(canonicalKey(expected))) return expected;
            }
        }
        return candidates.isEmpty() ? "" : candidates.iterator().next();
    }

    private String modelMediaUrlUnchecked(String modelUrl, int page) {
        try {
            return modelMediaUrl(modelUrl, page);
        } catch (IOException ignored) {
            return "";
        }
    }

    private String attribute(Document document, String selector, String attribute) {
        Element value = document.selectFirst(selector);
        return value == null ? "" : value.attr(attribute);
    }

    private static String modelSlug(String url) {
        try {
            String path = new URI(url).getPath();
            return path == null ? "" : path.replaceAll("^/+|/+$", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String postId(String url) {
        try {
            Matcher matcher = POST_ID.matcher(new URI(url).getPath());
            return matcher.find() ? matcher.group(1) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeListing(String value) throws IOException {
        return normalizeListingValue(value);
    }

    private static String normalizeListingValue(String value) throws IOException {
        String listing = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (LIST_NEW.equals(listing) || LIST_HOT.equals(listing) ||
                LIST_POPULAR.equals(listing)) return listing;
        throw new IOException("Unknown Fapello creator listing");
    }

    private String humanizeSlug(String slug) {
        String value = slug == null ? "" : slug.replace('-', ' ').replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String maximizeMediaUrl(String value) {
        if (value == null) return "";
        return value.trim()
                .replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("_300px", "")
                .replace(".md.", ".")
                .replace(".th.", ".");
    }

    private boolean isDirectMedia(String value) {
        return value != null && DIRECT_MEDIA.matcher(value).find();
    }

    private boolean isVideoUrl(String value) {
        return value != null && VIDEO_MEDIA.matcher(value).find();
    }

    private boolean isImageUrl(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.matches(".*\\.(?:jpe?g|png|webp|avif|gif|bmp|heic|heif)(?:$|[?#]).*");
    }

    static String normalizeUrl(String value, String baseUrl) {
        String url = value == null ? "" : value.trim().replace("&amp;", "&").replace("\\/", "/");
        if (url.isEmpty() || url.startsWith("javascript:") || url.startsWith("data:") ||
                url.startsWith("blob:") || url.startsWith("#")) return "";
        if (url.startsWith("//")) return "https:" + url;
        try {
            URI base = new URI(baseUrl == null || baseUrl.trim().isEmpty() ? BASE : baseUrl);
            URI resolved = base.resolve(url);
            String scheme = resolved.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return "";
            }
            return resolved.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String origin(String value) {
        try {
            URI uri = new URI(value == null ? BASE : value);
            if (uri.getScheme() != null && uri.getHost() != null) {
                return uri.getScheme() + "://" + uri.getHost();
            }
        } catch (Exception ignored) {
        }
        return "https://fapello.com";
    }

    static String canonicalModelUrl(String value) {
        String normalized = normalizeUrl(value, BASE);
        if (!isModelUrl(normalized)) return "";
        try {
            URI uri = new URI(normalized);
            return "https://fapello.com" + uri.getPath().replaceAll("/+$", "") + "/";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String canonicalKey(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim().replace("\\/", "/"));
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.US);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
            String path = uri.getPath() == null ? "/" : uri.getPath().replaceAll("/+$", "");
            if (path.isEmpty()) path = "/";
            return scheme + "://" + host + path +
                    (uri.getQuery() == null || isFapelloUrl(value) ? "" : "?" + uri.getQuery());
        } catch (Exception ignored) {
            return value == null ? "" : value.trim();
        }
    }

    private boolean isPlaceholder(String value) {
        String lower = clean(value).toLowerCase(Locale.US);
        return lower.isEmpty() || lower.contains("load.svg") || lower.contains("placeholder") ||
                lower.contains("/assets/images/") || lower.contains("/banners/") ||
                lower.contains("/data/avatars/default/") || lower.contains("logo");
    }

    private static String host(String value) {
        try {
            String candidate = value == null ? "" : value.trim();
            if (!candidate.contains("://")) candidate = "https://" + candidate;
            String host = new URI(candidate).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String encodeStatic(String value) throws IOException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            throw new IOException("Fapello search could not be encoded", error);
        }
    }

    private static String cleanStatic(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String fileTitle(String url) {
        if (url == null || url.isEmpty()) return "Fapello media";
        String clean = url.split("[?#]", 2)[0];
        int slash = clean.lastIndexOf('/');
        return slash >= 0 && slash + 1 < clean.length() ? clean.substring(slash + 1) : clean;
    }

    private String clean(String value) {
        return cleanStatic(value);
    }

    static final class MediaPage {
        final ArrayList<NativeContentItem> items;
        final boolean hasNext;
        final String nextPageUrl;

        MediaPage(List<NativeContentItem> items, boolean hasNext, String nextPageUrl) {
            this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
            this.hasNext = hasNext;
            this.nextPageUrl = nextPageUrl == null ? "" : nextPageUrl;
        }
    }

    static final class Model {
        final String name;
        final String url;
        final String imageUrl;

        Model(String name, String url, String imageUrl) {
            this.name = name == null ? "" : name.trim();
            this.url = url == null ? "" : url.trim();
            this.imageUrl = imageUrl == null ? "" : imageUrl.trim();
        }
    }
}
