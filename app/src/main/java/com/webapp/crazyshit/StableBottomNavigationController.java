package com.webapp.crazyshit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/** Final owner for portrait bottom navigation and page-specific ZeroChill chrome. */
final class StableBottomNavigationController {
    private static final int NAV_HOME = 1;
    private static final int NAV_SERIES = 2;
    private static final int NAV_CATEGORIES = 3;
    private static final int NAV_CHAOS = 4;
    private static final int NAV_MORE = 5;
    private static final WeakHashMap<NativeMainActivity, State> STATES = new WeakHashMap<>();

    private StableBottomNavigationController() {
    }

    /** Shared ZeroChill floating-glass portrait navigation styling. */
    static void styleBar(BottomNavigationView nav) {
        if (nav == null) return;
        Context context = nav.getContext();
        nav.setBackground(ZeroChillUi.navigationGlass(context));
        nav.setBackgroundTintList(null);
        nav.setElevation(ZeroChillUi.dimension(context, R.dimen.zc_elevation_navigation));
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setItemHorizontalTranslationEnabled(false);
        nav.setItemRippleColor(ColorStateList.valueOf(
                ZeroChillUi.color(context, R.color.zc_cyan_container)));

        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {}
        };
        int active = ZeroChillUi.color(context, R.color.zc_cyan);
        int inactive = ZeroChillUi.color(context, R.color.zc_text_secondary);
        ColorStateList colors = new ColorStateList(states, new int[] {active, inactive});
        nav.setItemIconTintList(colors);
        nav.setItemTextColor(colors);

        try {
            nav.setItemActiveIndicatorEnabled(true);
            nav.setItemActiveIndicatorColor(ColorStateList.valueOf(Color.TRANSPARENT));
            nav.setItemActiveIndicatorWidth(ZeroChillUi.dimension(context, R.dimen.zc_nav_indicator_width));
            nav.setItemActiveIndicatorHeight(ZeroChillUi.dimension(context, R.dimen.zc_nav_indicator_height));
            nav.setItemIconSize(ZeroChillUi.dimension(context, R.dimen.zc_nav_icon));
            nav.setItemPaddingTop(dp(context, 3));
            nav.setItemPaddingBottom(dp(context, 5));
            nav.setItemBackgroundResource(R.drawable.zc_nav_item_background);
        } catch (Throwable ignored) {
        }
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State state = STATES.get(activity);
        if (state == null) {
            state = new State(activity);
            STATES.put(activity, state);
            state.bind();
        }
        state.scheduleFinalPasses();
    }

    static void applyOrientation(NativeMainActivity activity) {
        State state = STATES.get(activity);
        if (state == null) {
            attach(activity);
            return;
        }
        state.apply();
    }

    static void detach(NativeMainActivity activity) {
        State state = STATES.remove(activity);
        if (state != null) state.detach();
    }

    private static final class State {
        final NativeMainActivity activity;
        BottomNavigationView nav;
        LinearLayout shell;
        ViewPager2 pager;
        MainPagerAdapter pagerAdapter;
        ViewPager2.OnPageChangeCallback pageCallback;
        View.OnLayoutChangeListener layoutListener;

        State(NativeMainActivity activity) {
            this.activity = activity;
        }

        void bind() {
            nav = field(activity, "bottomNavigation", BottomNavigationView.class);
            shell = field(activity, "shell", LinearLayout.class);
            pager = field(activity, "primaryPager", ViewPager2.class);
            pagerAdapter = field(activity, "primaryPagerAdapter", MainPagerAdapter.class);
            if (nav == null) return;

            layoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (activity.isFinishing()) return;
                if (activity.getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE) return;
                applyGeometry();
                resetLegacyItemTransforms();
            };
            nav.addOnLayoutChangeListener(layoutListener);

            if (pager != null) {
                pageCallback = new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        pager.post(State.this::apply);
                        pager.postDelayed(State.this::apply, 80L);
                        pager.postDelayed(State.this::apply, 500L);
                    }
                };
                pager.registerOnPageChangeCallback(pageCallback);
            }
            apply();
        }

        void detach() {
            if (nav != null && layoutListener != null) {
                try {
                    nav.removeOnLayoutChangeListener(layoutListener);
                } catch (Exception ignored) {
                }
            }
            if (pager != null && pageCallback != null) {
                try {
                    pager.unregisterOnPageChangeCallback(pageCallback);
                } catch (Exception ignored) {
                }
            }
            layoutListener = null;
            pageCallback = null;
        }

        void scheduleFinalPasses() {
            View decor = activity.getWindow().getDecorView();
            decor.post(this::apply);
            decor.postDelayed(this::apply, 180L);
            decor.postDelayed(this::apply, 640L);
            decor.postDelayed(this::apply, 900L);
        }

        void apply() {
            if (activity.isFinishing()) return;
            if (nav == null) {
                bind();
                if (nav == null) return;
            }
            if (pager == null) pager = field(activity, "primaryPager", ViewPager2.class);
            if (pagerAdapter == null) {
                pagerAdapter = field(activity, "primaryPagerAdapter", MainPagerAdapter.class);
            }

            ensureAttached();

            boolean landscape = activity.getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE;
            if (landscape) {
                return;
            }

            nav.setVisibility(View.VISIBLE);
            nav.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            styleBar(nav);
            applyGeometry();
            resetLegacyItemTransforms();
            stylePageChrome();
        }

        private void ensureAttached() {
            if (nav == null || shell == null || nav.getParent() != null) return;
            shell.addView(nav, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ZeroChillUi.dimension(activity, R.dimen.zc_bottom_nav_height)
            ));
        }

        private void stylePageChrome() {
            if (shell == null || shell.getChildCount() == 0) return;
            View topBar = shell.getChildAt(0);
            TextView title = field(activity, "headerTitle", TextView.class);
            TextView subtitle = field(activity, "headerSubtitle", TextView.class);

            topBar.setBackgroundColor(ZeroChillUi.background(activity));
            topBar.setElevation(0f);
            if (title != null) {
                title.setTextSize(22f);
            }
            if (subtitle != null) subtitle.setVisibility(View.GONE);
        }

        private void applyGeometry() {
            if (nav == null || nav.getLayoutParams() == null) return;
            if (activity.getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE) return;

            ViewGroup.LayoutParams raw = nav.getLayoutParams();
            boolean changed = false;
            int wantedHeight = ZeroChillUi.dimension(activity, R.dimen.zc_bottom_nav_height);
            if (raw.height != wantedHeight) {
                raw.height = wantedHeight;
                changed = true;
            }
            if (raw instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) raw;
                int side = dp(10);
                int top = dp(2);
                int bottom = dp(6);
                if (margins.leftMargin != side || margins.topMargin != top ||
                        margins.rightMargin != side || margins.bottomMargin != bottom) {
                    margins.setMargins(side, top, side, bottom);
                    changed = true;
                }
            }
            nav.setMinimumHeight(0);
            if (changed) nav.setLayoutParams(raw);
        }

        private void resetLegacyItemTransforms() {
            if (nav == null) return;
            for (int id : new int[] {NAV_HOME, NAV_SERIES, NAV_CHAOS, NAV_CATEGORIES, NAV_MORE}) {
                View item = nav.findViewById(id);
                if (item == null) continue;
                item.animate().cancel();
                item.setScaleX(1f);
                item.setScaleY(1f);
                item.setTranslationX(0f);
                item.setTranslationY(-dp(1));
                item.setAlpha(1f);
            }
        }

        private int dp(int value) {
            return StableBottomNavigationController.dp(activity, value);
        }
    }

    private static ImageView childImage(View view, int index) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        if (index < 0 || index >= group.getChildCount()) return null;
        View child = group.getChildAt(index);
        return child instanceof ImageView ? (ImageView) child : null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Object rawField(Object target, String name) {
        Field field = findField(target == null ? null : target.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) {
        Object value = rawField(target, name);
        return type.isInstance(value) ? (T) value : null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
