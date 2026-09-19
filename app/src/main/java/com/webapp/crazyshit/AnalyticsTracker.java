package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends aggregate-only usage signals. Stable installation IDs never leave the device for analytics.
 * Unique keys rotate by metric, value and UTC day/week/month so analytics cannot build a persistent
 * cross-feature viewing profile for an installation.
 */
final class AnalyticsTracker {
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Map<String, Long> RECENT = new ConcurrentHashMap<>();
    private static final long DUPLICATE_WINDOW_MS = 5_000L;
    private static final int MAX_ATTEMPTS = 3;

    private AnalyticsTracker() {
    }

    static void initialize(Context context) {
        if (!isConfigured() || !INITIALIZED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        track(app, "app_open", "all");
        track(app, "app_version", BuildConfig.VERSION_NAME);
    }

    static void onActivityCreated(Activity activity, Bundle state) {
        if (activity == null || state != null) return;
        String name = activity.getClass().getSimpleName();
        if (activity instanceof NativeMainActivity) attachMainPager(activity);
        else if ("SearchActivity".equals(name)) trackSection(activity, "search");
        else if ("FavoritesActivity".equals(name)) trackSection(activity, "favorites");
        else if ("DownloadedActivity".equals(name)) trackSection(activity, "downloads");
        else if ("SettingsActivity".equals(name)) trackSection(activity, "settings");
        else if ("ProfileActivity".equals(name)) trackSection(activity, "profile");
        else if (activity instanceof NativeFeedBrowserActivity) trackNativeBrowser(activity);
        else if (activity instanceof BunkrGalleryActivity) trackGalleryViewer(activity);
    }

    static void trackSection(Context context, String section) {
        if (section == null) return;
        track(context, "section", section.trim().toLowerCase(Locale.US));
    }

    static void trackSource(Context context, String source) {
        if (source == null) return;
        String clean = source.trim().toLowerCase(Locale.US);
        if (!clean.isEmpty()) track(context, "source", clean);
    }

    static void trackCreator(Context context, String creator) {
        String clean = cleanCreator(creator);
        if (clean.isEmpty()) return;
        trackSection(context, "creator_gallery");
        trackSource(context, "fapzone");
        track(context, "creator", clean);
    }

    private static void attachMainPager(Activity activity) {
        View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (root == null) {
            trackSection(activity, "home");
            return;
        }
        root.post(() -> {
            ViewPager2 pager = findPrimaryPager(root);
            if (pager == null) {
                trackSection(activity, "home");
                return;
            }
            trackPrimaryPage(activity, pager.getCurrentItem());
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    trackPrimaryPage(activity, position);
                }
            });
        });
    }

    private static ViewPager2 findPrimaryPager(View view) {
        if (view instanceof ViewPager2) return (ViewPager2) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            ViewPager2 found = findPrimaryPager(group.getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }

    private static void trackPrimaryPage(Context context, int position) {
        if (position == MainPagerAdapter.PAGE_SERIES) trackSection(context, "collections");
        else if (position == MainPagerAdapter.PAGE_CHAOS) trackSection(context, "chaos");
        else if (position == MainPagerAdapter.PAGE_LIBRARY) trackSection(context, "library");
        else trackSection(context, "home");
    }

    private static void trackNativeBrowser(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) return;
        String creator = value(intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
        if (!creator.isEmpty()) {
            String title = value(intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_TITLE));
            trackCreator(activity, title.isEmpty() ? creator : title);
            return;
        }
        String source = value(intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_SOURCE));
        if (!source.isEmpty()) trackSource(activity, source);
    }

    private static void trackGalleryViewer(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) return;
        String creator = value(intent.getStringExtra(BunkrGalleryActivity.EXTRA_CREATOR_QUERY));
        if (creator.isEmpty()) return;
        String title = value(intent.getStringExtra(BunkrGalleryActivity.EXTRA_TITLE));
        trackCreator(activity, title.isEmpty() ? creator : title);
    }

    private static void track(Context context, String metric, String value) {
        if (!isConfigured() || context == null || metric == null || value == null || value.isEmpty()) return;
        String recentKey = metric + '\n' + value;
        long now = android.os.SystemClock.elapsedRealtime();
        Long previous = RECENT.put(recentKey, now);
        if (previous != null && now - previous < DUPLICATE_WINDOW_MS) return;

        Context app = context.getApplicationContext();
        NETWORK.execute(() -> {
            try {
                String installation = FeedbackRepository.installationId(app);
                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                LocalDate week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate month = today.withDayOfMonth(1);
                JSONObject payload = new JSONObject()
                        .put("metric", metric)
                        .put("value", value)
                        .put("day_key", anonymousKey(installation, "day", today, metric, value))
                        .put("week_key", anonymousKey(installation, "week", week, metric, value))
                        .put("month_key", anonymousKey(installation, "month", month, metric, value));
                postWithRetry(payload);
            } catch (Exception ignored) {
                // Analytics never blocks or changes app behavior.
            }
        });
    }

    private static boolean isConfigured() {
        return !BuildConfig.ANALYTICS_ENDPOINT.trim().isEmpty();
    }

    private static String anonymousKey(
            String installation,
            String period,
            LocalDate periodStart,
            String metric,
            String value
    ) throws Exception {
        String input = "analytics-v1\n" + installation + '\n' + period + '\n' + periodStart + '\n'
                + metric + '\n' + value;
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte part : digest) result.append(String.format(Locale.US, "%02x", part & 0xff));
        return result.toString();
    }

    private static void postWithRetry(JSONObject payload) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                post(payload);
                return;
            } catch (Exception error) {
                last = error;
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
            }
        }
        if (last != null) throw last;
    }

    private static void post(JSONObject payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BuildConfig.ANALYTICS_ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(7_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Cache-Control", "no-store");
            connection.setRequestProperty("X-CrazyShit-Analytics", "1");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                InputStream error = connection.getErrorStream();
                if (error != null) error.close();
                throw new IOException("Analytics request failed with HTTP " + status);
            }
            InputStream input = connection.getInputStream();
            if (input != null) input.close();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String cleanCreator(String creator) {
        String clean = value(creator).replaceAll("\\s+", " ");
        if (clean.length() > 120) clean = clean.substring(0, 120).trim();
        for (int i = 0; i < clean.length(); i++) {
            char character = clean.charAt(i);
            if (character < 32 || character == 127) return "";
        }
        return clean;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
