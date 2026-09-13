package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Owns one validated source-config snapshot and refreshes it away from the startup/UI thread. */
final class RemoteSourceConfigManager {
    static final long REFRESH_INTERVAL_MS = 45L * 60L * 1_000L;
    private static final String TAG = "SourceConfig";
    private static final String PREFS = "remote_source_config";
    private static final String ACTIVE_JSON = "active_json";
    private static final String PREVIOUS_JSON = "previous_json";
    private static final String LAST_REFRESH_ATTEMPT = "last_refresh_attempt";
    private static final AtomicReference<SourceConfig> ACTIVE = new AtomicReference<>();
    private static final ScheduledExecutorService REFRESHER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "source-config-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean PERIODIC_REFRESH_SCHEDULED = new AtomicBoolean();
    private static volatile Context applicationContext;
    private static volatile String activeOrigin = "bundled";

    private RemoteSourceConfigManager() {}

    static synchronized void initialize(Context context) {
        if (context == null || applicationContext != null) return;
        applicationContext = context.getApplicationContext();
        SourceConfig bundled = null;
        try {
            bundled = readBundled(applicationContext);
            ACTIVE.set(bundled);
            activeOrigin = "bundled";
            Log.i(TAG, "Bundled defaults active, version=" + bundled.configVersion);
        } catch (Exception error) {
            activeOrigin = "compiled";
            Log.e(TAG, "Bundled source configuration unavailable; using compiled repository defaults: " +
                    safeReason(error));
        }

        SharedPreferences prefs = prefs(applicationContext);
        String cached = prefs.getString(ACTIVE_JSON, "");
        if (cached != null && !cached.trim().isEmpty()) {
            try {
                SourceConfig candidate = SourceConfig.parseAndValidate(cached);
                if (bundled == null || candidate.configVersion >= bundled.configVersion) {
                    ACTIVE.set(candidate);
                    activeOrigin = "cached";
                    Log.i(TAG, "Cached configuration active, version=" + candidate.configVersion);
                } else {
                    Log.w(TAG, "Cached configuration rejected: older than bundled defaults");
                }
            } catch (SourceConfig.ValidationException error) {
                Log.w(TAG, "Cached configuration rejected: " + safeReason(error));
                restorePrevious(prefs, bundled);
            }
        }
        if (ACTIVE.get() == null) restorePrevious(prefs, null);
    }

    static SourceConfig snapshot() {
        SourceConfig current = ACTIVE.get();
        if (current != null) return current;
        throw new IllegalStateException("RemoteSourceConfigManager was not initialized");
    }

    static SourceConfig snapshotOrNull() { return ACTIVE.get(); }

    static String activeOrigin() { return activeOrigin; }

    static void refreshInBackground(Context context) {
        initialize(context);
        Context app = applicationContext;
        if (app == null || snapshotOrNull() == null) return;
        queueRefreshIfDue(app);
        if (PERIODIC_REFRESH_SCHEDULED.compareAndSet(false, true)) {
            REFRESHER.scheduleWithFixedDelay(() -> queueRefreshIfDue(app),
                    REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static void queueRefreshIfDue(Context app) {
        SharedPreferences prefs = prefs(app);
        long now = System.currentTimeMillis();
        long previousAttempt = prefs.getLong(LAST_REFRESH_ATTEMPT, 0L);
        if (now - previousAttempt < REFRESH_INTERVAL_MS) return;
        prefs.edit().putLong(LAST_REFRESH_ATTEMPT, now).apply();
        REFRESHER.execute(() -> refresh(app, new HttpFetcher()));
    }

    private static void refresh(Context context, Fetcher fetcher) {
        refresh(context, fetcher, BuildConfig.SOURCE_CONFIG_ENDPOINT.trim(),
                BuildConfig.SOURCE_CONFIG_PUBLISHABLE_KEY.trim());
    }

    private static void refresh(Context context, Fetcher fetcher, String endpoint, String key) {
        if (endpoint.isEmpty() || key.isEmpty()) {
            Log.d(TAG, "Remote refresh skipped: endpoint or publishable key unavailable");
            return;
        }
        SourceConfig before = snapshot();
        try {
            FetchResult result = fetcher.fetch(endpoint, key, before.configVersion);
            if (result.notModified) {
                Log.d(TAG, "Remote configuration unchanged, version=" + before.configVersion);
                return;
            }
            SourceConfig candidate;
            try {
                candidate = SourceConfig.parseAndValidate(result.body);
            } catch (SourceConfig.ValidationException error) {
                Log.w(TAG, "Remote configuration rejected: " + safeReason(error));
                return;
            }
            Log.i(TAG, "Remote configuration version " + candidate.configVersion + " downloaded");
            if (candidate.configVersion <= before.configVersion) {
                Log.w(TAG, "Remote configuration rejected: version " + candidate.configVersion +
                        " is not newer than " + before.configVersion);
                return;
            }
            activateValidated(context, candidate);
        } catch (Exception error) {
            Log.w(TAG, "Remote refresh failed; keeping " + activeOrigin +
                    " version=" + before.configVersion + ": " + safeReason(error));
        }
    }

    private static synchronized void activateValidated(Context context, SourceConfig candidate) {
        SourceConfig current = snapshot();
        if (candidate.configVersion <= current.configVersion) return;
        prefs(context).edit()
                .putString(PREVIOUS_JSON, current.serialized())
                .putString(ACTIVE_JSON, candidate.serialized())
                .apply();
        ACTIVE.set(candidate);
        activeOrigin = "remote";
        Log.i(TAG, "Remote configuration version " + candidate.configVersion + " passed validation and is active");
    }

    private static void restorePrevious(SharedPreferences prefs, SourceConfig bundled) {
        String previous = prefs.getString(PREVIOUS_JSON, "");
        if (previous == null || previous.trim().isEmpty()) return;
        try {
            SourceConfig candidate = SourceConfig.parseAndValidate(previous);
            if (bundled != null && candidate.configVersion < bundled.configVersion) return;
            ACTIVE.set(candidate);
            activeOrigin = "rollback";
            prefs.edit().putString(ACTIVE_JSON, candidate.serialized()).apply();
            Log.i(TAG, "Fell back to previous known-good configuration, version=" + candidate.configVersion);
        } catch (SourceConfig.ValidationException error) {
            Log.w(TAG, "Previous configuration rejected: " + safeReason(error));
        }
    }

    private static SourceConfig readBundled(Context context) {
        try (InputStream input = context.getAssets().open("source_config_defaults.json")) {
            return SourceConfig.parseAndValidate(readLimited(input));
        } catch (Exception error) {
            throw new IllegalStateException("Bundled source configuration is invalid", error);
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > SourceConfig.MAX_JSON_BYTES) throw new IOException("Configuration response was too large");
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safeReason(Throwable error) {
        String reason = error == null ? "unknown error" : error.getMessage();
        if (reason == null || reason.trim().isEmpty()) return error.getClass().getSimpleName();
        return reason.replaceAll("(?i)(apikey|authorization|cookie|token|key)=[^\\s,]+", "$1=[redacted]");
    }

    interface Fetcher {
        FetchResult fetch(String endpoint, String publishableKey, long currentVersion) throws Exception;
    }

    static final class FetchResult {
        final boolean notModified;
        final String body;
        FetchResult(boolean notModified, String body) {
            this.notModified = notModified;
            this.body = body == null ? "" : body;
        }
    }

    private static final class HttpFetcher implements Fetcher {
        @Override public FetchResult fetch(String endpoint, String publishableKey, long currentVersion)
                throws Exception {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(endpoint + (endpoint.contains("?") ? "&" : "?") +
                        "currentVersion=" + currentVersion);
                if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Config endpoint must use HTTPS");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(8_000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("apikey", publishableKey);
                connection.setRequestProperty("Authorization", "Bearer " + publishableKey);
                connection.setRequestProperty("If-None-Match", "\"" + currentVersion + "\"");
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_NOT_MODIFIED || status == HttpURLConnection.HTTP_NO_CONTENT) {
                    return new FetchResult(true, "");
                }
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("Config service returned HTTP " + status);
                try (InputStream input = connection.getInputStream()) {
                    return new FetchResult(false, readLimited(input));
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
    }

    static synchronized void resetForTests() {
        applicationContext = null;
        ACTIVE.set(null);
        activeOrigin = "bundled";
    }

    static void applyRemoteForTests(Context context, String json) throws Exception {
        initialize(context);
        SourceConfig candidate = SourceConfig.parseAndValidate(json);
        activateValidated(context, candidate);
    }

    static void refreshForTests(Context context, Fetcher fetcher) {
        initialize(context);
        refresh(context, fetcher, "https://config.example/app-config", "test-publishable-key");
    }

    static void refreshInBackgroundForTests(Context context, Fetcher fetcher) {
        initialize(context);
        REFRESHER.execute(() -> refresh(context, fetcher,
                "https://config.example/app-config", "test-publishable-key"));
    }
}
