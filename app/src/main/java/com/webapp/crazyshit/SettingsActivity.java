package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends Activity {
    public static final String EXTRA_CHECK_FOR_UPDATES = "check_for_updates";

    private SharedPreferences prefs;
    private AppUpdater appUpdater;
    private AppBackupController backup;
    private TextView notificationStatusView;
    private TextView sourceConfigStatusView;
    private boolean notificationReceiverRegistered;

    private final BroadcastReceiver notificationCheckReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (notificationStatusView != null) {
                notificationStatusView.setText(
                        NotificationCoordinator.statusSummary(SettingsActivity.this)
                );
            }
            if (intent != null
                    && intent.getBooleanExtra(NotificationCoordinator.EXTRA_MANUAL_CHECK, false)) {
                Toast.makeText(
                        SettingsActivity.this,
                        "Creator update check finished.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        appUpdater = new AppUpdater(this);
        backup = new AppBackupController(this);
        ZeroChillUi.applySystemBars(this);
        buildUi();

        if (getIntent().getBooleanExtra(EXTRA_CHECK_FOR_UPDATES, false)) {
            getIntent().removeExtra(EXTRA_CHECK_FOR_UPDATES);
            getWindow().getDecorView().postDelayed(() -> {
                if (appUpdater != null) appUpdater.check(true);
            }, 250L);
        }
    }

    private void buildUi() {
        int background = ZeroChillUi.background(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), 0, dp(16), dp(36));
        root.setBackgroundColor(background);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(2), 0, dp(8));

        TextView back = text("‹", 34f, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> {
            haptic(v);
            finish();
        });
        ZeroChillMotion.installPressFeedback(back);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(52)));

        TextView title = text("Settings", 28f, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(8);
        header.addView(title, titleParams);
        root.addView(header);

        LinearLayout notifications = addGroup(root, "Notifications & updates");
        addSwitch(
                notifications,
                "Favorite creator updates",
                "Notify when your favorite creators have new content.",
                NotificationCoordinator.PREF_NEW_VIDEO_ALERTS,
                true
        );
        addAction(
                notifications,
                "Check frequency",
                "",
                NotificationCoordinator.frequencySummary(this),
                this::showNotificationFrequencyChoices
        );
        notificationStatusView = addAction(
                notifications,
                "Check creator updates",
                NotificationCoordinator.statusSummary(this),
                "",
                this::checkNotificationsNow
        );
        addSwitch(
                notifications,
                "App update alerts",
                "Notify when a new ZeroChill build is ready.",
                NotificationCoordinator.PREF_UPDATE_ALERTS,
                true
        );
        addAction(
                notifications,
                "Check for app update",
                "Installed version " + BuildConfig.VERSION_NAME,
                "",
                () -> {
                    if (appUpdater != null) appUpdater.check(true);
                }
        );

        LinearLayout playback = addGroup(root, "Playback");
        addSwitch(
                playback,
                "Native video player",
                "Play supported videos directly in ZeroChill.",
                "native_player_enabled",
                true
        );
        addSwitch(
                playback,
                "Picture-in-Picture",
                "",
                "player_auto_pip",
                true
        );
        addSwitch(
                playback,
                "Remember playback",
                "Resume unfinished videos where you left off.",
                "remember_video_position",
                true
        );
        addAction(
                playback,
                "ShitTok preloading",
                "",
                ChaosPreloadPolicy.summary(this),
                this::showChaosPreloadChoices
        );

        LinearLayout appearance = addGroup(root, "Appearance");
        addSwitch(
                appearance,
                "OLED black",
                "",
                "oled_black_enabled",
                true
        );
        addSwitch(
                appearance,
                "Cyan edge glow",
                "",
                "ambient_feed_glow",
                true
        );
        addSwitch(
                appearance,
                "Motion effects",
                "",
                "immersive_motion_enabled",
                true
        );
        addSwitch(
                appearance,
                "Haptic feedback",
                "",
                "haptics_enabled",
                true
        );

        LinearLayout library = addGroup(root, "Library & data");
        addAction(
                library,
                "Favorite creators",
                "Search and open your starred creators.",
                "",
                () -> startActivity(new Intent(this, CreatorsActivity.class))
        );
        addAction(
                library,
                "Library",
                "Continue Watching, History and Watch Later.",
                "",
                () -> startActivity(new Intent(this, FavoritesActivity.class))
        );
        addAction(
                library,
                "Backup & restore",
                "Save or restore favorites, Watch Later and settings.",
                "",
                this::showBackupRestore
        );
        addAction(
                library,
                "Manage history & Watch Later",
                "Clear local viewing or saved-item data.",
                "",
                this::showManageLibraryData
        );

        LinearLayout privacy = addGroup(root, "Privacy");
        addSwitch(
                privacy,
                "Block ads & pop-ups",
                "Block known ad hosts, popunders and redirect hijacks.",
                "ad_blocking_enabled",
                true
        );
        addAction(
                privacy,
                "Clear site data",
                "Sign out of websites and remove cookies and local storage.",
                "",
                this::confirmClearSiteData
        );

        LinearLayout advanced = addGroup(root, "Advanced");
        sourceConfigStatusView = addAction(
                advanced,
                "Source configuration",
                sourceConfigSummary(),
                "",
                this::showSourceConfigDetails
        );
        addAction(
                advanced,
                "Performance details",
                "Loading and scrolling timings from this session.",
                "",
                this::showPerformanceDetails
        );
        addAction(
                advanced,
                "Test notification",
                "Preview a ZeroChill update alert and check notification access.",
                "",
                () -> NotificationCoordinator.showTestNotification(this)
        );

        TextView footer = text(
                "ZeroChill  •  " + BuildConfig.VERSION_NAME
                        + "\nCommunity Android client",
                11.5f,
                ZeroChillUi.color(this, R.color.zc_text_muted)
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(28), dp(8), 0);
        root.addView(footer);

        applyWindowInsets(scroll);
        setContentView(scroll);
    }

    private void applyWindowInsets(ScrollView scroll) {
        if (scroll == null) return;
        scroll.setClipToPadding(true);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(scroll);
    }

    private LinearLayout addGroup(LinearLayout root, String title) {
        TextView label = text(
                title.toUpperCase(java.util.Locale.US),
                11f,
                ZeroChillUi.color(this, R.color.zc_text_muted)
        );
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setLetterSpacing(0.06f);
        label.setPadding(dp(6), dp(20), dp(6), dp(8));
        root.addView(label);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(
                ZeroChillUi.color(this, R.color.zc_surface_glass)
        );
        card.setRadius(dp(18));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(
                ZeroChillUi.color(this, R.color.zc_divider)
        );
        card.setRippleColor(ColorStateList.valueOf(
                ZeroChillUi.color(this, R.color.zc_cyan_container)
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new MaterialCardView.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(2);
        root.addView(card, params);
        return content;
    }

    private void addSwitch(
            LinearLayout group,
            String title,
            String subtitle,
            String key,
            boolean defaultValue
    ) {
        addDividerIfNeeded(group);

        LinearLayout row = row();
        LinearLayout copy = copy(title, subtitle);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setContentDescription(title);
        toggle.setMinWidth(dp(52));
        toggle.setMinimumWidth(dp(52));
        toggle.setMinHeight(dp(48));
        toggle.setMinimumHeight(dp(48));

        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            haptic(button);

            if ("oled_black_enabled".equals(key)) {
                button.postDelayed(this::recreate, 90L);
            }

            if (NotificationCoordinator.isNotificationPreference(key)) {
                NotificationCoordinator.onPreferencesChanged(this);
                if (checked
                        && (NotificationCoordinator.PREF_NEW_VIDEO_ALERTS.equals(key)
                        || NotificationCoordinator.PREF_UPDATE_ALERTS.equals(key))) {
                    NotificationCoordinator.requestPermissionFromSettings(this);
                }
            }
        });

        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        row.addView(toggle);
        group.addView(row);
    }

    private TextView addAction(
            LinearLayout group,
            String title,
            String subtitle,
            String trailing,
            Runnable action
    ) {
        addDividerIfNeeded(group);

        LinearLayout row = row();
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });
        ZeroChillMotion.installPressFeedback(row);

        LinearLayout copy = copy(title, subtitle);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        if (trailing != null && !trailing.trim().isEmpty()) {
            TextView value = text(
                    trailing.trim(),
                    13f,
                    ZeroChillUi.color(this, R.color.zc_cyan)
            );
            value.setSingleLine(true);
            value.setEllipsize(TextUtils.TruncateAt.END);
            value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            value.setMaxWidth(dp(138));
            LinearLayout.LayoutParams valueParams =
                    new LinearLayout.LayoutParams(-2, dp(42));
            valueParams.leftMargin = dp(10);
            row.addView(value, valueParams);
        }

        TextView chevron = text(
                "›",
                27f,
                ZeroChillUi.color(this, R.color.zc_text_muted)
        );
        chevron.setGravity(Gravity.CENTER);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(26), dp(44)));

        group.addView(row);

        if (copy.getChildCount() > 1) {
            return (TextView) copy.getChildAt(1);
        }
        return null;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(10), dp(10));
        row.setMinimumHeight(dp(62));
        return row;
    }

    private LinearLayout copy(String title, String subtitle) {
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = text(
                title,
                15.5f,
                ZeroChillUi.color(this, R.color.zc_text_primary)
        );
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = text(
                    subtitle.trim(),
                    12f,
                    ZeroChillUi.color(this, R.color.zc_text_secondary)
            );
            sub.setLineSpacing(0f, 1.06f);
            sub.setPadding(0, dp(2), dp(6), 0);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        }
        return copy;
    }

    private void addDividerIfNeeded(LinearLayout group) {
        if (group.getChildCount() == 0) return;
        View divider = new View(this);
        divider.setBackgroundColor(
                ZeroChillUi.color(this, R.color.zc_divider)
        );
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(1));
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        group.addView(divider, params);
    }

    private void checkNotificationsNow() {
        if (notificationStatusView != null) {
            notificationStatusView.setText("Checking favorite creators…");
        }
        NotificationCoordinator.checkNow(this);
        Toast.makeText(
                this,
                "Checking supported creator sources.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void showBackupRestore() {
        String[] actions = {"Export backup", "Restore backup"};
        new AlertDialog.Builder(this)
                .setTitle("Backup & restore")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) backup.exportFile();
                    else backup.importFile();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManageLibraryData() {
        String[] actions = {"Clear watch history", "Clear Watch Later"};
        new AlertDialog.Builder(this)
                .setTitle("Manage history & Watch Later")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        confirmDestructive(
                                "Clear watch history?",
                                "This removes watched and Continue Watching state from this device.",
                                () -> {
                                    PlaybackHistoryStore.clear(this);
                                    Toast.makeText(
                                            this,
                                            "Watch history cleared.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        );
                    } else {
                        confirmDestructive(
                                "Clear Watch Later?",
                                "This removes every item saved to Watch Later on this device.",
                                () -> {
                                    FavoriteStore.clear(this);
                                    Toast.makeText(
                                            this,
                                            "Watch Later cleared.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        );
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearSiteData() {
        confirmDestructive(
                "Clear site data?",
                "This signs you out of websites and removes their cookies and local storage.",
                () -> {
                    CookieManager.getInstance().removeAllCookies(
                            value -> CookieManager.getInstance().flush()
                    );
                    WebStorage.getInstance().deleteAllData();
                    Toast.makeText(this, "Site data cleared.", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void confirmDestructive(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> action.run())
                .show();
    }

    private void showPerformanceDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Performance details")
                .setMessage(AppPerformance.summary())
                .setPositiveButton("Close", null)
                .show();
    }

    private String sourceConfigSummary() {
        RemoteSourceConfigManager.initialize(this);
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        if (config == null) {
            return RemoteSourceConfigManager.statusSummary(this);
        }
        return "v" + config.configVersion
                + " • "
                + RemoteSourceConfigManager.statusSummary(this);
    }

    private String sourceConfigDetails() {
        RemoteSourceConfigManager.initialize(this);
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        if (config == null) {
            return "No active source configuration.\n\nStatus: "
                    + RemoteSourceConfigManager.statusSummary(this);
        }

        return "Active config: v"
                + config.configVersion
                + " • "
                + RemoteSourceConfigManager.activeOrigin()
                + "\nKill switches: "
                + onOff(config.sourceKillSwitchesEnabled)
                + "\nFallbacks: "
                + onOff(config.fallbacksEnabled)
                + "\n\nFapello: "
                + onOff(config.fapello.enabled)
                + "\nBunkr: "
                + onOff(config.bunkr.enabled)
                + "\nWikiFeet: "
                + onOff(config.wikiFeet.enabled)
                + "\nWikiFeet X: "
                + onOff(config.wikiFeetX.enabled)
                + "\n\nStatus: "
                + RemoteSourceConfigManager.statusSummary(this);
    }

    private String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private void showSourceConfigDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Source configuration")
                .setMessage(sourceConfigDetails())
                .setNeutralButton(
                        "Refresh now",
                        (dialog, which) -> checkSourceConfigNow()
                )
                .setPositiveButton("Close", null)
                .show();
    }

    private void checkSourceConfigNow() {
        if (sourceConfigStatusView != null) {
            sourceConfigStatusView.setText("Refreshing source configuration…");
        }
        RemoteSourceConfigManager.refreshNow(this, success -> {
            if (sourceConfigStatusView != null) {
                sourceConfigStatusView.setText(sourceConfigSummary());
            }
            Toast.makeText(
                    this,
                    success
                            ? "Source configuration refreshed."
                            : "Source configuration refresh failed.",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void showChaosPreloadChoices() {
        String[] choices = {
                "Full",
                "Wi-Fi / unmetered only",
                "Minimal"
        };
        new AlertDialog.Builder(this)
                .setTitle("ShitTok preloading")
                .setSingleChoiceItems(
                        choices,
                        ChaosPreloadPolicy.selectedIndex(this),
                        (dialog, which) -> {
                            ChaosPreloadPolicy.setMode(
                                    this,
                                    ChaosPreloadPolicy.modeForIndex(which)
                            );
                            dialog.dismiss();
                            recreate();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNotificationFrequencyChoices() {
        int selectedHours = prefs.getInt(
                NotificationCoordinator.PREF_FREQUENCY_HOURS,
                1
        );
        int selected = selectedHours >= 6 ? 2 : selectedHours >= 3 ? 1 : 0;
        String[] choices = {
                "Every hour",
                "Every 3 hours",
                "Every 6 hours"
        };

        new AlertDialog.Builder(this)
                .setTitle("Creator update frequency")
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    int hours = which == 2 ? 6 : which == 1 ? 3 : 1;
                    NotificationCoordinator.setFrequency(this, hours);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView text(String value, float size, int color) {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (backup != null) {
            backup.onResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        OledThemeController.applySoon(this);
        if (appUpdater != null) {
            appUpdater.onHostResume();
        }
        if (notificationStatusView != null) {
            notificationStatusView.setText(
                    NotificationCoordinator.statusSummary(this)
            );
        }
        if (sourceConfigStatusView != null) {
            sourceConfigStatusView.setText(sourceConfigSummary());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!notificationReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    notificationCheckReceiver,
                    new IntentFilter(NotificationCoordinator.ACTION_CHECK_FINISHED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            notificationReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        if (notificationReceiverRegistered) {
            unregisterReceiver(notificationCheckReceiver);
            notificationReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (appUpdater != null) {
            appUpdater.close();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
