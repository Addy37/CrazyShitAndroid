package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fapello model search, paged mixed-media feeds and original media resolution. */
final class FapelloRepository {
    static final String BASE = "https://fapello.com/";
    static final String LIST_NEW = "new";
    static final String LIST_HOT = "hot";
    static final String LIST_POPULAR = "popular";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final Pattern POST_PATH = Pattern.compile(
            "(?i)^/(?:video/(?:new|week)/[0-9]+|[^/]+/[0-9]+)/?$"
    );
    private static final Pattern POST_ID = Pattern.compile("(?i)/(?:new/)?([0-9]+)/?$");
    private static final Pattern CONTENT_URL = Pattern.compile(
            "(?is)[\\\"']contentUrl[\\\"']\\s*:\\s*[\\\"'](https?://[^\\\"']+)[\\\"']"
    );
    private static final Pattern DIRECT_MEDIA = Pattern.compile(
            "(?i)\\.(?:jpe?g|png|webp|avif|gif|bmp|heic|heif|mp4|webm|m4v|mov|m3u8|mpd)(?:$|[?#])"
    );
    private static final Pattern CREATOR_LINK_TEXT = Pattern.compile(
            "(?i)^(?:see|view)\\s+(?:all\\s+)?content\\s+(?:of|from)\\s+(.+)$"
    );

    List<Model> searchModels(Context context, String query, int limit) throws IOException {
        return searchModels(context, query, limit, true);
    }

    List<Model> searchConfirmedModels(Context context, String query, int limit) throws IOException {
        return searchModels(context, query, limit, false);
    }

    private List<Model> searchModels(Context context, String query, int limit, boolean allowGuessedProfile) throws IOException {
        String cleanQuery = clean(query);
        if (cleanQuery.isEmpty()) return new ArrayList<>();
        int safeLimit = Math.max(1, Math.min(8, limit));
        LinkedHashMap<String, Model> models = new LinkedHashMap<>();

        IOException searchError = null;
        try {
            String endpoint = BASE + "search_v2/?ajax=1&q=" + encode(cleanQuery) +
                    "&type=models&limit=" + safeLimit + "&offset=0";
            JSONObject payload = new JSONObject(fetchBody(context, endpoint, BASE, true));
            Object results = payload.opt("results");
            JSONArray values = results instanceof JSONObject
                    ? ((JSONObject) results).optJSONArray("models")
                    : results instanceof JSONArray ? (JSONArray) results : null;
            if (values != null) {
                for (int i = 0; i < values.length() && models.size() < safeLimit; i++) {
                    JSONObject value = values.optJSONObject(i);
                    if (value == null) continue;
                    String url = normalizeUrl(value.optString("url", ""), BASE);
                    String name = clean(value.optString("name", ""));
                    if (!isModelUrl(url) || name.isEmpty()) continue;
                    String image = firstJsonImage(value);
                    models.putIfAbsent(url, new Model(name, url, image));
                }
            }
        } catch (Exception error) {
            searchError = error instanceof IOException
                    ? (IOException) error
                    : new IOException("Fapello search response was invalid", error);
        }

        // Fapello names usually map directly to a lowercase hyphenated profile slug. This also
        // keeps Fapzone useful when Fapello's optional search endpoint is temporarily unavailable.
        if (models.isEmpty() && !allowGuessedProfile && searchError != null) throw searchError;
        if (models.isEmpty() && allowGuessedProfile) {
            String slug = slugify(cleanQuery);
            if (!slug.isEmpty()) {
                String url = BASE + slug + "/";
                models.put(url, new Model(cleanQuery, url, ""));
            } else if (searchError != null) {
                throw searchError;
            }
        }
        return new ArrayList<>(models.values());
    }

    List<Model> fetchModelListing(Context context, String listing, int page) throws IOException {
        String safeListing = normalizeListing(listing);
        int safePage = Math.max(1, page);
        String listingRoot = LIST_NEW.equals(safeListing) ? BASE : BASE + safeListing + "/";
        String endpoint = listingUrl(safeListing, safePage);
        Document document = fetchDocument(context, endpoint, listingRoot);
        return parseModelListing(document, endpoint);
    }

    List<Model> parseModelListing(Document document, String endpoint) {
        LinkedHashMap<String, Model> models = new LinkedHashMap<>();

        for (Element link : document.select("a[href]")) {
            String text = clean(link.text());
            Matcher creatorText = CREATOR_LINK_TEXT.matcher(text);
            if (!creatorText.matches()) continue;
            String name = clean(creatorText.group(1));
            String url = normalizeUrl(link.attr("href"), endpoint);
            if (name.isEmpty() || !isModelUrl(url)) continue;
            models.putIfAbsent(url, new Model(name, url, listingImage(link, endpoint)));
        }

        // Some Fapello responses omit or rename the "See all content" link. Group repeated
        // profile links from each creator card so the feed still works across those variants.
        LinkedHashMap<String, List<Element>> profileLinks = new LinkedHashMap<>();
        for (Element link : document.select("a[href]")) {
            String url = normalizeUrl(link.attr("href"), endpoint);
            if (!isModelUrl(url)) continue;
            profileLinks.computeIfAbsent(url, ignored -> new ArrayList<>()).add(link);
        }
        for (java.util.Map.Entry<String, List<Element>> entry : profileLinks.entrySet()) {
            if (models.containsKey(entry.getKey())) continue;
            List<Element> links = entry.getValue();
            String name = fallbackCreatorName(links);
            String image = fallbackCreatorImage(links, endpoint);
            if (name.isEmpty() || (links.size() < 2 && image.isEmpty())) continue;
            models.put(entry.getKey(), new Model(name, entry.getKey(), image));
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
        if (model == null || !isModelUrl(model.url)) {
            throw new IOException("Fapello model page was missing");
        }
        String slug = modelSlug(model.url);
        if (slug.isEmpty()) throw new IOException("Fapello model name was missing");
        int safePage = Math.max(1, page);
        String endpoint = BASE + "ajax/model/" + slug + "/page-" + safePage + "/";
        Document document = fetchDocument(context, endpoint, model.url);
        LinkedHashMap<String, NativeContentItem> items = new LinkedHashMap<>();

        for (Element link : document.select("a[href]")) {
            String pageUrl = normalizeUrl(link.attr("href"), endpoint);
            if (!isPostUrl(pageUrl)) continue;
            String thumbnail = imageFrom(link, endpoint);
            boolean video = isVideoCard(link, pageUrl);
            String id = postId(pageUrl);
            String title = clean(model.name);
            if (title.isEmpty()) title = humanizeSlug(slug);
            if (!id.isEmpty()) title += " #" + id;
            NativeContentItem candidate = new NativeContentItem(
                    video ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                    title,
                    pageUrl,
                    thumbnail,
                    "Fapello",
                    model.name,
                    "",
                    "Fapello"
            );
            NativeContentItem current = items.get(pageUrl);
            if (current == null ||
                    (candidate.isVideo() && !current.isVideo()) ||
                    (current.imageUrl.isEmpty() && !candidate.imageUrl.isEmpty())) {
                items.put(pageUrl, candidate);
            }
        }
        return new ArrayList<>(items.values());
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
        String resolvedPage = document.location().isEmpty() ? canonical : document.location();
        String title = clean(document.selectFirst("h1,h2") == null
                ? document.title()
                : document.selectFirst("h1,h2").text());

        String mediaUrl = attribute(document, "meta[property=og:video][content]", "content");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "video source[src]", "src");
        if (mediaUrl.isEmpty()) mediaUrl = attribute(document, "video[src]", "src");
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
            throw new IOException("Fapello did not return a supported media URL");
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
            return !lower.equals("search") && !lower.equals("search_v2") &&
                    !lower.equals("new") && !lower.equals("hot") &&
                    !lower.equals("videos") && !lower.equals("trending") &&
                    !lower.equals("popular") && !lower.startsWith("top-") &&
                    !lower.equals("ajax") && !lower.equals("video") &&
                    !lower.equals("welcome") && !lower.equals("login") &&
                    !lower.equals("signup") && !lower.equals("sign-up") &&
                    !lower.equals("tags") && !lower.equals("random") &&
                    !lower.equals("forum") && !lower.equals("report") &&
                    !lower.equals("dmca") && !lower.equals("contacts") &&
                    !lower.equals("language") && !lower.equals("privacy") &&
                    !lower.equals("terms") && !lower.equals("add-model");
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
        Connection connection = connection(context, url, referer)
                .ignoreContentType(true)
                .header("Accept", json
                        ? "application/json,text/javascript,*/*;q=0.8"
                        : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        if (json) connection.timeout(6000).maxBodySize(1024 * 1024).header("X-Requested-With", "XMLHttpRequest");
        Connection.Response response = connection.execute();
        if (response.statusCode() >= 400) {
            throw new IOException("Fapello returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private Document fetchDocument(Context context, String url, String referer) throws IOException {
        Connection.Response response = connection(context, url, referer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .execute();
        if (response.statusCode() >= 400) {
            throw new IOException("Fapello returned HTTP " + response.statusCode());
        }
        return response.parse();
    }

    private Connection connection(Context context, String url, String referer) {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(referer == null || referer.isEmpty() ? BASE : referer)
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
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
        String prefix = "See all content of ";
        for (Element link : root.select("a[href]")) {
            String text = clean(link.text());
            if (text.regionMatches(true, 0, prefix, 0, prefix.length())) count++;
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
        String[] attrs = {"src", "data-src", "data-original", "data-lazy-src"};
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
        String html = link.html().toLowerCase(Locale.US);
        return html.contains("icon-play") || html.contains("type=\"video\"") ||
                pageUrl.toLowerCase(Locale.US).contains("/video/new/");
    }

    private String attribute(Document document, String selector, String attribute) {
        Element value = document.selectFirst(selector);
        return value == null ? "" : value.attr(attribute);
    }

    private String modelSlug(String url) {
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

    private String slugify(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9._~_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return ascii;
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

    private boolean isImageUrl(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.matches(".*\\.(?:jpe?g|png|webp|avif|gif|bmp|heic|heif)(?:$|[?#]).*");
    }

    private String normalizeUrl(String value, String baseUrl) {
        String url = value == null ? "" : value.trim().replace("&amp;", "&").replace("\\/", "/");
        if (url.isEmpty() || url.startsWith("javascript:")) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return origin(baseUrl) + url;
        if (!url.contains("://")) return origin(baseUrl) + "/" + url.replaceAll("^/+", "");
        return url;
    }

    private String origin(String value) {
        try {
            URI uri = new URI(value == null ? BASE : value);
            if (uri.getScheme() != null && uri.getHost() != null) {
                return uri.getScheme() + "://" + uri.getHost();
            }
        } catch (Exception ignored) {
        }
        return "https://fapello.com";
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

    private String encode(String value) throws IOException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            throw new IOException("Fapello search could not be encoded", error);
        }
    }

    private String fileTitle(String url) {
        if (url == null || url.isEmpty()) return "Fapello media";
        String clean = url.split("[?#]", 2)[0];
        int slash = clean.lastIndexOf('/');
        return slash >= 0 && slash + 1 < clean.length() ? clean.substring(slash + 1) : clean;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
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
