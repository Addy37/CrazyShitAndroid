package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
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
    private static final String[] PAGE_ORIGINS = {
            "https://bunkr.cr",
            "https://bunkr.site",
            "https://bunkr.ph"
    };
    private static final String DEFAULT_SIGN_URL = "https://glb-apisign.cdn.cr/sign";
    private static final String DOWNLOAD_ROOT = "https://dl.bunkr.cr";
    private static final int COVER_ALBUM_LIMIT = 4;
    private static final int COVER_PROBES_PER_ALBUM = 8;
    private static final int COVER_PROBE_BYTES = 512 * 1024;
    private static final long COVER_LOOKUP_BUDGET_MS = 18_000L;
    private static final double COVER_GOOD_MATCH = 0.06d;
    private static volatile String preferredPageOrigin = "";

    private static final Pattern ALBUM_PATH = Pattern.compile("(?i)/a/([^/?#]+)");
    private static final Pattern FILE_PATH = Pattern.compile("(?i)/[fvid]/([^/?#]+)");
    private static final Pattern FILE_ID = Pattern.compile(
            "(?i)data-file-id\\s*=\\s*[\\\"']([0-9]+)"
    );
    private static final Pattern MEDIA_URL = Pattern.compile(
            "(?i)https?://[^\\s\\\"'<>]+?\\.(?:m3u8|mpd|mp4|webm|m4v|mov)(?:\\?[^\\s\\\"'<>]*)?"
    );
    private static final Pattern IMAGE_URL = Pattern.compile(
            "(?i)https?://[^\\s\\\"'<>]+?\\.(?:jpe?g|png|webp|avif|gif|bmp|heic|heif)(?:\\?[^\\s\\\"'<>]*)?"
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
    private static final Pattern CDN_ENDPOINT = Pattern.compile(
            "(?s)\\bcdnEndpoint\\s*:\\s*([\\\"'])(.*?)\\1\\s*(?:,|$)"
    );
    private static final Pattern JS_CDN = Pattern.compile(
            "(?is)\\bjsCDN\\s*=\\s*([\\\"'])(.*?)\\1"
    );
    private static final Pattern SIGN_URL = Pattern.compile(
            "(?is)\\bsignUrl\\s*=\\s*([\\\"'])(.*?)\\1"
    );
    private static final Pattern SIZE = Pattern.compile("(?i)\\bsize\\s*:\\s*([0-9]+)");
    private static final Pattern DOUBLE_THUMB_EXTENSION = Pattern.compile(
            "(?i)\\.(?:mp4|m4v|webm|mov|mkv|ts|m3u8|mpd|jpe?g|png|webp|gif|bmp|avif|heic|heif)" +
                    "(\\.(?:jpe?g|png|webp|gif|avif))$"
    );

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
        return parseAlbumIndex(fetchDocument(context, searchUrl(query, page)));
    }

    public static String searchUrl(String query) {
        return searchUrl(query, 1);
    }

    public static String searchUrl(String query, int page) {
        String encoded;
        try {
            encoded = URLEncoder.encode(query == null ? "" : query.trim(), "UTF-8");
        } catch (Exception ignored) {
            encoded = query == null ? "" : query.trim();
        }
        return INDEX + "?search=" + encoded + "&mode=broad&page=" + Math.max(1, page);
    }

    public List<NativeContentItem> fetchAlbum(Context context, String albumUrl, int page)
            throws IOException {
        String canonical = normalizeUrl(albumUrl, INDEX);
        Matcher album = ALBUM_PATH.matcher(canonical);
        if (!album.find()) throw new IOException("Not a Bunkr album URL");

        int safePage = Math.max(1, page);
        String albumPath = "/a/" + album.group(1);
        Document doc = fetchBunkrDocument(
                context,
                canonical,
                albumPath + "?page=" + safePage
        );
        String origin = origin(doc.location());
        LinkedHashMap<String, String> artwork = parseFileArtwork(doc);
        ArrayList<NativeContentItem> all = parseAlbumFiles(doc, origin, artwork);
        if (all.isEmpty()) all.addAll(parseAlbumDom(doc, origin));

        // Older layouts only expose file metadata in Advanced View. Keep it as a first-page
        // fallback, but use Bunkr's normal server-side pages for large albums so a phone never
        // needs to download tens of thousands of records in one response.
        if (all.isEmpty() && safePage == 1) {
            Document advanced = fetchBunkrDocument(
                    context,
                    doc.location(),
                    albumPath + "?advanced=1"
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

        Document doc = fetchBunkrDocument(
                context,
                canonical,
                "/a/" + album.group(1) + "?page=1"
        );
        String pageOrigin = origin(doc.location());
        for (String image : parseFileArtwork(doc).values()) {
            if (image != null && !image.trim().isEmpty()) return image.trim();
        }

        for (Element script : doc.select("script")) {
            String body = script.data().isEmpty() ? script.html() : script.data();
            Matcher thumbnails = THUMBNAIL.matcher(body);
            while (thumbnails.find()) {
                String image = normalizeThumbnailUrl(
                        thumbnails.group(2),
                        pageOrigin,
                        ""
                );
                if (!image.isEmpty()) return image;
            }
        }

        Element socialImage = doc.selectFirst(
                "meta[property=og:image][content],meta[name=twitter:image][content]"
        );
        return socialImage == null
                ? ""
                : normalizeThumbnailUrl(socialImage.attr("content"), pageOrigin, "");
    }

    /** Finds one useful creator preview without exhaustively probing an album for a perfect ratio. */
    public CreatorArtwork fetchCreatorArtworkPreview(Context context, String albumUrl)
            throws IOException {
        List<NativeContentItem> files = fetchAlbum(context, albumUrl, 1);
        NativeContentItem firstImage = null;
        for (NativeContentItem file : files) {
            if (isCoverImage(file)) {
                firstImage = file;
                break;
            }
        }
        if (firstImage == null) throw new IOException("No creator image was available");
        String thumbnail = firstImage.imageUrl == null ? "" : firstImage.imageUrl.trim();
        if (!thumbnail.isEmpty()) {
            return new CreatorArtwork(thumbnail, firstImage.url, firstImage.url, false);
        }
        throw new IOException("No creator image URL was available");
    }

    /** Upgrades a creator preview to its original image when Bunkr's file endpoint responds. */
    public CreatorArtwork resolveCreatorArtwork(Context context, CreatorArtwork preview)
            throws IOException {
        if (preview == null || preview.pageUrl.isEmpty()) {
            throw new IOException("Creator image page was unavailable");
        }
        CrazyShitRepository.StreamInfo resolved = resolvePlayable(context, preview.pageUrl);
        if (!isImageName(resolved.mediaUrl)) {
            throw new IOException("Creator file did not resolve to an image");
        }
        return new CreatorArtwork(
                resolved.mediaUrl,
                resolved.requestReferer,
                preview.pageUrl,
                true
        );
    }

    /** Finds a still image close to the requested card ratio, then resolves its original file. */
    public CrazyShitRepository.StreamInfo fetchBestCreatorArtwork(
            Context context,
            String creatorQuery,
            String preferredAlbumUrl,
            float targetAspectRatio
    ) throws IOException {
        float target = targetAspectRatio > 0f ? targetAspectRatio : 16f / 9f;
        LinkedHashMap<String, String> albumUrls = new LinkedHashMap<>();
        if (isAlbumUrl(preferredAlbumUrl)) albumUrls.put(preferredAlbumUrl, preferredAlbumUrl);
        String query = creatorQuery == null ? "" : creatorQuery.trim();
        if (!query.isEmpty()) {
            try {
                for (NativeContentItem album : searchAlbums(context, query, 1)) {
                    if (album != null && isAlbumUrl(album.url)) {
                        albumUrls.putIfAbsent(album.url, album.url);
                        if (albumUrls.size() >= COVER_ALBUM_LIMIT) break;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        CoverCandidate best = null;
        CoverCandidate firstImage = null;
        IOException last = null;
        int albumsChecked = 0;
        long deadline = SystemClock.elapsedRealtime() + COVER_LOOKUP_BUDGET_MS;
        outer:
        for (String albumUrl : albumUrls.keySet()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Creator artwork lookup was interrupted");
            }
            if (albumsChecked++ >= COVER_ALBUM_LIMIT) break;
            if (SystemClock.elapsedRealtime() >= deadline) break;
            List<NativeContentItem> files;
            try {
                files = fetchAlbum(context, albumUrl, 1);
            } catch (IOException error) {
                last = error;
                continue;
            }

            ArrayList<NativeContentItem> images = new ArrayList<>();
            for (NativeContentItem file : files) {
                if (isCoverImage(file)) images.add(file);
            }
            if (images.isEmpty()) continue;
            if (firstImage == null) firstImage = new CoverCandidate(images.get(0), Double.MAX_VALUE);

            int probes = Math.min(COVER_PROBES_PER_ALBUM, images.size());
            for (int probe = 0; probe < probes; probe++) {
                if (SystemClock.elapsedRealtime() >= deadline) break outer;
                int index = probes == 1
                        ? 0
                        : Math.round(probe * (images.size() - 1f) / (probes - 1f));
                NativeContentItem candidate = images.get(index);
                float ratio = imageAspectRatio(context, candidate.imageUrl, candidate.url);
                if (ratio <= 0f) continue;
                double score = Math.abs(Math.log(ratio / target));
                if (best == null || score < best.score) {
                    best = new CoverCandidate(candidate, score);
                }
                if (score <= COVER_GOOD_MATCH) break outer;
            }
        }

        CoverCandidate selected = best == null ? firstImage : best;
        if (selected != null) {
            try {
                CrazyShitRepository.StreamInfo resolved = resolvePlayable(
                        context,
                        selected.item.url
                );
                if (isImageName(resolved.mediaUrl)) return resolved;
            } catch (IOException error) {
                last = error;
            }
        }
        if (last != null) throw last;
        throw new IOException("No full-resolution creator image was available");
    }

    public CrazyShitRepository.StreamInfo resolvePlayable(Context context, String pageUrl)
            throws IOException {
        String canonical = normalizeUrl(pageUrl, INDEX);
        if (isDirectAsset(canonical)) {
            return new CrazyShitRepository.StreamInfo(canonical, pageUrl, fileTitle(canonical));
        }

        Document doc = isBunkrUrl(canonical)
                ? fetchBunkrDocument(context, canonical, pathAndQuery(canonical))
                : fetchDocument(context, canonical);
        String resolvedPageUrl = doc.location().isEmpty() ? canonical : doc.location();
        String title = clean(doc.selectFirst("h1") == null ? doc.title() : doc.selectFirst("h1").text());
        IOException last = null;
        String cdnUrl = scriptValue(doc, JS_CDN);
        String signUrl = scriptValue(doc, SIGN_URL);
        if (!cdnUrl.isEmpty()) {
            if (signUrl.isEmpty()) signUrl = DEFAULT_SIGN_URL;
            try {
                String signed = requestSignedCdnUrl(
                        context,
                        resolvedPageUrl,
                        cdnUrl,
                        signUrl
                );
                if (!signed.isEmpty() && !isMaintenanceVideo(signed)) {
                    return new CrazyShitRepository.StreamInfo(
                            signed, resolvedPageUrl, title, resolvedPageUrl
                    );
                }
                last = new IOException("Bunkr did not return a signed media URL");
            } catch (IOException error) {
                last = error;
            }
        } else if (!signUrl.isEmpty()) {
            last = new IOException("Bunkr's signed media data was incomplete");
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
                                mediaUrl,
                                resolvedPageUrl,
                                title,
                                downloadRoot + "/file/" + dataId
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
            String candidate = normalizeUrl(media.absUrl("src"), resolvedPageUrl);
            if (isDirectMedia(candidate) && !isMaintenanceVideo(candidate)) {
                return new CrazyShitRepository.StreamInfo(candidate, resolvedPageUrl, title);
            }
        }
        for (Element script : doc.select("script")) {
            String body = script.data().isEmpty() ? script.html() : script.data();
            Matcher direct = MEDIA_URL.matcher(body.replace("\\/", "/"));
            while (direct.find()) {
                String candidate = direct.group();
                if (!isMaintenanceVideo(candidate)) {
                    return new CrazyShitRepository.StreamInfo(candidate, resolvedPageUrl, title);
                }
            }
        }

        if (isImageName(title)) {
            for (Element image : doc.select(
                    "main img[src],.lightgallery img[src],img[data-src],picture source[src]"
            )) {
                String candidate = image.hasAttr("data-src")
                        ? normalizeUrl(image.attr("data-src"), resolvedPageUrl)
                        : normalizeUrl(image.absUrl("src"), resolvedPageUrl);
                if (isImageName(candidate) && !candidate.contains("/thumbs/")) {
                    return new CrazyShitRepository.StreamInfo(
                            candidate,
                            resolvedPageUrl,
                            title
                    );
                }
            }
            for (Element script : doc.select("script")) {
                String body = script.data().isEmpty() ? script.html() : script.data();
                Matcher direct = IMAGE_URL.matcher(body.replace("\\/", "/"));
                while (direct.find()) {
                    String candidate = direct.group();
                    if (!candidate.contains("/thumbs/")) {
                        return new CrazyShitRepository.StreamInfo(
                                candidate,
                                resolvedPageUrl,
                                title
                        );
                    }
                }
            }
            Element socialImage = doc.selectFirst(
                    "meta[property=og:image][content],meta[name=twitter:image][content]"
            );
            if (socialImage != null) {
                String candidate = normalizeUrl(socialImage.attr("content"), resolvedPageUrl);
                if (isImageName(candidate)) {
                    return new CrazyShitRepository.StreamInfo(
                            candidate,
                            resolvedPageUrl,
                            title
                    );
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
            String mime = type.toLowerCase(Locale.US);
            boolean video = isPlayableName(title) || isPlayableName(slug) ||
                    isPlayableName(extension) || "video".equals(mime) ||
                    mime.startsWith("video/");
            boolean imageFile = isImageName(title) || isImageName(slug) ||
                    isImageName(extension) || "image".equals(mime) ||
                    mime.startsWith("image/");
            if (!video && !imageFile) continue;
            String image = normalizeThumbnailUrl(
                    thumbnail,
                    origin,
                    jsValue(item, CDN_ENDPOINT)
            );
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
                .timeout(12000)
                .maxBodySize(1024 * 1024)
                .ignoreContentType(true)
                .ignoreHttpErrors(false)
                .followRedirects(true);
        addCookies(context, connection, endpoint);
        Connection.Response response = connection.execute();
        try {
            JSONObject data = new JSONObject(response.body());
            String url = data.optString("url", "");
            boolean needsSignature = false;
            if (data.optBoolean("encrypted", false)) {
                long timestamp = data.optLong("timestamp", 0L);
                url = decryptXor(url, "SECRET_KEY_" + (timestamp / 3600L));
            }
            if (url.isEmpty()) {
                String mediaFiles = data.optString("mediafiles", "");
                String path = data.optString("path", "");
                if (!mediaFiles.isEmpty() && !path.isEmpty()) {
                    url = joinCdnPath(mediaFiles, path);
                    needsSignature = true;
                }
            }
            if (url.isEmpty()) {
                throw new IOException("Bunkr's download response did not contain a media URL");
            }
            url = normalizeUrl(url, endpoint);
            return needsSignature
                    ? requestSignedCdnUrl(context, referer, url, DEFAULT_SIGN_URL)
                    : url;
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
            path = cdn.getPath();
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

        String requestUrl = signUrl + (signUrl.contains("?") ? "&" : "?") +
                "path=" + encodedPath;
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            Connection connection = Jsoup.connect(requestUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Origin", origin(pageUrl))
                    .referrer(pageUrl)
                    .timeout(4000)
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
                last = error;
            } catch (Exception error) {
                last = new IOException("Bunkr's signing response was invalid", error);
            }
        }
        throw last == null ? new IOException("Bunkr signing failed") : last;
    }

    private Document fetchBunkrDocument(
            Context context,
            String preferredUrl,
            String pathAndQuery
    ) throws IOException {
        LinkedHashMap<String, Boolean> origins = new LinkedHashMap<>();
        String requestedOrigin = origin(preferredUrl);
        if (isBunkrUrl(requestedOrigin)) origins.put(requestedOrigin, true);
        if (!preferredPageOrigin.isEmpty()) origins.put(preferredPageOrigin, true);
        for (String candidate : PAGE_ORIGINS) origins.put(candidate, true);

        IOException last = null;
        for (String candidate : origins.keySet()) {
            try {
                Document doc = fetchDocument(context, candidate + pathAndQuery);
                if (isBlockedPage(doc)) {
                    last = new IOException("Bunkr page host was blocked");
                    continue;
                }
                String loadedOrigin = origin(doc.location());
                if (isBunkrUrl(loadedOrigin)) preferredPageOrigin = loadedOrigin;
                return doc;
            } catch (IOException error) {
                last = error;
            }
        }
        throw last == null ? new IOException("No Bunkr page host was available") : last;
    }

    private Document fetchDocument(Context context, String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(isBunkrUrl(url) ? origin(url) + "/" : INDEX)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .timeout(12000)
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

    private float imageAspectRatio(Context context, String imageUrl, String referer) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) return 0f;
        try {
            Connection connection = Jsoup.connect(imageUrl)
                    .userAgent(USER_AGENT)
                    .referrer(referer == null || referer.isEmpty() ? INDEX : referer)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("Range", "bytes=0-" + (COVER_PROBE_BYTES - 1))
                    .timeout(8000)
                    .maxBodySize(COVER_PROBE_BYTES)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(false)
                    .followRedirects(true);
            addCookies(context, connection, imageUrl);
            byte[] data = connection.execute().bodyAsBytes();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            return bounds.outWidth > 0 && bounds.outHeight > 0
                    ? (float) bounds.outWidth / bounds.outHeight
                    : 0f;
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private boolean isCoverImage(NativeContentItem item) {
        if (item == null || !item.isImage() || item.imageUrl == null ||
                item.imageUrl.trim().isEmpty()) return false;
        String name = (item.title + " " + item.url).toLowerCase(Locale.US);
        return !name.contains(".gif") && !name.contains(".bmp");
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
            String value = normalizeThumbnailUrl(image.absUrl(attr), root.baseUri(), "");
            if (value.isEmpty()) {
                value = normalizeThumbnailUrl(image.attr(attr), root.baseUri(), "");
            }
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

    private boolean isDirectAsset(String url) {
        return isPlayableName(url) || isImageName(url);
    }

    private boolean isMaintenanceVideo(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.endsWith("/maint.mp4") || lower.contains("/maint.mp4?") ||
                lower.endsWith("/maintenance-vid.mp4") || lower.contains("/maintenance-vid.mp4?");
    }

    private boolean isBlockedPage(Document doc) {
        if (doc == null) return true;
        String title = doc.title().toLowerCase(Locale.US);
        if (title.contains("just a moment") || title.contains("attention required") ||
                title.contains("ddos-guard") || title.contains("404") ||
                title.contains("not found")) return true;
        String text = doc.text().toLowerCase(Locale.US);
        return text.contains("checking your browser before accessing") ||
                text.contains("server under maintenance") ||
                text.contains("album not found") || text.contains("file not found");
    }

    private String joinCdnPath(String endpoint, String path) {
        String cleanPath = unescapeScriptUrl(path);
        if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
            return cleanPath;
        }
        String base = normalizeUrl(unescapeScriptUrl(endpoint), DOWNLOAD_ROOT);
        if (base.isEmpty()) return "";
        return origin(base) + (cleanPath.startsWith("/") ? cleanPath : "/" + cleanPath);
    }

    private String pathAndQuery(String url) throws IOException {
        try {
            URI uri = new URI(url);
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
                path += "?" + uri.getRawQuery();
            }
            return path;
        } catch (Exception error) {
            throw new IOException("Bunkr page address was invalid", error);
        }
    }

    private String normalizeThumbnailUrl(String value, String base, String cdnEndpoint) {
        String raw = unescapeScriptUrl(value);
        if (raw.isEmpty()) return "";
        String normalized = normalizeUrl(raw, base);
        if (!normalized.startsWith("http")) {
            String endpoint = unescapeScriptUrl(cdnEndpoint);
            if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
                normalized = origin(endpoint) + (raw.startsWith("/") ? raw : "/" + raw);
            } else {
                normalized = origin(base) + (raw.startsWith("/") ? raw : "/" + raw);
            }
        }

        int query = normalized.indexOf('?');
        int fragment = normalized.indexOf('#');
        int suffixAt = query < 0 ? fragment : fragment < 0 ? query : Math.min(query, fragment);
        String path = suffixAt < 0 ? normalized : normalized.substring(0, suffixAt);
        String suffix = suffixAt < 0 ? "" : normalized.substring(suffixAt);
        String fixed = DOUBLE_THUMB_EXTENSION.matcher(path).replaceFirst("$1");
        return fixed + suffix;
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

    private static final class CoverCandidate {
        final NativeContentItem item;
        final double score;

        CoverCandidate(NativeContentItem item, double score) {
            this.item = item;
            this.score = score;
        }
    }

    public static final class CreatorArtwork {
        public final String imageUrl;
        public final String requestReferer;
        public final String pageUrl;
        public final boolean fullResolution;

        CreatorArtwork(
                String imageUrl,
                String requestReferer,
                String pageUrl,
                boolean fullResolution
        ) {
            this.imageUrl = imageUrl == null ? "" : imageUrl;
            this.requestReferer = requestReferer == null ? "" : requestReferer;
            this.pageUrl = pageUrl == null ? "" : pageUrl;
            this.fullResolution = fullResolution;
        }
    }
}
