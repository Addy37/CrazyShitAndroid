package com.webapp.crazyshit;

import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

/** Replaces the old one-field search dialog with the v2.6 full native Search screen. */
final class GlobalSearchUiController {
    private GlobalSearchUiController() {
    }

    static void attachSoon(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> attach(activity));
        decor.postDelayed(() -> attach(activity), 180L);
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View search = findByDescription(activity.getWindow().getDecorView(), "Search");
        if (search == null) return;
        search.setOnClickListener(v -> {
            if (activity.getSharedPreferences("app_prefs", NativeMainActivity.MODE_PRIVATE)
                    .getBoolean("haptics_enabled", true)) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            activity.openContextualSearch();
        });
        search.setContentDescription("Global Search");
    }

    private static View findByDescription(View view, String target) {
        if (view == null) return null;
        CharSequence description = view.getContentDescription();
        if (description != null && target.contentEquals(description)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findByDescription(group.getChildAt(i), target);
            if (found != null) return found;
        }
        return null;
    }
}
