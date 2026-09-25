package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small best-effort cache for the last rendered frame of Shows playback.
 *
 * Frames live in cache storage so Android may reclaim them at any time. Continue Watching always
 * falls back to the original poster when a frame is missing.
 */
final class ShowsContinueFrameStore {
    private static final String DIRECTORY = "shows_continue_frames";
    private static final int JPEG_QUALITY = 82;
    private static final int MAX_FILES = 32;
    private static final long MAX_BYTES = 8L * 1024L * 1024L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ShowsContinueFrameStore() {
    }

    static File find(Context context, String pageUrl) {
        if (context == null || clean(pageUrl).isEmpty()) return null;
        File file = new File(directory(context), key(pageUrl) + ".jpg");
        return file.isFile() && file.length() > 0L ? file : null;
    }

    static void saveAsync(Context context, String pageUrl, Bitmap bitmap) {
        if (bitmap == null) return;
        String cleanUrl = clean(pageUrl);
        if (context == null || cleanUrl.isEmpty() || bitmap.isRecycled()) {
            recycle(bitmap);
            return;
        }
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                save(app, cleanUrl, bitmap);
            } finally {
                recycle(bitmap);
            }
        });
    }

    static void deleteAsync(Context context, String pageUrl) {
        String cleanUrl = clean(pageUrl);
        if (context == null || cleanUrl.isEmpty()) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            File file = new File(directory(app), key(cleanUrl) + ".jpg");
            if (file.exists()) file.delete();
        });
    }

    private static void save(Context context, String pageUrl, Bitmap bitmap) {
        File dir = directory(context);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) return;

        File target = new File(dir, key(pageUrl) + ".jpg");
        File temp = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                temp.delete();
                return;
            }
            out.flush();
        } catch (Exception ignored) {
            temp.delete();
            return;
        }

        if (target.exists() && !target.delete()) {
            temp.delete();
            return;
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            return;
        }
        target.setLastModified(System.currentTimeMillis());
        trim(dir);
    }

    private static void trim(File dir) {
        File[] files = dir.listFiles((ignored, name) -> name != null && name.endsWith(".jpg"));
        if (files == null || files.length == 0) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        long total = 0L;
        for (File file : files) total += Math.max(0L, file.length());

        int remaining = files.length;
        int index = 0;
        while (index < files.length && (remaining > MAX_FILES || total > MAX_BYTES)) {
            File file = files[index++];
            long length = Math.max(0L, file.length());
            if (file.delete()) {
                total = Math.max(0L, total - length);
                remaining--;
            }
        }
    }

    private static File directory(Context context) {
        return new File(context.getCacheDir(), DIRECTORY);
    }

    private static String key(String pageUrl) {
        String value = clean(pageUrl);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }
}
