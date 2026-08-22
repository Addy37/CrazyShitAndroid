package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Local library for Continue Watching, History and Watch Later.
 * The class name stays FavoritesActivity so v2's existing Saved nav can open it without a routing change.
 */
public class FavoritesActivity extends Activity {
    public static final String EXTRA_SELECTED_URL = "selected_url";

    private static final int TAB_CONTINUE = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_WATCH_LATER = 2;

    private LinearLayout listContainer;
    private MaterialButton continueTab;
    private MaterialButton historyTab;
    private MaterialButton watchLaterTab;
    private TextView clearAction;
    private int tab = TAB_CONTINUE;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderItems();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 13, 15));
        root.setPadding(dp(18), dp(18), dp(18), dp(24));

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

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("Library", 28, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitle = text("Continue Watching • History • Watch Later", 12, Color.rgb(170, 170, 180));
        heading.addView(title);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        clearAction = text("CLEAR", 12, Color.rgb(255, 112, 60));
        clearAction.setTypeface(null, android.graphics.Typeface.BOLD);
        clearAction.setGravity(Gravity.CENTER);
        clearAction.setPadding(dp(10), dp(10), dp(10), dp(10));
        clearAction.setClickable(true);
        clearAction.setOnClickListener(v -> confirmClear());
        header.addView(clearAction, new LinearLayout.LayoutParams(dp(62), dp(52)));
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(12), 0, dp(8));

        continueTab = tabButton("Continue", TAB_CONTINUE);
        historyTab = tabButton("History", TAB_HISTORY);
        watchLaterTab = tabButton("Watch Later", TAB_WATCH_LATER);
        tabs.addView(continueTab, tabParams());
        tabs.addView(historyTab, tabParams());
        tabs.addView(watchLaterTab, tabParams());
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(6), 0, dp(24));
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        updateTabs();
    }

    private MaterialButton tabButton(String label, int target) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(v -> {
            if (tab == target) return;
            haptic(v);
            tab = target;
            updateTabs();
            renderItems();
        });
        return button;
    }

    private LinearLayout.LayoutParams tabParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private void updateTabs() {
        continueTab.setEnabled(tab != TAB_CONTINUE);
        historyTab.setEnabled(tab != TAB_HISTORY);
        watchLaterTab.setEnabled(tab != TAB_WATCH_LATER);
        clearAction.setText(tab == TAB_WATCH_LATER ? "CLEAR" : "CLEAR");
    }

    private void renderItems() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        if (tab == TAB_WATCH_LATER) {
            renderWatchLater();
        } else if (tab == TAB_HISTORY) {
            renderHistory(false);
        } else {
            renderHistory(true);
        }
    }

    private void renderHistory(boolean continueOnly) {
        List<PlaybackHistoryStore.Item> items = continueOnly
                ? PlaybackHistoryStore.continueWatching(this)
                : PlaybackHistoryStore.load(this);

        if (items.isEmpty()) {
            showEmpty(
                    continueOnly ? "Nothing to continue" : "No watch history yet",
                    continueOnly
                            ? "Videos watched for at least 30 seconds appear here until they're nearly finished."
                            : "Videos you watch in the native player will appear here."
            );
            return;
        }

        for (PlaybackHistoryStore.Item item : items) {
            listContainer.addView(makeHistoryCard(item, continueOnly), cardParams());
        }
    }

    private MaterialCardView makeHistoryCard(PlaybackHistoryStore.Item item, boolean continueOnly) {
        MaterialCardView card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> select(item.pageUrl));

        TextView title = text(item.title, 16, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        row.addView(title);

        int percent = item.progressPercent();
        String progressText;
        if (item.complete) {
            progressText = "Finished";
        } else if (item.durationMs > 0L) {
            progressText = formatTime(item.positionMs) + " / " + formatTime(item.durationMs) + "  •  " + percent + "%";
        } else {
            progressText = formatTime(item.positionMs) + " watched";
        }
        TextView progressLabel = text(progressText, 12, Color.rgb(190, 190, 198));
        progressLabel.setPadding(0, dp(7), 0, 0);
        row.addView(progressLabel);

        if (item.durationMs > 0L && !item.complete) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(percent);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(5));
            barParams.setMargins(0, dp(8), 0, dp(3));
            row.addView(bar, barParams);
        }

        String watched = item.lastWatched > 0L
                ? "Watched " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(item.lastWatched))
                : "Watched";
        TextView date = text(watched, 11, Color.rgb(135, 135, 145));
        date.setPadding(0, dp(7), 0, 0);
        row.addView(date);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        MaterialButton remove = new MaterialButton(this);
        remove.setText(continueOnly ? "Remove" : "Delete");
        remove.setOnClickListener(v -> {
            haptic(v);
            PlaybackHistoryStore.remove(this, item.pageUrl);
            renderItems();
        });
        actions.addView(remove, new LinearLayout.LayoutParams(-2, -2));
        row.addView(actions);

        card.addView(row);
        return card;
    }

    private void renderWatchLater() {
        List<FavoriteStore.Item> items = FavoriteStore.load(this);
        if (items.isEmpty()) {
            showEmpty("Nothing saved yet", "Long-press a video card and choose Save to Watch Later.");
            return;
        }
        for (FavoriteStore.Item item : items) {
            listContainer.addView(makeWatchLaterCard(item), cardParams());
        }
    }

    private MaterialCardView makeWatchLaterCard(FavoriteStore.Item item) {
        MaterialCardView card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> select(item.url));

        TextView title = text(item.title, 16, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        row.addView(title);

        String date = item.savedAt > 0L
                ? "Saved " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(item.savedAt))
                : "Saved";
        TextView saved = text(date, 12, Color.rgb(145, 145, 155));
        saved.setPadding(0, dp(7), 0, 0);
        row.addView(saved);

        MaterialButton remove = new MaterialButton(this);
        remove.setText("Remove");
        remove.setOnClickListener(v -> {
            haptic(v);
            FavoriteStore.remove(this, item.url);
            Toast.makeText(this, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
            renderItems();
        });
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-2, -2);
        removeParams.gravity = Gravity.END;
        removeParams.setMargins(0, dp(8), 0, 0);
        row.addView(remove, removeParams);

        card.addView(row);
        return card;
    }

    private void showEmpty(String titleValue, String bodyValue) {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(72), dp(24), dp(24));
        TextView title = text(titleValue, 20, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView body = text(bodyValue, 14, Color.rgb(170, 170, 180));
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(8), 0, 0);
        empty.addView(title);
        empty.addView(body);
        listContainer.addView(empty);
    }

    private void select(String url) {
        haptic(listContainer);
        Intent data = new Intent();
        data.putExtra(EXTRA_SELECTED_URL, url);
        setResult(RESULT_OK, data);
        finish();
    }

    private void confirmClear() {
        boolean watchLater = tab == TAB_WATCH_LATER;
        new AlertDialog.Builder(this)
                .setTitle(watchLater ? "Clear Watch Later?" : "Clear watch history?")
                .setMessage(watchLater
                        ? "This removes every saved Watch Later item from this device."
                        : "This clears History and Continue Watching from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    if (watchLater) FavoriteStore.clear(this);
                    else PlaybackHistoryStore.clear(this);
                    renderItems();
                })
                .show();
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(24, 24, 28));
        card.setRadius(dp(20));
        card.setStrokeWidth(1);
        card.setStrokeColor(Color.rgb(45, 45, 52));
        card.setCardElevation(0f);
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

    private String formatTime(long milliseconds) {
        long total = Math.max(0L, milliseconds) / 1000L;
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0L) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void haptic(View view) {
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
