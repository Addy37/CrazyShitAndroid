package com.webapp.crazyshit;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Approved Reddit Data API source used by MAYHEM and ShitTok.
 *
 * Reddit API access is intentionally disabled unless an approved installed-app client ID and
 * Reddit contact username are supplied at build time. The repository never falls back to HTML,
 * public JSON scraping, or browser automation.
 */
final class RedditSourceRepository {
    enum Placement { MAYHEM, SHITTOK }

    static final String SOURCE_NAME = "MAYHEM";
    private static final String API_ORIGIN = "https://oauth.reddit.com";
    private static final String TOKEN_URL = "https://www.reddit.com/api/v1/access_token";
    private static final String REDDIT_ORIGIN = "https://www.reddit.com";
    private static final String SUBREDDITS = "CrazyFuckingVideos+PublicFreakout+fightporn";
    private static final String MEDIA_PREFIX = "reddit-media:";
    private static final String PREFS = "reddit_source";
    private static final String DEVICE_ID = "oauth_device_id";
    private static final int LISTING_LIMIT = 100;
    private static final int HOME_TARGET = 28;
    private static final int SHITTOK_TARGET = 12;
    private static final float WIDE_MIN = 1.25f;
    private static final float TALL_MAX = 0.80f;
    private static final Pattern POST_ID = Pattern.compile(
            "(?i)(?:reddit\\.com)?/r/[^/]+/comments/([a-z0-9]+)"
    );

    private static final Object TOKEN_LOCK = new Object();
    private static String sharedToken = "";
    private static long sharedTokenExpiresAtMs;

    private final Map<Integer, String> mayhemAfterByPage = new LinkedHashMap<>();
    private String shitTokAfter = "";

    RedditSourceRepository() {
        mayhemAfterByPage.put(1, "");
    }

    static boolean isConfigured() {
        return !clean(BuildConfig.REDDIT_CLIENT_ID).isEmpty()
                && !clean(BuildConfig.REDDIT_API_USERNAME).isEmpty();
    }

    static String configurationMessage() {
        return "MAYHEM needs approved Reddit API access in this build.";
    }

    List<NativeContentItem> fetchMayhem(Context context, int page) throws IOException {
        int safePage = Math.max(1, page);
        String after = mayhemAfterByPage.get(safePage);
        if (safePage > 1 && after == null) {
            throw new IOException("MAYHEM pagination state expired. Refresh the feed.");
        }
        ListingResult result = fetchListing(context, Placement.MAYHEM, after, HOME_TARGET);
        mayhemAfterByPage.put(safePage + 1, result.after);
        return result.items;
    }

    List<NativeContentItem> fetchShitTokBatch(Context context) {
        if (!isConfigured()) return Collections.emptyList();
        try {
            ListingResult result = fetchListing(context, Placement.SHITTOK, shitTokAfter, SHITTOK_TARGET);
            shitTokAfter = result.after;
            if (shitTokAfter.isEmpty()) shitTokAfter = "";
            return result.items;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    void resetShitTok() {
        shitTokAfter = "";
    }

    private ListingResult fetchListing(
            Context context,
            Placement placement,
            String after,
            int target
    ) throws IOException {
        requireConfigured();
        ArrayList<NativeContentItem> collected = new ArrayList<>();
        String cursor = clean(after);
        String next = cursor;

        for (int request = 0; request < 3 && collected.size() < target; request++) {
            StringBuilder url = new StringBuilder(API_ORIGIN)
                    .append("/r/").append(SUBREDDITS)
                    .append("/hot?raw_json=1&limit=").append(LISTING_LIMIT);
            if (!next.isEmpty()) {
                url.append("&after=").append(encode(next));
            }

            JSONObject root = requestJson(context, url.toString());
            ListingResult page = parseListing(root.toString(), placement, target - collected.size());
            for (NativeContentItem item : page.items) {
                if (item == null || item.url.isEmpty()) continue;
                boolean duplicate = false;
                for (NativeContentItem existing : collected) {
                    if (item.url.equals(existing.url)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) collected.add(item);
            }
            next = page.after;
            if (next.isEmpty()) break;
        }

        return new ListingResult(collected, next);
    }

    CrazyShitRepository.StreamInfo resolvePlayable(Context context, NativeContentItem item)
            throws IOException {
        if (item == null) return null;
        MediaPayload stored = decodeMedia(item.searchQuery);
        if (stored != null) return stored.toStreamInfo(item.url, item.title);
        return resolvePlayable(context, item.url);
    }

    CrazyShitRepository.StreamInfo resolvePlayable(Context context, String postUrl)
            throws IOException {
        if (!isRedditUrl(postUrl)) return null;
        requireConfigured();
        String id = postId(postUrl);
        if (id.isEmpty()) return null;

        JSONObject root = requestJson(
                context,
                API_ORIGIN + "/api/info?id=t3_" + encode(id) + "&raw_json=1"
        );
        ListingResult listing = parseListing(root.toString(), null, 1);
        if (listing.items.isEmpty()) return null;
        NativeContentItem refreshed = listing.items.get(0);
        MediaPayload payload = decodeMedia(refreshed.searchQuery);
        return payload == null ? null : payload.toStreamInfo(postUrl, refreshed.title);
    }

    static boolean isRedditItem(NativeContentItem item) {
        return item != null && isRedditUrl(item.url) && decodeMedia(item.searchQuery) != null;
    }

    static boolean isRedditUrl(String value) {
        String url = clean(value).toLowerCase(Locale.US);
        return url.startsWith("https://www.reddit.com/")
                || url.startsWith("https://reddit.com/")
                || url.startsWith("https://old.reddit.com/");
    }

    static Placement placementFor(int width, int height) {
        if (width <= 0 || height <= 0) return null;
        float ratio = width / (float) height;
        if (ratio >= WIDE_MIN) return Placement.MAYHEM;
        if (ratio <= TALL_MAX) return Placement.SHITTOK;
        return null;
    }

    static ListingResult parseListing(String rawJson, Placement placement, int limit) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        String after = "";
        try {
            JSONObject root = new JSONObject(rawJson == null ? "{}" : rawJson);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return new ListingResult(result, after);
            after = clean(data.optString("after"));
            JSONArray children = data.optJSONArray("children");
            if (children == null) return new ListingResult(result, after);

            for (int i = 0; i < children.length() && result.size() < Math.max(1, limit); i++) {
                JSONObject child = children.optJSONObject(i);
                JSONObject post = child == null ? null : child.optJSONObject("data");
                NativeContentItem item = itemFromPost(post, placement);
                if (item != null) result.add(item);
            }
        } catch (Exception ignored) {
        }
        return new ListingResult(result, after);
    }

    private static NativeContentItem itemFromPost(JSONObject original, Placement placement) {
        if (original == null) return null;
        JSONObject post = postWithVideo(original);
        JSONObject video = redditVideo(post);
        if (video == null || video.optBoolean("is_gif", false)) return null;

        int width = video.optInt("width", 0);
        int height = video.optInt("height", 0);
        Placement actual = placementFor(width, height);
        if (actual == null || (placement != null && actual != placement)) return null;

        String fallback = html(video.optString("fallback_url"));
        String hls = html(video.optString("hls_url"));
        String dash = html(video.optString("dash_url"));
        if (fallback.isEmpty() && hls.isEmpty() && dash.isEmpty()) return null;

        String permalink = clean(original.optString("permalink"));
        if (permalink.isEmpty()) permalink = clean(post.optString("permalink"));
        String pageUrl = permalink.startsWith("http")
                ? permalink
                : permalink.isEmpty() ? "" : REDDIT_ORIGIN + permalink;
        if (pageUrl.isEmpty()) return null;

        String title = clean(original.optString("title"));
        if (title.isEmpty()) title = clean(post.optString("title"));
        if (title.isEmpty()) title = "Reddit video";

        String subreddit = clean(original.optString("subreddit"));
        if (subreddit.isEmpty()) subreddit = clean(post.optString("subreddit"));
        String author = clean(original.optString("author"));
        if (author.isEmpty()) author = clean(post.optString("author"));
        String attribution = attribution(subreddit, author);

        String image = previewImage(original);
        if (image.isEmpty()) image = previewImage(post);

        MediaPayload payload = new MediaPayload(fallback, hls, dash, width, height);
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                pageUrl,
                image,
                "",
                attribution,
                "",
                attribution,
                encodeMedia(payload)
        );
    }

    private static JSONObject postWithVideo(JSONObject post) {
        if (redditVideo(post) != null) return post;
        JSONArray crossposts = post.optJSONArray("crosspost_parent_list");
        if (crossposts == null) return post;
        for (int i = 0; i < crossposts.length(); i++) {
            JSONObject candidate = crossposts.optJSONObject(i);
            if (redditVideo(candidate) != null) return candidate;
        }
        return post;
    }

    private static JSONObject redditVideo(JSONObject post) {
        if (post == null) return null;
        JSONObject secure = post.optJSONObject("secure_media");
        JSONObject video = secure == null ? null : secure.optJSONObject("reddit_video");
        if (video != null) return video;
        JSONObject media = post.optJSONObject("media");
        video = media == null ? null : media.optJSONObject("reddit_video");
        if (video != null) return video;
        JSONObject preview = post.optJSONObject("preview");
        return preview == null ? null : preview.optJSONObject("reddit_video_preview");
    }

    private static String previewImage(JSONObject post) {
        if (post == null) return "";
        JSONObject preview = post.optJSONObject("preview");
        JSONArray images = preview == null ? null : preview.optJSONArray("images");
        JSONObject first = images == null ? null : images.optJSONObject(0);
        JSONObject source = first == null ? null : first.optJSONObject("source");
        String url = source == null ? "" : html(source.optString("url"));
        if (!url.isEmpty()) return url;
        String thumbnail = html(post.optString("thumbnail"));
        return thumbnail.startsWith("http") ? thumbnail : "";
    }

    private JSONObject requestJson(Context context, String url) throws IOException {
        String token = accessToken(context);
        HttpURLConnection connection = open(url, "GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("User-Agent", userAgent());
        connection.setRequestProperty("Accept", "application/json");
        return readJson(connection);
    }

    private String accessToken(Context context) throws IOException {
        long now = System.currentTimeMillis();
        synchronized (TOKEN_LOCK) {
            if (!sharedToken.isEmpty() && now + 60_000L < sharedTokenExpiresAtMs) {
                return sharedToken;
            }

            String deviceId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(DEVICE_ID, "");
            if (clean(deviceId).isEmpty()) {
                deviceId = UUID.randomUUID().toString();
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(DEVICE_ID, deviceId).apply();
            }

            HttpURLConnection connection = open(TOKEN_URL, "POST");
            String basic = BuildConfig.REDDIT_CLIENT_ID + ":";
            connection.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString(
                            basic.getBytes(StandardCharsets.UTF_8),
                            Base64.NO_WRAP
                    )
            );
            connection.setRequestProperty("User-Agent", userAgent());
            connection.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8"
            );
            connection.setDoOutput(true);
            String body = "grant_type="
                    + encode("https://oauth.reddit.com/grants/installed_client")
                    + "&device_id=" + encode(deviceId);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }

            JSONObject response = readJson(connection);
            String token = clean(response.optString("access_token"));
            if (token.isEmpty()) throw new IOException("Reddit OAuth did not return an access token.");
            long expiresSeconds = Math.max(300L, response.optLong("expires_in", 3600L));
            sharedToken = token;
            sharedTokenExpiresAtMs = now + expiresSeconds * 1000L;
            return sharedToken;
        }
    }

    private static HttpURLConnection open(String value, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(18_000);
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private static JSONObject readJson(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("Reddit request failed (" + status + ").");
        }
        try {
            return new JSONObject(body);
        } catch (Exception badJson) {
            throw new IOException("Reddit returned invalid JSON.", badJson);
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }

    private static String userAgent() {
        return "android:com.addy37.crazyshitunofficial:"
                + BuildConfig.VERSION_NAME
                + " (by /u/" + clean(BuildConfig.REDDIT_API_USERNAME) + ")";
    }

    private static void requireConfigured() throws IOException {
        if (!isConfigured()) throw new IOException(configurationMessage());
    }

    private static String postId(String url) {
        Matcher matcher = POST_ID.matcher(clean(url));
        return matcher.find() ? clean(matcher.group(1)) : "";
    }

    private static String attribution(String subreddit, String author) {
        ArrayList<String> parts = new ArrayList<>();
        if (!clean(subreddit).isEmpty()) parts.add("r/" + clean(subreddit));
        if (!clean(author).isEmpty()) parts.add("u/" + clean(author));
        parts.add("via Reddit");
        return android.text.TextUtils.join(" · ", parts);
    }

    private static String encodeMedia(MediaPayload payload) {
        try {
            JSONObject json = new JSONObject()
                    .put("fallback", payload.fallback)
                    .put("hls", payload.hls)
                    .put("dash", payload.dash)
                    .put("w", payload.width)
                    .put("h", payload.height);
            return MEDIA_PREFIX + Base64.encodeToString(
                    json.toString().getBytes(StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
            );
        } catch (Exception ignored) {
            return "";
        }
    }

    private static MediaPayload decodeMedia(String encoded) {
        String value = clean(encoded);
        if (!value.startsWith(MEDIA_PREFIX)) return null;
        try {
            byte[] raw = Base64.decode(
                    value.substring(MEDIA_PREFIX.length()),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
            );
            JSONObject json = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            return new MediaPayload(
                    html(json.optString("fallback")),
                    html(json.optString("hls")),
                    html(json.optString("dash")),
                    json.optInt("w"),
                    json.optInt("h")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String html(String value) {
        return clean(value)
                .replace("&amp;", "&")
                .replace("&#x2F;", "/")
                .replace("&#39;", "'");
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static final class ListingResult {
        final List<NativeContentItem> items;
        final String after;

        ListingResult(List<NativeContentItem> items, String after) {
            this.items = items == null ? Collections.emptyList() : items;
            this.after = clean(after);
        }
    }

    private static final class MediaPayload {
        final String fallback;
        final String hls;
        final String dash;
        final int width;
        final int height;

        MediaPayload(String fallback, String hls, String dash, int width, int height) {
            this.fallback = clean(fallback);
            this.hls = clean(hls);
            this.dash = clean(dash);
            this.width = width;
            this.height = height;
        }

        CrazyShitRepository.StreamInfo toStreamInfo(String pageUrl, String title) {
            String media = !hls.isEmpty() ? hls : !dash.isEmpty() ? dash : fallback;
            if (media.isEmpty()) return null;
            return new CrazyShitRepository.StreamInfo(media, pageUrl, title, pageUrl);
        }
    }
}
