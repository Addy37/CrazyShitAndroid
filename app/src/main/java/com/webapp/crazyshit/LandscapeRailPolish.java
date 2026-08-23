package com.webapp.crazyshit;

import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small follow-up polish for short landscape screens. */
final class LandscapeRailPolish {
    private LandscapeRailPolish() {
    }

    static void apply(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) return;

        View root = activity.findViewById(android.R.id.content);
        LinearLayout rail = findRail(root);
        if (rail == null || rail.getChildCount() < 2) return;

        int heightDp = activity.getResources().getConfiguration().screenHeightDp;
        boolean shortLandscape = heightDp > 0 && heightDp <= 400;

        rail.setClipChildren(false);
        rail.setClipToPadding(false);

        View logoView = rail.getChildAt(0);
        if (logoView instanceof ImageView) {
            LinearLayout.LayoutParams lp = asLinearParams(logoView.getLayoutParams());
            if (lp != null) {
                int logoSize = dp(activity, shortLandscape ? 38 : 42);
                lp.width = logoSize;
                lp.height = logoSize;
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                lp.setMargins(0, dp(activity, shortLandscape ? 5 : 8), 0,
                        dp(activity, shortLandscape ? 8 : 4));
                logoView.setLayoutParams(lp);
            }
        }

        View menuView = rail.getChildAt(1);
        if (!(menuView instanceof LinearLayout)) return;
        LinearLayout menu = (LinearLayout) menuView;
        menu.setClipChildren(false);
        menu.setClipToPadding(false);
        menu.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        int buttonHeight = dp(activity, shortLandscape ? 48 : 52);
        int verticalMargin = dp(activity, shortLandscape ? 0 : 1);
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            LinearLayout.LayoutParams lp = asLinearParams(child.getLayoutParams());
            if (lp == null) continue;
            lp.height = buttonHeight;
            lp.setMargins(lp.leftMargin, verticalMargin, lp.rightMargin, verticalMargin);
            child.setLayoutParams(lp);
            if (child instanceof TextView) {
                ((TextView) child).setPadding(
                        dp(activity, 3),
                        dp(activity, shortLandscape ? 2 : 4),
                        dp(activity, 3),
                        dp(activity, shortLandscape ? 2 : 3)
                );
            }
        }

        rail.requestLayout();
    }

    static void applySoon(NativeMainActivity activity) {
        if (activity == null) return;
        View decor = activity.getWindow().getDecorView();
        decor.postDelayed(() -> apply(activity), 120L);
        decor.postDelayed(() -> apply(activity), 260L);
    }

    private static LinearLayout findRail(View view) {
        if (view == null) return null;
        CharSequence description = view.getContentDescription();
        if (view instanceof LinearLayout && description != null &&
                "Landscape navigation".contentEquals(description)) {
            return (LinearLayout) view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            LinearLayout found = findRail(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static LinearLayout.LayoutParams asLinearParams(ViewGroup.LayoutParams raw) {
        return raw instanceof LinearLayout.LayoutParams ? (LinearLayout.LayoutParams) raw : null;
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
