package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 13, 15));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton back = new MaterialButton(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setContentDescription("Back");
        back.setMinWidth(dp(48));
        back.setMinimumWidth(dp(48));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMargins(dp(12), 0, 0, 0);
        header.addView(title, titleParams);
        root.addView(header);

        addSection(root, "Playback");
        addSwitch(root,
                "Open videos in native player",
                "Automatically hand compatible video pages to the dedicated player.",
                "native_player_enabled",
                true);
        addSwitch(root,
                "Picture-in-Picture",
                "Automatically enter PiP when leaving native video playback.",
                "player_auto_pip",
                true);
        addSwitch(root,
                "Remember playback position",
                "Resume videos close to where you stopped.",
                "remember_video_position",
                true);
        addSwitch(root,
                "Minimize player on Back",
                "Back returns to browsing with the video in the in-app mini-player.",
                "minimize_on_back",
                true);

        addSection(root, "Browsing & privacy");
        addSwitch(root,
                "Block ads & pop-ups",
                "Blocks known ad hosts, popunders and third-party redirect hijacks.",
                "ad_blocking_enabled",
                true);
        addSwitch(root,
                "Haptic feedback",
                "Use subtle vibration for player gestures and app controls.",
                "haptics_enabled",
                true);

        addSection(root, "Library");
        addAction(root, "Watch Later", "View locally saved video pages.", () ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        addAction(root, "Clear Watch Later", "Remove all locally saved items.", () -> {
            FavoriteStore.clear(this);
            Toast.makeText(this, "Watch Later cleared.", Toast.LENGTH_SHORT).show();
        });

        addSection(root, "App");
        addAction(root, "Check for updates", "Open the latest GitHub release.", () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Addy37/CrazyShitAndroid/releases/latest")));
            } catch (Exception ignored) {
            }
        });
        addAction(root, "Clear site data", "Sign out and remove website cookies and local storage.", () -> {
            CookieManager.getInstance().removeAllCookies(value -> CookieManager.getInstance().flush());
            WebStorage.getInstance().deleteAllData();
            Toast.makeText(this, "Site data cleared.", Toast.LENGTH_SHORT).show();
        });

        TextView footer = new TextView(this);
        footer.setText("CrazyShit Unofficial  •  Community Android wrapper\nNot affiliated with or endorsed by CrazyShit.com");
        footer.setTextColor(Color.rgb(150, 150, 158));
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(28), dp(8), 0);
        root.addView(footer);

        setContentView(scroll);
    }

    private void addSection(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text.toUpperCase());
        label.setTextColor(Color.rgb(175, 175, 185));
        label.setTextSize(12);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(dp(8), dp(24), dp(8), dp(8));
        root.addView(label);
    }

    private void addSwitch(
            LinearLayout root,
            String title,
            String subtitle,
            String key,
            boolean defaultValue
    ) {
        MaterialCardView card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(12), dp(14));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 16, Color.WHITE);
        TextView subView = text(subtitle, 13, Color.rgb(180, 180, 188));
        subView.setPadding(0, dp(3), 0, 0);
        copy.addView(titleView);
        copy.addView(subView);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            haptic(button);
        });
        row.addView(toggle);
        card.addView(row);
        root.addView(card, cardParams());
    }

    private void addAction(LinearLayout root, String title, String subtitle, Runnable action) {
        MaterialCardView card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 16, Color.WHITE));
        TextView sub = text(subtitle, 13, Color.rgb(180, 180, 188));
        sub.setPadding(0, dp(3), 0, 0);
        copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chevron = text("›", 28, Color.rgb(190, 190, 198));
        row.addView(chevron);
        card.addView(row);
        root.addView(card, cardParams());
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(24, 24, 28));
        card.setRadius(dp(20));
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(Color.rgb(45, 45, 52));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void haptic(View view) {
        if (!prefs.getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
