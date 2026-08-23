package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v2.2 visual layer. Adds floating navigation, collapsing header motion, animated feed depth and
 * content-shaped loading skeletons without changing Chaos playback behavior.
 */
final class FlashUiController {
    private static final long TICK_MS = 320L;
    private static final Map<NativeMainActivity, State> STATES = new WeakHashMap<>();

    private FlashUiController() {
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State old = STATES.remove(activity);
        if (old != null) old.detach();
        State state = new State(activity);
        STATES.put(activity, state);
        activity.getWindow().getDecorView().postDelayed(state::attach, 140L);
    }

    static void detach(NativeMainActivity activity) {
        State state = STATES.remove(activity);
        if (state != null) state.detach();
    }

    private static final class State implements Runnable {
        private final WeakReference<NativeMainActivity> ref;
        private BottomNavigationView nav;
        private View topBar;
        private boolean collapsed;
        private boolean running;
        private int lastSelectedId = -1;
        private final Map<RecyclerView, RecyclerView.OnScrollListener> listeners = new WeakHashMap<>();
        private final Map<ProgressBar, SkeletonLoaderView> skeletons = new WeakHashMap<>();

        State(NativeMainActivity activity) {
            ref = new WeakReference<>(activity);
        }

        void attach() {
            NativeMainActivity activity = ref.get();
            if (activity == null || activity.isFinishing()) return;
            nav = field(activity, "bottomNavigation", BottomNavigationView.class);
            topBar = findTopBar(activity);
            polishFloatingNav(activity);
            running = true;
            activity.getWindow().getDecorView().removeCallbacks(this);
            activity.getWindow().getDecorView().post(this);
        }

        void detach() {
            running = false;
            NativeMainActivity activity = ref.get();
            if (activity != null) activity.getWindow().getDecorView().removeCallbacks(this);
            for (Map.Entry<RecyclerView, RecyclerView.OnScrollListener> entry : listeners.entrySet()) {
                RecyclerView recycler = entry.getKey();
                if (recycler != null) recycler.removeOnScrollListener(entry.getValue());
            }
            listeners.clear();
            for (SkeletonLoaderView skeleton : skeletons.values()) {
                if (skeleton != null) skeleton.stop();
            }
            skeletons.clear();
        }

        @Override
        public void run() {
            NativeMainActivity activity = ref.get();
            if (!running || activity == null || activity.isFinishing()) return;

            if (nav == null) nav = field(activity, "bottomNavigation", BottomNavigationView.class);
            if (topBar == null) topBar = findTopBar(activity);

            if (nav != null && !isLandscape(activity)) {
                int selected = nav.getSelectedItemId();
                if (selected != lastSelectedId) {
                    lastSelectedId = selected;
                    animateSelected(nav, selected);
                }
            }

            View root = activity.findViewById(android.R.id.content);
            if (root != null) scanForFeeds(activity, root, false);

            activity.getWindow().getDecorView().postDelayed(this, TICK_MS);
        }

        private void polishFloatingNav(NativeMainActivity activity) {
            if (nav == null || isLandscape(activity)) return;
            nav.setBackground(navBackground(activity));
            nav.setElevation(dp(activity, 18));
            nav.setClipToOutline(false);
            nav.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);

            if (nav.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) nav.getLayoutParams();
                lp.height = dp(activity, 68);
                lp.setMargins(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 10));
                nav.setLayoutParams(lp);
            }

            nav.setItemRippleColor(ColorStateList.valueOf(Color.argb(50, 255, 90, 31)));
            lastSelectedId = nav.getSelectedItemId();
            animateSelected(nav, lastSelectedId);
            nav.setOnItemReselectedListener(item -> {
                pulse(nav.findViewById(item.getItemId()), item.getItemId() == 4);
                animateSelected(nav, item.getItemId());
            });
        }

        private void animateSelected(BottomNavigationView nav, int selectedId) {
            if (nav == null) return;
            for (int id = 1; id <= 5; id++) {
                View child = nav.findViewById(id);
                if (child == null) continue;
                boolean selected = id == selectedId;
                float scale = selected ? (id == 4 ? 1.16f : 1.06f) : 1f;
                child.animate().cancel();
                child.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .translationY(selected && id == 4 ? -dp(nav, 3) : 0f)
                        .setDuration(180L)
                        .start();
            }
        }

        private void pulse(View view, boolean stronger) {
            if (view == null) return;
            float up = stronger ? 1.24f : 1.12f;
            view.animate().cancel();
            view.animate().scaleX(up).scaleY(up).setDuration(90L).withEndAction(() -> {
                view.animate().scaleX(stronger ? 1.16f : 1.06f)
                        .scaleY(stronger ? 1.16f : 1.06f)
                        .setDuration(130L).start();
            }).start();
        }

        private void scanForFeeds(NativeMainActivity activity, View view, boolean chaos) {
            boolean insideChaos = chaos || view instanceof ChaosFeedView;

            if (view instanceof FrameLayout && !insideChaos) {
                syncSkeleton((FrameLayout) view);
            }

            if (view instanceof RecyclerView && !insideChaos) {
                RecyclerView recycler = (RecyclerView) view;
                if (!listeners.containsKey(recycler)) {
                    RecyclerView.OnScrollListener listener = new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrolled(RecyclerView rv, int dx, int dy) {
                            if (Math.abs(dy) < dp(activity, 2)) return;
                            if (dy > 0) collapse(activity);
                            else if (!rv.canScrollVertically(-1) || dy < -dp(activity, 2)) expand(activity);
                            applyCardDepth(rv);
                        }
                    };
                    recycler.addOnScrollListener(listener);
                    listeners.put(recycler, listener);
                }
                applyCardDepth(recycler);
            }

            if (!(view instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanForFeeds(activity, group.getChildAt(i), insideChaos);
            }
        }

        private void syncSkeleton(FrameLayout frame) {
            ProgressBar progress = null;
            for (int i = 0; i < frame.getChildCount(); i++) {
                View child = frame.getChildAt(i);
                if (child instanceof ProgressBar) {
                    progress = (ProgressBar) child;
                    break;
                }
            }
            if (progress == null) return;
            RecyclerView recycler = findRecycler(frame);
            if (recycler == null) return;

            SkeletonLoaderView skeleton = skeletons.get(progress);
            if (skeleton == null) {
                skeleton = new SkeletonLoaderView(frame.getContext());
                frame.addView(skeleton, new FrameLayout.LayoutParams(-1, -1));
                skeletons.put(progress, skeleton);
            }

            boolean shouldShow = progress.getVisibility() == View.VISIBLE &&
                    (recycler.getAdapter() == null || recycler.getAdapter().getItemCount() == 0);
            if (shouldShow) skeleton.start(); else skeleton.stop();
        }

        private RecyclerView findRecycler(View view) {
            if (view instanceof RecyclerView) return (RecyclerView) view;
            if (!(view instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                RecyclerView found = findRecycler(group.getChildAt(i));
                if (found != null) return found;
            }
            return null;
        }

        private void applyCardDepth(RecyclerView recycler) {
            if (recycler.getHeight() <= 0) return;
            float center = recycler.getHeight() / 2f;
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View child = recycler.getChildAt(i);
                float childCenter = (child.getTop() + child.getBottom()) / 2f;
                float distance = Math.min(1f, Math.abs(childCenter - center) / Math.max(1f, center));
                float scale = 1f - (distance * 0.018f);
                child.setScaleX(scale);
                child.setScaleY(scale);
                child.setAlpha(1f - (distance * 0.08f));
            }
        }

        private void collapse(NativeMainActivity activity) {
            if (collapsed || topBar == null || topBar.getVisibility() != View.VISIBLE || isLandscape(activity)) return;
            collapsed = true;
            animateHeader(activity, dp(activity, 48), 0.88f, 175L);
        }

        private void expand(NativeMainActivity activity) {
            if (!collapsed || topBar == null) return;
            collapsed = false;
            animateHeader(activity, dp(activity, 70), 1f, 195L);
        }

        private void animateHeader(NativeMainActivity activity, int targetHeight, float targetAlpha, long duration) {
            if (!(topBar.getLayoutParams() instanceof LinearLayout.LayoutParams)) return;
            int startHeight = topBar.getHeight() > 0 ? topBar.getHeight() : topBar.getLayoutParams().height;
            if (startHeight <= 0) startHeight = dp(activity, 70);
            ValueAnimator height = ValueAnimator.ofInt(startHeight, targetHeight);
            height.setDuration(duration);
            height.addUpdateListener(animator -> {
                ViewGroup.LayoutParams lp = topBar.getLayoutParams();
                lp.height = (int) animator.getAnimatedValue();
                topBar.setLayoutParams(lp);
            });
            height.start();
            topBar.animate().cancel();
            topBar.animate().alpha(targetAlpha).setDuration(duration).start();
        }
    }

    private static View findTopBar(NativeMainActivity activity) {
        View title = field(activity, "headerTitle", View.class);
        if (title == null || !(title.getParent() instanceof View)) return null;
        View labels = (View) title.getParent();
        return labels.getParent() instanceof View ? (View) labels.getParent() : labels;
    }

    private static GradientDrawable navBackground(NativeMainActivity activity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(activity, 24));
        bg.setColor(Color.rgb(23, 23, 27));
        bg.setStroke(dp(activity, 1), Color.rgb(74, 47, 39));
        return bg;
    }

    private static boolean isLandscape(NativeMainActivity activity) {
        return activity.getResources().getConfiguration().orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private static <T> T field(Object target, String name, Class<T> type) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(target);
                return type.isInstance(value) ? type.cast(value) : null;
            } catch (Exception ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
