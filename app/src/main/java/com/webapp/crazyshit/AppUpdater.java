package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppUpdater {
    private static final String STABLE_API =
            "https://api.github.com/repos/Addy37/CrazyShitAndroid/releases/latest";
    private static final String RELEASES_API =
            "https://api.github.com/repos/Addy37/CrazyShitAndroid/releases?per_page=20";
    private static final long CHECK_INTERVAL_MS = 4L * 60L * 60L * 1000L;
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final Activity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final boolean betaChannel;

    private volatile boolean checking;
    private volatile boolean cancelDownload;
    private File pendingInstall;
    private boolean waitingForInstallPermission;

    AppUpdater(Activity activity) {
        this.activity = activity;
        this.betaChannel = activity.getPackageName().endsWith(".dev");
    }

    void check(boolean manual) {
        if (checking) {
            if (manual) Toast.makeText(activity, "Already checking for updates…", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        String key = betaChannel ? "beta_last_update_check" : "stable_last_update_check";
        long now = System.currentTimeMillis();
        long last = prefs.getLong(key, 0L);
        if (!manual && now - last < CHECK_INTERVAL_MS) return;
        prefs.edit().putLong(key, now).apply();

        checking = true;
        if (manual) Toast.makeText(activity, "Checking for updates…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                ReleaseInfo release = betaChannel ? fetchLatestBeta() : fetchStable();
                String current = currentVersion();
                boolean newer = release != null && compareVersions(release.version, current) > 0;
                activity.runOnUiThread(() -> {
                    checking = false;
                    if (newer) {
                        showUpdateDialog(release, current);
                    } else if (manual) {
                        Toast.makeText(
                                activity,
                                betaChannel ? "You're on the latest v2 beta." : "You're up to date.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    checking = false;
                    if (manual) Toast.makeText(
                            activity,
                            "Couldn't check for updates right now.",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    void onHostResume() {
        if (!waitingForInstallPermission || pendingInstall == null) return;
        if (Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = false;
            launchInstaller(pendingInstall);
        }
    }

    void close() {
        cancelDownload = true;
        io.shutdownNow();
    }

    private ReleaseInfo fetchStable() throws Exception {
        JSONObject release = new JSONObject(httpGet(STABLE_API));
        if (release.optBoolean("draft", false)) return null;
        return parseRelease(release, false);
    }

    private ReleaseInfo fetchLatestBeta() throws Exception {
        JSONArray releases = new JSONArray(httpGet(RELEASES_API));
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft", false)) continue;
            if (!release.optBoolean("prerelease", false)) continue;
            String tag = release.optString("tag_name", "");
            if (!tag.toLowerCase(Locale.US).contains("beta")) continue;
            ReleaseInfo info = parseRelease(release, true);
            if (info != null) return info;
        }
        return null;
    }

    private ReleaseInfo parseRelease(JSONObject release, boolean beta) {
        String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
        if (tag.isEmpty()) return null;

        JSONArray assets = release.optJSONArray("assets");
        String apkName = "";
        String apkUrl = "";
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (!name.toLowerCase(Locale.US).endsWith(".apk")) continue;
                String lower = name.toLowerCase(Locale.US);
                if (beta && !(lower.contains("test") || lower.contains("beta"))) continue;
                if (!beta && lower.contains("test")) continue;
                apkName = name;
                apkUrl = asset.optString("browser_download_url", "");
                if (!apkUrl.isEmpty()) break;
            }
        }
        if (apkUrl.isEmpty()) return null;

        String title = release.optString("name", tag);
        String page = release.optString("html_url", "");
        return new ReleaseInfo(tag, title, apkName, apkUrl, page, beta);
    }

    private String httpGet(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "CrazyShit-Jeremy-Edition-Android");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            InputStream in = connection.getInputStream();
            byte[] bytes = readAll(in);
            in.close();
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private void showUpdateDialog(ReleaseInfo release, String current) {
        String channel = release.beta ? "v2 beta" : "stable";
        new AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage(
                        release.title + " is available.\n\n" +
                        "Installed: " + current + "\n" +
                        "Available: " + release.version + "\n" +
                        "Channel: " + channel + "\n\n" +
                        "The APK can be downloaded inside the app. Android will still show its required install confirmation."
                )
                .setNegativeButton("Later", null)
                .setPositiveButton("Download & install", (dialog, which) -> downloadAndInstall(release))
                .show();
    }

    private void downloadAndInstall(ReleaseInfo release) {
        cancelDownload = false;

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(12), pad, dp(6));

        TextView label = new TextView(activity);
        label.setText("Starting download…");
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        box.addView(label, new LinearLayout.LayoutParams(-1, -2));

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(8));
        bp.setMargins(0, dp(14), 0, dp(8));
        box.addView(bar, bp);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Downloading update")
                .setView(box)
                .setNegativeButton("Cancel", (d, w) -> cancelDownload = true)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Couldn't create update folder");
                File target = new File(dir, "CrazyShit-update.apk");
                if (target.exists()) target.delete();

                connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "CrazyShit-Jeremy-Edition-Android");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

                long total = connection.getContentLengthLong();
                long downloaded = 0L;
                int lastPercent = -1;
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (cancelDownload) throw new InterruptedException("Cancelled");
                        output.write(buffer, 0, read);
                        downloaded += read;
                        if (total > 0) {
                            int percent = (int) Math.min(100L, downloaded * 100L / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int p = percent;
                                activity.runOnUiThread(() -> {
                                    bar.setIndeterminate(false);
                                    bar.setProgress(p);
                                    label.setText("Downloading… " + p + "%");
                                });
                            }
                        }
                    }
                }

                verifyPackage(target);
                pendingInstall = target;
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    requestInstall(target);
                });
            } catch (InterruptedException cancelled) {
                activity.runOnUiThread(dialog::dismiss);
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(activity, "Update download failed.", Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void verifyPackage(File apk) throws Exception {
        PackageInfo archive = activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null || archive.packageName == null) throw new Exception("Invalid APK");
        if (!activity.getPackageName().equals(archive.packageName)) {
            throw new Exception("Update package doesn't match installed app");
        }
    }

    private void requestInstall(File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = apk;
            waitingForInstallPermission = true;
            Toast.makeText(
                    activity,
                    "Allow updates from this app, then you'll return automatically.",
                    Toast.LENGTH_LONG
            ).show();
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(settings);
            return;
        }
        launchInstaller(apk);
    }

    private void launchInstaller(File apk) {
        try {
            pendingInstall = apk;
            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".files",
                    apk
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Couldn't open Android's installer.", Toast.LENGTH_LONG).show();
        }
    }

    private String currentVersion() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (Exception e) {
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
        ArrayList<Integer> out = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        while (matcher.find()) {
            try {
                out.add(Integer.parseInt(matcher.group()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class ReleaseInfo {
        final String version;
        final String title;
        final String apkName;
        final String apkUrl;
        final String pageUrl;
        final boolean beta;

        ReleaseInfo(String version, String title, String apkName, String apkUrl, String pageUrl, boolean beta) {
            this.version = version;
            this.title = title;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.pageUrl = pageUrl;
            this.beta = beta;
        }
    }
}
