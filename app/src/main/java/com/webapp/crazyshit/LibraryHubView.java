package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class LibraryHubView extends ScrollView {
    private final Activity activity;

    LibraryHubView(Activity activity) {
        super(activity);
        this.activity = activity;
        setFillViewport(true);
        setBackgroundColor(ZeroChillUi.background(activity));
        setClipToPadding(false);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(30));

        TextView subtitle = new TextView(activity);
        subtitle.setText("Your saved viewing and downloads");
        ZeroChillUi.styleSecondary(subtitle);
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(0, 0, 0, dp(16));
        content.addView(subtitle, subtitleParams);

        addAction(content, "Favorite creators", "Browse creators you saved", v ->
                activity.startActivity(new Intent(activity, CreatorsActivity.class)));
        addAction(content, "Continue Watching", "Resume where you left off", v ->
                openSavedVideos(FavoritesActivity.START_CONTINUE));
        addAction(content, "History", "See videos you watched", v ->
                openSavedVideos(FavoritesActivity.START_HISTORY));
        addAction(content, "Watch Later", "Open your saved queue", v ->
                openSavedVideos(FavoritesActivity.START_WATCH_LATER));
        addAction(content, "Downloads", "Watch videos available offline", v ->
                activity.startActivity(new Intent(activity, DownloadedActivity.class)));

        addView(content, new ScrollView.LayoutParams(-1, -2));
    }

    private void addAction(
            LinearLayout content,
            String title,
            String detail,
            View.OnClickListener listener
    ) {
        LinearLayout action = new LinearLayout(activity);
        action.setOrientation(LinearLayout.VERTICAL);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(dp(18), dp(14), dp(18), dp(14));
        ZeroChillUi.styleCard(action);
        action.setClickable(true);
        action.setFocusable(true);
        action.setContentDescription(title);
        action.setOnClickListener(listener);
        ZeroChillMotion.installPressFeedback(action);

        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(ZeroChillUi.color(activity, R.color.zc_text_primary));
        heading.setTextSize(17);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        action.addView(heading);

        TextView copy = new TextView(activity);
        copy.setText(detail);
        ZeroChillUi.styleSecondary(copy);
        copy.setTextSize(13);
        action.addView(copy);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(76));
        params.setMargins(0, 0, 0, dp(10));
        content.addView(action, params);
    }

    private void openSavedVideos(int startTab) {
        Intent intent = new Intent(activity, FavoritesActivity.class)
                .putExtra(FavoritesActivity.EXTRA_START_TAB, startTab);
        activity.startActivityForResult(intent, NativeMainActivity.FAVORITES_REQUEST);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
