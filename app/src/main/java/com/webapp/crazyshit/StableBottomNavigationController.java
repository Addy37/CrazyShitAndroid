package com.webapp.crazyshit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Single owner for the visible portrait bottom navigation.
 *
 * The old Material BottomNavigationView is retained only as a detached-looking logical router so
 * its existing activity callbacks and landscape rail behavior keep working. It is always GONE in
 * portrait. This custom bar owns every visible pixel, selected state and item position, so Material
 * label animations and the old Flash nav polling loop cannot rearrange it after launch.
 */
final class StableBottomNavigationController {
    private static final int NAV_HOME = 1;
    private static final int NAV_SERIES = 2;
    private static final int NAV_CATEGORIES = 3;
    private static final int NAV_CHAOS = 4;
    private static final int NAV_MORE = 5;

    private static final Map<NativeMainActivity, State> STATES = new WeakHashMap<>();

    private StableBottomNavigationController() {
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State existing = STATES.get(activity);
        if (existing != null) {
            existing.applyOrientation();
            existing.syncFromPager();
            return;
        }
        State state = new State(activity);
        STATES.put(activity, state);
        View decor = activity.getWindow().getDecorView();
        decor.post(state::attach);
        decor.postDelayed(state::restyle, 220L);
        decor.postDelayed(state::restyle, 620L);
    }

    static void applyOrientation(NativeMainActivity activity) {
        State state = STATES.get(activity);
        if (state != null) state.applyOrientation();
    }

    static void detach(NativeMainActivity activity) {
        State state = STATES.remove(activity);
        if (state != null) state.detach();
    }

    private static final class State {
        final NativeMainActivity activity;
        final Map<Integer, View> slots = new HashMap<>();
        final Map<Integer, ImageView> icons = new HashMap<>();
        final Map<Integer, TextView> labels = new HashMap<>();

        BottomNavigationView materialRouter;
        LinearLayout shell;
        ViewPager2 pager;
        FrameLayout customRoot;
        LinearLayout track;
        ViewPager2.OnPageChangeCallback pageCallback;
        int selectedId = NAV_HOME;

        State(NativeMainActivity activity) {
            this.activity = activity;
        }

        void attach() {
            if (activity.isFinishing()) return;
            materialRouter = field(activity, "bottomNavigation", BottomNavigationView.class);
            shell = field(activity, "shell", LinearLayout.class);
            pager = field(activity, "primaryPager", ViewPager2.class);
            if (materialRouter == null || shell == null || pager == null) return;

            // Keep the Material view available to the existing navigation listener and landscape
            // rail, but remove it completely from portrait layout and accessibility.
            materialRouter.setVisibility(View.GONE);
            materialRouter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

            buildCustomBar();
            installPagerCallback();
            syncFromPager();
            applyOrientation();
        }

        void detach() {
            if (pager != null && pageCallback != null) {
                try {
                    pager.unregisterOnPageChangeCallback(pageCallback);
                } catch (Exception ignored) {
                }
            }
            pageCallback = null;
            if (customRoot != null && customRoot.getParent() instanceof ViewGroup) {
                ((ViewGroup) customRoot.getParent()).removeView(customRoot);
            }
            customRoot = null;
            track = null;
            slots.clear();
            icons.clear();
            labels.clear();
        }

        void restyle() {
            if (activity.isFinishing() || customRoot == null) return;
            styleTrack();
            updateSelection(false);
            applyOrientation();
        }

        void applyOrientation() {
            if (materialRouter == null || customRoot == null) return;
            boolean landscape = activity.getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE;
            if (landscape) {
                customRoot.setVisibility(View.GONE);
                // LandscapeUiController owns the rail. Its router may remain GONE.
            } else {
                materialRouter.setVisibility(View.GONE);
                customRoot.setVisibility(View.VISIBLE);
            }
        }

        void syncFromPager() {
            if (pager == null) return;
            selectedId = idForPage(pager.getCurrentItem());
            updateSelection(false);
        }

        private void buildCustomBar() {
            if (customRoot != null || shell == null) return;

            FrameLayout root = new FrameLayout(activity);
            root.setClipChildren(false);
            root.setClipToPadding(false);
            root.setBackgroundColor(Color.TRANSPARENT);
            root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

            LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(-1, dp(64));
            shell.addView(root, rootParams);
            customRoot = root;

            LinearLayout bar = new LinearLayout(activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setClipChildren(false);
            bar.setClipToPadding(false);
            styleTrackBackground(bar);
            FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-1, dp(54));
            barParams.gravity = Gravity.BOTTOM;
            barParams.setMargins(dp(8), 0, dp(8), dp(3));
            root.addView(bar, barParams);
            track = bar;

            addRegularItem(NAV_HOME, "Home", R.drawable.ic_nav_home);
            addRegularItem(NAV_SERIES, "Series", R.drawable.ic_nav_series);
            addChaosItem();
            addRegularItem(NAV_CATEGORIES, "Categories", R.drawable.ic_nav_categories);
            addRegularItem(NAV_MORE, "More", R.drawable.ic_nav_more);
        }

        private void addRegularItem(int id, String label, int iconRes) {
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), dp(4), dp(2), dp(2));
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(label);

            ImageView icon = new ImageView(activity);
            icon.setImageResource(iconRes);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            item.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));

            TextView text = new TextView(activity);
            text.setText(label);
            text.setTextSize(10f);
            text.setGravity(Gravity.CENTER);
            text.setSingleLine(true);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, dp(18));
            textParams.topMargin = dp(1);
            item.addView(text, textParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            track.addView(item, params);

            slots.put(id, item);
            icons.put(id, icon);
            labels.put(id, text);
            item.setOnClickListener(v -> selectFromUser(id, v));
        }

        private void addChaosItem() {
            FrameLayout item = new FrameLayout(activity);
            item.setClipChildren(false);
            item.setClipToPadding(false);
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription("Chaos");

            ImageView button = new ImageView(activity);
            button.setImageResource(R.drawable.ic_nav_chaos);
            button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            button.setPadding(dp(13), dp(13), dp(13), dp(13));
            button.setBackground(circle(Color.rgb(255, 82, 22)));
            button.setElevation(dp(10));
            FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(dp(50), dp(50));
            buttonParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            buttonParams.topMargin = -dp(6);
            item.addView(button, buttonParams);

            TextView text = new TextView(activity);
            text.setText("Chaos");
            text.setTextSize(10f);
            text.setGravity(Gravity.CENTER);
            text.setSingleLine(true);
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(-1, dp(17));
            textParams.gravity = Gravity.BOTTOM;
            textParams.bottomMargin = dp(1);
            item.addView(text, textParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
            params.setMargins(dp(1), 0, dp(1), 0);
            track.addView(item, params);

            slots.put(NAV_CHAOS, item);
            icons.put(NAV_CHAOS, button);
            labels.put(NAV_CHAOS, text);
            item.setOnClickListener(v -> selectFromUser(NAV_CHAOS, v));
        }

        private void selectFromUser(int id, View source) {
            source.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (materialRouter == null) return;

            int previous = selectedId;
            materialRouter.setSelectedItemId(id);
            if (id != NAV_MORE) {
                selectedId = id;
                updateSelection(true);
            } else {
                selectedId = previous;
                pulse(source);
            }
        }

        private void installPagerCallback() {
            if (pager == null || pageCallback != null) return;
            pageCallback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    selectedId = idForPage(position);
                    updateSelection(true);
                }
            };
            pager.registerOnPageChangeCallback(pageCallback);
        }

        private void updateSelection(boolean animate) {
            if (customRoot == null) return;
            int active = Color.rgb(255, 105, 50);
            int inactive = Color.rgb(158, 158, 170);
            for (int id : new int[] {NAV_HOME, NAV_SERIES, NAV_CHAOS, NAV_CATEGORIES, NAV_MORE}) {
                View slot = slots.get(id);
                ImageView icon = icons.get(id);
                TextView label = labels.get(id);
                if (slot == null || icon == null || label == null) continue;
                boolean checked = id == selectedId;

                label.setTextColor(checked ? active : inactive);
                if (id != NAV_CHAOS) {
                    icon.setImageTintList(ColorStateList.valueOf(checked ? active : inactive));
                    slot.setBackground(checked ? pill(Color.argb(44, 255, 90, 31)) : null);
                } else {
                    icon.setBackground(circle(checked
                            ? Color.rgb(255, 92, 28)
                            : Color.rgb(244, 72, 16)));
                }

                float target = checked ? (id == NAV_CHAOS ? 1.035f : 1.02f) : 1f;
                slot.animate().cancel();
                if (animate) {
                    slot.animate().scaleX(target).scaleY(target).setDuration(135L).start();
                } else {
                    slot.setScaleX(target);
                    slot.setScaleY(target);
                }
                slot.setAlpha(checked ? 1f : 0.82f);
            }
        }

        private void pulse(View view) {
            if (view == null) return;
            view.animate().cancel();
            view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(70L).withEndAction(() ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
            ).start();
        }

        private void styleTrack() {
            if (track != null) styleTrackBackground(track);
        }

        private void styleTrackBackground(View view) {
            boolean oled = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("oled_black_enabled", true);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(oled ? Color.rgb(2, 2, 3) : Color.rgb(20, 20, 24));
            bg.setStroke(dp(1), oled ? Color.rgb(35, 35, 40) : Color.rgb(52, 52, 60));
            bg.setCornerRadius(dp(27));
            view.setBackground(bg);
            view.setElevation(dp(oled ? 7 : 10));
        }

        private GradientDrawable circle(int color) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke(dp(1), Color.argb(120, 255, 150, 110));
            return bg;
        }

        private GradientDrawable pill(int color) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(dp(20));
            return bg;
        }

        private int idForPage(int page) {
            if (page == MainPagerAdapter.PAGE_SERIES) return NAV_SERIES;
            if (page == MainPagerAdapter.PAGE_CATEGORIES) return NAV_CATEGORIES;
            if (page == MainPagerAdapter.PAGE_CHAOS) return NAV_CHAOS;
            return NAV_HOME;
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