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
 * Small interaction polish layer for the native shell.
 *
 * 2.8 removes the old permanent view-tree polling loop. Existing views are polished once and
 * RecyclerView children are handled when they attach, so scrolling does not trigger a full app
 * hierarchy scan every few hundred milliseconds.
 */
final class UiPolishController {
    private static final int PRIMARY = UiPalette.PRIMARY;
    private static final int MUTED = Color.rgb(166, 166, 176);

    private static final Map<NativeMainActivity, State> STATES = new WeakHashMap<>();
    private static final Map<View, Boolean> POLISHED = new WeakHashMap<>();
    private static final Map<ViewPager2, Boolean> PAGERS = new WeakHashMap<>();

    private UiPolishController() {
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State old = STATES.remove(activity);
        if (old != null) old.detach();

        State state = new State(activity);
        STATES.put(activity, state);
        activity.getWindow().getDecorView().post(state::attach);
    }

    static void detach(NativeMainActivity activity) {
        State state = STATES.remove(activity);
        if (state != null) state.detach();
    }

    private static final class State {
        private final WeakReference<NativeMainActivity> activityRef;
        private final Map<RecyclerView, RecyclerView.OnChildAttachStateChangeListener> childListeners =
                new WeakHashMap<>();

        State(NativeMainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        void attach() {
            NativeMainActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return;
            View root = activity.findViewById(android.R.id.content);
            if (root != null) polishTree(activity, root, false, this);
        }

        void watchRecycler(NativeMainActivity activity, RecyclerView recycler) {
            if (recycler == null || childListeners.containsKey(recycler)) return;
            RecyclerView.OnChildAttachStateChangeListener listener =
                    new RecyclerView.OnChildAttachStateChangeListener() {
                        @Override
                        public void onChildViewAttachedToWindow(View view) {
                            polishAttachedTree(activity, view, false);
                        }

                        @Override
                        public void onChildViewDetachedFromWindow(View view) {
                        }
                    };
            recycler.addOnChildAttachStateChangeListener(listener);
            childListeners.put(recycler, listener);
        }

        void detach() {
            for (Map.Entry<RecyclerView, RecyclerView.OnChildAttachStateChangeListener> entry
                    : childListeners.entrySet()) {
                RecyclerView recycler = entry.getKey();
                if (recycler != null) recycler.removeOnChildAttachStateChangeListener(entry.getValue());
            }
            childListeners.clear();
        }
    }

    private static void polishTree(
            NativeMainActivity activity,
            View view,
            boolean insideChaos,
            State state
    ) {
        boolean chaos = insideChaos || view instanceof ChaosFeedView;

        if (view instanceof ViewPager2) {
            ViewPager2 pager = (ViewPager2) view;
            if (!chaos && pager.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL &&
                    !PAGERS.containsKey(pager)) {
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
            polishRecycler(recycler);
            state.watchRecycler(activity, recycler);
        }

        polishAttachedTree(activity, view, chaos);

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            polishTree(activity, group.getChildAt(i), chaos, state);
        }
    }

    private static void polishAttachedTree(NativeMainActivity activity, View view, boolean insideChaos) {
        boolean chaos = insideChaos || view instanceof ChaosFeedView;

        if (view instanceof MaterialCardView && !chaos) {
            polishCard(activity, (MaterialCardView) view);
        }
        if (view instanceof ProgressBar) {
            ((ProgressBar) view).setIndeterminateTintList(ColorStateList.valueOf(PRIMARY));
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            polishAttachedTree(activity, group.getChildAt(i), chaos);
        }
    }

    private static void polishRecycler(RecyclerView recycler) {
        if (recycler.getItemAnimator() != null) return;
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setAddDuration(180L);
        animator.setRemoveDuration(150L);
        animator.setMoveDuration(180L);
        animator.setChangeDuration(140L);
        animator.setSupportsChangeAnimations(false);
        recycler.setItemAnimator(animator);
    }

    private static void polishNavigation(BottomNavigationView nav) {
        StableBottomNavigationController.styleBar(nav);
    }

    private static void polishCard(NativeMainActivity activity, MaterialCardView card) {
        if (POLISHED.containsKey(card)) return;
        POLISHED.put(card, Boolean.TRUE);

        float maxRadius = dp(activity, 16);
        if (card.getRadius() > maxRadius) card.setRadius(maxRadius);
        card.setRippleColor(ColorStateList.valueOf(Color.argb(52, 251, 245, 6)));

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

