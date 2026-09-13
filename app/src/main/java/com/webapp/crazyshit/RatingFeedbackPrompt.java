package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/** Tracks meaningful app use and presents a restrained, version-aware feedback prompt. */
final class RatingFeedbackPrompt {
    static final int REQUIRED_SESSIONS = 3;
    static final int REQUIRED_PLAYBACKS = 5;
    static final long REQUIRED_ACTIVE_MS = 15L * 60L * 1000L;
    static final long SNOOZE_MS = 14L * 24L * 60L * 60L * 1000L;
    static final long ERROR_COOLDOWN_MS = 10L * 60L * 1000L;
    private static final long DUPLICATE_PLAYBACK_MS = 10L * 60L * 1000L;
    private static final String PREFS = "app_prefs";

    private static int startedActivities;
    private static long foregroundStartedAt;
    private static boolean configurationRestartPending;
    private static boolean showing;

    private RatingFeedbackPrompt() {
    }

    static synchronized void onActivityStarted(Activity activity) {
        if (startedActivities++ != 0) return;
        foregroundStartedAt = SystemClock.elapsedRealtime();
        if (configurationRestartPending) {
            configurationRestartPending = false;
            return;
        }
        SharedPreferences prefs = prefs(activity);
        String key = key("sessions");
        prefs.edit().putInt(key, Math.min(REQUIRED_SESSIONS, prefs.getInt(key, 0) + 1)).apply();
    }

    static synchronized void onActivityStopped(Activity activity) {
        if (startedActivities > 0) startedActivities--;
        if (startedActivities != 0) return;
        saveForegroundTime(activity);
        configurationRestartPending = activity.isChangingConfigurations();
    }

    static void recordSuccessfulPlayback(Context context, String mediaKey) {
        if (context == null) return;
        long now = System.currentTimeMillis();
        String fingerprint = Integer.toHexString((mediaKey == null ? "video" : mediaKey).hashCode());
        SharedPreferences prefs = prefs(context);
        if (fingerprint.equals(prefs.getString(key("last_playback"), ""))
                && now - prefs.getLong(key("last_playback_at"), 0L) < DUPLICATE_PLAYBACK_MS) {
            return;
        }
        int plays = prefs.getInt(key("playbacks"), 0);
        prefs.edit()
                .putInt(key("playbacks"), Math.min(REQUIRED_PLAYBACKS, plays + 1))
                .putString(key("last_playback"), fingerprint)
                .putLong(key("last_playback_at"), now)
                .apply();
    }

    static void recordPlaybackError(Context context) {
        if (context == null) return;
        prefs(context).edit().putLong(key("last_error_at"), System.currentTimeMillis()).apply();
    }

    static synchronized boolean isEligible(Context context) {
        SharedPreferences prefs = prefs(context);
        long now = System.currentTimeMillis();
        long activeMs = prefs.getLong(key("active_ms"), 0L);
        if (startedActivities > 0 && foregroundStartedAt > 0L) {
            activeMs += Math.max(0L, SystemClock.elapsedRealtime() - foregroundStartedAt);
        }
        return isEligibleState(
                prefs.getInt(key("sessions"), 0),
                activeMs,
                prefs.getInt(key("playbacks"), 0),
                prefs.getInt(key("dismissals"), 0),
                prefs.getBoolean(key("completed"), false),
                prefs.getLong(key("snooze_until"), 0L),
                prefs.getLong(key("last_error_at"), 0L),
                now
        );
    }

    static boolean isEligibleState(
            int sessions,
            long activeMs,
            int playbacks,
            int dismissals,
            boolean completed,
            long snoozeUntil,
            long lastErrorAt,
            long now
    ) {
        return sessions >= REQUIRED_SESSIONS
                && activeMs >= REQUIRED_ACTIVE_MS
                && playbacks >= REQUIRED_PLAYBACKS
                && dismissals < 2
                && !completed
                && now >= snoozeUntil
                && (lastErrorAt <= 0L || now - lastErrorAt >= ERROR_COOLDOWN_MS);
    }

    static synchronized void maybeShow(Activity activity) {
        if (activity == null || activity.isFinishing() || showing || !isEligible(activity)) return;
        if (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        if (!decor.hasWindowFocus()) return;
        showing = true;
        markOffered(activity);
        showDialog(activity);
    }

    static void markFeedbackSubmitted(Context context) {
        prefs(context).edit().putBoolean(key("completed"), true).apply();
    }

    private static void showDialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 16));
        panel.setBackground(rounded(Color.rgb(24, 24, 28), 22, activity));

        TextView title = text(activity, "Enjoying CrazyShit?", 23, Color.WHITE, true);
        panel.addView(title);

        TextView message = text(activity, "Your feedback helps improve the app.", 14,
                Color.rgb(178, 178, 187), false);
        message.setPadding(0, dp(activity, 7), 0, dp(activity, 15));
        panel.addView(message);

        LinearLayout stars = new LinearLayout(activity);
        stars.setGravity(Gravity.CENTER);
        final int[] selectedRating = {0};
        for (int value = 1; value <= 5; value++) {
            final int rating = value;
            TextView star = text(activity, "☆", 36, Color.rgb(126, 126, 136), false);
            star.setGravity(Gravity.CENTER);
            star.setClickable(true);
            star.setFocusable(true);
            star.setTag(rating);
            star.setContentDescription(rating + (rating == 1 ? " star" : " stars"));
            star.setOnClickListener(view -> {
                selectedRating[0] = rating;
                updateStars(stars, rating);
            });
            stars.addView(star, new LinearLayout.LayoutParams(0, dp(activity, 58), 1f));
        }
        panel.addView(stars);

        MaterialButton send = button(activity, "Send feedback", true);
        panel.addView(send, margin(-1, dp(activity, 50), 0, dp(activity, 14), 0, 0, activity));

        MaterialButton later = button(activity, "Not now", false);
        panel.addView(later, margin(-1, dp(activity, 46), 0, dp(activity, 5), 0, 0, activity));

        final boolean[] handled = {false};
        send.setOnClickListener(view -> {
            handled[0] = true;
            markOffered(activity);
            Intent intent = new Intent(activity, FeedbackActivity.class);
            intent.putExtra(FeedbackActivity.EXTRA_PRESELECTED_RATING, selectedRating[0]);
            intent.putExtra(FeedbackActivity.EXTRA_SOURCE, "Rating prompt");
            dialog.dismiss();
            activity.startActivity(intent);
        });
        later.setOnClickListener(view -> {
            handled[0] = true;
            markDismissed(activity);
            dialog.dismiss();
        });
        dialog.setOnCancelListener(ignored -> {
            if (!handled[0]) {
                handled[0] = true;
                markDismissed(activity);
            }
        });
        dialog.setOnDismissListener(ignored -> showing = false);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        attributes.width = Math.min(screenWidth - dp(activity, 32), dp(activity, 430));
        attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attributes.gravity = Gravity.CENTER;
        attributes.dimAmount = 0.68f;
        window.setAttributes(attributes);
    }

    private static void updateStars(LinearLayout stars, int selected) {
        for (int index = 0; index < stars.getChildCount(); index++) {
            TextView star = (TextView) stars.getChildAt(index);
            int value = (int) star.getTag();
            star.setText(value <= selected ? "★" : "☆");
            star.setTextColor(value <= selected ? UiPalette.PRIMARY : Color.rgb(126, 126, 136));
        }
    }

    private static void markOffered(Context context) {
        prefs(context).edit()
                .putLong(key("snooze_until"), System.currentTimeMillis() + SNOOZE_MS)
                .apply();
    }

    private static void markDismissed(Context context) {
        SharedPreferences prefs = prefs(context);
        int dismissals = Math.min(2, prefs.getInt(key("dismissals"), 0) + 1);
        prefs.edit()
                .putInt(key("dismissals"), dismissals)
                .putLong(key("snooze_until"), System.currentTimeMillis() + SNOOZE_MS)
                .apply();
    }

    private static synchronized void saveForegroundTime(Context context) {
        if (foregroundStartedAt <= 0L) return;
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - foregroundStartedAt);
        foregroundStartedAt = 0L;
        SharedPreferences prefs = prefs(context);
        long total = prefs.getLong(key("active_ms"), 0L);
        prefs.edit().putLong(key("active_ms"), Math.min(REQUIRED_ACTIVE_MS, total + elapsed)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String name) {
        return "rating_prompt_v" + majorVersion() + "_" + name;
    }

    private static String majorVersion() {
        String version = BuildConfig.VERSION_NAME == null ? "0" : BuildConfig.VERSION_NAME;
        int dot = version.indexOf('.');
        String major = dot > 0 ? version.substring(0, dot) : version;
        String digits = major.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? String.valueOf(BuildConfig.VERSION_CODE) : digits;
    }

    private static MaterialButton button(Context context, String label, boolean primary) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(primary ? UiPalette.ON_PRIMARY : Color.WHITE);
        button.setTextSize(15);
        button.setCornerRadius(dp(context, 15));
        button.setBackgroundTintList(ColorStateList.valueOf(
                primary ? UiPalette.PRIMARY : Color.rgb(38, 38, 43)));
        return button;
    }

    private static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private static GradientDrawable rounded(int color, int radius, Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(context, radius));
        background.setStroke(dp(context, 1), Color.rgb(48, 48, 54));
        return background;
    }

    private static LinearLayout.LayoutParams margin(
            int width, int height, int left, int top, int right, int bottom, Context context
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
