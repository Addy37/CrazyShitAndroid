package com.webapp.crazyshit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Creates branded alerts, owns their settings, and schedules background checks. */
final class NotificationCoordinator {
    static final String PREF_NEW_VIDEO_ALERTS = "new_video_notifications_enabled";
    static final String PREF_CRAZYSHIT_ALERTS = "crazyshit_notifications_enabled";
    static final String PREF_EFUKT_ALERTS = "efukt_notifications_enabled";
    static final String PREF_UPDATE_ALERTS = "update_notifications_enabled";
    static final String PREF_SHOW_TITLES = "notification_show_video_titles";
    static final String PREF_FREQUENCY_HOURS = "notification_check_frequency_hours";

    private static final String PREFS = "app_prefs";
    private static final String STATE = "notification_state";
    private static final String CHANNEL_VIDEOS = "new_videos_v1";
    private static final String CHANNEL_UPDATES = "app_updates_v1";
    private static final String GROUP_NEW_CONTENT = "crazyshit_new_content";
    private static final String PERIODIC_WORK = "content_and_update_notifications";
    private static final String INITIAL_WORK = "initial_content_and_update_check";
    private static final String MANUAL_WORK = "manual_content_and_update_check";
    private static final String KEY_EDUCATION_SHOWN = "notification_education_shown";
    private static final String KEY_PERMISSION_REQUESTED = "notification_permission_requested";
    private static final String KEY_LAST_UPDATE = "last_notified_update";
    static final String KEY_CHECK_STARTED = "content_check_started_at";
    static final String KEY_CHECK_FINISHED = "content_check_finished_at";
    static final String KEY_STATUS_CRAZYSHIT = "content_check_status_crazyshit";
    static final String KEY_STATUS_EFUKT = "content_check_status_efukt";
    static final String KEY_STATUS_KAOTIC = "content_check_status_kaotic";
    static final String KEY_STATUS_BUNKR = "content_check_status_bunkr";
    static final String KEY_STATUS_FAPELLO = "content_check_status_fapello";
    static final String KEY_STATUS_ONLYHAVEN = "content_check_status_onlyhaven";
    static final String KEY_STATUS_UPDATES = "content_check_status_updates";
    static final String INPUT_MANUAL_CHECK = "manual_check";
    static final String EXTRA_MANUAL_CHECK = "manual_check";
    static final String ACTION_CHECK_FINISHED =
            BuildConfig.APPLICATION_ID + ".action.NOTIFICATION_CHECK_FINISHED";

    private static final long STARTUP_CHECK_AGE_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long STUCK_CHECK_AGE_MS = TimeUnit.MINUTES.toMillis(3);

    private static final int REQUEST_NOTIFICATIONS = 731;
    private static final int ID_GROUP = 4199;
    private static final int ID_UPDATE = 4201;
    private static final int ID_TEST = 4301;

    private NotificationCoordinator() {
    }

    static void initialize(Context context) {
        createChannels(context);
        schedule(context, false);
    }

    static void onPreferencesChanged(Context context) {
        createChannels(context);
        schedule(context, true);
    }

    static void onAppForeground(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!alertsEnabled(prefs)) return;

        SharedPreferences state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        long finished = state.getLong(KEY_CHECK_FINISHED, 0L);
        long started = state.getLong(KEY_CHECK_STARTED, 0L);
        long now = System.currentTimeMillis();
        boolean running = started > finished && now - started <= STUCK_CHECK_AGE_MS;
        boolean stale = finished == 0L || now - finished >= STARTUP_CHECK_AGE_MS;
        if (!running && stale) enqueueImmediate(context, INITIAL_WORK, false);
    }

    static boolean isNotificationPreference(String key) {
        return PREF_NEW_VIDEO_ALERTS.equals(key) ||
                PREF_CRAZYSHIT_ALERTS.equals(key) ||
                PREF_EFUKT_ALERTS.equals(key) ||
                PREF_UPDATE_ALERTS.equals(key) ||
                PREF_SHOW_TITLES.equals(key);
    }

    static String frequencySummary(Context context) {
        int hours = normalizedHours(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_FREQUENCY_HOURS, 1));
        return hours == 1
                ? "Check about every hour. Android may delay background work to save battery."
                : "Check about every " + hours + " hours. Android may delay background work to save battery.";
    }

    static String statusSummary(Context context) {
        SharedPreferences state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        long started = state.getLong(KEY_CHECK_STARTED, 0L);
        long finished = state.getLong(KEY_CHECK_FINISHED, 0L);
        long now = System.currentTimeMillis();
        if (started > finished && now - started <= STUCK_CHECK_AGE_MS) {
            return "Checking ZEROCHILL sources now…";
        }
        if (finished == 0L) {
            return started > 0L
                    ? "The last check did not finish. Tap to try again."
                    : "No completed check yet. Tap to scan all supported sources now.";
        }

        CharSequence relative = DateUtils.getRelativeTimeSpanString(
                finished,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        );
        String crazyShit = state.getString(KEY_STATUS_CRAZYSHIT, "Not checked");
        String efukt = state.getString(KEY_STATUS_EFUKT, "Not checked");
        String kaotic = state.getString(KEY_STATUS_KAOTIC, "Not checked");
        String bunkr = state.getString(KEY_STATUS_BUNKR, "Not checked");
        String fapello = state.getString(KEY_STATUS_FAPELLO, "Not checked");
        String onlyHaven = state.getString(KEY_STATUS_ONLYHAVEN, "Not checked");
        return "Last checked " + relative + ". "
                + "CrazyShit: " + crazyShit + " · "
                + "EFukt: " + efukt + " · "
                + "Kaotic: " + kaotic + " · "
                + "Bunkr: " + bunkr + " · "
                + "Fapello: " + fapello + " · "
                + "OnlyHaven: " + onlyHaven + ".";
    }

    static UUID checkNow(Context context) {
        return enqueueImmediate(context, MANUAL_WORK, true);
    }

    static void setFrequency(Context context, int hours) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_FREQUENCY_HOURS, normalizedHours(hours))
                .apply();
        onPreferencesChanged(context);
    }

    static void maybeOfferPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < 33 || canPost(activity)) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_EDUCATION_SHOWN, false)) return;
        if (!alertsEnabled(prefs)) return;
        prefs.edit().putBoolean(KEY_EDUCATION_SHOWN, true).apply();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(notificationEducationView(activity))
                .setNegativeButton("Not now", null)
                .setPositiveButton("Enable", (ignored, which) -> requestPermission(activity))
                .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiPalette.PRIMARY);
            }
        });
        dialog.show();
    }

    private static android.view.View notificationEducationView(Activity activity) {
        android.widget.LinearLayout card = new android.widget.LinearLayout(activity);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(20f * activity.getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(ZeroChillUi.panelGlass(activity));

        android.widget.ImageView icon = new android.widget.ImageView(activity);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int iconSize = Math.round(72f * activity.getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams iconParams =
                new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.bottomMargin = Math.round(
                12f * activity.getResources().getDisplayMetrics().density
        );
        card.addView(icon, iconParams);

        android.widget.TextView title = new android.widget.TextView(activity);
        title.setText("ZEROCHILL ALERTS");
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        card.addView(title, new android.widget.LinearLayout.LayoutParams(-1, -2));

        android.widget.TextView body = new android.widget.TextView(activity);
        body.setText(
                "Fresh content from supported ZEROCHILL sources plus app updates. "
                        + "Titles are shown by default and can be changed anytime in Settings."
        );
        body.setTextColor(android.graphics.Color.rgb(184, 190, 198));
        body.setTextSize(14f);
        body.setGravity(android.view.Gravity.CENTER);
        body.setPadding(0,
                Math.round(8f * activity.getResources().getDisplayMetrics().density),
                0,
                0);
        card.addView(body, new android.widget.LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    static void requestPermissionFromSettings(Activity activity) {
        if (canPost(activity)) return;
        if (Build.VERSION.SDK_INT < 33) {
            openSystemNotificationSettings(activity);
            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean requested = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false);
        if (!requested || ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
        )) {
            requestPermission(activity);
        } else {
            openSystemNotificationSettings(activity);
        }
    }

    static void showTestNotification(Activity activity) {
        createChannels(activity);
        if (!canPost(activity)) {
            requestPermissionFromSettings(activity);
            return;
        }

        PendingIntent open = appPendingIntent(activity, 9301);
        Notification notification = baseBuilder(activity, CHANNEL_VIDEOS, "ZC")
                .setContentTitle("ZEROCHILL alerts are ready")
                .setContentText("Fresh content and app updates will appear here.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "Fresh content from supported ZEROCHILL sources and app update alerts "
                                + "will appear here."
                ))
                .setContentIntent(open)
                .addAction(R.drawable.ic_nav_home, "Open app", open)
                .setAutoCancel(true)
                .build();
        NotificationManagerCompat.from(activity).notify(ID_TEST, notification);
    }

    static void showNewVideoNotifications(Context context, List<SourceAlert> alerts) {
        if (!canPost(context) || alerts == null || alerts.isEmpty()) return;
        createChannels(context);

        List<ExperienceAlert> experiences = consolidateAlerts(alerts);
        if (experiences.isEmpty()) return;

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.cancel(ID_GROUP);
        cancelLegacySourceNotifications(manager);

        boolean grouped = experiences.size() > 1;
        int total = 0;
        for (ExperienceAlert alert : experiences) total += alert.items.size();

        for (ExperienceAlert alert : experiences) {
            NotificationCompat.Builder builder = experienceBuilder(context, alert);
            if (grouped) {
                builder.setGroup(GROUP_NEW_CONTENT)
                        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
            }
            manager.notify(sourceNotificationId(alert.key), builder.build());
        }

        if (grouped) {
            PendingIntent open = appPendingIntent(context, 9302);
            String text = total + (total == 1 ? " new item" : " new items") +
                    " across " + experienceLabels(experiences);
            Notification summary = baseBuilder(context, CHANNEL_VIDEOS, "ZC")
                    .setContentTitle("Fresh ZEROCHILL content")
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open)
                    .setGroup(GROUP_NEW_CONTENT)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setNumber(total)
                    .setAutoCancel(true)
                    .build();
            manager.notify(ID_GROUP, summary);
        }
    }

    static List<ExperienceAlert> consolidateAlerts(List<SourceAlert> alerts) {
        LinkedHashMap<String, ExperienceAlert> grouped = new LinkedHashMap<>();
        if (alerts == null) return new ArrayList<>();

        for (SourceAlert sourceAlert : alerts) {
            if (sourceAlert == null || sourceAlert.items == null || sourceAlert.items.isEmpty()) continue;
            String experienceKey = experienceKey(sourceAlert.key);
            ExperienceAlert experience = grouped.get(experienceKey);
            if (experience == null) {
                experience = new ExperienceAlert(experienceKey, experienceLabel(experienceKey));
                grouped.put(experienceKey, experience);
            }
            for (NativeContentItem item : sourceAlert.items) {
                if (item != null) experience.items.add(new ExperienceItem(sourceAlert.key, item));
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private static String experienceKey(String sourceKey) {
        String key = sourceKey == null ? "" : sourceKey.trim().toLowerCase(Locale.US);
        if ("fapello".equals(key) || "bunkr".equals(key) || "onlyhaven".equals(key)) {
            return "onlyfap";
        }
        if ("crazyshit".equals(key) || "efukt".equals(key) || "kaotic".equals(key)) {
            return "shittok";
        }
        return "zerochill";
    }

    private static String experienceLabel(String key) {
        if ("onlyfap".equals(key)) return "OnlyFap";
        if ("shittok".equals(key)) return "ShitTok";
        return "ZEROCHILL";
    }

    private static String experienceLabels(List<ExperienceAlert> alerts) {
        StringBuilder result = new StringBuilder();
        for (ExperienceAlert alert : alerts) {
            if (alert == null || alert.label == null || alert.label.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(result.indexOf(" and ") >= 0 ? ", " : " and ");
            result.append(alert.label);
        }
        return result.length() == 0 ? "ZEROCHILL" : result.toString();
    }

    private static void cancelLegacySourceNotifications(NotificationManagerCompat manager) {
        for (String key : new String[] {
                "crazyshit", "efukt", "kaotic", "bunkr", "fapello", "onlyhaven"
        }) {
            manager.cancel(sourceNotificationId(key));
        }
    }

    static void showUpdateNotification(Context context, String version, String title, boolean beta) {
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_UPDATE_ALERTS, true)) return;
        if (!canPost(context) || version == null || version.trim().isEmpty()) return;

        SharedPreferences state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        if (version.equals(state.getString(KEY_LAST_UPDATE, ""))) return;
        createChannels(context);

        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_CHECK_FOR_UPDATES, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(
                context,
                9400 + Math.abs(version.hashCode() % 500),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String cleanTitle = title == null || title.trim().isEmpty()
                ? "ZeroChill " + version
                : title.trim();
        String channel = beta ? "beta" : "stable";
        String details = cleanTitle + " is ready on the " + channel + " channel. " +
                "Tap to review it, then Android will ask before installation.";

        Notification publicVersion = new NotificationCompat.Builder(context, CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("App update available")
                .setContentText("Open ZeroChill to view it.")
                .build();

        Notification notification = baseBuilder(context, CHANNEL_UPDATES, "UP")
                .setContentTitle("ZeroChill " + version + " is ready")
                .setContentText("Tap to review and install the " + channel + " update.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(details))
                .setContentIntent(open)
                .addAction(R.drawable.ic_more_update, "View update", open)
                .setPublicVersion(publicVersion)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(context).notify(ID_UPDATE, notification);
        state.edit().putString(KEY_LAST_UPDATE, version).apply();
    }

    static void clearUpdateNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(ID_UPDATE);
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_UPDATE)
                .apply();
    }

    private static NotificationCompat.Builder experienceBuilder(
            Context context,
            ExperienceAlert alert
    ) {
        boolean showTitles = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_SHOW_TITLES, true);
        int count = alert.items.size();
        boolean onlyFap = "onlyfap".equals(alert.key);
        String title = onlyFap ? onlyFapTitle(alert) : generalExperienceTitle(alert);
        List<String> lines = notificationLines(alert);

        String body;
        if (showTitles && !lines.isEmpty()) {
            body = lines.get(0);
        } else if (onlyFap) {
            body = "Fresh creator content is ready.";
        } else {
            body = "Tap to see what was added.";
        }

        NotificationCompat.Style style;
        if (showTitles) {
            NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle()
                    .setBigContentTitle(title)
                    .setSummaryText(alert.label + " • New content");
            for (int i = 0; i < Math.min(5, lines.size()); i++) {
                inbox.addLine(lines.get(i));
            }
            if (lines.size() > 5) inbox.addLine("+" + (lines.size() - 5) + " more");
            if (lines.isEmpty()) inbox.addLine(body);
            style = inbox;
        } else {
            style = new NotificationCompat.BigTextStyle().bigText(
                    count == 1
                            ? "Fresh " + alert.label + " content is ready. Tap to open ZEROCHILL."
                            : count + " fresh " + alert.label + " items are ready. Tap to open ZEROCHILL."
            );
        }

        Notification publicVersion = new NotificationCompat.Builder(context, CHANNEL_VIDEOS)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("New ZEROCHILL content")
                .setContentText("Open ZEROCHILL to view it.")
                .build();

        PendingIntent open = appPendingIntent(
                context,
                sourceNotificationId(alert.key) + 5000
        );
        return baseBuilder(context, CHANNEL_VIDEOS, "ZC")
                .setContentTitle(title)
                .setContentText(body)
                .setSubText(alert.label)
                .setStyle(style)
                .setContentIntent(open)
                .addAction(R.drawable.ic_nav_home, "Open ZEROCHILL", open)
                .setPublicVersion(publicVersion)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setNumber(count)
                .setAutoCancel(true);
    }

    private static String onlyFapTitle(ExperienceAlert alert) {
        int count = alert.items.size();
        boolean allVideos = count > 0;
        for (ExperienceItem wrapped : alert.items) {
            if (wrapped.item == null || !wrapped.item.isVideo()) {
                allVideos = false;
                break;
            }
        }
        if (allVideos) {
            return count + (count == 1 ? " new video on OnlyFap" : " new videos on OnlyFap");
        }
        return count + (count == 1 ? " new item on OnlyFap" : " new items on OnlyFap");
    }

    private static String generalExperienceTitle(ExperienceAlert alert) {
        int count = alert.items.size();
        boolean allVideos = count > 0;
        for (ExperienceItem wrapped : alert.items) {
            if (wrapped.item == null || !wrapped.item.isVideo()) {
                allVideos = false;
                break;
            }
        }
        String noun = allVideos ? (count == 1 ? " video" : " videos")
                : (count == 1 ? " item" : " items");
        return count + " new" + noun + " on " + alert.label;
    }

    static List<String> notificationLines(ExperienceAlert alert) {
        if (alert == null) return new ArrayList<>();
        if ("onlyfap".equals(alert.key)) return onlyFapCreatorLines(alert.items);

        ArrayList<String> lines = new ArrayList<>();
        for (ExperienceItem wrapped : alert.items) {
            String title = cleanText(wrapped.item == null ? "" : wrapped.item.title);
            if (title.isEmpty()) continue;
            lines.add(title);
        }
        return lines;
    }

    private static List<String> onlyFapCreatorLines(List<ExperienceItem> items) {
        LinkedHashMap<String, CreatorUpdate> creators = new LinkedHashMap<>();
        if (items == null) return new ArrayList<>();

        for (ExperienceItem wrapped : items) {
            String creator = onlyFapCreatorName(wrapped);
            if (creator.isEmpty()) continue;
            String key = creator.toLowerCase(Locale.US);
            CreatorUpdate update = creators.get(key);
            if (update == null) {
                update = new CreatorUpdate(creator);
                creators.put(key, update);
            }
            update.count++;
            if ("fapello".equals(wrapped.sourceKey) &&
                    wrapped.item != null &&
                    wrapped.item.isVideo()) {
                update.videoCount++;
            }
        }

        ArrayList<String> lines = new ArrayList<>();
        for (CreatorUpdate update : creators.values()) {
            if (update.videoCount == update.count) {
                lines.add(update.name + " · " + update.count +
                        (update.count == 1 ? " new video" : " new videos"));
            } else if (update.count > 1) {
                lines.add(update.name + " · " + update.count + " new items");
            } else {
                lines.add(update.name + " · new content");
            }
        }
        return lines;
    }

    static String onlyFapCreatorName(ExperienceItem wrapped) {
        if (wrapped == null || wrapped.item == null) return "";
        String sourceKey = wrapped.sourceKey == null
                ? ""
                : wrapped.sourceKey.trim().toLowerCase(Locale.US);
        NativeContentItem item = wrapped.item;

        if ("onlyhaven".equals(sourceKey)) {
            return normalizeCreatorLabel(item.title);
        }
        if ("bunkr".equals(sourceKey)) {
            return creatorFromBunkrTitle(item.title);
        }
        if (!"fapello".equals(sourceKey)) {
            return normalizeCreatorLabel(item.title);
        }

        String uploader = normalizeCreatorLabel(item.uploader);
        if (!uploader.isEmpty()) return uploader;

        String title = cleanText(item.title)
                .replaceFirst("(?i)\\s*#\\d+\\s*$", "")
                .trim();
        if (!title.isEmpty() &&
                !title.matches("(?i)onlyfap\\s+video") &&
                !title.matches("(?i)fapello\\s+video")) {
            return normalizeCreatorLabel(title);
        }

        String slug = fapelloCreatorSlug(item.url);
        return humanizeSlug(slug);
    }

    private static String creatorFromBunkrTitle(String value) {
        String title = cleanText(value);
        if (title.isEmpty()) return "";

        int pipe = title.indexOf('|');
        if (pipe > 0) title = title.substring(0, pipe).trim();

        int dash = title.lastIndexOf(" - ");
        if (dash > 0 && dash + 3 < title.length()) {
            String left = title.substring(0, dash).trim();
            String right = title.substring(dash + 3).trim();
            String lower = left.toLowerCase(Locale.US);
            title = (lower.contains("leak") || lower.contains("vietcos") || left.length() > 48)
                    ? right
                    : left;
        }

        title = title.replace('_', ' ');
        return normalizeCreatorLabel(title);
    }

    private static String normalizeCreatorLabel(String value) {
        String clean = cleanText(value)
                .replaceFirst("(?i)^onlyfap\\s*[-:•]\\s*", "")
                .replaceFirst("(?i)\\s+(?:leaks?|content|collection)$", "")
                .trim();
        if (clean.isEmpty() || clean.matches("\\d+")) return "";
        if (clean.length() > 52) clean = clean.substring(0, 52).trim();
        return clean;
    }

    private static String fapelloCreatorSlug(String value) {
        String url = cleanText(value);
        if (url.isEmpty()) return "";
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null || path.trim().isEmpty()) return "";
            String[] raw = path.split("/");
            ArrayList<String> parts = new ArrayList<>();
            for (String part : raw) {
                if (part != null && !part.trim().isEmpty()) parts.add(part.trim());
            }
            if (parts.size() < 2) return "";

            int last = parts.size() - 1;
            if (!parts.get(last).matches("\\d+")) return "";
            if (last >= 2 && "video".equalsIgnoreCase(parts.get(last - 2))) {
                return parts.get(last - 1);
            }
            String candidate = parts.get(last - 1);
            return "video".equalsIgnoreCase(candidate) ? "" : candidate;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String humanizeSlug(String value) {
        String clean = cleanText(value).replace('-', ' ').replace('_', ' ').trim();
        if (clean.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String part : clean.split("\\s+")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            if (part.length() == 1) {
                result.append(part.toUpperCase(Locale.US));
            } else {
                result.append(part.substring(0, 1).toUpperCase(Locale.US))
                        .append(part.substring(1));
            }
        }
        return normalizeCreatorLabel(result.toString());
    }

    private static String cleanText(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static NotificationCompat.Builder baseBuilder(Context context, String channel, String mark) {
        return new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setLargeIcon(brandIcon(context, mark))
                .setColor(UiPalette.PRIMARY)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());
    }

    private static Bitmap brandIcon(Context context, String mark) {
        int size = Math.max(96, Math.round(
                64f * context.getResources().getDisplayMetrics().density
        ));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        android.graphics.drawable.Drawable icon = context.getDrawable(R.mipmap.ic_launcher);
        if (icon != null) {
            icon.setBounds(0, 0, size, size);
            icon.draw(canvas);
        } else {
            canvas.drawColor(Color.BLACK);
        }
        return bitmap;
    }

    private static PendingIntent feedPendingIntent(Context context, SourceAlert alert) {
        if ("crazyshit".equals(alert.key) || "efukt".equals(alert.key)) {
            boolean efukt = "efukt".equals(alert.key);
            Intent intent = new Intent(context, NativeFeedBrowserActivity.class);
            intent.putExtra(
                    NativeFeedBrowserActivity.EXTRA_TITLE,
                    efukt ? "New on EFukt" : "New on CrazyShit"
            );
            intent.putExtra(
                    NativeFeedBrowserActivity.EXTRA_BASE_URL,
                    efukt ? EfuktRepository.BASE : CrazyShitRepository.HOME
            );
            intent.putExtra(NativeFeedBrowserActivity.EXTRA_MEME_MODE, false);
            intent.putExtra(
                    NativeFeedBrowserActivity.EXTRA_SOURCE,
                    efukt
                            ? NativeFeedBrowserActivity.SOURCE_EFUKT
                            : NativeFeedBrowserActivity.SOURCE_CRAZYSHIT
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return PendingIntent.getActivity(
                    context,
                    sourceNotificationId(alert.key) + 5000,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }
        return appPendingIntent(context, sourceNotificationId(alert.key) + 5000);
    }

    private static int sourceNotificationId(String key) {
        String value = key == null ? "source" : key;
        return 4100 + Math.abs(value.hashCode() % 700);
    }

    private static PendingIntent appPendingIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void requestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < 33) return;
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PERMISSION_REQUESTED, true)
                .apply();
        ActivityCompat.requestPermissions(
                activity,
                new String[] {Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS
        );
    }

    private static void openSystemNotificationSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        activity.startActivity(intent);
    }

    private static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel videos = new NotificationChannel(
                CHANNEL_VIDEOS,
                "New content",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        videos.setDescription("Alerts when supported ZEROCHILL sources add fresh content");
        videos.enableLights(true);
        videos.setLightColor(UiPalette.PRIMARY);
        videos.setShowBadge(true);

        NotificationChannel updates = new NotificationChannel(
                CHANNEL_UPDATES,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        updates.setDescription("Alerts when a new ZeroChill app build is available");
        updates.enableLights(true);
        updates.setLightColor(UiPalette.PRIMARY);
        updates.setShowBadge(true);

        manager.createNotificationChannel(videos);
        manager.createNotificationChannel(updates);
    }

    private static void schedule(Context context, boolean updateExisting) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        WorkManager workManager = WorkManager.getInstance(context);
        if (!alertsEnabled(prefs)) {
            workManager.cancelUniqueWork(PERIODIC_WORK);
            workManager.cancelUniqueWork(INITIAL_WORK);
            return;
        }

        Constraints constraints = networkConstraints();
        int hours = normalizedHours(prefs.getInt(PREF_FREQUENCY_HOURS, 1));
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                ContentUpdateWorker.class,
                hours,
                TimeUnit.HOURS
        ).setConstraints(constraints).build();
        workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                updateExisting ? ExistingPeriodicWorkPolicy.UPDATE : ExistingPeriodicWorkPolicy.KEEP,
                periodic
        );

        SharedPreferences state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        long finished = state.getLong(KEY_CHECK_FINISHED, 0L);
        long started = state.getLong(KEY_CHECK_STARTED, 0L);
        long now = System.currentTimeMillis();
        boolean running = started > finished && now - started <= STUCK_CHECK_AGE_MS;
        if (updateExisting || (!running && finished == 0L)) {
            enqueueImmediate(context, INITIAL_WORK, false);
        }
    }

    private static UUID enqueueImmediate(Context context, String name, boolean manual) {
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_CHECK_STARTED, System.currentTimeMillis())
                .apply();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ContentUpdateWorker.class)
                .setConstraints(networkConstraints())
                .setInputData(new Data.Builder().putBoolean(INPUT_MANUAL_CHECK, manual).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.REPLACE,
                request
        );
        return request.getId();
    }

    private static Constraints networkConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    private static boolean alertsEnabled(SharedPreferences prefs) {
        boolean content = prefs.getBoolean(PREF_NEW_VIDEO_ALERTS, true);
        return content || prefs.getBoolean(PREF_UPDATE_ALERTS, true);
    }

    private static int normalizedHours(int value) {
        if (value >= 6) return 6;
        if (value >= 3) return 3;
        return 1;
    }

    static final class ExperienceItem {
        final String sourceKey;
        final NativeContentItem item;

        ExperienceItem(String sourceKey, NativeContentItem item) {
            this.sourceKey = sourceKey == null
                    ? ""
                    : sourceKey.trim().toLowerCase(Locale.US);
            this.item = item;
        }
    }

    static final class ExperienceAlert {
        final String key;
        final String label;
        final List<ExperienceItem> items = new ArrayList<>();

        ExperienceAlert(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private static final class CreatorUpdate {
        final String name;
        int count;
        int videoCount;

        CreatorUpdate(String name) {
            this.name = name;
        }
    }

    static final class SourceAlert {
        final String key;
        final String source;
        final List<NativeContentItem> items;

        SourceAlert(String key, String source, List<NativeContentItem> items) {
            this.key = key == null ? "source" : key.trim().toLowerCase(java.util.Locale.US);
            this.source = source == null ? "ZEROCHILL" : source;
            this.items = items == null ? new ArrayList<>() : items;
        }
    }
}
