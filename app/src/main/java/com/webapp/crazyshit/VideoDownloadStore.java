package com.webapp.crazyshit;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Resolves, queues, tracks and opens native video downloads. */
final class VideoDownloadStore {
    private static final String PREFS = "native_video_downloads";
    private static final String KEY_IDS = "ids";
    private static final String KEY_PREFIX = "entry_";
    private static final ExecutorService RESOLVER = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicInteger CUSTOM_ID_SEQUENCE = new AtomicInteger();

    private VideoDownloadStore() {
    }

    static void downloadPage(Context context, NativeContentItem item) {
        if (item == null) return;
        downloadPage(context, item.title, item.url, item.imageUrl);
    }

    static void downloadPage(Context context, String title, String pageUrl, String imageUrl) {
        if (context == null || pageUrl == null || pageUrl.trim().isEmpty()) return;
        Context app = context.getApplicationContext();
        toast(app, "Preparing download…");
        RESOLVER.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = PlayableSourceRouter.resolve(context, pageUrl);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            MAIN.post(() -> {
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    toast(app, "Couldn't find a downloadable video file.");
                    return;
                }
                String finalTitle = cleanTitle(title, resolved.title);
                enqueue(
                        app,
                        finalTitle,
                        pageUrl,
                        imageUrl,
                        resolved.mediaUrl,
                        defaultUserAgent(app),
                        cookiesFor(resolved.mediaUrl, pageUrl),
                        resolved.requestReferer
                );
            });
        });
    }

    static void downloadKnown(
            Context context,
            String title,
            String pageUrl,
            String imageUrl,
            String mediaUrl,
            String userAgent,
            String cookies
    ) {
        if (context == null) return;
        enqueue(
                context.getApplicationContext(),
                cleanTitle(title, "Video"),
                safe(pageUrl),
                safe(imageUrl),
                safe(mediaUrl),
                safe(userAgent).isEmpty() ? defaultUserAgent(context) : userAgent,
                safe(cookies).isEmpty() ? cookiesFor(mediaUrl, pageUrl) : cookies,
                safe(pageUrl)
        );
    }

    static void downloadKnown(
            Context context,
            String title,
            String pageUrl,
            String imageUrl,
            String mediaUrl,
            String userAgent,
            String cookies,
            String requestReferer
    ) {
        if (context == null) return;
        enqueue(
                context.getApplicationContext(),
                cleanTitle(title, "Video"),
                safe(pageUrl),
                safe(imageUrl),
                safe(mediaUrl),
                safe(userAgent).isEmpty() ? defaultUserAgent(context) : userAgent,
                safe(cookies).isEmpty() ? cookiesFor(mediaUrl, pageUrl) : cookies,
                safe(requestReferer).isEmpty() ? safe(pageUrl) : requestReferer
        );
    }

    private static synchronized void enqueue(
            Context context,
            String title,
            String pageUrl,
            String imageUrl,
            String mediaUrl,
            String userAgent,
            String cookies,
            String requestReferer
    ) {
        String lower = mediaUrl.toLowerCase(Locale.US);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            toast(context, "This video source can't be downloaded.");
            return;
        }
        if (lower.contains(".m3u8") || lower.contains(".mpd")) {
            toast(context, "This clip uses a streaming playlist and can't be saved as one video file.");
            return;
        }

        Entry existing = findExisting(context, pageUrl, mediaUrl);
        if (existing != null) {
            if (existing.status != DownloadManager.STATUS_FAILED) {
                toast(context, existing.status == DownloadManager.STATUS_SUCCESSFUL
                        ? "This video is already downloaded."
                        : "This video is already downloading.");
                return;
            }
            remove(context, existing);
        }

        String mime = mimeType(mediaUrl);
        String fileName = fileName(title, pageUrl.isEmpty() ? mediaUrl : pageUrl, mime);
        Map<String, String> requestHeaders = requestHeaders(
                mediaUrl, requestReferer, userAgent, cookies
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            long id = nextCustomId();
            try {
                saveMetadata(context, new Entry(
                        id,
                        title,
                        pageUrl,
                        imageUrl,
                        mediaUrl,
                        mime,
                        System.currentTimeMillis(),
                        DownloadManager.STATUS_PENDING,
                        0L,
                        -1L,
                        "",
                        0
                ));
                AcceleratedDownloadService.start(
                        context,
                        id,
                        title,
                        fileName,
                        mediaUrl,
                        mime,
                        requestHeaders
                );
                toast(context, "Fast download started in Downloads.");
            } catch (Exception ignored) {
                removeMetadata(context, id);
                toast(context, "Couldn't start the download.");
            }
            return;
        }

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            toast(context, "Android's download service isn't available.");
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mediaUrl));
            request.setTitle(title);
            request.setDescription("CrazyShit video");
            request.setMimeType(mime);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );
            for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                addHeader(request, header.getKey(), header.getValue());
            }

            long id = manager.enqueue(request);
            saveMetadata(context, new Entry(
                    id,
                    title,
                    pageUrl,
                    imageUrl,
                    mediaUrl,
                    mime,
                    System.currentTimeMillis(),
                    DownloadManager.STATUS_PENDING,
                    0L,
                    -1L,
                    "",
                    0
            ));
            toast(context, "Download started in Downloads.");
        } catch (Exception ignored) {
            toast(context, "Couldn't start the download.");
        }
    }

    static synchronized List<Entry> entries(Context context) {
        if (context == null) return Collections.emptyList();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> rawIds = prefs.getStringSet(KEY_IDS, Collections.emptySet());
        if (rawIds == null || rawIds.isEmpty()) return Collections.emptyList();

        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<Entry> custom = new ArrayList<>();
        HashMap<Long, Entry> metadata = new HashMap<>();
        for (String rawId : new HashSet<>(rawIds)) {
            try {
                long id = Long.parseLong(rawId);
                Entry entry = metadata(prefs.getString(KEY_PREFIX + id, ""));
                if (entry != null) {
                    metadata.put(id, entry);
                    if (id < 0L) custom.add(entry);
                    else ids.add(id);
                }
            } catch (Exception ignored) {
            }
        }
        if (ids.isEmpty()) {
            custom.sort(Comparator.comparingLong((Entry item) -> item.createdAt).reversed());
            return custom;
        }

        long[] filter = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) filter[i] = ids.get(i);
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return new ArrayList<>(metadata.values());

        ArrayList<Entry> result = new ArrayList<>(custom);
        HashSet<Long> found = new HashSet<>();
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(filter))) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int downloadedColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int localColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                int reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Entry base = metadata.get(id);
                    if (base == null) continue;
                    found.add(id);
                    result.add(base.withState(
                            value(cursor, statusColumn, DownloadManager.STATUS_PENDING),
                            longValue(cursor, downloadedColumn, 0L),
                            longValue(cursor, totalColumn, -1L),
                            stringValue(cursor, localColumn),
                            value(cursor, reasonColumn, 0)
                    ));
                }
            }
        } catch (Exception ignored) {
            for (Entry entry : metadata.values()) {
                if (entry.id >= 0L) result.add(entry);
            }
            for (Long id : metadata.keySet()) {
                if (id >= 0L) found.add(id);
            }
        }

        for (Long id : metadata.keySet()) {
            if (id >= 0L && !found.contains(id)) removeMetadata(context, id);
        }
        result.sort(Comparator.comparingLong((Entry item) -> item.createdAt).reversed());
        return result;
    }

    static void open(android.app.Activity activity, Entry entry) {
        if (activity == null || entry == null) return;
        if (entry.status != DownloadManager.STATUS_SUCCESSFUL) {
            toast(activity, statusText(entry));
            return;
        }
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = entry.id < 0L || manager == null ? null : manager.getUriForDownloadedFile(entry.id);
        if (uri == null && !entry.localUri.isEmpty()) uri = Uri.parse(entry.localUri);
        if (uri == null) {
            toast(activity, "The downloaded file is no longer available.");
            return;
        }
        Intent player = new Intent(activity, PlayerActivity.class);
        player.putExtra(PlayerActivity.EXTRA_MEDIA_URL, uri.toString());
        player.putExtra(PlayerActivity.EXTRA_PAGE_URL, entry.pageUrl);
        player.putExtra(PlayerActivity.EXTRA_TITLE, entry.title);
        player.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(player);
    }

    static synchronized void remove(Context context, Entry entry) {
        if (context == null || entry == null) return;
        if (entry.id < 0L) {
            AcceleratedDownloadService.cancel(context, entry.id);
            if (!entry.localUri.isEmpty()) {
                try {
                    context.getContentResolver().delete(Uri.parse(entry.localUri), null, null);
                } catch (Exception ignored) {
                }
            }
        } else {
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            try {
                if (manager != null) manager.remove(entry.id);
            } catch (Exception ignored) {
            }
        }
        removeMetadata(context, entry.id);
    }

    static String statusText(Entry entry) {
        if (entry == null) return "Unavailable";
        switch (entry.status) {
            case DownloadManager.STATUS_RUNNING:
                if (entry.totalBytes > 0L) {
                    return "Downloading  " + Math.round(entry.downloadedBytes * 100f / entry.totalBytes) + "%";
                }
                return "Downloading";
            case DownloadManager.STATUS_PAUSED:
                return "Paused by Android";
            case DownloadManager.STATUS_SUCCESSFUL:
                return "Ready offline";
            case DownloadManager.STATUS_FAILED:
                return "Download failed";
            case DownloadManager.STATUS_PENDING:
            default:
                return "Waiting to download";
        }
    }

    private static Entry findExisting(Context context, String pageUrl, String mediaUrl) {
        for (Entry entry : entries(context)) {
            if (!pageUrl.isEmpty() && pageUrl.equals(entry.pageUrl)) return entry;
            if (!mediaUrl.isEmpty() && mediaUrl.equals(entry.mediaUrl)) return entry;
        }
        return null;
    }

    private static void saveMetadata(Context context, Entry entry) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> storedIds = prefs.getStringSet(KEY_IDS, Collections.emptySet());
        HashSet<String> ids = storedIds == null
                ? new HashSet<>()
                : new HashSet<>(storedIds);
        ids.add(Long.toString(entry.id));
        JSONObject json = new JSONObject();
        json.put("id", entry.id);
        json.put("title", entry.title);
        json.put("page", entry.pageUrl);
        json.put("image", entry.imageUrl);
        json.put("media", entry.mediaUrl);
        json.put("mime", entry.mimeType);
        json.put("created", entry.createdAt);
        json.put("status", entry.status);
        json.put("downloaded", entry.downloadedBytes);
        json.put("total", entry.totalBytes);
        json.put("local", entry.localUri);
        json.put("reason", entry.reason);
        prefs.edit()
                .putStringSet(KEY_IDS, ids)
                .putString(KEY_PREFIX + entry.id, json.toString())
                .apply();
    }

    private static Entry metadata(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(raw);
            return new Entry(
                    json.getLong("id"),
                    json.optString("title", "Video"),
                    json.optString("page", ""),
                    json.optString("image", ""),
                    json.optString("media", ""),
                    json.optString("mime", "video/mp4"),
                    json.optLong("created", 0L),
                    json.optInt("status", DownloadManager.STATUS_PENDING),
                    json.optLong("downloaded", 0L),
                    json.optLong("total", -1L),
                    json.optString("local", ""),
                    json.optInt("reason", 0)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void removeMetadata(Context context, long id) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> storedIds = prefs.getStringSet(KEY_IDS, Collections.emptySet());
        HashSet<String> ids = storedIds == null
                ? new HashSet<>()
                : new HashSet<>(storedIds);
        ids.remove(Long.toString(id));
        prefs.edit().putStringSet(KEY_IDS, ids).remove(KEY_PREFIX + id).apply();
    }

    private static void addHeader(DownloadManager.Request request, String name, String value) {
        String cleanName = safe(name).replace("\r", "").replace("\n", "").trim();
        String clean = safe(value).replace("\r", "").replace("\n", "");
        if (!cleanName.isEmpty() && !clean.isEmpty()) request.addRequestHeader(cleanName, clean);
    }

    private static Map<String, String> requestHeaders(
            String mediaUrl,
            String pageUrl,
            String userAgent,
            String cookies
    ) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(
                ShitShowPlayableResolver.playbackHeaders(mediaUrl)
        );
        putHeaderIfMissing(headers, "User-Agent", userAgent);
        putHeaderIfMissing(headers, "Cookie", cookies);
        putHeaderIfMissing(headers, "Referer", pageUrl);
        try {
            Uri page = Uri.parse(pageUrl);
            if (page.getScheme() != null && page.getHost() != null) {
                putHeaderIfMissing(
                        headers,
                        "Origin",
                        page.getScheme() + "://" + page.getHost()
                );
            }
        } catch (Exception ignored) {
        }
        return headers;
    }

    private static void putHeaderIfMissing(Map<String, String> headers, String name, String value) {
        for (String existing : headers.keySet()) {
            if (name.equalsIgnoreCase(safe(existing))) return;
        }
        String clean = safe(value).replace("\r", "").replace("\n", "");
        if (!clean.isEmpty()) headers.put(name, clean);
    }

    private static long nextCustomId() {
        long base = System.currentTimeMillis() * 1_000L;
        return -(base + CUSTOM_ID_SEQUENCE.incrementAndGet() % 1_000);
    }

    static synchronized Entry entry(Context context, long id) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return metadata(prefs.getString(KEY_PREFIX + id, ""));
    }

    static synchronized void updateCustom(
            Context context,
            long id,
            int status,
            long downloaded,
            long total,
            String localUri,
            int reason
    ) {
        Entry current = entry(context, id);
        if (current == null) return;
        try {
            saveMetadata(context, current.withState(status, downloaded, total, localUri, reason));
        } catch (Exception ignored) {
        }
    }

    private static String cookiesFor(String mediaUrl, String pageUrl) {
        try {
            String cookies = CookieManager.getInstance().getCookie(mediaUrl);
            if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            return safe(cookies);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String defaultUserAgent(Context context) {
        try {
            return WebSettings.getDefaultUserAgent(context);
        } catch (Exception ignored) {
            return "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36";
        }
    }

    private static String mimeType(String url) {
        String lower = safe(url).toLowerCase(Locale.US);
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".m4v")) return "video/x-m4v";
        return "video/mp4";
    }

    private static String fileName(String title, String identity, String mime) {
        String clean = safe(title)
                .replaceAll("[^A-Za-z0-9._ -]+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.isEmpty()) clean = "CrazyShit video";
        if (clean.length() > 70) clean = clean.substring(0, 70).trim();
        String extension = "video/webm".equals(mime) ? ".webm"
                : "video/x-m4v".equals(mime) ? ".m4v" : ".mp4";
        return clean + "-" + Integer.toHexString(safe(identity).hashCode()) + extension;
    }

    private static String cleanTitle(String preferred, String fallback) {
        String title = safe(preferred).trim();
        if (title.isEmpty()) title = safe(fallback).trim();
        return title.isEmpty() ? "Video" : title;
    }

    private static int value(Cursor cursor, int column, int fallback) {
        return column < 0 || cursor.isNull(column) ? fallback : cursor.getInt(column);
    }

    private static long longValue(Cursor cursor, int column, long fallback) {
        return column < 0 || cursor.isNull(column) ? fallback : cursor.getLong(column);
    }

    private static String stringValue(Cursor cursor, int column) {
        return column < 0 || cursor.isNull(column) ? "" : safe(cursor.getString(column));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void toast(Context context, String message) {
        MAIN.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    static final class Entry {
        final long id;
        final String title;
        final String pageUrl;
        final String imageUrl;
        final String mediaUrl;
        final String mimeType;
        final long createdAt;
        final int status;
        final long downloadedBytes;
        final long totalBytes;
        final String localUri;
        final int reason;

        Entry(
                long id,
                String title,
                String pageUrl,
                String imageUrl,
                String mediaUrl,
                String mimeType,
                long createdAt,
                int status,
                long downloadedBytes,
                long totalBytes,
                String localUri,
                int reason
        ) {
            this.id = id;
            this.title = safe(title);
            this.pageUrl = safe(pageUrl);
            this.imageUrl = safe(imageUrl);
            this.mediaUrl = safe(mediaUrl);
            this.mimeType = safe(mimeType);
            this.createdAt = createdAt;
            this.status = status;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.localUri = safe(localUri);
            this.reason = reason;
        }

        Entry withState(int status, long downloaded, long total, String localUri, int reason) {
            return new Entry(
                    id, title, pageUrl, imageUrl, mediaUrl, mimeType, createdAt,
                    status, downloaded, total, localUri, reason
            );
        }
    }
}
