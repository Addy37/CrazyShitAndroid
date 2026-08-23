package com.webapp.crazyshit;

import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Keeps the proven NativeMainActivity navigation plumbing while repurposing its old secondary
 * pager slots as Series and Categories. This controller only changes chrome, never feed data.
 */
final class SeriesCategoriesNavController {
    private static final int NAV_SERIES = 2;
    private static final int NAV_CATEGORIES = 3;
    private static final WeakHashMap<NativeMainActivity, State> STATES = new WeakHashMap<>();

    private SeriesCategoriesNavController() {
    }

    static void attachSoon(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> attach(activity));
        decor.postDelayed(() -> apply(activity), 120L);
        decor.postDelayed(() -> apply(activity), 300L);
    }

    static void detach(NativeMainActivity activity) {
        State state = STATES.remove(activity);
        if (state != null && state.pager != null && state.callback != null) {
            state.pager.unregisterOnPageChangeCallback(state.callback);
        }
    }

    private static void attach(NativeMainActivity activity) {
        State state = STATES.get(activity);
        if (state == null) {
            state = new State();
            STATES.put(activity, state);
        }

        BottomNavigationView nav = field(activity, "bottomNavigation", BottomNavigationView.class);
        ViewPager2 pager = field(activity, "primaryPager", ViewPager2.class);
        if (nav == null || pager == null) return;

        state.nav = nav;
        if (state.pager != pager || state.callback == null) {
            if (state.pager != null && state.callback != null) {
                state.pager.unregisterOnPageChangeCallback(state.callback);
            }
            state.pager = pager;
            state.callback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    syncHeader(activity, position);
                    updateLandscapeRail(activity);
                }
            };
            pager.registerOnPageChangeCallback(state.callback);
        }
        apply(activity);
    }

    static void apply(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        BottomNavigationView nav = field(activity, "bottomNavigation", BottomNavigationView.class);
        ViewPager2 pager = field(activity, "primaryPager", ViewPager2.class);
        if (nav == null || pager == null) return;

        MenuItem series = nav.getMenu().findItem(NAV_SERIES);
        if (series != null) {
            series.setTitle("Series");
            series.setIcon(R.drawable.ic_nav_series);
        }
        MenuItem categories = nav.getMenu().findItem(NAV_CATEGORIES);
        if (categories != null) {
            categories.setTitle("Categories");
            categories.setIcon(R.drawable.ic_nav_categories);
        }

        syncHeader(activity, pager.getCurrentItem());
        updateLandscapeRail(activity);
    }

    private static void syncHeader(NativeMainActivity activity, int position) {
        TextView title = field(activity, "headerTitle", TextView.class);
        TextView subtitle = field(activity, "headerSubtitle", TextView.class);
        if (title == null || subtitle == null) return;

        if (position == MainPagerAdapter.PAGE_SERIES) {
            title.setText("Series");
            subtitle.setText("CrazyShit  •  Browse series");
        } else if (position == MainPagerAdapter.PAGE_CATEGORIES) {
            title.setText("Categories");
            subtitle.setText("CrazyShit  •  Browse categories");
        }
    }

    private static void updateLandscapeRail(NativeMainActivity activity) {
        View root = activity.findViewById(android.R.id.content);
        LinearLayout rail = findRail(root);
        if (rail == null || rail.getChildCount() < 2) return;
        View menuView = rail.getChildAt(1);
        if (!(menuView instanceof LinearLayout)) return;
        LinearLayout menu = (LinearLayout) menuView;

        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView button = (TextView) child;
            CharSequence description = button.getContentDescription();
            String value = description == null ? button.getText().toString() : description.toString();
            if ("Trending".equals(value) || "Series".equals(value)) {
                styleRailButton(activity, button, "Series", R.drawable.ic_nav_series);
            } else if ("Memes".equals(value) || "Categories".equals(value)) {
                styleRailButton(activity, button, "Categories", R.drawable.ic_nav_categories);
            }
        }
    }

    private static void styleRailButton(
            NativeMainActivity activity,
            TextView button,
            String label,
            int iconRes
    ) {
        button.setText(label);
        button.setContentDescription(label);
        Drawable icon = activity.getDrawable(iconRes);
        if (icon != null) {
            icon = icon.mutate();
            int size = dp(activity, 23);
            icon.setBounds(0, 0, size, size);
            Drawable[] old = button.getCompoundDrawables();
            Drawable left = old.length > 0 ? old[0] : null;
            Drawable right = old.length > 2 ? old[2] : null;
            Drawable bottom = old.length > 3 ? old[3] : null;
            button.setCompoundDrawables(left, icon, right, bottom);
        }
    }

    private static LinearLayout findRail(View view) {
        if (view == null) return null;
        CharSequence description = view.getContentDescription();
        if (view instanceof LinearLayout && description != null &&
                "Landscape navigation".contentEquals(description)) {
            return (LinearLayout) view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            LinearLayout found = findRail(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) {
        if (target == null) return null;
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? (T) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class State {
        BottomNavigationView nav;
        ViewPager2 pager;
        ViewPager2.OnPageChangeCallback callback;
    }
}