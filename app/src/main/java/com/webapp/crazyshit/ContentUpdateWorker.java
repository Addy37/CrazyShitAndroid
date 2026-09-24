package com.webapp.crazyshit;

import android.content.Context;
import android.content.Intent;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Polls current ZEROCHILL source feeds and GitHub while the matching alert type is enabled. */
public final class ContentUpdateWorker extends Worker {
    private static final String APP_PREFS = "app_prefs";
    private static final String STATE_PREFS = "notification_state";
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
        SharedPreferences state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        ArrayList<NotificationCoordinator.SourceAlert> alerts = new ArrayList<>();
        LinkedHashMap<String, String> statuses = new LinkedHashMap<>();
        boolean manual = getInputData().getBoolean(NotificationCoordinator.INPUT_MANUAL_CHECK, false);
        int attempted = 0;
        int succeeded = 0;

        state.edit()
                .putLong(NotificationCoordinator.KEY_CHECK_STARTED, System.currentTimeMillis())
                .apply();

        if (prefs.getBoolean(NotificationCoordinator.PREF_NEW_VIDEO_ALERTS, true)) {
            for (SourceCheck check : sourceChecks(context)) {
                attempted++;
                CheckResult result = runSourceCheck(context, state, check, alerts);
                statuses.put(check.key, result.status);
                if (result.success) succeeded++;
            }
        } else {
            for (SourceCheck check : sourceChecks(context)) {
                statuses.put(check.key, "Content alerts off");
            }
        }

        if (!alerts.isEmpty()) {
            UpdateInboxStore.record(context, alerts);
        }
        NotificationCoordinator.clearContentNotifications(context);

        String updateStatus = prefs.getBoolean(NotificationCoordinator.PREF_UPDATE_ALERTS, true)
                ? "Waiting to check"
                : "Off";
        if (prefs.getBoolean(NotificationCoordinator.PREF_UPDATE_ALERTS, true)) {
            attempted++;
            try {
                checkForAppUpdate(context);
                updateStatus = "Up to date";
                succeeded++;
            } catch (Exception error) {
                updateStatus = friendlyError(error);
            }
        }

        SharedPreferences.Editor finished = state.edit()
                .putLong(NotificationCoordinator.KEY_CHECK_FINISHED, System.currentTimeMillis())
                .putString(
                        NotificationCoordinator.KEY_STATUS_CRAZYSHIT,
                        status(statuses, "crazyshit")
                )
                .putString(
                        NotificationCoordinator.KEY_STATUS_EFUKT,
                        status(statuses, "efukt")
                )
                .putString(
                        NotificationCoordinator.KEY_STATUS_KAOTIC,
                        status(statuses, "kaotic")
                )
                .putString(
                        NotificationCoordinator.KEY_STATUS_BUNKR,
                        status(statuses, "bunkr")
                )
                .putString(
                        NotificationCoordinator.KEY_STATUS_FAPELLO,
                        status(statuses, "fapello")
                )
                .putString(
                        NotificationCoordinator.KEY_STATUS_ONLYHAVEN,
                        status(statuses, "onlyhaven")
                )
                .putString(NotificationCoordinator.KEY_STATUS_UPDATES, updateStatus);
        finished.apply();

        Intent complete = new Intent(NotificationCoordinator.ACTION_CHECK_FINISHED)
                .setPackage(context.getPackageName())
                .putExtra(NotificationCoordinator.EXTRA_MANUAL_CHECK, manual);
        context.sendBroadcast(complete);

        if (!manual && attempted > 0 && succeeded == 0 && getRunAttemptCount() < 2) {
            return Result.retry();
        }
        return Result.success();
    }

    private List<SourceCheck> sourceChecks(Context context) {
        ArrayList<SourceCheck> checks = new ArrayList<>();
        checks.add(new SourceCheck(
                "crazyshit",
                "CrazyShit",
                () -> new CrazyShitRepository()
                        .fetchFeed(context, CrazyShitRepository.HOME, 1)
        ));
        checks.add(new SourceCheck(
                "efukt",
                "EFukt",
                () -> new EfuktRepository().fetchLatest(context)
        ));
        checks.add(new SourceCheck(
                "kaotic",
                "Kaotic",
                () -> new WebVideoSourceRepository().fetchFeed(
                        context,
                        WebVideoSourceRepository.Source.KAOTIC,
                        1
                )
        ));
        checks.add(new SourceCheck(
                "bunkr",
                "Bunkr",
                () -> new BunkrRepository().fetchAlbums(context, 1)
        ));
        checks.add(new SourceCheck(
                "fapello",
                "Fapello",
                () -> new FapelloRepository().fetchPopularVideos(context, 1)
        ));
        checks.add(new SourceCheck(
                "onlyhaven",
                "OnlyHaven",
                () -> onlyHavenTrending(context)
        ));
        return checks;
    }

    private CheckResult runSourceCheck(
            Context context,
            SharedPreferences state,
            SourceCheck check,
            List<NotificationCoordinator.SourceAlert> alerts
    ) {
        try {
            List<NativeContentItem> items = check.loader.load();
            int itemCount = countFeedItems(items);
            if (itemCount == 0) throw new Exception("No content found");
            boolean alreadyWatching =
                    state.getBoolean("seen_initialized_" + check.key, false);
            NotificationCoordinator.SourceAlert alert = findNew(
                    context,
                    check.key,
                    check.label,
                    items
            );
            if (alert != null) alerts.add(alert);
            return new CheckResult(true, sourceStatus(alreadyWatching, itemCount, alert));
        } catch (Exception error) {
            return new CheckResult(false, friendlyError(error));
        }
    }

    private List<NativeContentItem> onlyHavenTrending(Context context) throws Exception {
        ArrayList<NativeContentItem> items = new ArrayList<>();
        for (OnlyHavenRepository.Creator creator :
                new OnlyHavenRepository().fetchTrendingCreators(context, 24)) {
            if (creator == null || creator.url == null || creator.url.trim().isEmpty()) continue;
            if (creator.isKnownEmpty()) continue;
            String title = creator.name == null || creator.name.trim().isEmpty()
                    ? "OnlyHaven creator"
                    : creator.name.trim();
            String count = creator.postCount >= 0
                    ? creator.postCount + (creator.postCount == 1 ? " post" : " posts")
                    : "";
            items.add(new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    title,
                    creator.url,
                    creator.imageUrl,
                    count,
                    "OnlyHaven",
                    ""
            ));
        }
        return items;
    }

    private String status(Map<String, String> statuses, String key) {
        String value = statuses.get(key);
        return value == null ? "Not checked" : value;
    }

    private int countFeedItems(List<NativeContentItem> items) {
        int count = 0;
        if (items == null) return count;
        for (NativeContentItem item : items) {
            if (item != null && !item.isSection() && item.url != null && !item.url.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private String sourceStatus(
            boolean alreadyWatching,
            int itemCount,
            NotificationCoordinator.SourceAlert alert
    ) {
        if (!alreadyWatching) return "Watching " + itemCount + " current items";
        if (alert == null || alert.items.isEmpty()) return "No new content";
        int count = alert.items.size();
        return count + (count == 1 ? " new item found" : " new items found");
    }

    private String friendlyError(Exception error) {
        String message = error == null || error.getMessage() == null
                ? ""
                : error.getMessage().toLowerCase(Locale.US);
        if (message.contains("region") || message.contains("area")) return "Unavailable in this region";
        if (message.contains("timed out") || message.contains("timeout")) return "Timed out";
        if (message.contains("unable to resolve host") || message.contains("unknown host")) {
            return "No connection";
        }
        if (message.contains("no content") || message.contains("no videos")) {
            return "Source returned no content";
        }
        return "Could not check source";
    }

    private NotificationCoordinator.SourceAlert findNew(
            Context context,
            String key,
            String source,
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

        if (added.isEmpty() || added.size() > MAX_REASONABLE_NEW_ITEMS) return null;
        return new NotificationCoordinator.SourceAlert(key, source, added);
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

    private interface SourceLoader {
        List<NativeContentItem> load() throws Exception;
    }

    private static final class SourceCheck {
        final String key;
        final String label;
        final SourceLoader loader;

        SourceCheck(String key, String label, SourceLoader loader) {
            this.key = key;
            this.label = label;
            this.loader = loader;
        }
    }

    private static final class CheckResult {
        final boolean success;
        final String status;

        CheckResult(boolean success, String status) {
            this.success = success;
            this.status = status;
        }
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
        JSONObject release = new JSONObject(httpGetFirst(ZeroChillReleaseEndpoints.stableApis()));
        if (release.optBoolean("draft", false)) return null;
        return parseRelease(release, false);
    }

    private Release fetchLatestBeta() throws Exception {
        JSONArray releases = new JSONArray(httpGetFirst(ZeroChillReleaseEndpoints.releasesApis()));
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

    private String httpGetFirst(List<String> addresses) throws Exception {
        Exception lastError = null;
        for (String address : addresses) {
            try {
                return httpGet(address);
            } catch (Exception error) {
                lastError = error;
            }
        }
        if (lastError != null) throw lastError;
        throw new Exception("No update endpoint was available");
    }

    private String httpGet(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "ZeroChill-Android");
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
