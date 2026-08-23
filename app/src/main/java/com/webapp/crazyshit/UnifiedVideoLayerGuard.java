package com.webapp.crazyshit;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.google.android.material.card.MaterialCardView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Keeps the unified video session visually above shell-only polish and smooths the
 * full-to-mini transition without changing the player/session architecture.
 */
final class UnifiedVideoLayerGuard {
    private static final long FRAME_MS = 16L;
    private static final WeakHashMap<NativeMainActivity, GuardLoop> LOOPS = new WeakHashMap<>();

    private UnifiedVideoLayerGuard() {
    }

    static void raise(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        GuardLoop loop;
        synchronized (LOOPS) {
            loop = LOOPS.get(activity);
            if (loop == null) {
                loop = new GuardLoop(activity);
                LOOPS.put(activity, loop);
            }
        }
        loop.start();
    }

    private static final class GuardLoop implements Runnable {
        private final WeakReference<NativeMainActivity> ref;
        private boolean running;
        private int idleFrames;

        GuardLoop(NativeMainActivity activity) {
            ref = new WeakReference<>(activity);
        }

        void start() {
            NativeMainActivity activity = ref.get();
            if (activity == null || activity.isFinishing()) return;
            if (running) return;
            running = true;
            idleFrames = 0;
            activity.getWindow().getDecorView().removeCallbacks(this);
            activity.getWindow().getDecorView().post(this);
        }

        @Override
        public void run() {
            NativeMainActivity activity = ref.get();
            if (!running || activity == null || activity.isFinishing()) {
                running = false;
                return;
            }

            FrameLayout root = root(activity);
            if (root == null) {
                running = false;
                return;
            }

            View detail = null;
            MaterialCardView mini = null;
            SwipeMinimizeFrameLayout player = null;
            ScrollView details = null;

            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child instanceof SwipeMinimizeFrameLayout) {
                    player = (SwipeMinimizeFrameLayout) child;
                } else if (child instanceof MaterialCardView) {
                    mini = (MaterialCardView) child;
                } else if (child instanceof FrameLayout) {
                    ScrollView found = findScrollView((ViewGroup) child);
                    if (found != null) {
                        detail = child;
                        details = found;
                    }
                }
            }

            boolean active = player != null && player.getVisibility() == View.VISIBLE;
            if (!active) {
                idleFrames++;
                if (idleFrames > 18) {
                    running = false;
                    return;
                }
            } else {
                idleFrames = 0;
                polish(activity, root, detail, details, mini, player);
            }

            activity.getWindow().getDecorView().postDelayed(this, FRAME_MS);
        }
    }

    private static void polish(
            NativeMainActivity activity,
            FrameLayout root,
            View detail,
            ScrollView details,
            MaterialCardView mini,
            SwipeMinimizeFrameLayout player
    ) {
        if (detail != null && detail.getVisibility() == View.VISIBLE) {
            root.bringChildToFront(detail);
        }
        if (mini != null && mini.getVisibility() == View.VISIBLE) {
            root.bringChildToFront(mini);
            mini.setCardElevation(dp(activity, 9));
            mini.setTranslationZ(dp(activity, 9));
            FrameLayout slot = findEmptyFrame(mini);
            if (slot != null) {
                slot.setBackgroundColor(Color.TRANSPARENT);
                slot.setElevation(0f);
                slot.setTranslationZ(0f);
            }
        }

        // The live player must always win the Z-order over the mini-card's placeholder slot.
        root.bringChildToFront(player);
        player.setElevation(dp(activity, 28));
        player.setTranslationZ(dp(activity, 28));

        if (details != null) {
            boolean miniVisible = mini != null && mini.getVisibility() == View.VISIBLE;
            if (!miniVisible) {
                details.setAlpha(1f);
            } else {
                // Hide text/cards very early in a minimize, and bring them back only near the end
                // of an expand. The dark detail backdrop can still fade normally underneath.
                float scale = Math.max(player.getScaleX(), player.getScaleY());
                float contentAlpha = clamp((scale - 0.88f) / 0.12f);
                details.setAlpha(contentAlpha);
            }
        }
    }

    private static ScrollView findScrollView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ScrollView) return (ScrollView) child;
        }
        return null;
    }

    private static FrameLayout findEmptyFrame(View view) {
        if (view instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) view;
            if (frame.getChildCount() == 0 && frame.getLayoutParams() != null &&
                    frame.getLayoutParams().width > 0 && frame.getLayoutParams().height > 0) {
                return frame;
            }
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            FrameLayout found = findEmptyFrame(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static FrameLayout root(NativeMainActivity activity) {
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("overlayRoot");
                field.setAccessible(true);
                Object value = field.get(activity);
                return value instanceof FrameLayout ? (FrameLayout) value : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
