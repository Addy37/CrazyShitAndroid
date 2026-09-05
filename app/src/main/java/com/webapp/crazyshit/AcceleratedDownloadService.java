package com.webapp.crazyshit;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Foreground downloader that uses parallel byte ranges when the media host supports them. */
@RequiresApi(Build.VERSION_CODES.Q)
public final class AcceleratedDownloadService extends Service {
    private static final String CHANNEL_ID = "video_downloads";
    private static final int FOREGROUND_NOTIFICATION_ID = 8_201;
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final int MAX_SEGMENTS = 4;
    private static final long MIN_BYTES_PER_SEGMENT = 2L * 1024L * 1024L;
    private static final Pattern CONTENT_RANGE = Pattern.compile(
            "bytes\\s+\\d+-\\d+/(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final String EXTRA_ID = "id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_FILE_NAME = "file_name";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_MIME = "mime";
    private static final String EXTRA_HEADERS = "headers";

    private static final ExecutorService JOBS = Executors.newFixedThreadPool(2);
    private static final ConcurrentHashMap<Long, AtomicBoolean> CANCELLATIONS =
            new ConcurrentHashMap<>();
    private static final java.util.Set<Long> CANCELLED_BEFORE_START =
            ConcurrentHashMap.newKeySet();

    private static final ConcurrentHashMap<Long, java.util.Set<HttpURLConnection>> CONNECTIONS = new ConcurrentHashMap<>();
    private static final java.util.Set<Long> PAUSES = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, AtomicLong> lastProgressUpdates = new ConcurrentHashMap<>();
    private final AtomicInteger activeJobs = new AtomicInteger();

    static void start(
            Context context,
            long id,
            String title,
            String fileName,
            String url,
            String mime,
            Map<String, String> headers
    ) throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            json.put(header.getKey(), header.getValue());
        }
        Intent intent = new Intent(context, AcceleratedDownloadService.class);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_MIME, mime);
        intent.putExtra(EXTRA_HEADERS, json.toString());
        ContextCompat.startForegroundService(context, intent);
    }

    static void cancel(Context context, long id) {
        PAUSES.remove(id);
        AtomicBoolean cancellation = CANCELLATIONS.get(id);
        if (cancellation != null) { cancellation.set(true); disconnectAll(id); }
        else JOBS.execute(() -> deleteRecursively(directory(context, id)));
    }

    static boolean isActive(long id) { return CANCELLATIONS.containsKey(id); }

    static void pause(Context context, long id) {
        AtomicBoolean cancellation = CANCELLATIONS.get(id);
        if (cancellation != null) { PAUSES.add(id); cancellation.set(true); disconnectAll(id); }
    }

    private static void disconnectAll(long id) {
        java.util.Set<HttpURLConnection> connections = CONNECTIONS.remove(id);
        if (connections != null) for (HttpURLConnection connection : connections) connection.disconnect();
    }

    private static File directory(Context context, long id) {
        return new File(context.getFilesDir(), "download-parts/" + Math.abs(id));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        long id = intent.getLongExtra(EXTRA_ID, 0L);
        String title = clean(intent.getStringExtra(EXTRA_TITLE), "CrazyShit video");
        String fileName = clean(intent.getStringExtra(EXTRA_FILE_NAME), "CrazyShit video.mp4");
        String url = clean(intent.getStringExtra(EXTRA_URL), "");
        String mime = clean(intent.getStringExtra(EXTRA_MIME), "video/mp4");
        Map<String, String> headers = parseHeaders(intent.getStringExtra(EXTRA_HEADERS));
        if (id >= 0L || url.isEmpty()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (CANCELLED_BEFORE_START.remove(id) || VideoDownloadStore.entry(this, id) == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        showForeground(title, 0L, -1L);
        AtomicBoolean cancellation = new AtomicBoolean(false);
        if (CANCELLATIONS.putIfAbsent(id, cancellation) != null) return START_NOT_STICKY;
        activeJobs.incrementAndGet();
        JOBS.execute(() -> runDownload(id, title, fileName, url, mime, headers, cancellation));
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void runDownload(
            long id,
            String title,
            String fileName,
            String url,
            String mime,
            Map<String, String> headers,
            AtomicBoolean cancellation
    ) {
        Uri destination = null;
        File tempDirectory = directory(this, id);
        boolean completed = false;
        AtomicLong downloaded = new AtomicLong();
        long total = -1L;
        try {
            Probe probe = probe(id, url, headers, cancellation);
            total = probe.totalBytes;
            VideoDownloadStore.Entry previous = VideoDownloadStore.entry(this, id);
            if (previous == null) throw new CancelledException();
            if (!previous.localUri.isEmpty()) deleteDestination(Uri.parse(previous.localUri));
            File checkpoint = new File(tempDirectory, "resource.json");
            JSONObject saved = null;
            try { saved = new JSONObject(new String(java.nio.file.Files.readAllBytes(checkpoint.toPath()), java.nio.charset.StandardCharsets.UTF_8)); }
            catch (Exception ignored) { }
            int segments = probe.acceptsRanges && total > 0L
                    ? (total >= MIN_BYTES_PER_SEGMENT * 2L ? (int) Math.min(MAX_SEGMENTS, Math.max(2L, total / MIN_BYTES_PER_SEGMENT)) : 1)
                    : 0;
            if (saved == null || !probe.acceptsRanges || !DownloadResumePolicy.sameResource(
                    saved.optString("validator"), probe.validator, saved.optLong("total", -1L), total)
                    || saved.optInt("segments", -1) != segments) deleteRecursively(tempDirectory);
            if (!tempDirectory.exists() && !tempDirectory.mkdirs()) throw new IllegalStateException("No download storage");
            java.nio.file.Files.write(checkpoint.toPath(), new JSONObject().put("validator", probe.validator)
                    .put("total", total).put("segments", segments).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Map<String, String> validatedHeaders = new LinkedHashMap<>(headers);
            if (!probe.validator.isEmpty() && !probe.validator.startsWith("W/")) validatedHeaders.put("If-Range", probe.validator);
            destination = createDestination(fileName, mime);
            if (destination == null) throw new IllegalStateException("No Downloads destination");

            VideoDownloadStore.updateCustom(
                    this,
                    id,
                    DownloadManager.STATUS_RUNNING,
                    0L,
                    total,
                    destination.toString(),
                    0
            );

            List<File> parts;
            if (segments > 0) {
                try {
                    parts = downloadRanges(
                            id, title, url, validatedHeaders, total, segments, tempDirectory,
                            downloaded, cancellation
                    );
                } catch (CancelledException cancelled) {
                    throw cancelled;
                } catch (Exception rangeFailure) {
                    throwIfCancelled(cancellation);
                    // Keep valid partial ranges for a later retry. Only a rejected range restarts as one stream.
                    Throwable cause = rangeFailure;
                    while (cause.getCause() != null) cause = cause.getCause();
                    if (!(cause instanceof RangeRejectedException)) throw rangeFailure;
                    deleteRecursively(tempDirectory);
                    downloaded.set(0L);
                    parts = new ArrayList<>();
                    File part = new File(tempDirectory, "part-0");
                    downloadSingle(
                            id, title, url, headers, total, part, downloaded, cancellation
                    );
                    parts.add(part);
                }
            } else {
                parts = new ArrayList<>();
                File part = new File(tempDirectory, "part-0");
                downloadSingle(
                        id, title, url, headers, total, part, downloaded, cancellation
                );
                parts.add(part);
            }
            throwIfCancelled(cancellation);
            copyParts(parts, destination, cancellation);
            publish(destination);
            long completedBytes = total > 0L ? total : downloaded.get();
            VideoDownloadStore.updateCustom(
                    this,
                    id,
                    DownloadManager.STATUS_SUCCESSFUL,
                    completedBytes,
                    completedBytes,
                    destination.toString(),
                    0
            );
            completed = true;
            showCompleted(title);
        } catch (CancelledException ignored) {
            deleteDestination(destination);
            if (PAUSES.contains(id)) {
                VideoDownloadStore.updateCustom(this, id, DownloadManager.STATUS_PAUSED,
                        partialBytes(tempDirectory), total, "", 2);
            }
        } catch (Exception ignored) {
            deleteDestination(destination);
            VideoDownloadStore.updateCustom(this, id,
                    PAUSES.contains(id) ? DownloadManager.STATUS_PAUSED : DownloadManager.STATUS_FAILED,
                    partialBytes(tempDirectory), total, "", PAUSES.contains(id) ? 2 : 1);
        } finally {
            disconnectAll(id);
            if (completed || (cancellation.get() && !PAUSES.contains(id))) deleteRecursively(tempDirectory);
            CANCELLATIONS.remove(id);
            PAUSES.remove(id);
            lastProgressUpdates.remove(id);
            CANCELLED_BEFORE_START.remove(id);
            if (activeJobs.decrementAndGet() <= 0) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            } else showForeground("Downloading videos", 0L, -1L);
        }
    }

    private static long partialBytes(File directory) {
        long bytes = 0;
        File[] parts = directory.listFiles((dir, name) -> name.startsWith("part-"));
        if (parts != null) for (File part : parts) bytes += part.length();
        return bytes;
    }

    private List<File> downloadRanges(
            long id,
            String title,
            String address,
            Map<String, String> headers,
            long total,
            int count,
            File directory,
            AtomicLong downloaded,
            AtomicBoolean cancellation
    ) throws Exception {
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("No temporary download directory");
        }
        ExecutorService pool = Executors.newFixedThreadPool(count);
        AtomicBoolean abort = new AtomicBoolean(false);
        ArrayList<File> parts = new ArrayList<>();
        ArrayList<Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long start = total * index / count;
            long end = total * (index + 1L) / count - 1L;
            File part = new File(directory, "part-" + index);
            parts.add(part);
            futures.add(pool.submit(() -> {
                downloadRange(
                        id, title, address, headers, start, end, total, part,
                        downloaded, cancellation, abort
                );
                return null;
            }));
        }
        try {
            for (Future<?> future : futures) future.get();
        } catch (Exception error) {
            abort.set(true);
            disconnectAll(id);
            for (Future<?> future : futures) future.cancel(true);
            throw error;
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5L, TimeUnit.SECONDS);
        }
        return parts;
    }

    private void downloadRange(
            long id,
            String title,
            String address,
            Map<String, String> headers,
            long start,
            long end,
            long total,
            File part,
            AtomicLong downloaded,
            AtomicBoolean cancellation,
            AtomicBoolean abort
    ) throws Exception {
        throwIfCancelled(cancellation);
        if (abort.get()) throw new CancelledException();
        long originalLength = end - start + 1L;
        long already = part.exists() ? part.length() : 0L;
        if (already > originalLength) { if (!part.delete()) throw new IllegalStateException("Invalid partial file"); already = 0L; }
        downloaded.addAndGet(already);
        if (already == originalLength) return;
        start += already;
        HttpURLConnection connection = open(id, address, headers);
        connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
        int code = connection.getResponseCode();
        if (!DownloadResumePolicy.validRange(code, connection.getHeaderField("Content-Range"), start, end, total)) {
            connection.disconnect();
            throw new RangeRejectedException();
        }
        long expected = end - start + 1L;
        long received = 0L;
        try (InputStream input = new BufferedInputStream(connection.getInputStream(), BUFFER_SIZE);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(part, true), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                if (abort.get()) throw new CancelledException();
                throwIfCancelled(cancellation);
                int read = input.read(buffer);
                if (read < 0) break;
                if (received + read > expected) throw new RangeRejectedException();
                output.write(buffer, 0, read);
                received += read;
                long current = downloaded.addAndGet(read);
                reportProgress(id, title, current, total);
            }
        } finally {
            connection.disconnect();
        }
        if (received != expected) throw new IllegalStateException("Incomplete byte range");
    }

    private void downloadSingle(
            long id,
            String title,
            String address,
            Map<String, String> headers,
            long knownTotal,
            File part,
            AtomicLong downloaded,
            AtomicBoolean cancellation
    ) throws Exception {
        File parent = part.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("No temporary download directory");
        }
        HttpURLConnection connection = open(id, address, headers);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("Download HTTP " + code);
        }
        long total = knownTotal > 0L ? knownTotal : connection.getContentLengthLong();
        long received = 0L;
        try (InputStream input = new BufferedInputStream(connection.getInputStream(), BUFFER_SIZE);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(part), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                throwIfCancelled(cancellation);
                int read = input.read(buffer);
                if (read < 0) break;
                received += read;
                if (total > 0L && received > total) throw new IllegalStateException("Unexpected download size");
                output.write(buffer, 0, read);
                long current = downloaded.addAndGet(read);
                reportProgress(id, title, current, total);
            }
        } finally {
            connection.disconnect();
        }
        if (total > 0L && received != total) throw new IllegalStateException("Incomplete download");
    }

    private Probe probe(long id, String address, Map<String, String> headers, AtomicBoolean cancellation)
            throws Exception {
        throwIfCancelled(cancellation);
        HttpURLConnection connection = open(id, address, headers);
        connection.setRequestProperty("Range", "bytes=0-0");
        int code = connection.getResponseCode();
        String contentRange = connection.getHeaderField("Content-Range");
        long total = -1L;
        boolean acceptsRanges = code == HttpURLConnection.HTTP_PARTIAL;
        if (contentRange != null) {
            Matcher matcher = CONTENT_RANGE.matcher(contentRange);
            if (matcher.find()) total = Long.parseLong(matcher.group(1));
        }
        if (total <= 0L && code == HttpURLConnection.HTTP_OK) {
            total = connection.getContentLengthLong();
        }
        try (InputStream input = connection.getInputStream()) {
            input.read();
        } finally {
            connection.disconnect();
        }
        String validator = clean(connection.getHeaderField("ETag"), "");
        if (validator.isEmpty() || validator.startsWith("W/")) validator = clean(connection.getHeaderField("Last-Modified"), "");
        return new Probe(acceptsRanges && total > 0L, total, validator);
    }

    private HttpURLConnection open(long id, String address, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        CONNECTIONS.computeIfAbsent(id, key -> ConcurrentHashMap.newKeySet()).add(connection);
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Connection", "keep-alive");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = clean(header.getKey(), "");
            if (name.isEmpty() || "Range".equalsIgnoreCase(name) ||
                    "Accept-Encoding".equalsIgnoreCase(name) ||
                    "Connection".equalsIgnoreCase(name) ||
                    "Host".equalsIgnoreCase(name) ||
                    "Content-Length".equalsIgnoreCase(name)) {
                continue;
            }
            String value = clean(header.getValue(), "");
            if (!value.isEmpty()) connection.setRequestProperty(name, value);
        }
        return connection;
    }

    private Uri createDestination(String fileName, String mime) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS
        );
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        return getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    }

    private void copyParts(List<File> parts, Uri destination, AtomicBoolean cancellation)
            throws Exception {
        ContentResolver resolver = getContentResolver();
        try (OutputStream output = resolver.openOutputStream(destination, "w")) {
            if (output == null) throw new IllegalStateException("No destination stream");
            byte[] buffer = new byte[BUFFER_SIZE];
            for (File part : parts) {
                try (InputStream input = new BufferedInputStream(new FileInputStream(part), BUFFER_SIZE)) {
                    while (true) {
                        throwIfCancelled(cancellation);
                        int read = input.read(buffer);
                        if (read < 0) break;
                        output.write(buffer, 0, read);
                    }
                }
            }
            output.flush();
        }
    }

    private void publish(Uri destination) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        getContentResolver().update(destination, values, null, null);
    }

    private void deleteDestination(Uri destination) {
        if (destination == null) return;
        try {
            getContentResolver().delete(destination, null, null);
        } catch (Exception ignored) {
        }
    }

    private void reportProgress(long id, String title, long downloaded, long total) {
        if (PAUSES.contains(id)) return;
        AtomicLong lastProgressUpdate = lastProgressUpdates.computeIfAbsent(id, key -> new AtomicLong());
        long now = System.currentTimeMillis();
        long previous = lastProgressUpdate.get();
        if (now - previous < 500L || !lastProgressUpdate.compareAndSet(previous, now)) return;
        VideoDownloadStore.Entry entry = VideoDownloadStore.entry(this, id);
        VideoDownloadStore.updateCustom(
                this,
                id,
                DownloadManager.STATUS_RUNNING,
                downloaded,
                total,
                entry == null ? "" : entry.localUri,
                0
        );
        showForeground(title, downloaded, total);
    }

    private void showForeground(String title, long downloaded, long total) {
        Notification notification = notification(title, downloaded, total, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification);
        }
    }

    private void showCompleted(String title) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        Notification notification = notification(title, 1L, 1L, false);
        manager.notify((int) (Math.abs(title.hashCode()) % 20_000) + 9_000, notification);
    }

    private Notification notification(String title, long downloaded, long total, boolean ongoing) {
        Intent open = new Intent(this, DownloadedActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_download)
                .setContentTitle(ongoing ? "Downloading " + title : "Download complete")
                .setContentText(ongoing ? "Saving to Downloads" : title)
                .setContentIntent(pending)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (ongoing) {
            if (total > 0L) {
                builder.setProgress(100, (int) Math.min(100L, downloaded * 100L / total), false);
            } else {
                builder.setProgress(0, 0, true);
            }
        }
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Video downloads",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("CrazyShit video download progress");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static Map<String, String> parseHeaders(String raw) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return headers;
        try {
            JSONObject json = new JSONObject(raw);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                headers.put(key, json.optString(key, ""));
            }
        } catch (Exception ignored) {
        }
        return headers;
    }

    private static void throwIfCancelled(AtomicBoolean cancellation) throws CancelledException {
        if (cancellation.get() || Thread.currentThread().isInterrupted()) throw new CancelledException();
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        target.delete();
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String clean = value.replace("\r", "").replace("\n", "").trim();
        return clean.isEmpty() ? fallback : clean;
    }

    private static final class Probe {
        final boolean acceptsRanges;
        final long totalBytes;

        final String validator;
        Probe(boolean acceptsRanges, long totalBytes, String validator) {
            this.acceptsRanges = acceptsRanges;
            this.totalBytes = totalBytes;
            this.validator = validator;
        }
    }

    private static final class RangeRejectedException extends Exception { }

    private static final class CancelledException extends Exception {
    }
}
