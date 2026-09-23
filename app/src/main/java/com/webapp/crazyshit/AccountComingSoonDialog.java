package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One temporary entry point for account prompts until authentication is ready. */
final class AccountComingSoonDialog {
    private AccountComingSoonDialog() {
    }

    static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 20));
        panel.setBackground(ZeroChillUi.sheetGlass(activity));

        TextView title = text(activity, "Account · Coming Soon", 20, Color.WHITE, true);
        panel.addView(title);
        TextView message = text(activity, "Accounts and sync are coming in a future update.",
                14, ZeroChillUi.color(activity, R.color.zc_text_secondary), false);
        message.setPadding(0, dp(activity, 12), 0, dp(activity, 20));
        panel.addView(message);

        TextView close = text(activity, "Got it", 15, UiPalette.PRIMARY, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(ZeroChillUi.glass(activity));
        close.setClickable(true);
        close.setFocusable(true);
        ZeroChillMotion.installPressFeedback(close);
        close.setOnClickListener(v -> dialog.dismiss());
        panel.addView(close, new LinearLayout.LayoutParams(-1, dp(activity, 48)));
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels
                    - dp(activity, 40), dp(activity, 360)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
