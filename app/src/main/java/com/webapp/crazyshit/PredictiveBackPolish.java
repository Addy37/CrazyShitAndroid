package com.webapp.crazyshit;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds Android 14+ predictive-back progress to screens that already own custom back behavior.
 * Simple activities continue using Android's normal system predictive-back animation.
 */
@TargetApi(34)
final class PredictiveBackPolish {
    private static final Map<Activity, OnBackInvokedCallback> CALLBACKS = new WeakHashMap<>();

    private PredictiveBackPolish() {
    }

    static void attach(Activity activity) {
        if (Build.VERSION.SDK_INT < 34 || activity == null || activity.isFinishing()) return;
        if (!(activity instanceof NativeMainActivity) && !(activity instanceof VideoDetailActivity)) return;
        if (CALLBACKS.containsKey(activity)) return;

        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        OnBackAnimationCallback callback = new OnBackAnimationCallback() {
            private int edge = BackEvent.EDGE_LEFT;
            private boolean relatedPreview;

            @Override
            public void onBackStarted(BackEvent backEvent) {
                edge = backEvent.getSwipeEdge();
                relatedPreview = activity instanceof VideoDetailActivity
                        && ((VideoDetailActivity) activity).startRelatedBackPreview(
                                edge == BackEvent.EDGE_RIGHT
                        );
                if (relatedPreview) return;
                content.animate().cancel();
                content.setPivotY(content.getHeight() * 0.5f);
                content.setPivotX(edge == BackEvent.EDGE_RIGHT ? content.getWidth() : 0f);
            }

            @Override
            public void onBackProgressed(BackEvent backEvent) {
                float p = Math.max(0f, Math.min(1f, backEvent.getProgress()));
                if (relatedPreview && activity instanceof VideoDetailActivity) {
                    ((VideoDetailActivity) activity).updateRelatedBackPreview(
                            p,
                            edge == BackEvent.EDGE_RIGHT
                    );
                    return;
                }
                float eased = 1f - (1f - p) * (1f - p);
                float direction = edge == BackEvent.EDGE_RIGHT ? -1f : 1f;
                content.setTranslationX(direction * dp(activity, 34) * eased);
                float scale = 1f - (0.035f * eased);
                content.setScaleX(scale);
                content.setScaleY(scale);
                content.setAlpha(1f - (0.08f * eased));
            }

            @Override
            public void onBackCancelled() {
                if (relatedPreview && activity instanceof VideoDetailActivity) {
                    relatedPreview = false;
                    ((VideoDetailActivity) activity).cancelRelatedBackPreview();
                    return;
                }
                restore(content, true);
            }

            @Override
            public void onBackInvoked() {
                if (relatedPreview && activity instanceof VideoDetailActivity) {
                    relatedPreview = false;
                    ((VideoDetailActivity) activity).commitRelatedBackPreview();
                    return;
                }
                restore(content, false);
                activity.onBackPressed();
            }
        };

        try {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                    callback
            );
            CALLBACKS.put(activity, callback);
        } catch (Exception ignored) {
        }
    }

    static void detach(Activity activity) {
        if (Build.VERSION.SDK_INT < 34 || activity == null) return;
        if (activity instanceof VideoDetailActivity) {
            ((VideoDetailActivity) activity).abortRelatedBackPreview();
        }
        OnBackInvokedCallback callback = CALLBACKS.remove(activity);
        if (callback == null) return;
        try {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
        } catch (Exception ignored) {
        }
    }

    private static void restore(View content, boolean animate) {
        if (content == null) return;
        content.animate().cancel();
        if (!animate) {
            content.setTranslationX(0f);
            content.setScaleX(1f);
            content.setScaleY(1f);
            content.setAlpha(1f);
            return;
        }
        content.animate()
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(150L)
                .start();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
