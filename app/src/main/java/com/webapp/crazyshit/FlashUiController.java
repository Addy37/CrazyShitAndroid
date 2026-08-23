package com.webapp.crazyshit;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v2.2 visual layer. Adds floating navigation, collapsing header motion and richer feed depth
 * without touching the Chaos playback implementation itself.
 */
final class FlashUiController {
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

    private static final class State {
        private final WeakReference<NativeMainActivity> ref;
        private BottomNavigationView nav;
        private View topBar;
        private boolean collapsed;
        private final Map<RecyclerView, RecyclerView.OnScrollListener> listeners = new WeakHashMap<>();

        State(NativeMainActivity activity) {
            ref = new WeakReference<>(activity);
        }

        void attach() {
            NativeMainActivity activity = ref.get();
            if (activity == null || activity.isFinishing()) return;
            nav = field(activity, "bottomNavigation", BottomNavigationView.class);
            topBar = findTopBar(activity);
            polishFloatingNav(activity);
            View root = activity.findViewById(android.R.id.content);
            if (root != null) scanForFeeds(activity, root, false);
        }

        void detach() {
            for (Map.Entry<RecyclerView, RecyclerView.OnScrollListener> entry : listeners.entrySet()) {
                RecyclerView recycler = entry.getKey();
                if (recycler != null) recycler.removeOnScrollListener(entry.getValue());
            }
            listeners.clear();
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
            animateSelected(nav, nav.getSelectedItemId());
            nav.setOnItemReselectedListener(item -> animateSelected(nav, item.getItemId()));
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

        private void scanForFeeds(NativeMainActivity activity, View view, boolean chaos) {
            boolean insideChaos = chaos || view instanceof ChaosFeedView;
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
            }
            if (!(view instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanForFeeds(activity, group.getChildAt(i), insideChaos);
            }
        }

        private void applyCardDepth(RecyclerView recycler) {
            int h = Math.max(1, recycler.getHeight());
            float center = h / 2f;
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
            topBar.animate().cancel();
            topBar.animate()
                    .translationY(-dp(activity, 18))
                    .alpha(0.78f)
                    .scaleY(0.88f)
                    .setDuration(180L)
                    .start();
        }

        private void expand(NativeMainActivity activity) {
            if (!collapsed || topBar == null) return;
            collapsed = false;
            topBar.animate().cancel();
            topBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .scaleY(1f)
                    .setDuration(200L)
                    .start();
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
        bg.setStroke(dp(activity, 1), Color.rgb(62, 45, 40));
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
