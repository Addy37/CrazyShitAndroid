package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
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

/** Creator search and image galleries shared by WikiFeet and WikiFeet X. */
final class WikiFeetRepository {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final int MAX_BODY = 12 * 1024 * 1024;

    enum Site {
        WIKIFEET("wikifeet", "WikiFeet", "https://wikifeet.com/",
                "pics.wikifeet.com", "thumbs.wikifeet.com"),
        WIKIFEET_X("wikifeetx", "WikiFeet X", "https://wikifeetx.com/",
                "pics.wikifeet.com", "thumbs.wikifeet.com");

        final String id;
        final String label;
        final String baseUrl;
        final String pictureHost;
        final String thumbnailHost;

        Site(String id, String label, String baseUrl, String pictureHost, String thumbnailHost) {
            this.id = id;
            this.label = label;
            this.baseUrl = baseUrl;
            this.pictureHost = pictureHost;
            this.thumbnailHost = thumbnailHost;
        }
    }

    static final class Creator {
        final Site site;
        final String name;
        final String url;
        final String imageUrl;
        final int photoCount;

        Creator(Site site, String name, String url, String imageUrl, int photoCount) {
            this.site = site;
            this.name = clean(name);
            this.url = normalizeProfileUrl(site, url);
            this.imageUrl = normalizeMediaUrl(site, imageUrl);
            this.photoCount = Math.max(0, photoCount);
        }

        NativeContentItem asItem() {
            String detail = site.label + (photoCount > 0 ? " · " + photoCount + " photos" : "");
            return new NativeContentItem(NativeContentItem.KIND_CREATOR, name, url,
                    imageUrl, "", url, "", detail, name);
        }
    }

    List<Creator> searchCreators(Context context, Site site, String query, int limit)
            throws IOException {
        String cleanQuery = clean(query);
        if (cleanQuery.length() < 2) return new ArrayList<>();
        int safeLimit = Math.max(1, Math.min(20, limit));
        String endpoint = searchUrl(site, cleanQuery);
        String body = fetchBody(context, endpoint, site.baseUrl, true);
        List<Creator> parsed = matching(parseSearch(body, site, endpoint, 100), cleanQuery, safeLimit);
        if (!parsed.isEmpty()) return parsed;

        // The search service can occasionally return an empty body for an exact name. Confirm the
        // corresponding profile before exposing it so predictive results never contain dead links.
        String guessed = site.baseUrl + profileSlug(cleanQuery);
        try {
            Creator exact = parseProfile(fetchBody(context, guessed, site.baseUrl, false), site, guessed);
            if (!exact.name.isEmpty()) parsed.add(exact);
        } catch (Exception ignored) { }
        return parsed;
    }

    private List<Creator> matching(List<Creator> creators, String query, int limit) {
        ArrayList<Creator> result = new ArrayList<>();
        if (creators != null) for (Creator creator : creators) {
            if (creator != null && CreatorNameMatcher.rank(creator.name, query) != Integer.MAX_VALUE) {
                result.add(creator);
            }
        }
        result.sort(java.util.Comparator.comparingInt(
                creator -> CreatorNameMatcher.rank(creator.name, query)));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    List<NativeContentItem> fetchCreatorMedia(
            Context context,
            Creator creator,
            int page,
            int pageSize
    ) throws IOException {
        if (creator == null || !isProfileUrl(creator.url, creator.site)) {
            throw new IOException("WikiFeet creator page was missing");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(120, pageSize));
        String body = fetchBody(context, creator.url, creator.site.baseUrl, false);
        return parseMedia(body, creator, safePage, safeSize);
    }

    static List<NativeContentItem> parseMedia(
            String body,
            Creator creator,
            int page,
            int pageSize
    ) throws IOException {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(120, pageSize));
        JSONObject data = profileData(body);
        String name = clean(data.optString("cname", creator.name));
        JSONArray gallery = data.optJSONArray("gallery");
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (gallery == null || name.isEmpty()) return result;
        int skip = (safePage - 1) * safeSize;
        int active = 0;
        for (int index = gallery.length() - 1; index >= 0 && result.size() < safeSize; index--) {
            JSONObject photo = gallery.optJSONObject(index);
            if (photo == null || photo.optInt("removed", 0) != 0) continue;
            long id = photo.optLong("pid", 0L);
            if (id <= 0L) continue;
            if (active++ < skip) continue;
            String original = originalUrl(creator.site, name, id);
            String thumbnail = thumbnailUrl(creator.site, id);
            String dimensions = dimensions(photo);
            result.add(new NativeContentItem(
                    NativeContentItem.KIND_IMAGE,
                    name + " #" + id,
                    original,
                    thumbnail,
                    creator.site.label,
                    creator.url,
                    "",
                    dimensions.isEmpty() ? creator.site.label : creator.site.label + " · " + dimensions
            ));
        }
        return result;
    }

    Creator fetchProfile(Context context, Site site, String profileUrl) throws IOException {
        String canonical = normalizeProfileUrl(site, profileUrl);
        if (!isProfileUrl(canonical, site)) throw new IOException("Invalid WikiFeet profile URL");
        return parseProfile(fetchBody(context, canonical, site.baseUrl, false), site, canonical);
    }

    CrazyShitRepository.StreamInfo resolvePlayable(String value) throws IOException {
        String url = normalizeMediaUrl(siteFor(value), value);
        if (!isOriginalImageUrl(url)) throw new IOException("Not a WikiFeet image URL");
        return new CrazyShitRepository.StreamInfo(url, url, fileTitle(url), refererFor(url));
    }

    static List<Creator> parseSearch(String body, Site site, String endpoint, int limit) {
        LinkedHashMap<String, Creator> result = new LinkedHashMap<>();
        String value = body == null ? "" : body.trim();
        if (value.startsWith("{") || value.startsWith("[")) {
            try { collectJson(new JSONTokener(value).nextValue(), site, result, limit); }
            catch (Exception ignored) { }
        }
        String bodyTree = assignedArray(value, "tbody");
        if (!bodyTree.isEmpty()) {
            try { collectJson(new JSONArray(bodyTree), site, result, limit); }
            catch (Exception ignored) { }
        }
        Document document = Jsoup.parse(value, endpoint);
        for (Element link : document.select("#searchresults a[href], a[href]")) {
            if (result.size() >= limit) break;
            String url = normalizeProfileUrl(site, link.absUrl("href"));
            if (!isProfileUrl(url, site)) continue;
            Element nameNode = link.selectFirst("div");
            String name = clean(nameNode == null ? link.ownText() : nameNode.text());
            if (name.isEmpty()) name = humanizeProfile(url);
            int count = firstNumber(link.select("small").text());
            String image = imageFrom(link, site);
            put(result, new Creator(site, name, url, image, count));
        }
        return limited(result, limit);
    }

    static Creator parseProfile(String body, Site site, String profileUrl) throws IOException {
        JSONObject data = profileData(body);
        String name = clean(data.optString("cname"));
        long creatorId = data.optLong("cid", 0L);
        if (name.isEmpty() || creatorId <= 0L) throw new IOException("WikiFeet profile was not found");
        JSONArray gallery = data.optJSONArray("gallery");
        int count = gallery == null ? 0 : gallery.length();
        String image = "";
        if (gallery != null) {
            for (int i = gallery.length() - 1; i >= 0; i--) {
                JSONObject photo = gallery.optJSONObject(i);
                long id = photo == null ? 0L : photo.optLong("pid", 0L);
                if (id > 0L && photo.optInt("removed", 0) == 0) {
                    image = thumbnailUrl(site, id);
                    break;
                }
            }
        }
        return new Creator(site, name, profileUrl, image, count);
    }

    static JSONObject profileData(String body) throws IOException {
        String object = assignedObject(body, "tdata");
        if (object.isEmpty()) throw new IOException("WikiFeet profile data was missing");
        try { return new JSONObject(object); }
        catch (JSONException error) { throw new IOException("WikiFeet profile data was invalid", error); }
    }

    static String assignedObject(String body, String variable) {
        return assignedValue(body, variable, '{', '}');
    }

    static String assignedArray(String body, String variable) {
        return assignedValue(body, variable, '[', ']');
    }

    private static String assignedValue(String body, String variable, char open, char close) {
        if (body == null || variable == null) return "";
        int name = body.indexOf(variable);
        while (name >= 0) {
            int equals = body.indexOf('=', name + variable.length());
            if (equals < 0) return "";
            int start = body.indexOf(open, equals + 1);
            if (start < 0) return "";
            boolean quoted = false;
            boolean escaped = false;
            char quote = 0;
            int depth = 0;
            for (int i = start; i < body.length(); i++) {
                char character = body.charAt(i);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (character == '\\') escaped = true;
                    else if (character == quote) quoted = false;
                    continue;
                }
                if (character == '\"' || character == '\'') {
                    quoted = true;
                    quote = character;
                } else if (character == open) depth++;
                else if (character == close && --depth == 0) return body.substring(start, i + 1);
            }
            name = body.indexOf(variable, name + variable.length());
        }
        return "";
    }

    static String searchUrl(Site site, String query) {
        return site.baseUrl + "search/" + encode(clean(query));
    }

    static boolean isWikiFeetUrl(String value) {
        return siteFor(value) != null;
    }

    static boolean isOriginalImageUrl(String value) {
        Site site = siteFor(value);
        if (site == null) return false;
        String host = host(value);
        return host.equals(site.pictureHost) && value.toLowerCase(Locale.US).matches(
                ".*\\.(?:jpe?g|png|webp)(?:[?#].*)?$");
    }

    static boolean isProfileUrl(String value, Site site) {
        if (site == null || !profileHost(value).equals(host(site.baseUrl))) return false;
        try {
            String path = new URI(value).getPath();
            if (path == null) return false;
            String cleanPath = path.replaceAll("^/+|/+$", "");
            return !cleanPath.isEmpty() && !cleanPath.contains("/") &&
                    !cleanPath.equalsIgnoreCase("search") &&
                    !cleanPath.equalsIgnoreCase("photos") &&
                    !cleanPath.equalsIgnoreCase("videos");
        } catch (Exception ignored) { return false; }
    }

    static String originalUrl(Site site, String creatorName, long id) {
        String name = clean(creatorName).replaceAll("\\s+", "-")
                .replaceAll("[^a-zA-Z-]", "");
        return "https://" + site.pictureHost + "/" + encodePath(name + "-Feet-" + id + ".jpg");
    }

    static String thumbnailUrl(Site site, long id) {
        return id <= 0L ? "" : "https://" + site.thumbnailHost + "/" + id + ".jpg";
    }

    private String fetchBody(Context context, String url, String referer, boolean ajax)
            throws IOException {
        if (!isWikiFeetUrl(url)) throw new IOException("Invalid WikiFeet URL");
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(clean(referer).isEmpty() ? refererFor(url) : referer)
                .header("Accept-Encoding", "identity")
                .header("Accept", ajax
                        ? "application/json,text/html,*/*;q=0.8"
                        : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .timeout(ajax ? 6_000 : 15_000)
                .maxBodySize(MAX_BODY)
                .followRedirects(true)
                .ignoreContentType(true)
                .ignoreHttpErrors(true);
        if (ajax) connection.header("X-Requested-With", "XMLHttpRequest");
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) { }
        Connection.Response response = connection.execute();
        if (response.statusCode() >= 400) {
            throw new IOException(siteFor(url).label + " returned HTTP " + response.statusCode());
        }
        String finalUrl = response.url().toString();
        if (!isWikiFeetUrl(finalUrl) || siteFor(finalUrl) != siteFor(url)) {
            throw new IOException("WikiFeet redirected outside its source");
        }
        return response.body();
    }

    private static void collectJson(
            Object value,
            Site site,
            LinkedHashMap<String, Creator> output,
            int limit
    ) {
        if (value == null || value == JSONObject.NULL || output.size() >= limit) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length() && output.size() < limit; i++) {
                collectJson(array.opt(i), site, output, limit);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        String name = firstString(object, "name", "cname", "title", "label");
        String url = firstString(object, "url", "href", "path", "slug", "fetchname");
        if (!url.isEmpty() && !url.startsWith("http") && !url.startsWith("/")) url = "/" + url;
        url = normalizeProfileUrl(site, url);
        if (!name.isEmpty() && isProfileUrl(url, site)) {
            String image = firstString(object, "image", "thumbnail", "thumb", "photo");
            long pid = firstLong(object, "pid", "picture_id", "photo_id");
            if (pid <= 0L) {
                String pictures = firstString(object, "pics");
                if (!pictures.isEmpty()) {
                    try { pid = Long.parseLong(pictures.split(",", 2)[0].trim()); }
                    catch (Exception ignored) { }
                }
            }
            if (image.isEmpty() && pid > 0L) image = thumbnailUrl(site, pid);
            int count = (int) firstLong(object, "photos", "count", "photo_count", "pictures");
            put(output, new Creator(site, name, url, image, count));
        }
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext() && output.size() < limit) collectJson(object.opt(keys.next()), site, output, limit);
    }

    private static void put(LinkedHashMap<String, Creator> output, Creator creator) {
        if (creator == null || creator.name.isEmpty() || !isProfileUrl(creator.url, creator.site)) return;
        output.putIfAbsent(creator.url, creator);
    }

    private static List<Creator> limited(LinkedHashMap<String, Creator> values, int limit) {
        ArrayList<Creator> output = new ArrayList<>(values.values());
        return output.size() > limit ? new ArrayList<>(output.subList(0, limit)) : output;
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value instanceof String && !clean((String) value).isEmpty()) return clean((String) value);
            if (value instanceof JSONObject) {
                String nested = firstString((JSONObject) value, "url", "src", "href");
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private static long firstLong(JSONObject object, String... keys) {
        for (String key : keys) {
            long value = object.optLong(key, 0L);
            if (value > 0L) return value;
        }
        return 0L;
    }

    private static String imageFrom(Element link, Site site) {
        Element image = link.selectFirst("img[src],img[data-src]");
        if (image == null) return "";
        String value = image.hasAttr("data-src") ? image.absUrl("data-src") : image.absUrl("src");
        return normalizeMediaUrl(site, value);
    }

    private static String dimensions(JSONObject photo) {
        int width = photo.optInt("pw", 0);
        int height = photo.optInt("ph", 0);
        return width > 0 && height > 0 ? width + " × " + height : "";
    }

    private static int firstNumber(String value) {
        try {
            String digits = clean(value).replaceAll("[^0-9].*$", "").replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception ignored) { return 0; }
    }

    private static String normalizeProfileUrl(Site site, String value) {
        String cleanValue = clean(value);
        if (site == null || cleanValue.isEmpty()) return "";
        if (cleanValue.startsWith("/")) cleanValue = site.baseUrl.substring(0, site.baseUrl.length() - 1) + cleanValue;
        else if (!cleanValue.startsWith("http://") && !cleanValue.startsWith("https://")) cleanValue = site.baseUrl + cleanValue;
        try {
            URI uri = new URI(cleanValue);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !profileHost(cleanValue).equals(host(site.baseUrl))) return "";
            return new URI("https", uri.getAuthority(), uri.getPath(), null, null).toASCIIString();
        } catch (Exception ignored) { return ""; }
    }

    private static String normalizeMediaUrl(Site site, String value) {
        String cleanValue = clean(value);
        if (cleanValue.isEmpty()) return "";
        try {
            URI uri = new URI(cleanValue);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return "";
            String host = host(cleanValue);
            if (site != null && !host.equals(site.pictureHost) && !host.equals(site.thumbnailHost)) return "";
            return uri.toASCIIString();
        } catch (Exception ignored) { return ""; }
    }

    private static Site siteFor(String value) {
        String host = host(value);
        for (Site site : Site.values()) {
            if (host.equals(host(site.baseUrl)) || host.equals("www." + host(site.baseUrl)) ||
                    host.equals(site.pictureHost) || host.equals(site.thumbnailHost)) return site;
        }
        return null;
    }

    private static String profileHost(String value) {
        String host = host(value);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String host(String value) {
        try {
            String host = new URI(clean(value)).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) { return ""; }
    }

    private static String refererFor(String value) {
        Site site = siteFor(value);
        return site == null ? "https://wikifeet.com/" : site.baseUrl;
    }

    private static String humanizeProfile(String value) {
        try {
            String path = new URI(value).getPath().replaceAll("^/+|/+$", "");
            return clean(path.replace('_', ' ').replace('-', ' ').replaceAll("\\([0-9]+\\)$", ""));
        } catch (Exception ignored) { return ""; }
    }

    private static String profileSlug(String value) {
        return encodePath(clean(value).replaceAll("\\s+", "_"));
    }

    private static String fileTitle(String value) {
        try {
            String path = new URI(value).getPath();
            int slash = path == null ? -1 : path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1).replace('-', ' ') : "WikiFeet photo";
        } catch (Exception ignored) { return "WikiFeet photo"; }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodePath(String value) {
        return encode(value).replace("%2F", "/");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

