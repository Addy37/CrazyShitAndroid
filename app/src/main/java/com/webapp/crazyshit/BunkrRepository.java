package com.webapp.crazyshit;

import android.content.Context;
import android.util.Base64;
import android.webkit.CookieManager;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Balbums album discovery plus native Bunkr album and playable-file support. */
public final class BunkrRepository {
    public static final String INDEX = "https://balbums.st/";
    public static final String MOST_FILES_ALBUMS =
            INDEX + "?search=&mode=broad&per=20&sort=files&page=1";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final String[] API_ENDPOINTS = {
            "https://dl.bunkr.cr/api/_001_v2",
            "https://apidl.bunkr.ru/api/_001_v2"
    };
    private static final String DOWNLOAD_ROOT = "https://dl.bunkr.cr";

    private static final Pattern ALBUM_PATH = Pattern.compile("(?i)/a/([^/?#]+)");
    private static final Pattern FILE_PATH = Pattern.compile("(?i)/[fvid]/([^/?#]+)");
    private static final Pattern FILE_ID = Pattern.compile(
            "(?i)data-file-id\\s*=\\s*[\\\"']([0-9]+)"
    );
    private static final Pattern MEDIA_URL = Pattern.compile(
            "(?i)https?://[^\\s\\\"'<>]+?\\.(?:m3u8|mpd|mp4|webm|m4v|mov)(?:\\?[^\\s\\\"'<>]*)?"
    );
    private static final Pattern ORIGINAL = Pattern.compile(
            "(?s)\\boriginal\\s*:\\s*([\\\"'])(.*?)\\1\\s*,"
    );
    private static final Pattern SLUG = Pattern.compile(
            "(?s)\\bslug\\s*:\\s*([\\\"'])(.*?)\\1\\s*,"
    );
    private static final Pattern NAME = Pattern.compile(
            "(?s)\\bname\\s*:\\s*([\\\"'])(.*?)\\1\\s*,"
    );
    private static final Pattern THUMBNAIL = Pattern.compile(
            "(?s)\\bthumbnail\\s*:\\s*([\\\"'])(.*?)\\1\\s*,"
    );
    private static final Pattern TYPE = Pattern.compile(
            "(?s)\\btype\\s*:\\s*([\\\"'])(.*?)\\1\\s*,"
    );
    private static final Pattern EXTENSION = Pattern.compile(
            "(?s)\\bextension\\s*:\\s*([\\\"'])(.*?)\\1\\s*,"
    );
    private static final Pattern JS_CDN = Pattern.compile(
            "(?is)\\bjsCDN\\s*=\\s*([\\\"'])(.*?)\\1"
    );
    private static final Pattern SIGN_URL = Pattern.compile(
            "(?is)\\bsignUrl\\s*=\\s*([\\\"'])(.*?)\\1"
    );
    private static final Pattern SIZE = Pattern.compile("(?i)\\bsize\\s*:\\s*([0-9]+)");

    public List<NativeContentItem> fetchAlbums(Context context, int page) throws IOException {
        int safePage = Math.max(1, page);
        String url = safePage == 1 ? INDEX : INDEX + "?page=" + safePage;
        return parseAlbumIndex(fetchDocument(context, url));
    }

    public List<NativeContentItem> fetchAlbumsByFileCount(Context context, int page)
            throws IOException {
        int safePage = Math.max(1, page);
        String url = INDEX + "?search=&mode=broad&per=20&sort=files&page=" + safePage;
        return parseAlbumIndex(fetchDocument(context, url));
    }

    public List<NativeContentItem> searchAlbums(Context context, String query, int page)
            throws IOException {
        String encoded;
        try {
            encoded = URLEncoder.encode(query == null ? "" : query.trim(), "UTF-8");
        } catch (Exception ignored) {
            encoded = query == null ? "" : query.trim();
        }
        String url = INDEX + "?search=" + encoded + "&mode=broad&page=" + Math.max(1, page);
        return parseAlbumIndex(fetchDocument(context, url));
    }

    public List<NativeContentItem> fetchAlbum(Context context, String albumUrl, int page)
            throws IOException {
        String canonical = normalizeUrl(albumUrl, INDEX);
        Matcher album = ALBUM_PATH.matcher(canonical);
        if (!album.find()) throw new IOException("Not a Bunkr album URL");

        String origin = origin(canonical);
        int safePage = Math.max(1, page);
        String pageUrl = origin + "/a/" + album.group(1) + "?page=" + safePage;
        Document doc = fetchDocument(context, pageUrl);
        origin = origin(doc.location());
        LinkedHashMap<String, String> artwork = parseFileArtwork(doc);
        ArrayList<NativeContentItem> all = parseAlbumFiles(doc, origin, artwork);
        if (all.isEmpty()) all.addAll(parseAlbumDom(doc, origin));

        // Older layouts only expose file metadata in Advanced View. Keep it as a first-page
        // fallback, but use Bunkr's normal server-side pages for large albums so a phone never
        // needs to download tens of thousands of records in one response.
        if (all.isEmpty() && safePage == 1) {
            Document advanced = fetchDocument(
                    context,
                    origin + "/a/" + album.group(1) + "?advanced=1"
            );
            String advancedOrigin = origin(advanced.location());
            LinkedHashMap<String, String> advancedArtwork = parseFileArtwork(advanced);
            all.addAll(parseAlbumFiles(advanced, advancedOrigin, advancedArtwork));
            if (all.isEmpty()) all.addAll(parseAlbumDom(advanced, advancedOrigin));
        }
        return all;
    }

    /** Returns the first real file thumbnail, including artwork from image-only albums. */
    public String fetchAlbumArtwork(Context context, String albumUrl) throws IOException {
        String canonical = normalizeUrl(albumUrl, INDEX);
        Matcher album = ALBUM_PATH.matcher(canonical);
        if (!album.find()) throw new IOException("Not a Bunkr album URL");

        String firstPageUrl = origin(canonical) + "/a/" + album.group(1) + "?page=1";
        Document doc = fetchDocument(context, firstPageUrl);
        String pageOrigin = origin(doc.location());
        for (String image : parseFileArtwork(doc).values()) {
            if (image != null && !image.trim().isEmpty()) return image.trim();
        }

        for (Element script : doc.select("script")) {
            String body = script.data().isEmpty() ? script.html() : script.data();
            Matcher thumbnails = THUMBNAIL.matcher(body);
            while (thumbnails.find()) {
                String image = normalizeUrl(unescapeScriptUrl(thumbnails.group(2)), pageOrigin);
                if (!image.isEmpty()) return image;
            }
        }

        Element socialImage = doc.selectFirst(
                "meta[property=og:image][content],meta[name=twitter:image][content]"
        );
        return socialImage == null
                ? ""
                : normalizeUrl(socialImage.attr("content"), pageOrigin);
    }

    public CrazyShitRepository.StreamInfo resolvePlayable(Context context, String pageUrl)
            throws IOException {
        String canonical = normalizeUrl(pageUrl, INDEX);
        if (isDirectMedia(canonical)) {
            return new CrazyShitRepository.StreamInfo(canonical, pageUrl, fileTitle(canonical));
        }

        Document doc = fetchDocument(context, canonical);
        String title = clean(doc.selectFirst("h1") == null ? doc.title() : doc.selectFirst("h1").text());
        IOException last = null;
        String cdnUrl = scriptValue(doc, JS_CDN);
        String signUrl = scriptValue(doc, SIGN_URL);
        if (!cdnUrl.isEmpty() || !signUrl.isEmpty()) {
            if (cdnUrl.isEmpty() || signUrl.isEmpty()) {
                throw new IOException("Bunkr's signed stream data was incomplete");
            }
            String signed = requestSignedCdnUrl(context, canonical, cdnUrl, signUrl);
            if (!signed.isEmpty() && !isMaintenanceVideo(signed)) {
                return new CrazyShitRepository.StreamInfo(
                        signed, canonical, title, "https://get.bunkrr.su/"
                );
            }
            throw new IOException("Bunkr did not return a signed playable URL");
        }

        // Older mirrors still use Bunkr's legacy download API.
        String dataId = dataFileId(doc);
        if (!dataId.isEmpty()) {
            for (String endpoint : API_ENDPOINTS) {
                try {
                    String mediaUrl = requestDownloadUrl(context, endpoint, dataId);
                    if (!mediaUrl.isEmpty() && !isMaintenanceVideo(mediaUrl)) {
                        String downloadRoot = endpoint.contains("apidl.bunkr.ru")
                                ? "https://get.bunkrr.su"
                                : DOWNLOAD_ROOT;
                        return new CrazyShitRepository.StreamInfo(
                                mediaUrl, canonical, title, downloadRoot + "/file/" + dataId
                        );
                    }
                } catch (IOException error) {
                    last = error;
                }
            }
        }

        // Some older Bunkr mirrors still expose the real source directly. Only use this after the
        // signed API path, since page scripts also mention Bunkr's maintenance placeholder video.
        for (Element media : doc.select("video[src],video source[src],source[type*=video][src]")) {
            String candidate = normalizeUrl(media.absUrl("src"), canonical);
            if (isDirectMedia(candidate) && !isMaintenanceVideo(candidate)) {
                return new CrazyShitRepository.StreamInfo(candidate, canonical, title);
            }
        }
        for (Element script : doc.select("script")) {
            String body = script.data().isEmpty() ? script.html() : script.data();
            Matcher direct = MEDIA_URL.matcher(body.replace("\\/", "/"));
            while (direct.find()) {
                String candidate = direct.group();
                if (!isMaintenanceVideo(candidate)) {
                    return new CrazyShitRepository.StreamInfo(candidate, canonical, title);
                }
            }
        }

        if (last != null) throw last;
        throw new IOException(dataId.isEmpty()
                ? "Bunkr file id was not found"
                : "Bunkr did not return a playable URL");
    }

    public static boolean isBalbumsUrl(String url) {
        String host = host(url);
        return "balbums.st".equals(host) || host.endsWith(".balbums.st");
    }

    public static boolean isBunkrUrl(String url) {
        String host = host(url);
        return host.matches("(?:app\\.)?bunkr+\\.[a-z0-9]+") || host.contains("bunkr.");
    }

    public static boolean isAlbumUrl(String url) {
        return isBunkrUrl(url) && ALBUM_PATH.matcher(url == null ? "" : url).find();
    }

    public static boolean supportsComments(String url) {
        return !isBunkrUrl(url);
    }

    private List<NativeContentItem> parseAlbumIndex(Document doc) {
        LinkedHashMap<String, NativeContentItem> albums = new LinkedHashMap<>();
        for (Element link : doc.select("a[href*=/a/]")) {
            String url = normalizeUrl(link.absUrl("href"), INDEX);
            if (!isAlbumUrl(url)) continue;
            Element card = cardScope(link);
            String title = clean(link.attr("title"));
            if (title.length() < 2) title = clean(link.text());
            if (title.length() < 2 && card != null) {
                Element heading = card.selectFirst("h1,h2,h3,h4,h5,.title,[class*=title]");
                if (heading != null) title = clean(heading.text());
            }
            title = cleanAlbumTitle(title);
            if (title.length() < 2) title = albumTitle(url);

            String image = imageFrom(link);
            if (image.isEmpty() && card != null) image = imageFrom(card);
            String description = card == null ? "Bunkr album" : albumDescription(card.text());
            albums.putIfAbsent(url, new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    title,
                    url,
                    image,
                    "",
                    "Bunkr",
                    "",
                    description
            ));
        }
        return new ArrayList<>(albums.values());
    }

    private ArrayList<NativeContentItem> parseAlbumFiles(
            Document doc,
            String origin,
            LinkedHashMap<String, String> artwork
    ) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        String html = doc.html();
        int marker = html.indexOf("window.albumFiles = [");
        if (marker < 0) return result;
        int end = html.indexOf("</script>", marker);
        String script = end < 0 ? html.substring(marker) : html.substring(marker, end);

        Matcher ids = Pattern.compile("(?i)\\bid\\s*:\\s*([0-9]+)").matcher(script);
        ArrayList<Integer> starts = new ArrayList<>();
        while (ids.find()) starts.add(ids.start());
        for (int i = 0; i < starts.size(); i++) {
            int to = i + 1 < starts.size() ? starts.get(i + 1) : script.length();
            String item = script.substring(starts.get(i), to);
            String original = jsValue(item, ORIGINAL);
            String slug = jsValue(item, SLUG);
            String name = jsValue(item, NAME);
            String thumbnail = jsValue(item, THUMBNAIL);
            if (slug.isEmpty()) continue;
            String title = original.isEmpty() ? fileTitle(slug) : original;
            String type = jsValue(item, TYPE);
            String extension = jsValue(item, EXTENSION);
            boolean video = isPlayableName(title) || isPlayableName(slug) ||
                    isPlayableName(extension) || "video".equalsIgnoreCase(type);
            boolean imageFile = isImageName(title) || isImageName(slug) ||
                    isImageName(extension) || "image".equalsIgnoreCase(type);
            if (!video && !imageFile) continue;
            String image = normalizeUrl(thumbnail, origin);
            if (image.isEmpty()) image = artwork.get(slug);
            if (image == null || image.isEmpty()) image = artwork.get(name);
            String size = value(item, SIZE);
            result.add(new NativeContentItem(
                    imageFile && !video
                            ? NativeContentItem.KIND_IMAGE
                            : NativeContentItem.KIND_MEDIA,
                    title,
                    origin + "/f/" + slug,
                    image == null ? "" : image,
                    size.isEmpty() ? "" : readableBytes(size),
                    "Bunkr",
                    ""
            ));
        }
        return result;
    }

    private LinkedHashMap<String, String> parseFileArtwork(Document doc) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Element link : doc.select(
                "a[href*=/f/],a[href*=/v/],a[href*=/i/],a[href*=/d/]"
        )) {
            Matcher path = FILE_PATH.matcher(link.attr("href"));
            if (!path.find()) continue;
            String image = imageFrom(link);
            if (image.isEmpty() && link.parent() != null) image = imageFrom(link.parent());
            if (!image.isEmpty()) result.putIfAbsent(path.group(1), image);
        }
        for (Element image : doc.select("img[src*=/thumbs/],img[data-src*=/thumbs/]")) {
            String url = imageFrom(image);
            Matcher thumb = Pattern.compile("/thumbs/([^./?]+)").matcher(url);
            if (thumb.find()) result.putIfAbsent(thumb.group(1), url);
        }
        return result;
    }

    private ArrayList<NativeContentItem> parseAlbumDom(Document doc, String origin) {
        LinkedHashMap<String, NativeContentItem> result = new LinkedHashMap<>();
        for (Element link : doc.select(
                "a[href*=/f/],a[href*=/v/],a[href*=/i/],a[href*=/d/]"
        )) {
            String url = normalizeUrl(link.absUrl("href"), origin);
            Matcher path = FILE_PATH.matcher(url);
            if (!path.find()) continue;
            String title = clean(link.text());
            if (title.isEmpty()) title = fileTitle(path.group(1));
            boolean video = isPlayableName(title) || isPlayableName(path.group(1));
            boolean imageFile = isImageName(title) || isImageName(path.group(1));
            if (!video && !imageFile) continue;
            result.putIfAbsent(url, new NativeContentItem(
                    imageFile && !video
                            ? NativeContentItem.KIND_IMAGE
                            : NativeContentItem.KIND_MEDIA,
                    title,
                    url,
                    imageFrom(link),
                    "",
                    "Bunkr",
                    ""
            ));
        }
        return new ArrayList<>(result.values());
    }

    private String requestDownloadUrl(Context context, String endpoint, String dataId)
            throws IOException {
        String downloadRoot = endpoint.contains("apidl.bunkr.ru")
                ? "https://get.bunkrr.su"
                : DOWNLOAD_ROOT;
        String referer = downloadRoot + "/file/" + dataId;
        Connection connection = Jsoup.connect(endpoint)
                .userAgent(USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Origin", downloadRoot)
                .referrer(referer)
                .requestBody("{\"id\":\"" + dataId + "\"}")
                .method(Connection.Method.POST)
                .timeout(18000)
                .maxBodySize(1024 * 1024)
                .ignoreContentType(true)
                .ignoreHttpErrors(false)
                .followRedirects(true);
        addCookies(context, connection, endpoint);
        Connection.Response response = connection.execute();
        try {
            JSONObject data = new JSONObject(response.body());
            String url = data.optString("url", "");
            if (data.optBoolean("encrypted", false)) {
                long timestamp = data.optLong("timestamp", 0L);
                url = decryptXor(url, "SECRET_KEY_" + (timestamp / 3600L));
            }
            return normalizeUrl(url, endpoint);
        } catch (Exception error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Bunkr download response was invalid", error);
        }
    }

    private String requestSignedCdnUrl(
            Context context,
            String pageUrl,
            String rawCdnUrl,
            String rawSignUrl
    ) throws IOException {
        String cdnUrl = unescapeScriptUrl(rawCdnUrl);
        String signUrl = unescapeScriptUrl(rawSignUrl);
        if (!isTrustedSignUrl(signUrl)) {
            throw new IOException("Bunkr returned an untrusted signing address");
        }

        String path;
        try {
            URI cdn = new URI(cdnUrl);
            path = cdn.getRawPath();
        } catch (Exception error) {
            throw new IOException("Bunkr's CDN address was invalid", error);
        }
        if (path == null || path.isEmpty()) {
            throw new IOException("Bunkr's CDN path was missing");
        }

        String encodedPath;
        try {
            encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("%7E", "~");
        } catch (Exception error) {
            throw new IOException("Bunkr's CDN path could not be encoded", error);
        }

        String requestUrl = signUrl + (signUrl.contains("?") ? "&" : "?") + "path=" + encodedPath;
        Connection connection = Jsoup.connect(requestUrl)
                .userAgent(USER_AGENT)
                .header("Accept", "application/json")
                .header("Origin", origin(pageUrl))
                .referrer(pageUrl)
                .timeout(18000)
                .maxBodySize(1024 * 1024)
                .ignoreContentType(true)
                .ignoreHttpErrors(false)
                .followRedirects(true);
        addCookies(context, connection, requestUrl);

        try {
            JSONObject payload = new JSONObject(connection.execute().body());
            String token = payload.optString("token", "");
            String expiry = payload.optString("ex", "");
            if (token.isEmpty() || expiry.isEmpty()) {
                throw new IOException("Bunkr's signing response was incomplete");
            }
            String base = cdnUrl.split("#", 2)[0].split("\\?", 2)[0];
            return base + "?token=" + queryValue(token) + "&ex=" + queryValue(expiry);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Bunkr's signing response was invalid", error);
        }
    }

    private Document fetchDocument(Context context, String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(INDEX)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .timeout(20000)
                .maxBodySize(12 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(false);
        addCookies(context, connection, url);
        return connection.get();
    }

    private void addCookies(Context context, Connection connection, String url) {
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) {
        }
    }

    private String decryptXor(String encrypted, String key) throws IOException {
        try {
            byte[] input = Base64.decode(encrypted, Base64.DEFAULT);
            byte[] secret = key.getBytes(StandardCharsets.UTF_8);
            byte[] output = new byte[input.length];
            for (int i = 0; i < input.length; i++) output[i] = (byte) (input[i] ^ secret[i % secret.length]);
            return new String(output, StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IOException("Bunkr URL decryption failed", error);
        }
    }

    private Element cardScope(Element link) {
        Element current = link;
        for (int i = 0; i < 6 && current != null; i++, current = current.parent()) {
            if (current.hasClass("rounded-xl") && current.hasClass("bg-mute")) return current;
            if (!current.select(
                    "img,[class*=title],[class*=text-subs],[class*=text-xs],h1,h2,h3,h4,h5"
            ).isEmpty()) return current;
        }
        return link.parent();
    }

    private String imageFrom(Element root) {
        if (root == null) return "";
        Element image = root.is("img") ? root : root.selectFirst("img,video[poster],[data-src],[data-thumbnail]");
        if (image == null) return "";
        String[] attrs = {"data-src", "data-original", "data-thumbnail", "poster", "src"};
        for (String attr : attrs) {
            String value = normalizeUrl(image.absUrl(attr), root.baseUri());
            if (value.isEmpty()) value = normalizeUrl(image.attr(attr), root.baseUri());
            if (value.startsWith("http")) return value;
        }
        return "";
    }

    private String albumDescription(String text) {
        String clean = clean(text);
        Matcher rank = Pattern.compile("(?i)^#\\s*([0-9]+)").matcher(clean);
        Matcher views = Pattern.compile("(?i)([0-9][0-9,]*)\\s+views?").matcher(clean);
        Matcher count = Pattern.compile("(?i)([0-9][0-9,]*)\\s+(?:files?|items?|videos?)").matcher(clean);
        StringBuilder description = new StringBuilder();
        if (rank.find()) description.append('#').append(rank.group(1));
        if (views.find()) {
            if (description.length() > 0) description.append("  •  ");
            description.append(views.group(1)).append(" views");
        }
        if (count.find()) {
            if (description.length() > 0) description.append("  •  ");
            description.append(count.group(1)).append(" files");
        }
        return description.length() > 0 ? description.toString() : "Bunkr album";
    }

    private String albumTitle(String url) {
        Matcher match = ALBUM_PATH.matcher(url);
        return match.find() ? "Bunkr album " + match.group(1) : "Bunkr album";
    }

    private String cleanAlbumTitle(String value) {
        String title = clean(value)
                .replaceFirst("(?i)^#\\s*[0-9]+\\s+", "")
                .replaceFirst("(?i)^view\\s+album\\s+", "");
        title = title.replaceFirst(
                "(?i)\\s+[0-9][0-9,]*\\s+(?:files?|items?|videos?)" +
                        "(?:\\s+[0-9][0-9,]*\\s+views?)?" +
                        "\\s*(?:→|->|›)?\\s*open\\s*$",
                ""
        );
        title = title.replaceFirst(
                "(?i)\\s+[0-9][0-9,]*\\s+(?:files?|items?|videos?)" +
                        "(?:\\s+[0-9][0-9,]*\\s+views?)?\\s*$",
                ""
        );
        return clean(title);
    }

    private String fileTitle(String value) {
        String text = value == null ? "" : value;
        int slash = text.lastIndexOf('/');
        if (slash >= 0) text = text.substring(slash + 1);
        int query = text.indexOf('?');
        if (query >= 0) text = text.substring(0, query);
        try {
            text = java.net.URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
        }
        return clean(text.replace('_', ' '));
    }

    private boolean isPlayableName(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US).split("\\?", 2)[0];
        return lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".webm") ||
                lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".ts") ||
                lower.endsWith(".m3u8") || lower.endsWith(".mpd") ||
                lower.matches("^\\.?(?:mp4|m4v|webm|mov|mkv|ts|m3u8|mpd)$");
    }

    private boolean isImageName(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US).split("\\?", 2)[0];
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.endsWith(".bmp") ||
                lower.endsWith(".avif") || lower.endsWith(".heic") ||
                lower.endsWith(".heif") ||
                lower.matches("^\\.?(?:jpg|jpeg|png|webp|gif|bmp|avif|heic|heif)$");
    }

    private boolean isDirectMedia(String url) {
        return isPlayableName(url);
    }

    private boolean isMaintenanceVideo(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.endsWith("/maint.mp4") || lower.contains("/maint.mp4?") ||
                lower.endsWith("/maintenance-vid.mp4") || lower.contains("/maintenance-vid.mp4?");
    }

    private String dataFileId(Document doc) {
        if (doc == null) return "";
        Element tagged = doc.selectFirst("[data-file-id]");
        if (tagged != null) {
            String value = tagged.attr("data-file-id").trim();
            if (value.matches("[0-9]+")) return value;
        }
        Matcher matcher = FILE_ID.matcher(doc.html());
        return matcher.find() ? matcher.group(1) : "";
    }

    private String scriptValue(Document doc, Pattern pattern) {
        if (doc == null) return "";
        for (Element script : doc.select("script")) {
            String body = script.data();
            if (body == null || body.isEmpty()) body = script.html();
            Matcher matcher = pattern.matcher(body == null ? "" : body);
            if (matcher.find()) return unescapeScriptUrl(matcher.group(2));
        }
        return "";
    }

    private String unescapeScriptUrl(String value) {
        return clean(value)
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");
    }

    private boolean isTrustedSignUrl(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return false;
            host = host.toLowerCase(Locale.US);
            return "cdn.cr".equals(host) || host.endsWith(".cdn.cr");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String queryValue(String value) throws IOException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("%7E", "~");
        } catch (Exception error) {
            throw new IOException("Bunkr's stream token could not be encoded", error);
        }
    }

    private String jsValue(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) return "";
        return clean(matcher.group(2).replace("\\/", "/").replace("\\'", "'").replace("\\\"", "\""));
    }

    private String value(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String readableBytes(String raw) {
        try {
            double bytes = Double.parseDouble(raw);
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int unit = 0;
            while (bytes >= 1024d && unit < units.length - 1) {
                bytes /= 1024d;
                unit++;
            }
            return unit == 0 ? ((long) bytes) + " " + units[unit]
                    : String.format(Locale.US, "%.1f %s", bytes, units[unit]);
        } catch (Exception ignored) {
            return "";
        }
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

    private String origin(String url) {
        try {
            URI uri = new URI(url);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception ignored) {
            return "https://bunkr.cr";
        }
    }

    private String normalizeUrl(String value, String base) {
        if (value == null) return "";
        String url = value.trim().replace("&amp;", "&").replace("\\/", "/");
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return origin(base) + url;
        return url;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
