package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationBarView;

import java.lang.reflect.Field;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Final owner for portrait bottom navigation and the restored v2.10 Home presentation.
 *
 * Home keeps the current repositories and navigation behavior, but returns to the compact
 * CrazyShit-only list, classic header and smaller outlined bottom pill used in v2.10.0.
 */
final class StableBottomNavigationController {
    private static final int NAV_HOME = 1;
    private static final int NAV_SERIES = 2;
    private static final int NAV_CATEGORIES = 3;
    private static final int NAV_CHAOS = 4;
    private static final int NAV_MORE = 5;
    private static final int HOME_SOURCE_CRAZYSHIT = 1;
    private static final String PREF_HOME_SOURCE = "home_source";
    private static final String PREF_HOME_VIEW = "native_view_home";
    private static final String PREF_LEGACY_HOME_MIGRATED = "legacy_home_v2_10_restored";

    private static final WeakHashMap<NativeMainActivity, State> STATES = new WeakHashMap<>();

    private StableBottomNavigationController() {
    }

    /** v2.10.0 portrait navigation styling. */
    static void styleBar(BottomNavigationView nav) {
        if (nav == null) return;
        Context context = nav.getContext();
        boolean oled = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("oled_black_enabled", true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(oled ? Color.BLACK : Color.rgb(20, 20, 24));
        bg.setStroke(dp(context, 1), oled ? Color.rgb(31, 31, 35) : Color.rgb(48, 48, 55));
        bg.setCornerRadius(dp(context, 30));
        nav.setBackground(bg);
        nav.setElevation(dp(context, oled ? 4 : 6));
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setItemHorizontalTranslationEnabled(false);
        nav.setItemRippleColor(ColorStateList.valueOf(Color.argb(28, 34, 211, 238)));

        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {}
        };
        int active = UiPalette.PRIMARY;
        int inactive = Color.rgb(168, 168, 178);
        ColorStateList colors = new ColorStateList(states, new int[] {active, inactive});
        nav.setItemIconTintList(colors);
        nav.setItemTextColor(colors);

        try {
            nav.setItemActiveIndicatorEnabled(true);
            nav.setItemActiveIndicatorColor(ColorStateList.valueOf(Color.argb(50, 34, 211, 238)));
            nav.setItemActiveIndicatorWidth(dp(context, 48));
            nav.setItemActiveIndicatorHeight(dp(context, 28));
            nav.setItemIconSize(dp(context, 23));
            nav.setItemPaddingTop(dp(context, 4));
            nav.setItemPaddingBottom(dp(context, 4));
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
        RecyclerView homeRecycler;
        RecyclerView.OnChildAttachStateChangeListener homeChildListener;

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
            if (homeRecycler != null && homeChildListener != null) {
                try {
                    homeRecycler.removeOnChildAttachStateChangeListener(homeChildListener);
                } catch (Exception ignored) {
                }
            }
            layoutListener = null;
            pageCallback = null;
            homeChildListener = null;
            homeRecycler = null;
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
            configureLegacyHome();
            bindHomeRecycler();

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
            styleLegacyHomeRows();
        }

        private void ensureAttached() {
            if (nav == null || shell == null || nav.getParent() != null) return;
            shell.addView(nav, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(60)
            ));
        }

        private void configureLegacyHome() {
            if (pagerAdapter == null) return;
            Object home = homePage();
            if (home == null) return;

            SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean migrated = prefs.getBoolean(PREF_LEGACY_HOME_MIGRATED, false);
            int currentSource = intField(home, "homeSource", HOME_SOURCE_CRAZYSHIT);
            boolean sourceChanged = currentSource != HOME_SOURCE_CRAZYSHIT;

            SharedPreferences.Editor editor = prefs.edit()
                    .putInt(PREF_HOME_SOURCE, HOME_SOURCE_CRAZYSHIT);
            if (!migrated) {
                pagerAdapter.setViewMode(MainPagerAdapter.PAGE_HOME, NativeFeedAdapter.VIEW_LIST);
                editor.putInt(PREF_HOME_VIEW, NativeFeedAdapter.VIEW_LIST)
                        .putBoolean(PREF_LEGACY_HOME_MIGRATED, true);
            }
            editor.apply();

            if (sourceChanged) {
                setIntField(home, "homeSource", HOME_SOURCE_CRAZYSHIT);
                pagerAdapter.refresh(MainPagerAdapter.PAGE_HOME);
            }
            hideHomeSourceSelector(home);
        }

        private void hideHomeSourceSelector(Object home) {
            Object chips = rawField(home, "homeChips");
            if (chips instanceof List && !((List<?>) chips).isEmpty()) {
                Object first = ((List<?>) chips).get(0);
                if (first instanceof View) {
                    View chip = (View) first;
                    if (chip.getParent() instanceof View) {
                        View row = (View) chip.getParent();
                        if (row.getParent() instanceof View) {
                            ((View) row.getParent()).setVisibility(View.GONE);
                        }
                    }
                }
            }

            View refresh = field(home, "refresh", View.class);
            if (refresh != null && refresh.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) refresh.getLayoutParams();
                if (params.topMargin != 0) {
                    params.topMargin = 0;
                    refresh.setLayoutParams(params);
                }
            }
            View empty = field(home, "empty", View.class);
            if (empty != null && empty.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) empty.getLayoutParams();
                if (params.topMargin != 0) {
                    params.topMargin = 0;
                    empty.setLayoutParams(params);
                }
            }
        }

        private void bindHomeRecycler() {
            Object home = homePage();
            RecyclerView next = field(home, "recycler", RecyclerView.class);
            if (next == homeRecycler && homeChildListener != null) return;

            if (homeRecycler != null && homeChildListener != null) {
                try {
                    homeRecycler.removeOnChildAttachStateChangeListener(homeChildListener);
                } catch (Exception ignored) {
                }
            }
            homeRecycler = next;
            if (homeRecycler == null) return;

            homeChildListener = new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(View view) {
                    styleLegacyHomeChild(view);
                }

                @Override
                public void onChildViewDetachedFromWindow(View view) {
                }
            };
            homeRecycler.addOnChildAttachStateChangeListener(homeChildListener);
            styleLegacyHomeRows();
        }

        private void styleLegacyHomeRows() {
            if (homeRecycler == null || pagerAdapter == null ||
                    pagerAdapter.viewMode(MainPagerAdapter.PAGE_HOME) != NativeFeedAdapter.VIEW_LIST) return;
            for (int i = 0; i < homeRecycler.getChildCount(); i++) {
                styleLegacyHomeChild(homeRecycler.getChildAt(i));
            }
        }

        private void styleLegacyHomeChild(View child) {
            if (child == null || pagerAdapter == null ||
                    pagerAdapter.viewMode(MainPagerAdapter.PAGE_HOME) != NativeFeedAdapter.VIEW_LIST) return;
            MaterialCardView card = child instanceof MaterialCardView
                    ? (MaterialCardView) child : findCard(child);
            if (card == null) return;

            if (hasImage(card)) {
                card.setCardBackgroundColor(Color.rgb(25, 25, 28));
                card.setRadius(dp(15));
                card.setCardElevation(dp(1));
                card.setStrokeColor(Color.rgb(50, 50, 57));
                card.setStrokeWidth(dp(1));
            } else {
                card.setCardBackgroundColor(Color.rgb(13, 13, 15));
                card.setRadius(0f);
                card.setCardElevation(0f);
                card.setStrokeWidth(0);
            }
        }

        private void stylePageChrome() {
            if (shell == null || shell.getChildCount() == 0) return;
            View topBar = shell.getChildAt(0);
            TextView title = field(activity, "headerTitle", TextView.class);
            TextView subtitle = field(activity, "headerSubtitle", TextView.class);
            int position = pager == null ? MainPagerAdapter.PAGE_HOME : pager.getCurrentItem();

            ImageView appIcon = childImage(topBar, 0);
            View profile = topBar instanceof ViewGroup && ((ViewGroup) topBar).getChildCount() > 3
                    ? ((ViewGroup) topBar).getChildAt(3) : null;

            if (position == MainPagerAdapter.PAGE_HOME) {
                topBar.setBackgroundColor(Color.rgb(17, 17, 20));
                topBar.setElevation(0f);
                if (title != null) {
                    title.setText("Home");
                    title.setTextColor(Color.WHITE);
                    title.setTextSize(18f);
                }
                if (subtitle != null) {
                    subtitle.setVisibility(View.VISIBLE);
                    subtitle.setTextColor(Color.rgb(168, 168, 178));
                    subtitle.setTextSize(11f);
                    String mode = pagerAdapter == null
                            ? "List"
                            : FeedViewStyleController.label(pagerAdapter.viewMode(MainPagerAdapter.PAGE_HOME));
                    subtitle.setText("CrazyShit  •  " + mode);
                }
                if (appIcon != null) appIcon.setVisibility(View.VISIBLE);
                if (profile != null) profile.setVisibility(View.GONE);
                return;
            }

            topBar.setBackgroundColor(Color.BLACK);
            if (title != null) {
                title.setTextColor(UiPalette.PRIMARY);
                title.setTextSize(23f);
            }
            if (subtitle != null) subtitle.setVisibility(View.GONE);
            if (appIcon != null) appIcon.setVisibility(View.GONE);
            if (profile != null) profile.setVisibility(View.VISIBLE);
        }

        private Object homePage() {
            if (pagerAdapter == null) return null;
            Object raw = rawField(pagerAdapter, "pages");
            if (!(raw instanceof Object[])) return null;
            Object[] pages = (Object[]) raw;
            return pages.length > MainPagerAdapter.PAGE_HOME ? pages[MainPagerAdapter.PAGE_HOME] : null;
        }

        private void applyGeometry() {
            if (nav == null || nav.getLayoutParams() == null) return;
            if (activity.getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE) return;

            ViewGroup.LayoutParams raw = nav.getLayoutParams();
            boolean changed = false;
            int wantedHeight = dp(60);
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
                item.setTranslationY(0f);
                item.setAlpha(1f);
            }
        }

        private int dp(int value) {
            return StableBottomNavigationController.dp(activity, value);
        }
    }

    private static MaterialCardView findCard(View view) {
        if (view instanceof MaterialCardView) return (MaterialCardView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            MaterialCardView found = findCard(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean hasImage(View view) {
        if (view instanceof ImageView) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (hasImage(group.getChildAt(i))) return true;
        }
        return false;
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

    private static int intField(Object target, String name, int fallback) {
        Object value = rawField(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static void setIntField(Object target, String name, int value) {
        Field field = findField(target == null ? null : target.getClass(), name);
        if (field == null) return;
        try {
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (Exception ignored) {
        }
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