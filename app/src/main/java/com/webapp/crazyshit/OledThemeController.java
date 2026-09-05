package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

/** Applies the 2.7 true-black OLED surface treatment across native screens. */
final class OledThemeController {
    private static final int OLED_BLACK = Color.BLACK;
    private static final int OLED_CARD = Color.rgb(9, 9, 11);
    private static final int OLED_STROKE = Color.rgb(29, 29, 33);

    private OledThemeController() {
    }

    static void applySoon(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> apply(activity));
        decor.postDelayed(() -> apply(activity), 140L);
        decor.postDelayed(() -> apply(activity), 520L);
    }

    private static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        boolean enabled = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("oled_black_enabled", true);
        if (!enabled) return;

        Window window = activity.getWindow();
        if (window != null) {
            window.setStatusBarColor(OLED_BLACK);
            window.setNavigationBarColor(OLED_BLACK);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
                window.setStatusBarContrastEnforced(false);
            }
        }

        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(OLED_BLACK);
            normalize(content);
        }
    }

    private static void normalize(View view) {
        if (view == null) return;

        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            if (!NativeFeedAdapter.STYLE_TAG.equals(card.getTag())) {
                card.setCardBackgroundColor(OLED_CARD);
                if (card.getStrokeWidth() > 0) card.setStrokeColor(OLED_STROKE);
            }
        } else if (!(view instanceof TextView) && !(view instanceof ImageView)) {
            Drawable background = view.getBackground();
            if (background instanceof ColorDrawable) {
                int color = ((ColorDrawable) background).getColor();
                if (Color.alpha(color) >= 160 && isDarkNeutral(color)) {
                    view.setBackgroundColor(OLED_BLACK);
                }
            }
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            normalize(group.getChildAt(i));
        }
    }

    private static boolean isDarkNeutral(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max <= 36 && (max - min) <= 12;
    }
}

