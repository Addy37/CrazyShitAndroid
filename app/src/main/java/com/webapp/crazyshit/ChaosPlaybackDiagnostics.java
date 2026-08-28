package com.webapp.crazyshit;

import android.net.Uri;
import android.os.Build;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;

import java.util.Locale;

/** Builds a small playback report without cookies, headers, or private stream URLs. */
final class ChaosPlaybackDiagnostics {
    private ChaosPlaybackDiagnostics() {
    }

    static String build(
            NativeContentItem item,
            CrazyShitRepository.StreamInfo stream,
            PlaybackException error,
            String stage,
            boolean retryAttempted
    ) {
        String pageUrl = item == null ? "" : clean(item.url);
        String source = pageUrl.toLowerCase(Locale.US).contains("/shitshow/")
                ? "Shit Show via Chaos"
                : "CrazyShit site feed via Chaos";

        StringBuilder report = new StringBuilder();
        report.append("CrazyShit playback report\n");
        report.append("App: ").append(BuildConfig.VERSION_NAME).append("\n");
        report.append("Device: ")
                .append(clean(Build.MANUFACTURER))
                .append(" ")
                .append(clean(Build.MODEL))
                .append("\n");
        report.append("Android: ")
                .append(clean(Build.VERSION.RELEASE))
                .append(" (API ")
                .append(Build.VERSION.SDK_INT)
                .append(")\n");
        report.append("Source: ").append(source).append("\n");
        report.append("Page: ").append(pageUrl.isEmpty() ? "Unavailable" : pageUrl).append("\n");
        report.append("Media host: ").append(mediaHost(stream)).append("\n");
        report.append("Stage: ").append(clean(stage).isEmpty() ? "No failure captured" : clean(stage)).append("\n");
        report.append("Fresh retry: ").append(retryAttempted ? "Attempted" : "Not attempted");

        if (error != null) {
            report.append("\nMedia3 code: ").append(error.errorCode);
            int httpCode = httpCode(error);
            if (httpCode > 0) report.append("\nHTTP status: ").append(httpCode);
            Throwable root = rootCause(error);
            if (root != null) {
                report.append("\nCause: ").append(clean(root.getClass().getSimpleName()));
            }
        }
        return report.toString();
    }

    private static String mediaHost(CrazyShitRepository.StreamInfo stream) {
        if (stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty()) {
            return "Not resolved";
        }
        try {
            String host = Uri.parse(stream.mediaUrl).getHost();
            return host == null || host.trim().isEmpty() ? "Unknown" : clean(host);
        } catch (Exception ignored) {
            return "Unknown";
        }
    }

    private static int httpCode(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) cursor).responseCode;
            }
            cursor = cursor.getCause();
        }
        return -1;
    }

    private static Throwable rootCause(Throwable error) {
        if (error == null) return null;
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
