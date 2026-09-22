package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Responsive shell motion with a single reduced-motion gate. */
final class ZeroChillMotion {
    static final long PRESS_IN_MS = 80L;
    static final long QUICK_MS = 140L;
    static final long STANDARD_MS = 190L;

    private ZeroChillMotion() {
    }

    static boolean animationsEnabled(Context context) {
        return ValueAnimator.areAnimatorsEnabled()
                && context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("immersive_motion_enabled", true);
    }

    static void installPressFeedback(View view) {
        if (view == null || view.getTag(R.id.zerochill_motion_installed) != null) return;
        view.setTag(R.id.zerochill_motion_installed, Boolean.TRUE);
        view.setOnTouchListener((target, event) -> {
            if (!animationsEnabled(target.getContext())) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                target.animate().cancel();
                target.animate().scaleX(0.965f).scaleY(0.965f).alpha(0.94f)
                        .setDuration(PRESS_IN_MS)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                target.animate().cancel();
                target.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(QUICK_MS)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
            return false;
        });
    }

    static void animateSelection(View view, boolean selected) {
        if (view == null) return;
        if (!animationsEnabled(view.getContext())) {
            view.animate().cancel();
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        float scale = selected ? 1.04f : 1f;
        view.animate().cancel();
        view.animate().scaleX(scale).scaleY(scale).setDuration(STANDARD_MS)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    static void enterFromEnd(View view, float distance) {
        if (view == null || !animationsEnabled(view.getContext())) return;
        view.setAlpha(0f);
        view.setTranslationX(distance);
        view.animate().alpha(1f).translationX(0f).setDuration(STANDARD_MS)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}
