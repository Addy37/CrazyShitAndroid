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
import java.util.List;
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
    static final String KEY_STATUS_UPDATES = "content_check_status_updates";
    static final String INPUT_MANUAL_CHECK = "manual_check";
    static final String EXTRA_MANUAL_CHECK = "manual_check";
    static final String ACTION_CHECK_FINISHED =
            BuildConfig.APPLICATION_ID + ".action.NOTIFICATION_CHECK_FINISHED";

    private static final long STARTUP_CHECK_AGE_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long STUCK_CHECK_AGE_MS = TimeUnit.MINUTES.toMillis(3);

    private static final int REQUEST_NOTIFICATIONS = 731;
    private static final int ID_CRAZYSHIT = 4101;
    private static final int ID_EFUKT = 4102;
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
            return "Checking CrazyShit and EFukt now…";
        }
        if (finished == 0L) {
            return started > 0L
                    ? "The last check did not finish. Tap to try again."
                    : "No completed check yet. Tap to scan both sites now.";
        }

        CharSequence relative = DateUtils.getRelativeTimeSpanString(
                finished,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        );
        String crazyShit = state.getString(KEY_STATUS_CRAZYSHIT, "Not checked");
        String efukt = state.getString(KEY_STATUS_EFUKT, "Not checked");
        return "Last checked " + relative + ". CrazyShit: " + crazyShit + ". EFukt: " + efukt + ".";
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

        new AlertDialog.Builder(activity)
                .setTitle("Stay in the loop")
                .setMessage(
                        "Get alerts when new videos or app updates arrive. " +
                        "Video titles stay hidden by default, and you can change each alert in Settings."
                )
                .setNegativeButton("Not now", null)
                .setPositiveButton("Turn on alerts", (dialog, which) -> requestPermission(activity))
                .show();
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
        Notification notification = baseBuilder(activity, CHANNEL_VIDEOS, "ZF")
                .setContentTitle("Notifications are ready")
                .setContentText("New video and app update alerts will appear here.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "New video and app update alerts will appear here. " +
                        "You can keep video titles hidden in Notification Settings."
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

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.cancel(ID_CRAZYSHIT);
        manager.cancel(ID_EFUKT);
        manager.cancel(ID_GROUP);

        boolean grouped = alerts.size() > 1;
        int total = 0;
        for (SourceAlert alert : alerts) total += alert.items.size();

        for (SourceAlert alert : alerts) {
            NotificationCompat.Builder builder = videoBuilder(context, alert);
            if (grouped) {
                builder.setGroup(GROUP_NEW_CONTENT)
                        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
            }
            manager.notify(alert.efukt ? ID_EFUKT : ID_CRAZYSHIT, builder.build());
        }

        if (grouped) {
            PendingIntent open = appPendingIntent(context, 9302);
            String text = total + (total == 1 ? " new video" : " new videos") +
                    " across CrazyShit and EFukt";
            Notification summary = baseBuilder(context, CHANNEL_VIDEOS, "ZF")
                    .setContentTitle("Fresh uploads are ready")
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
                ? "ZeroFilter " + version
                : title.trim();
        String channel = beta ? "beta" : "stable";
        String details = cleanTitle + " is ready on the " + channel + " channel. " +
                "Tap to review it, then Android will ask before installation.";

        Notification publicVersion = new NotificationCompat.Builder(context, CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("App update available")
                .setContentText("Open ZeroFilter to view it.")
                .build();

        Notification notification = baseBuilder(context, CHANNEL_UPDATES, "UP")
                .setContentTitle("ZeroFilter " + version + " is ready")
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

    private static NotificationCompat.Builder videoBuilder(Context context, SourceAlert alert) {
        boolean showTitles = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_SHOW_TITLES, false);
        int count = alert.items.size();
        String source = alert.source;
        String title = count + (count == 1 ? " new video on " : " new videos on ") + source;
        String body = showTitles && count == 1
                ? alert.items.get(0).title
                : "Tap to see what was added.";
        PendingIntent open = feedPendingIntent(context, alert);

        NotificationCompat.Style style;
        if (showTitles) {
            NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle()
                    .setBigContentTitle(title)
                    .setSummaryText(source + " • New uploads");
            for (int i = 0; i < Math.min(5, count); i++) {
                String itemTitle = alert.items.get(i).title;
                if (itemTitle != null && !itemTitle.trim().isEmpty()) inbox.addLine(itemTitle.trim());
            }
            if (count > 5) inbox.addLine("+" + (count - 5) + " more");
            style = inbox;
        } else {
            style = new NotificationCompat.BigTextStyle().bigText(
                    count == 1
                            ? "A fresh upload from " + source + " is ready. Tap to open the latest feed."
                            : count + " fresh uploads from " + source + " are ready. Tap to open the latest feed."
            );
        }

        Notification publicVersion = new NotificationCompat.Builder(context, CHANNEL_VIDEOS)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("New videos available")
                .setContentText("Open ZeroFilter to view them.")
                .build();

        return baseBuilder(context, CHANNEL_VIDEOS, alert.efukt ? "EF" : "CS")
                .setContentTitle(title)
                .setContentText(body)
                .setSubText(source)
                .setStyle(style)
                .setContentIntent(open)
                .addAction(R.drawable.ic_nav_home, "View new videos", open)
                .setPublicVersion(publicVersion)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setNumber(count)
                .setAutoCancel(true);
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
        int size = Math.max(96, Math.round(64f * context.getResources().getDisplayMetrics().density));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float center = size / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(10, 10, 12));
        canvas.drawCircle(center, center, center * 0.94f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(4f, size * 0.055f));
        paint.setColor(UiPalette.PRIMARY);
        canvas.drawCircle(center, center, center * 0.82f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        ));
        paint.setTextSize(size * (mark.length() > 2 ? 0.30f : 0.38f));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = center - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(mark, center, baseline, paint);
        return bitmap;
    }

    private static PendingIntent feedPendingIntent(Context context, SourceAlert alert) {
        Intent intent = new Intent(context, NativeFeedBrowserActivity.class);
        intent.putExtra(NativeFeedBrowserActivity.EXTRA_TITLE, alert.efukt ? "New on EFukt" : "New on CrazyShit");
        intent.putExtra(
                NativeFeedBrowserActivity.EXTRA_BASE_URL,
                alert.efukt ? EfuktRepository.BASE : CrazyShitRepository.HOME
        );
        intent.putExtra(NativeFeedBrowserActivity.EXTRA_MEME_MODE, false);
        intent.putExtra(
                NativeFeedBrowserActivity.EXTRA_SOURCE,
                alert.efukt
                        ? NativeFeedBrowserActivity.SOURCE_EFUKT
                        : NativeFeedBrowserActivity.SOURCE_CRAZYSHIT
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                alert.efukt ? 9202 : 9201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
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
                "New videos",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        videos.setDescription("Alerts when followed sites add new videos");
        videos.enableLights(true);
        videos.setLightColor(UiPalette.PRIMARY);
        videos.setShowBadge(true);

        NotificationChannel updates = new NotificationChannel(
                CHANNEL_UPDATES,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        updates.setDescription("Alerts when a new ZeroFilter app build is available");
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
        boolean videos = prefs.getBoolean(PREF_NEW_VIDEO_ALERTS, true) &&
                (prefs.getBoolean(PREF_CRAZYSHIT_ALERTS, true) ||
                        prefs.getBoolean(PREF_EFUKT_ALERTS, true));
        return videos || prefs.getBoolean(PREF_UPDATE_ALERTS, true);
    }

    private static int normalizedHours(int value) {
        if (value >= 6) return 6;
        if (value >= 3) return 3;
        return 1;
    }

    static final class SourceAlert {
        final String source;
        final boolean efukt;
        final List<NativeContentItem> items;

        SourceAlert(String source, boolean efukt, List<NativeContentItem> items) {
            this.source = source;
            this.efukt = efukt;
            this.items = items == null ? new ArrayList<>() : items;
        }
    }
}
