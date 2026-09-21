package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
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

        if (creators.isEmpty()) {
            for (String route : routes) {
                try {
                    Document rendered = RenderedSourcePageFetcher.fetch(
                            context,
                            config.baseUrl + route,
                            config.userAgent,
                            config.requestHeaders,
                            config.refererOverride.isEmpty() ? config.baseUrl : config.refererOverride,
                            "/creators/"
                    );
                    parseCreators(rendered, config, creators, Math.max(1, limit));
                    if (!creators.isEmpty()) break;
                } catch (IOException error) {
                    lastError = error;
                }
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

        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, limit);
        int offset = (safePage - 1) * safeLimit;
        String apiRoute = config.creatorPostsApiRoute
                .replace("{service}", urlToken(creator.service))
                .replace("{id}", urlToken(creator.id))
                .replace("{offset}", String.valueOf(offset))
                .replace("{limit}", String.valueOf(safeLimit));

        IOException apiFailure = null;
        try {
            String body = fetchConfiguredText(context, config, config.baseUrl + apiRoute);
            List<NativeContentItem> apiItems =
                    parseCreatorMediaJson(body, config, creator, safeLimit);
            if (!apiItems.isEmpty()) return apiItems;
        } catch (IOException error) {
            apiFailure = error;
        }

        String route = config.creatorPageRoute
                .replace("{service}", urlToken(creator.service))
                .replace("{id}", urlToken(creator.id))
                .replace("{page}", String.valueOf(safePage));
        try {
            Document document = fetchConfigured(context, config, config.baseUrl + route);
            List<NativeContentItem> htmlItems =
                    parseMedia(document, config, creator, safeLimit);
            if (!htmlItems.isEmpty() || apiFailure == null) return htmlItems;
        } catch (IOException htmlFailure) {
            if (apiFailure == null) throw htmlFailure;
        }

        throw apiFailure == null
                ? new IOException("OnlyHaven returned no creator media")
                : apiFailure;
    }

    List<NativeContentItem> parseCreatorMediaJson(
            String body,
            SourceConfig.OnlyHaven config,
            Creator creator,
            int limit
    ) throws IOException {
        try {
            Object root = new JSONTokener(body == null ? "" : body).nextValue();
            JSONArray posts = null;
            if (root instanceof JSONArray) {
                posts = (JSONArray) root;
            } else if (root instanceof JSONObject) {
                JSONObject object = (JSONObject) root;
                for (String key : new String[]{"posts", "items", "results", "data"}) {
                    posts = object.optJSONArray(key);
                    if (posts != null) break;
                }
            }
            if (posts == null) return new ArrayList<>();

            LinkedHashMap<String, NativeContentItem> items = new LinkedHashMap<>();
            for (int index = 0; index < posts.length() && items.size() < limit; index++) {
                JSONObject post = posts.optJSONObject(index);
                if (post == null) continue;
                String title = firstJsonText(post, "title", "caption", "name");
                if (title.isEmpty()) title = creator.name.isEmpty() ? "OnlyHaven media" : creator.name;

                ArrayList<JSONObject> files = new ArrayList<>();
                addJsonObject(files, post.optJSONObject("file"));
                addJsonArray(files, post.optJSONArray("attachments"));
                addJsonArray(files, post.optJSONArray("files"));
                addJsonArray(files, post.optJSONArray("media"));

                String preview = "";
                for (JSONObject file : files) {
                    String candidate = mediaUrl(config, file);
                    if (isDirectImage(candidate)) {
                        preview = candidate;
                        break;
                    }
                }

                for (JSONObject file : files) {
                    if (items.size() >= limit) break;
                    String candidate = mediaUrl(config, file);
                    boolean video = isDirectVideo(candidate);
                    boolean image = isDirectImage(candidate);
                    if (!video && !image) continue;
                    NativeContentItem item = new NativeContentItem(
                            video ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                            title,
                            candidate,
                            image ? candidate : preview,
                            "",
                            creator.url,
                            "",
                            creator.service + " · OnlyHaven"
                    );
                    items.putIfAbsent(candidate, item);
                }
            }
            return new ArrayList<>(items.values());
        } catch (Exception error) {
            throw new IOException("OnlyHaven posts API returned unreadable JSON", error);
        }
    }

    private void addJsonObject(List<JSONObject> output, JSONObject value) {
        if (value != null) output.add(value);
    }

    private void addJsonArray(List<JSONObject> output, JSONArray values) {
        if (values == null) return;
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value != null) output.add(value);
        }
    }

    private String firstJsonText(JSONObject object, String... keys) {
        for (String key : keys) {
            Object raw = object.opt(key);
            if (!(raw instanceof String)) continue;
            String value = clean((String) raw);
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private String mediaUrl(SourceConfig.OnlyHaven config, JSONObject file) {
        if (file == null) return "";

        String direct = firstJsonText(file, "url", "src");
        if (direct.startsWith("https://")) return cleanUrl(direct);

        String storageKey = firstJsonText(file, "storageKey", "storage_key", "sha256");
        String path = firstJsonText(file, "path");
        if (storageKey.isEmpty() && path.matches("(?i)^[0-9a-f]{16,}$")) storageKey = path;

        if (!storageKey.isEmpty()) {
            String variant = preferredVariant(file.optJSONArray("variants"));
            if (variant.isEmpty()) {
                String name = firstJsonText(file, "name", "originalFilename", "filename");
                String extension = extension(name);
                if (extension.isEmpty()) extension = extensionForMime(firstJsonText(file, "mimeType", "mime_type"));
                if (extension.isEmpty()) extension = "jpg";
                variant = "original." + extension;
            }
            return config.mediaBaseUrl + stripSlashes(storageKey) + "/" + stripSlashes(variant);
        }

        if (path.startsWith("https://")) return cleanUrl(path);
        if (!path.isEmpty()) {
            String normalized = path.startsWith("/") ? path : "/" + path;
            if (normalized.startsWith("/data/")) normalized = normalized.substring(5);
            return config.baseUrl + "data" + normalized;
        }
        return "";
    }

    private String preferredVariant(JSONArray variants) {
        if (variants == null || variants.length() == 0) return "";
        String first = "";
        for (int index = 0; index < variants.length(); index++) {
            Object raw = variants.opt(index);
            String name = "";
            if (raw instanceof JSONObject) {
                name = firstJsonText((JSONObject) raw, "name", "path", "filename");
            } else if (raw instanceof String) {
                name = clean((String) raw);
            }
            if (name.isEmpty()) continue;
            if (first.isEmpty()) first = name;
            if (name.toLowerCase(Locale.US).contains("original")) return name;
        }
        return first;
    }

    private String extension(String value) {
        String clean = value == null ? "" : value.trim();
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        int dot = clean.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= clean.length()) return "";
        String extension = clean.substring(dot + 1).toLowerCase(Locale.US);
        return extension.matches("[a-z0-9]{2,5}") ? extension : "";
    }

    private String extensionForMime(String mime) {
        String value = mime == null ? "" : mime.toLowerCase(Locale.US);
        if (value.contains("jpeg")) return "jpg";
        if (value.contains("png")) return "png";
        if (value.contains("gif")) return "gif";
        if (value.contains("webp")) return "webp";
        if (value.contains("avif")) return "avif";
        if (value.contains("mp4")) return "mp4";
        if (value.contains("webm")) return "webm";
        if (value.contains("quicktime")) return "mov";
        return "";
    }

    private String stripSlashes(String value) {
        String result = value == null ? "" : value.trim();
        while (result.startsWith("/")) result = result.substring(1);
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
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

    private String fetchConfiguredText(
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
                            .maxBodySize(16 * 1024 * 1024)
                            .followRedirects(true)
                            .ignoreContentType(true)
                            .ignoreHttpErrors(false);
                    for (Map.Entry<String, String> header : config.requestHeaders.entrySet()) {
                        connection.header(header.getKey(), header.getValue());
                    }
                    connection.header("Accept", "application/json,text/plain,*/*;q=0.8");
                    try {
                        String cookies = CookieManager.getInstance().getCookie(candidate);
                        if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
                    } catch (Exception ignored) {
                    }
                    return connection.execute().body();
                } catch (IOException error) {
                    failure = error;
                }
            }
        }
        throw failure == null ? new IOException("OnlyHaven API request failed") : failure;
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
