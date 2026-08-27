package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.WeakHashMap;

/** One-time control introduction that can also be reopened from More. */
final class GestureGuideDialog {
    private static final String PREF_SEEN = "gesture_guide_beta15_seen";
    private static final WeakHashMap<Activity, Boolean> PENDING = new WeakHashMap<>();

    private GestureGuideDialog() {
    }

    static void maybeShow(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean(PREF_SEEN, false)) {
            return;
        }
        synchronized (PENDING) {
            if (Boolean.TRUE.equals(PENDING.get(activity))) return;
            PENDING.put(activity, true);
        }
        schedule(activity, 0);
    }

    static void show(Activity activity) {
        show(activity, false);
    }

    private static void schedule(Activity activity, int attempt) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            clearPending(activity);
            return;
        }
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                clearPending(activity);
                return;
            }
            if (ChaosStartupHandoff.isWaiting() || !activity.hasWindowFocus()) {
                if (attempt < 24) schedule(activity, attempt + 1);
                else clearPending(activity);
                return;
            }
            show(activity, true);
        }, attempt == 0 ? 900L : 260L);
    }

    private static void show(Activity activity, boolean markSeen) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            clearPending(activity);
            return;
        }
        if (markSeen) {
            activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_SEEN, true)
                    .apply();
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 18), dp(activity, 4), dp(activity, 18), dp(activity, 10));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        TextView intro = text(activity,
                "The app keeps the controls out of the way until you need them.",
                14,
                Color.rgb(194, 194, 202),
                false);
        intro.setPadding(dp(activity, 2), 0, dp(activity, 2), dp(activity, 12));
        content.addView(intro);

        addGesture(activity, content, "TAP", "Show or hide controls",
                "Tap a regular video once to reveal the player controls.");
        addGesture(activity, content, "SEEK", "Move through a video",
                "Use the back and forward controls or drag the yellow timeline.");
        addGesture(activity, content, "↓", "Minimize a regular video",
                "Swipe down from a portrait video to keep it playing above the tabs.");
        addGesture(activity, content, "MINI", "Return to the player",
                "Tap the mini-player to expand the same playback session.");
        addGesture(activity, content, "2×", "Chaos speed boost",
                "Press and hold a Chaos video to play at double speed. Swipe up or down for the next clip.");
        addGesture(activity, content, "BACK", "Related video history",
                "Swipe back to preview and return through the related videos you opened.");

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Gesture guide")
                .setView(scroll)
                .setPositiveButton("Got it", null)
                .create();
        dialog.setOnDismissListener(ignored -> clearPending(activity));
        dialog.show();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiPalette.PRIMARY);
        }
    }

    private static void addGesture(
            Activity activity,
            LinearLayout parent,
            String badge,
            String title,
            String body
    ) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));
        card.setBackground(cardBackground(activity));

        TextView badgeView = text(activity, badge, 11, Color.rgb(10, 10, 0), true);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setMinWidth(dp(activity, 46));
        badgeView.setBackground(badgeBackground(activity));
        card.addView(badgeView, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 36)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 12), 0, 0, 0);
        labels.addView(text(activity, title, 15, Color.WHITE, true));
        TextView description = text(activity, body, 12, Color.rgb(177, 177, 186), false);
        description.setPadding(0, dp(activity, 3), 0, 0);
        labels.addView(description);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(activity, 7));
        parent.addView(card, params);
    }

    private static TextView text(Activity activity, String value, float size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static GradientDrawable cardBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(22, 22, 25));
        background.setCornerRadius(dp(activity, 17));
        background.setStroke(dp(activity, 1), Color.rgb(48, 48, 54));
        return background;
    }

    private static GradientDrawable badgeBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.PRIMARY);
        background.setCornerRadius(dp(activity, 12));
        return background;
    }

    private static void clearPending(Activity activity) {
        if (activity == null) return;
        synchronized (PENDING) {
            PENDING.remove(activity);
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
