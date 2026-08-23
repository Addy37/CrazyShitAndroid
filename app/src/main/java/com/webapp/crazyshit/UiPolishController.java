package com.webapp.crazyshit;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Small app-wide polish layer for the native shell.
 *
 * Keeps the existing layouts and behavior intact while adding softer transitions,
 * consistent accent colors and subtle touch feedback to feed cards.
 */
final class UiPolishController {
    private static final int ORANGE = Color.rgb(255, 90, 31);
    private static final int MUTED = Color.rgb(166, 166, 176);
    private static final long TICK_MS = 650L;

    private static final Map<NativeMainActivity, Loop> LOOPS = new WeakHashMap<>();
    private static final Map<View, Boolean> POLISHED = new WeakHashMap<>();
    private static final Map<ViewPager2, Boolean> PAGERS = new WeakHashMap<>();

    private UiPolishController() {
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        Loop loop = LOOPS.get(activity);
        if (loop == null) {
            loop = new Loop(activity);
            LOOPS.put(activity, loop);
        }
        loop.start();
    }

    static void detach(NativeMainActivity activity) {
        Loop loop = LOOPS.remove(activity);
        if (loop != null) loop.stop();
    }

    private static final class Loop implements Runnable {
        private final WeakReference<NativeMainActivity> activityRef;
        private boolean running;

        Loop(NativeMainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        void start() {
            NativeMainActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return;
            running = true;
            View decor = activity.getWindow().getDecorView();
            decor.removeCallbacks(this);
            decor.post(this);
        }

        void stop() {
            running = false;
            NativeMainActivity activity = activityRef.get();
            if (activity != null) activity.getWindow().getDecorView().removeCallbacks(this);
        }

        @Override
        public void run() {
            NativeMainActivity activity = activityRef.get();
            if (!running || activity == null || activity.isFinishing()) return;

            activity.getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
            activity.getWindow().setNavigationBarColor(Color.rgb(13, 13, 15));

            View root = activity.findViewById(android.R.id.content);
            if (root != null) polishTree(activity, root, false);

            activity.getWindow().getDecorView().postDelayed(this, TICK_MS);
        }
    }

    private static void polishTree(NativeMainActivity activity, View view, boolean insideChaos) {
        boolean chaos = insideChaos || view instanceof ChaosFeedView;

        if (view instanceof ViewPager2) {
            ViewPager2 pager = (ViewPager2) view;
            if (!chaos && pager.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL && !PAGERS.containsKey(pager)) {
                PAGERS.put(pager, Boolean.TRUE);
                pager.setPageTransformer((page, position) -> {
                    float distance = Math.min(1f, Math.abs(position));
                    float focus = 1f - distance;
                    page.setAlpha(0.88f + (0.12f * focus));
                    float scale = 0.985f + (0.015f * focus);
                    page.setScaleX(scale);
                    page.setScaleY(scale);
                });
            }
        }

        if (view instanceof BottomNavigationView) {
            polishNavigation((BottomNavigationView) view);
        }

        if (view instanceof RecyclerView && !chaos && !(view.getParent() instanceof ViewPager2)) {
            RecyclerView recycler = (RecyclerView) view;
            if (recycler.getItemAnimator() == null) {
                DefaultItemAnimator animator = new DefaultItemAnimator();
                animator.setAddDuration(180L);
                animator.setRemoveDuration(150L);
                animator.setMoveDuration(180L);
                animator.setChangeDuration(140L);
                animator.setSupportsChangeAnimations(false);
                recycler.setItemAnimator(animator);
            }
        }

        if (view instanceof MaterialCardView && !chaos) {
            polishCard(activity, (MaterialCardView) view);
        }

        if (view instanceof ProgressBar) {
            ProgressBar progress = (ProgressBar) view;
            progress.setIndeterminateTintList(ColorStateList.valueOf(ORANGE));
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            polishTree(activity, group.getChildAt(i), chaos);
        }
    }

    private static void polishNavigation(BottomNavigationView nav) {
        if (POLISHED.containsKey(nav)) return;
        POLISHED.put(nav, Boolean.TRUE);

        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] {}
        };
        int[] colors = new int[] { ORANGE, MUTED };
        ColorStateList tint = new ColorStateList(states, colors);
        nav.setItemIconTintList(tint);
        nav.setItemTextColor(tint);
        nav.setItemRippleColor(ColorStateList.valueOf(Color.argb(48, 255, 90, 31)));

        // v2.2.1 draws its own moving indicator, so disable Material's extra selected bubble.
        try {
            Method enabled = nav.getClass().getMethod("setItemActiveIndicatorEnabled", boolean.class);
            enabled.invoke(nav, false);
        } catch (Exception ignored) {
        }
    }

    private static void polishCard(NativeMainActivity activity, MaterialCardView card) {
        if (POLISHED.containsKey(card)) return;
        POLISHED.put(card, Boolean.TRUE);

        float maxRadius = dp(activity, 16);
        if (card.getRadius() > maxRadius) card.setRadius(maxRadius);
        card.setRippleColor(ColorStateList.valueOf(Color.argb(52, 255, 90, 31)));

        if (!card.isClickable()) return;
        card.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(85L).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f).setDuration(145L).start();
            }
            return false;
        });
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
