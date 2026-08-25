package com.webapp.crazyshit;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Final geometry pass for the floating main bottom navigation.
 *
 * Visual layers may style the bar, but this class owns its final height, margins and item spacing
 * after those style passes have completed.
 */
final class MainBottomNavigationFitment {
    private static final int NAV_CHAOS = 4;

    private MainBottomNavigationFitment() {
    }

    static void applySoon(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> apply(activity));
        decor.postDelayed(() -> apply(activity), 180L);
        // OLED styling currently has a late visual pass. Re-assert geometry afterward so styling
        // cannot make the bar tall again.
        decor.postDelayed(() -> apply(activity), 560L);
    }

    private static void apply(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        BottomNavigationView nav = findFirst(
                activity.findViewById(android.R.id.content),
                BottomNavigationView.class
        );
        if (nav == null || nav.getLayoutParams() == null) return;

        boolean landscape = activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;

        ViewGroup.LayoutParams raw = nav.getLayoutParams();
        raw.height = dp(activity, landscape ? 54 : 56);
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) raw;
            int side = dp(activity, landscape ? 7 : 8);
            margins.setMargins(
                    side,
                    dp(activity, 1),
                    side,
                    dp(activity, landscape ? 2 : 3)
            );
        }
        nav.setMinimumHeight(0);
        nav.setLayoutParams(raw);

        try {
            nav.setItemPaddingTop(dp(activity, 3));
            nav.setItemPaddingBottom(dp(activity, 2));
        } catch (Throwable ignored) {
        }

        View chaosItem = nav.findViewById(NAV_CHAOS);
        if (chaosItem != null) {
            chaosItem.setScaleX(1.08f);
            chaosItem.setScaleY(1.08f);
            chaosItem.setTranslationY(-dp(activity, 1));
            chaosItem.setElevation(dp(activity, 4));
        }
    }

    private static <T> T findFirst(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T found = findFirst(group.getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
