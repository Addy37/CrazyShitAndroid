package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Polls site feeds and GitHub only while the user has the matching alert enabled. */
public final class ContentUpdateWorker extends Worker {
    private static final String APP_PREFS = "app_prefs";
    private static final String STATE_PREFS = "notification_state";
    private static final String STABLE_API =
            "https://api.github.com/repos/Addy37/CrazyShitAndroid/releases/latest";
    private static final String RELEASES_API =
            "https://api.github.com/repos/Addy37/CrazyShitAndroid/releases?per_page=100";
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final int MAX_SEEN = 160;
    private static final int MAX_REASONABLE_NEW_ITEMS = 20;

    public ContentUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE);
        ArrayList<NotificationCoordinator.SourceAlert> alerts = new ArrayList<>();
        boolean failed = false;

        if (prefs.getBoolean(NotificationCoordinator.PREF_NEW_VIDEO_ALERTS, true)) {
            if (prefs.getBoolean(NotificationCoordinator.PREF_CRAZYSHIT_ALERTS, true)) {
                try {
                    List<NativeContentItem> items = new CrazyShitRepository()
                            .fetchFeed(context, CrazyShitRepository.HOME, 1);
                    NotificationCoordinator.SourceAlert alert = findNew(
                            context,
                            "crazyshit",
                            "CrazyShit",
                            false,
                            items
                    );
                    if (alert != null) alerts.add(alert);
                } catch (Exception ignored) {
                    failed = true;
                }
            }

            if (prefs.getBoolean(NotificationCoordinator.PREF_EFUKT_ALERTS, true)) {
                try {
                    List<NativeContentItem> items = new EfuktRepository().fetchLatest(context);
                    NotificationCoordinator.SourceAlert alert = findNew(
                            context,
                            "efukt",
                            "EFukt",
                            true,
                            items
                    );
                    if (alert != null) alerts.add(alert);
                } catch (Exception ignored) {
                    failed = true;
                }
            }
        }

        if (!alerts.isEmpty()) NotificationCoordinator.showNewVideoNotifications(context, alerts);

        if (prefs.getBoolean(NotificationCoordinator.PREF_UPDATE_ALERTS, true)) {
            try {
                checkForAppUpdate(context);
            } catch (Exception ignored) {
                failed = true;
            }
        }

        return failed ? Result.retry() : Result.success();
    }

    private NotificationCoordinator.SourceAlert findNew(
            Context context,
            String key,
            String source,
            boolean efukt,
            List<NativeContentItem> rawItems
    ) {
        LinkedHashMap<String, NativeContentItem> current = new LinkedHashMap<>();
        if (rawItems != null) {
            for (NativeContentItem item : rawItems) {
                if (item == null || item.isSection() || item.url == null || item.url.trim().isEmpty()) continue;
                current.putIfAbsent(item.url.trim(), item);
            }
        }
        if (current.isEmpty()) return null;

        SharedPreferences state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        String initializedKey = "seen_initialized_" + key;
        String seenKey = "seen_urls_" + key;
        LinkedHashSet<String> seen = decode(state.getString(seenKey, ""));

        if (!state.getBoolean(initializedKey, false)) {
            saveSeen(state, initializedKey, seenKey, current.keySet(), seen);
            return null;
        }

        ArrayList<NativeContentItem> added = new ArrayList<>();
        for (NativeContentItem item : current.values()) {
            if (!seen.contains(item.url)) added.add(item);
        }
        saveSeen(state, initializedKey, seenKey, current.keySet(), seen);

        // A large jump usually means the site changed its URL format. Re-baseline instead of
        // flooding the notification shade with old uploads presented as new ones.
        if (added.isEmpty() || added.size() > MAX_REASONABLE_NEW_ITEMS) return null;
        return new NotificationCoordinator.SourceAlert(source, efukt, added);
    }

    private void saveSeen(
            SharedPreferences state,
            String initializedKey,
            String seenKey,
            Set<String> current,
            Set<String> previous
    ) {
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        combined.addAll(current);
        combined.addAll(previous);
        StringBuilder encoded = new StringBuilder();
        int count = 0;
        for (String url : combined) {
            if (url == null || url.trim().isEmpty()) continue;
            if (count++ >= MAX_SEEN) break;
            if (encoded.length() > 0) encoded.append('\n');
            encoded.append(url.trim());
        }
        state.edit()
                .putBoolean(initializedKey, true)
                .putString(seenKey, encoded.toString())
                .apply();
    }

    private LinkedHashSet<String> decode(String encoded) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (encoded == null || encoded.isEmpty()) return result;
        for (String line : encoded.split("\\n")) {
            String value = line.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private void checkForAppUpdate(Context context) throws Exception {
        boolean beta = context.getPackageName().endsWith(".dev");
        Release release = beta ? fetchLatestBeta() : fetchStable();
        if (release == null) return;
        String current = currentVersion(context);
        if (compareVersions(release.version, current) > 0) {
            NotificationCoordinator.showUpdateNotification(
                    context,
                    release.version,
                    release.title,
                    release.beta
            );
        } else {
            NotificationCoordinator.clearUpdateNotification(context);
        }
    }

    private Release fetchStable() throws Exception {
        JSONObject release = new JSONObject(httpGet(STABLE_API));
        if (release.optBoolean("draft", false)) return null;
        return parseRelease(release, false);
    }

    private Release fetchLatestBeta() throws Exception {
        JSONArray releases = new JSONArray(httpGet(RELEASES_API));
        Release latest = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft", false)) continue;
            if (!release.optBoolean("prerelease", false)) continue;
            String tag = release.optString("tag_name", "");
            if (!tag.toLowerCase(Locale.US).contains("beta")) continue;
            Release parsed = parseRelease(release, true);
            if (parsed != null && (latest == null || compareVersions(parsed.version, latest.version) > 0)) {
                latest = parsed;
            }
        }
        return latest;
    }

    private Release parseRelease(JSONObject release, boolean beta) {
        String version = release.optString("tag_name", "").replaceFirst("^[vV]", "");
        if (version.isEmpty()) return null;
        JSONArray assets = release.optJSONArray("assets");
        boolean hasMatchingApk = false;
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "").toLowerCase(Locale.US);
                if (!name.endsWith(".apk")) continue;
                if (beta && !(name.contains("test") || name.contains("beta"))) continue;
                if (!beta && name.contains("test")) continue;
                hasMatchingApk = true;
                break;
            }
        }
        if (!hasMatchingApk) return null;
        String title = release.optString("name", version);
        return new Release(version, title, beta);
    }

    private String httpGet(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "CrazyShit-Android");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String currentVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (Exception ignored) {
            return "0";
        }
    }

    private int compareVersions(String left, String right) {
        List<Integer> a = numbers(left);
        List<Integer> b = numbers(right);
        int count = Math.max(a.size(), b.size());
        for (int i = 0; i < count; i++) {
            int av = i < a.size() ? a.get(i) : 0;
            int bv = i < b.size() ? b.get(i) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private List<Integer> numbers(String value) {
        ArrayList<Integer> result = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        while (matcher.find()) {
            try {
                result.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static final class Release {
        final String version;
        final String title;
        final boolean beta;

        Release(String version, String title, boolean beta) {
            this.version = version;
            this.title = title;
            this.beta = beta;
        }
    }
}
