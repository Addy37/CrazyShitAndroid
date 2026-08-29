package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One-time access, content, regional availability, and affiliation notice. */
final class AccessNoticeDialog {
    // Use a versioned key so existing installs see materially revised notice text once.
    private static final String PREF_ACCEPTED = "access_notice_2_8_3_accepted";

    private AccessNoticeDialog() {
    }

    static boolean isAccepted(Activity activity) {
        return activity != null && activity
                .getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean(PREF_ACCEPTED, false);
    }

    static void show(Activity activity, Runnable onAccepted) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 20), dp(activity, 18),
                dp(activity, 20), dp(activity, 10));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        addHeader(activity, content);
        addNoticeCard(
                activity,
                content,
                "CONTENT WARNING",
                "CrazyShit and EFukt contain adult, graphic, violent, and otherwise sensitive " +
                        "material. Continue only if you are 18 or older and choose to view it."
        );
        addNoticeCard(
                activity,
                content,
                "REGIONAL ACCESS",
                "Access may be restricted in some U.S. states and other regions because of " +
                        "local age-verification laws, provider blocks, or site policies. " +
                        "Availability can change based on your location."
        );
        addNoticeCard(
                activity,
                content,
                "VPN NOTE",
                "If a site is unavailable, a reputable VPN may help with a regional or provider " +
                        "block. A VPN does not make restricted access legal or replace required " +
                        "age verification. Follow your local laws and each site's terms."
        );
        addNoticeCard(
                activity,
                content,
                "UNOFFICIAL APP",
                "This independent, unofficial app was made for fun. It is not affiliated with, " +
                        "endorsed by, sponsored by, or published by CrazyShit.com or EFukt.com."
        );

        TextView responsibility = text(
                activity,
                "By continuing, you confirm that you are at least 18 and responsible for how " +
                        "you access and use the app.",
                12,
                Color.rgb(169, 169, 179),
                false
        );
        responsibility.setGravity(Gravity.CENTER);
        responsibility.setPadding(dp(activity, 5), dp(activity, 7),
                dp(activity, 5), dp(activity, 4));
        content.addView(responsibility);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .setCancelable(false)
                .setNegativeButton("Exit", (ignored, which) -> activity.finish())
                .setPositiveButton("I understand", (ignored, which) -> {
                    activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                            .edit()
                            .putBoolean(PREF_ACCEPTED, true)
                            .apply();
                    if (onAccepted != null) onAccepted.run();
                })
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> styleDialog(activity, dialog));
        dialog.show();
    }

    private static void addHeader(Activity activity, LinearLayout content) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(activity, 14));

        TextView badge = text(activity, "18+", 15, UiPalette.ON_PRIMARY, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(badgeBackground(activity));
        header.addView(badge, new LinearLayout.LayoutParams(dp(activity, 54), dp(activity, 54)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 13), 0, 0, 0);
        labels.addView(text(activity, "Before you continue", 22, Color.WHITE, true));
        TextView subtitle = text(
                activity,
                "Adult content and access notice",
                12,
                Color.rgb(177, 177, 187),
                false
        );
        subtitle.setPadding(0, dp(activity, 3), 0, 0);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void addNoticeCard(
            Activity activity,
            LinearLayout content,
            String label,
            String body
    ) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 14), dp(activity, 12),
                dp(activity, 14), dp(activity, 12));
        card.setBackground(cardBackground(activity));

        TextView labelView = text(activity, label, 11, UiPalette.PRIMARY, true);
        labelView.setLetterSpacing(0.08f);
        card.addView(labelView);

        TextView bodyView = text(activity, body, 13, Color.rgb(220, 220, 226), false);
        bodyView.setLineSpacing(0f, 1.08f);
        bodyView.setPadding(0, dp(activity, 5), 0, 0);
        card.addView(bodyView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(activity, 8));
        content.addView(card, params);
    }

    private static void styleDialog(Activity activity, AlertDialog dialog) {
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiPalette.PRIMARY);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.rgb(183, 183, 193));
        }

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(panelBackground(activity));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.72f;
        window.setAttributes(params);
    }

    private static TextView text(
            Activity activity,
            String value,
            float size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static GradientDrawable panelBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(15, 15, 18));
        background.setCornerRadius(dp(activity, 26));
        background.setStroke(dp(activity, 1), Color.rgb(55, 55, 61));
        return background;
    }

    private static GradientDrawable cardBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(24, 24, 28));
        background.setCornerRadius(dp(activity, 16));
        background.setStroke(dp(activity, 1), Color.rgb(45, 45, 52));
        return background;
    }

    private static GradientDrawable badgeBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.PRIMARY);
        background.setCornerRadius(dp(activity, 17));
        return background;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
