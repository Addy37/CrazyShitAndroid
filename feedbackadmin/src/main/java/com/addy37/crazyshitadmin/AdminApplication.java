package com.addy37.crazyshitadmin;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class AdminApplication extends Application {
    static final String CHANNEL_ID = "new_feedback";

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "New feedback", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Alerts when ZeroFilter receives new feedback");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        scheduleNotifications();
    }

    void scheduleNotifications() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                FeedbackNotificationWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "feedback_notifications", ExistingPeriodicWorkPolicy.UPDATE, request);
    }
}
