package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class FavoritesActivity extends Activity {
    public static final String EXTRA_SELECTED_URL = "selected_url";

    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        back.setMinWidth(dp(48));
        back.setMinimumWidth(dp(48));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("Watch Later", 28, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitle = text("Saved only on this device", 13, Color.rgb(170, 170, 180));
        heading.addView(title);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(12), 0, dp(24));
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private void renderItems() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        List<FavoriteStore.Item> items = FavoriteStore.load(this);

        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(72), dp(24), dp(24));
            TextView icon = text("♡", 44, Color.rgb(205, 205, 214));
            icon.setGravity(Gravity.CENTER);
            TextView title = text("Nothing saved yet", 20, Color.WHITE);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setPadding(0, dp(10), 0, 0);
            TextView body = text("Long-press a video link and choose Save for later.", 14, Color.rgb(170, 170, 180));
            body.setGravity(Gravity.CENTER);
            body.setPadding(0, dp(6), 0, 0);
            empty.addView(icon);
            empty.addView(title);
            empty.addView(body);
            listContainer.addView(empty);
            return;
        }

        for (FavoriteStore.Item item : items) {
            listContainer.addView(makeCard(item), cardParams());
        }
    }

    private MaterialCardView makeCard(FavoriteStore.Item item) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(24, 24, 28));
        card.setRadius(dp(20));
        card.setStrokeWidth(1);
        card.setStrokeColor(Color.rgb(45, 45, 52));
        card.setCardElevation(0f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            haptic(v);
            Intent data = new Intent();
            data.putExtra(EXTRA_SELECTED_URL, item.url);
            setResult(RESULT_OK, data);
            finish();
        });

        TextView title = text(item.title, 16, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        row.addView(title);

        TextView url = text(item.url, 12, Color.rgb(160, 160, 170));
        url.setMaxLines(1);
        url.setPadding(0, dp(5), 0, 0);
        row.addView(url);

        String date = item.savedAt > 0
                ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(item.savedAt))
                : "Saved";
        TextView saved = text(date, 12, Color.rgb(130, 130, 140));
        saved.setPadding(0, dp(8), 0, 0);
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
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
