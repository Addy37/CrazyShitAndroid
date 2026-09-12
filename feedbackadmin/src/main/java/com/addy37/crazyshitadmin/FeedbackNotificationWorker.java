package com.addy37.crazyshitadmin;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public final class FeedbackNotificationWorker extends Worker {
    private static final String PREFS = "notification_state";
    private static final String LAST_ID = "last_feedback_id";

    public FeedbackNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        String token = SecureTokenStore.read(getApplicationContext());
        if (token.isEmpty()) return Result.success();
        try {
            List<AdminRepository.Item> items = AdminRepository.list(token);
            if (items.isEmpty()) return Result.success();
            String previous = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(LAST_ID, "");
            AdminRepository.Item newest = items.get(0);
            getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(LAST_ID, newest.id).apply();
            if (previous == null || previous.isEmpty() || previous.equals(newest.id)) return Result.success();
            notify(newest);
            return Result.success();
        } catch (SecurityException error) {
            return Result.failure();
        } catch (Exception error) {
            return Result.retry();
        }
    }

    private void notify(AdminRepository.Item item) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = item.type.replace('_', ' ');
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplicationContext(), AdminApplication.CHANNEL_ID)
                .setSmallIcon(com.addy37.crazyshitadmin.R.drawable.ic_admin)
                .setContentTitle("New " + title)
                .setContentText(item.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(item.message))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(getApplicationContext())
                .notify(item.id.hashCode(), builder.build());
    }
}
