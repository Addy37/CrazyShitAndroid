package com.webapp.crazyshit;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared compact top chrome for every regular native video player. */
final class PlayerTopChrome {
    final LinearLayout root;
    final ImageView back;
    final ImageView menu;
    final TextView title;

    private PlayerTopChrome(
            LinearLayout root,
            ImageView back,
            ImageView menu,
            TextView title
    ) {
        this.root = root;
        this.back = back;
        this.menu = menu;
        this.title = title;
    }

    static PlayerTopChrome create(
            Activity activity,
            String value,
            View.OnClickListener backClick,
            View.OnClickListener menuClick
    ) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.TOP | Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 16));
        bar.setBackground(topGradient());
        bar.setClickable(false);
        bar.setFocusable(false);

        ImageView back = button(activity, R.drawable.ic_player_back, "Back");
        back.setOnClickListener(backClick);
        bar.addView(back, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));

        TextView title = new TextView(activity);
        title.setText(value == null || value.trim().isEmpty() ? "Video" : value);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(activity, 11), 0, dp(activity, 11), 0);
        title.setShadowLayer(dp(activity, 3), 0f, dp(activity, 1), Color.BLACK);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));

        ImageView menu = button(activity, R.drawable.ic_player_more, "Video menu");
        menu.setOnClickListener(menuClick);
        bar.addView(menu, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));

        return new PlayerTopChrome(bar, back, menu, title);
    }

    void setTitle(String value) {
        title.setText(value == null || value.trim().isEmpty() ? "Video" : value);
    }

    void setVisible(boolean visible, boolean animate) {
        root.animate().cancel();
        if (visible) {
            root.setVisibility(View.VISIBLE);
            if (animate) {
                root.setAlpha(0f);
                root.setTranslationY(-dp(root, 8));
                root.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(160L)
                        .start();
            } else {
                root.setAlpha(1f);
                root.setTranslationY(0f);
            }
        } else if (animate) {
            root.animate()
                    .alpha(0f)
                    .translationY(-dp(root, 6))
                    .setDuration(140L)
                    .withEndAction(() -> root.setVisibility(View.GONE))
                    .start();
        } else {
            root.setAlpha(0f);
            root.setTranslationY(0f);
            root.setVisibility(View.GONE);
        }
    }

    void setChromeAlpha(float alpha) {
        root.animate().cancel();
        root.setAlpha(Math.max(0f, Math.min(1f, alpha)));
    }

    private static ImageView button(Activity activity, int icon, String description) {
        ImageView button = new ImageView(activity);
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(activity, 11), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        button.setBackground(buttonBackground(activity));
        button.setContentDescription(description);
        button.setClickable(true);
        button.setFocusable(true);
        button.setElevation(dp(activity, 7));
        button.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.78f).setDuration(70L).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(110L).start();
            }
            return false;
        });
        return button;
    }

    private static GradientDrawable topGradient() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(235, 0, 0, 0),
                        Color.argb(150, 0, 0, 0),
                        Color.argb(0, 0, 0, 0)
                }
        );
    }

    private static GradientDrawable buttonBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(175, 18, 18, 21));
        background.setStroke(dp(activity, 1), Color.argb(64, 255, 255, 255));
        return background;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static float dp(View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
