package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class LibraryHubActivity extends Activity {
    private static final int SAVED_VIDEOS_REQUEST = 4101;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        setContentView(buildContent());
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ZeroChillUi.background(this));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(18), dp(8));

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_player_back);
        back.setColorFilter(ZeroChillUi.color(this, R.color.zc_text_primary));
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setContentDescription("Back");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(8), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("Library");
        ZeroChillUi.styleTitle(title);
        title.setTextSize(24);
        labels.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your saved viewing and downloads");
        ZeroChillUi.styleSecondary(subtitle);
        subtitle.setTextSize(12);
        labels.addView(subtitle);

        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(28));

        addAction(content, "Favorite creators", "Browse creators you saved", v ->
                startActivity(new Intent(this, CreatorsActivity.class)));
        addAction(content, "Continue Watching", "Resume where you left off", v ->
                openSavedVideos(FavoritesActivity.START_CONTINUE));
        addAction(content, "History", "See videos you watched", v ->
                openSavedVideos(FavoritesActivity.START_HISTORY));
        addAction(content, "Watch Later", "Open your saved queue", v ->
                openSavedVideos(FavoritesActivity.START_WATCH_LATER));
        addAction(content, "Downloads", "Watch videos available offline", v ->
                startActivity(new Intent(this, DownloadedActivity.class)));

        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void addAction(
            LinearLayout content,
            String title,
            String detail,
            View.OnClickListener listener
    ) {
        LinearLayout action = new LinearLayout(this);
        action.setOrientation(LinearLayout.VERTICAL);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(dp(18), dp(14), dp(18), dp(14));
        ZeroChillUi.styleCard(action);
        action.setClickable(true);
        action.setFocusable(true);
        action.setContentDescription(title);
        action.setOnClickListener(listener);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(ZeroChillUi.color(this, R.color.zc_text_primary));
        heading.setTextSize(17);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        action.addView(heading);

        TextView copy = new TextView(this);
        copy.setText(detail);
        ZeroChillUi.styleSecondary(copy);
        copy.setTextSize(13);
        action.addView(copy);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(76));
        params.setMargins(0, 0, 0, dp(10));
        content.addView(action, params);
    }

    private void openSavedVideos(int startTab) {
        Intent intent = new Intent(this, FavoritesActivity.class)
                .putExtra(FavoritesActivity.EXTRA_START_TAB, startTab);
        startActivityForResult(intent, SAVED_VIDEOS_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVED_VIDEOS_REQUEST || resultCode != RESULT_OK || data == null) return;
        String selected = data.getStringExtra(FavoritesActivity.EXTRA_SELECTED_URL);
        if (selected == null || selected.isEmpty()) return;
        setResult(RESULT_OK, data);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
