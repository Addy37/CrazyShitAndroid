package com.webapp.crazyshit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Single final owner for the visible portrait Material bottom navigation.
 *
 * The Material/OLED bar is the desired UI. This controller does not create a replacement bar.
 * It only keeps the real BottomNavigationView attached, removes leftover per-item transforms from
 * older Flash/original navigation code, and reasserts the final portrait geometry after visual and
 * responsive passes have finished.
 */
final class StableBottomNavigationController {
    private static final int NAV_HOME = 1;
    private static final int NAV_SERIES = 2;
    private static final int NAV_CATEGORIES = 3;
    private static final int NAV_CHAOS = 4;
    private static final int NAV_MORE = 5;

    private static final WeakHashMap<NativeMainActivity, State> STATES = new WeakHashMap<>();

    private StableBottomNavigationController() {
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
        View.OnLayoutChangeListener layoutListener;

        State(NativeMainActivity activity) {
            this.activity = activity;
        }

        void bind() {
            nav = field(activity, "bottomNavigation", BottomNavigationView.class);
            shell = field(activity, "shell", LinearLayout.class);
            if (nav == null) return;

            layoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (activity.isFinishing()) return;
                if (activity.getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE) return;
                applyGeometry();
                resetLegacyItemTransforms();
            };
            nav.addOnLayoutChangeListener(layoutListener);
            apply();
        }

        void detach() {
            if (nav != null && layoutListener != null) {
                try {
                    nav.removeOnLayoutChangeListener(layoutListener);
                } catch (Exception ignored) {
                }
            }
            layoutListener = null;
        }

        void scheduleFinalPasses() {
            View decor = activity.getWindow().getDecorView();
            decor.post(this::apply);
            decor.postDelayed(this::apply, 180L);
            // OLED + responsive passes can run late. This is the final one-shot geometry pass.
            decor.postDelayed(this::apply, 640L);
        }

        void apply() {
            if (activity.isFinishing()) return;
            if (nav == null) {
                bind();
                if (nav == null) return;
            }

            ensureAttached();
            boolean landscape = activity.getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE;
            if (landscape) {
                // LandscapeUiController owns the left rail and visibility in horizontal mode.
                return;
            }

            nav.setVisibility(View.VISIBLE);
            nav.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            styleMaterialBar();
            applyGeometry();
            resetLegacyItemTransforms();
        }

        private void ensureAttached() {
            if (nav == null || shell == null || nav.getParent() != null) return;
            shell.addView(nav, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(62)
            ));
        }

        private void styleMaterialBar() {
            boolean oled = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("oled_black_enabled", true);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(oled ? Color.rgb(0, 0, 0) : Color.rgb(20, 20, 24));
            bg.setStroke(dp(1), oled ? Color.rgb(31, 31, 35) : Color.rgb(48, 48, 55));
            bg.setCornerRadius(dp(30));
            nav.setBackground(bg);
            nav.setElevation(dp(oled ? 8 : 11));
            nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
            nav.setItemRippleColor(ColorStateList.valueOf(Color.argb(28, 255, 90, 31)));

            int[][] states = new int[][] {
                    new int[] {android.R.attr.state_checked},
                    new int[] {}
            };
            int active = Color.rgb(255, 98, 42);
            int inactive = Color.rgb(174, 174, 184);
            ColorStateList colors = new ColorStateList(states, new int[] {active, inactive});
            nav.setItemIconTintList(colors);
            nav.setItemTextColor(colors);

            try {
                nav.setItemActiveIndicatorEnabled(true);
                nav.setItemActiveIndicatorColor(
                        ColorStateList.valueOf(Color.argb(52, 255, 90, 31))
                );
            } catch (Throwable ignored) {
            }

            try {
                nav.setItemIconSize(dp(25));
                nav.setItemPaddingTop(dp(5));
                nav.setItemPaddingBottom(dp(4));
            } catch (Throwable ignored) {
            }
        }

        private void applyGeometry() {
            if (nav == null || nav.getLayoutParams() == null) return;
            if (activity.getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE) return;

            ViewGroup.LayoutParams raw = nav.getLayoutParams();
            boolean changed = false;
            int wantedHeight = dp(62);
            if (raw.height != wantedHeight) {
                raw.height = wantedHeight;
                changed = true;
            }
            if (raw instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) raw;
                int side = dp(8);
                int top = dp(2);
                int bottom = dp(5);
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
                item.setTranslationY(0f);
                item.setAlpha(1f);
            }
        }

        private int dp(int value) {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) {
        Field field = findField(target == null ? null : target.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? (T) value : null;
        } catch (Exception ignored) {
            return null;
        }
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
