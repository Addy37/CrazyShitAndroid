package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
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

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
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
                notificationStatusView.setText(NotificationCoordinator.statusSummary(SettingsActivity.this));
            }
            if (intent != null && intent.getBooleanExtra(NotificationCoordinator.EXTRA_MANUAL_CHECK, false)) {
                Toast.makeText(SettingsActivity.this, "Site check finished.", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        appUpdater = new AppUpdater(this);
        backup = new AppBackupController(this);
        buildUi();
        if (getIntent().getBooleanExtra(EXTRA_CHECK_FOR_UPDATES, false)) {
            getIntent().removeExtra(EXTRA_CHECK_FOR_UPDATES);
            getWindow().getDecorView().postDelayed(() -> {
                if (appUpdater != null) appUpdater.check(true);
            }, 250L);
        }
    }

    private void buildUi() {
        boolean oled = prefs.getBoolean("oled_black_enabled", true);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(oled ? Color.BLACK : Color.rgb(13, 13, 15));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        root.setBackgroundColor(oled ? Color.BLACK : Color.rgb(13, 13, 15));
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

        addSection(root, "Notifications");
        addSwitch(root,
                "New video alerts",
                "Notify you when followed sites add fresh uploads.",
                NotificationCoordinator.PREF_NEW_VIDEO_ALERTS,
                true);
        addSwitch(root,
                "CrazyShit alerts",
                "Include new uploads from CrazyShit.",
                NotificationCoordinator.PREF_CRAZYSHIT_ALERTS,
                true);
        addSwitch(root,
                "EFukt alerts",
                "Include new uploads from EFukt when the site is available in your region.",
                NotificationCoordinator.PREF_EFUKT_ALERTS,
                true);
        addSwitch(root,
                "Show video titles",
                "List titles inside expanded alerts. Leave this off for discreet notifications.",
                NotificationCoordinator.PREF_SHOW_TITLES,
                false);
        addAction(root,
                "Check frequency",
                NotificationCoordinator.frequencySummary(this),
                this::showNotificationFrequencyChoices);
        notificationStatusView = addAction(root,
                "Check now",
                NotificationCoordinator.statusSummary(this),
                this::checkNotificationsNow);
        addSwitch(root,
                "App update alerts",
                "Notify you when a new build is ready. Nothing downloads until you tap it.",
                NotificationCoordinator.PREF_UPDATE_ALERTS,
                true);
        addAction(root,
                "Preview notification",
                "Send a branded test alert and check Android notification access.",
                () -> NotificationCoordinator.showTestNotification(this));

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
                "Resume unfinished videos close to where you stopped.",
                "remember_video_position",
                true);
        addAction(root,
                "Chaos preloading",
                ChaosPreloadPolicy.summary(this),
                this::showChaosPreloadChoices);

        addSection(root, "Appearance");
        addSwitch(root,
                "OLED black",
                "Use true black backgrounds and near-black cards throughout the native app.",
                "oled_black_enabled",
                true);
        addSwitch(root,
                "Ambient feed glow",
                "Add a very faint artwork glow without tinting the whole screen.",
                "ambient_feed_glow",
                true);
        addSwitch(root,
                "Motion effects",
                "Use light focus and thumbnail movement while scrolling.",
                "immersive_motion_enabled",
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
        addAction(root, "Favorite creators", "Search and open your starred creators.", () ->
                startActivity(new Intent(this, CreatorsActivity.class)));
        addAction(root, "Library", "Continue Watching, History and Watch Later.", () ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        addAction(root, "Clear watch history", "Remove watched and Continue Watching state from this device.", () -> {
            PlaybackHistoryStore.clear(this);
            Toast.makeText(this, "Watch history cleared.", Toast.LENGTH_SHORT).show();
        });
        addAction(root, "Clear Watch Later", "Remove all locally saved Watch Later items.", () -> {
            FavoriteStore.clear(this);
            Toast.makeText(this, "Watch Later cleared.", Toast.LENGTH_SHORT).show();
        });

        addSection(root, "Backup & restore");
        addAction(root, "Export backup", "Save creator favorites, Watch Later and your settings.", backup::exportFile);
        addAction(root, "Restore backup", "Add saved items from a backup and restore its settings.", backup::importFile);

        addSection(root, "App");
        addAction(root, "Performance details", "View loading and scrolling timings from this session.", () ->
                new AlertDialog.Builder(this).setTitle("Performance details").setMessage(AppPerformance.summary())
                        .setPositiveButton("Close", null).show());
        sourceConfigStatusView = addAction(root,
                "Source configuration",
                sourceConfigSummary(),
                this::showSourceConfigDetails);
        addAction(root,
                "Check source config now",
                "Force an immediate remote source configuration refresh.",
                this::checkSourceConfigNow);
        addAction(root, "App updates", "Version " + BuildConfig.VERSION_NAME + " · Check for updates.", () -> {
            if (appUpdater != null) appUpdater.check(true);
        });
        addAction(root, "Clear site data", "Sign out and remove website cookies and local storage.", () -> {
            CookieManager.getInstance().removeAllCookies(value -> CookieManager.getInstance().flush());
            WebStorage.getInstance().deleteAllData();
            Toast.makeText(this, "Site data cleared.", Toast.LENGTH_SHORT).show();
        });

        TextView footer = new TextView(this);
        footer.setText("ZeroFilter\nCommunity Android client\nNot affiliated with or endorsed by CrazyShit.com");
        footer.setTextColor(Color.rgb(145, 145, 153));
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(28), dp(8), 0);
        root.addView(footer);

        setContentView(scroll);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (backup != null) backup.onResult(requestCode, resultCode, data);
    }

    private void addSection(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text.toUpperCase());
        label.setTextColor(Color.rgb(170, 170, 180));
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
        TextView subView = text(subtitle, 13, Color.rgb(174, 174, 182));
        subView.setPadding(0, dp(3), 0, 0);
        copy.addView(titleView);
        copy.addView(subView);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setContentDescription(title);
        toggle.setMinWidth(dp(48));
        toggle.setMinimumWidth(dp(48));
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
                if (checked && (NotificationCoordinator.PREF_NEW_VIDEO_ALERTS.equals(key) ||
                        NotificationCoordinator.PREF_UPDATE_ALERTS.equals(key))) {
                    NotificationCoordinator.requestPermissionFromSettings(this);
                }
            }
        });
        row.addView(toggle);
        card.addView(row);
        root.addView(card, cardParams());
    }

    private TextView addAction(LinearLayout root, String title, String subtitle, Runnable action) {
        MaterialCardView card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title + ". " + subtitle);
        row.setMinimumHeight(dp(48));
        row.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 16, Color.WHITE));
        TextView sub = text(subtitle, 13, Color.rgb(174, 174, 182));
        sub.setPadding(0, dp(3), 0, 0);
        copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chevron = text("›", 28, Color.rgb(184, 184, 192));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron);
        card.addView(row);
        root.addView(card, cardParams());
        return sub;
    }

    private void checkNotificationsNow() {
        if (notificationStatusView != null) {
            notificationStatusView.setText("Checking CrazyShit and EFukt now…");
        }
        NotificationCoordinator.checkNow(this);
        Toast.makeText(this, "Checking both sites in the background.", Toast.LENGTH_SHORT).show();
    }

    private String sourceConfigSummary() {
        RemoteSourceConfigManager.initialize(this);
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        if (config == null) return RemoteSourceConfigManager.statusSummary(this);
        return RemoteSourceConfigManager.statusSummary(this)
                + " · Kill switches " + onOff(config.sourceKillSwitchesEnabled)
                + "\nFapello " + onOff(config.fapello.enabled)
                + " · Bunkr " + onOff(config.bunkr.enabled)
                + " · WikiFeet " + onOff(config.wikiFeet.enabled)
                + " · WikiFeet X " + onOff(config.wikiFeetX.enabled);
    }

    private String sourceConfigDetails() {
        RemoteSourceConfigManager.initialize(this);
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        if (config == null) {
            return "No active source configuration.\n\nStatus: "
                    + RemoteSourceConfigManager.statusSummary(this);
        }
        return "Active config: v" + config.configVersion + " · " + RemoteSourceConfigManager.activeOrigin()
                + "\nKill switches: " + onOff(config.sourceKillSwitchesEnabled)
                + "\nFallbacks: " + onOff(config.fallbacksEnabled)
                + "\n\nFapello: " + onOff(config.fapello.enabled)
                + "\nBunkr: " + onOff(config.bunkr.enabled)
                + "\nWikiFeet: " + onOff(config.wikiFeet.enabled)
                + "\nWikiFeet X: " + onOff(config.wikiFeetX.enabled)
                + "\n\nStatus: " + RemoteSourceConfigManager.statusSummary(this);
    }

    private String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private void showSourceConfigDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Source configuration")
                .setMessage(sourceConfigDetails())
                .setPositiveButton("Close", null)
                .show();
    }

    private void checkSourceConfigNow() {
        if (sourceConfigStatusView != null) {
            sourceConfigStatusView.setText("Refreshing remote source configuration…");
        }
        RemoteSourceConfigManager.refreshNow(this, success -> {
            if (sourceConfigStatusView != null) {
                sourceConfigStatusView.setText(sourceConfigSummary());
            }
            Toast.makeText(
                    this,
                    success ? "Source configuration refreshed." : "Source configuration refresh failed.",
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
                .setTitle("Chaos preloading")
                .setSingleChoiceItems(choices, ChaosPreloadPolicy.selectedIndex(this), (dialog, which) -> {
                    ChaosPreloadPolicy.setMode(this, ChaosPreloadPolicy.modeForIndex(which));
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNotificationFrequencyChoices() {
        int selectedHours = prefs.getInt(NotificationCoordinator.PREF_FREQUENCY_HOURS, 1);
        int selected = selectedHours >= 6 ? 2 : selectedHours >= 3 ? 1 : 0;
        String[] choices = {"Every hour", "Every 3 hours", "Every 6 hours"};
        new AlertDialog.Builder(this)
                .setTitle("Notification check frequency")
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    int hours = which == 2 ? 6 : which == 1 ? 3 : 1;
                    NotificationCoordinator.setFrequency(this, hours);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private MaterialCardView card() {
        boolean oled = prefs.getBoolean("oled_black_enabled", true);
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(oled ? Color.rgb(9, 9, 11) : Color.rgb(24, 24, 28));
        card.setRadius(dp(20));
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(oled ? Color.rgb(29, 29, 33) : Color.rgb(45, 45, 52));
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

    @Override
    protected void onResume() {
        super.onResume();
        OledThemeController.applySoon(this);
        if (appUpdater != null) appUpdater.onHostResume();
        if (notificationStatusView != null) {
            notificationStatusView.setText(NotificationCoordinator.statusSummary(this));
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
        if (appUpdater != null) appUpdater.close();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
