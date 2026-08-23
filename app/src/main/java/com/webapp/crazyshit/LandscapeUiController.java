package com.webapp.crazyshit;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Responsive landscape treatment for the native app shell.
 *
 * Portrait stays untouched. Landscape replaces the tall bottom navigation with a slim
 * left rail, compacts the top bar, and renders native feeds as a denser grid. Chaos keeps
 * its existing immersive fullscreen behavior and hides the adaptive rail entirely.
 */
final class LandscapeUiController {
    private static final int NAV_HOME = 1;
    private static final int NAV_TRENDING = 2;
    private static final int NAV_MEMES = 3;
    private static final int NAV_CHAOS = 4;
    private static final int NAV_MORE = 5;

    private static final int RAIL_WIDTH_DP = 68;
    private static final int LANDSCAPE_TOP_BAR_DP = 52;
    private static final int PORTRAIT_TOP_BAR_DP = 70;

    private static final Map<NativeMainActivity, State> STATES = new WeakHashMap<>();

    private LandscapeUiController() {
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State state = STATES.get(activity);
        if (state == null) {
            state = new State();
            STATES.put(activity, state);
        }

        if (state.globalLayoutListener == null) {
            final State attachedState = state;
            state.globalLayoutListener = () -> applyInternal(activity, attachedState);
            View content = activity.findViewById(android.R.id.content);
            if (content != null) {
                state.observedRoot = content;
                content.getViewTreeObserver().addOnGlobalLayoutListener(state.globalLayoutListener);
            }
        }

        View decor = activity.getWindow().getDecorView();
        decor.post(() -> apply(activity));
    }

    static void detach(NativeMainActivity activity) {
        State state = STATES.remove(activity);
        if (state == null || state.observedRoot == null || state.globalLayoutListener == null) return;
        ViewTreeObserver observer = state.observedRoot.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnGlobalLayoutListener(state.globalLayoutListener);
    }

    static void apply(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State state = STATES.get(activity);
        if (state == null) {
            attach(activity);
            return;
        }
        applyInternal(activity, state);
    }

    private static void applyInternal(NativeMainActivity activity, State state) {
        if (!bindExistingShell(activity, state)) return;

        boolean landscape = isLandscape(activity);
        int selected = state.bottomNavigation.getSelectedItemId();
        boolean chaosFullscreen = landscape && selected == NAV_CHAOS;

        ensureRail(activity, state);
        adaptTopBar(activity, state, landscape, chaosFullscreen);
        adaptNavigation(activity, state, landscape, chaosFullscreen, selected);
        adaptFeedLayouts(activity, state, landscape);
        installAdaptiveInsets(activity, state);
    }

    private static boolean bindExistingShell(NativeMainActivity activity, State state) {
        if (state.bottomNavigation != null && state.shell != null && state.overlayRoot != null) return true;

        View content = activity.findViewById(android.R.id.content);
        BottomNavigationView bottom = findFirst(content, BottomNavigationView.class);
        if (bottom == null || !(bottom.getParent() instanceof ViewGroup)) return false;

        ViewGroup shell = (ViewGroup) bottom.getParent();
        if (!(shell.getParent() instanceof ViewGroup)) return false;

        state.bottomNavigation = bottom;
        state.shell = shell;
        state.overlayRoot = (ViewGroup) shell.getParent();
        state.topBar = shell.getChildCount() > 0 ? shell.getChildAt(0) : null;
        return true;
    }

    private static void ensureRail(NativeMainActivity activity, State state) {
        if (state.rail != null) return;

        LinearLayout rail = new LinearLayout(activity);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackgroundColor(Color.rgb(17, 17, 20));
        rail.setElevation(dp(activity, 12));
        rail.setVisibility(View.GONE);
        rail.setContentDescription("Landscape navigation");

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        logoParams.setMargins(0, dp(activity, 8), 0, dp(activity, 4));
        rail.addView(logo, logoParams);

        LinearLayout menu = new LinearLayout(activity);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
        rail.addView(menu, new LinearLayout.LayoutParams(-1, 0, 1f));

        addRailButton(activity, state, menu, NAV_HOME, "Home", R.drawable.ic_nav_home);
        addRailButton(activity, state, menu, NAV_TRENDING, "Trending", R.drawable.ic_nav_trending);
        addRailButton(activity, state, menu, NAV_CHAOS, "Chaos", R.drawable.ic_nav_chaos);
        addRailButton(activity, state, menu, NAV_MEMES, "Memes", R.drawable.ic_nav_memes);
        addRailButton(activity, state, menu, NAV_MORE, "More", R.drawable.ic_nav_more);

        if (state.overlayRoot instanceof FrameLayout) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    dp(activity, RAIL_WIDTH_DP),
                    -1
            );
            params.gravity = Gravity.START;
            state.overlayRoot.addView(rail, params);
        } else {
            state.overlayRoot.addView(rail, new ViewGroup.LayoutParams(dp(activity, RAIL_WIDTH_DP), -1));
        }

        state.rail = rail;
        state.railWidth = dp(activity, RAIL_WIDTH_DP);
        activity.getWindow().getDecorView().requestApplyInsets();
    }

    private static void addRailButton(
            NativeMainActivity activity,
            State state,
            LinearLayout menu,
            int id,
            String label,
            int iconRes
    ) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(8);
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablePadding(dp(activity, 2));
        button.setContentDescription(label);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(activity, 3), dp(activity, 4), dp(activity, 3), dp(activity, 3));

        Drawable icon = activity.getDrawable(iconRes);
        if (icon != null) {
            icon = icon.mutate();
            int iconSize = dp(activity, id == NAV_CHAOS ? 25 : 23);
            icon.setBounds(0, 0, iconSize, iconSize);
        }
        button.setCompoundDrawables(null, icon, null, null);
        if (id == NAV_CHAOS) {
            button.setScaleX(1.04f);
            button.setScaleY(1.04f);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 60), dp(activity, 52));
        params.setMargins(0, dp(activity, 1), 0, dp(activity, 1));
        menu.addView(button, params);
        state.railButtons.put(id, button);

        button.setOnClickListener(v -> {
            if (state.bottomNavigation == null) return;
            state.bottomNavigation.setSelectedItemId(id);
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.postDelayed(() -> {
                if (state.bottomNavigation == null) return;
                updateRailSelection(activity, state, state.bottomNavigation.getSelectedItemId());
                apply(activity);
            }, 40L);
        });
    }

    private static void adaptNavigation(
            NativeMainActivity activity,
            State state,
            boolean landscape,
            boolean chaosFullscreen,
            int selected
    ) {
        if (state.bottomNavigation == null || state.rail == null) return;

        if (chaosFullscreen) {
            setVisibility(state.bottomNavigation, View.GONE);
            setVisibility(state.rail, View.GONE);
            setShellStartMargin(state, 0);
        } else if (landscape) {
            setVisibility(state.bottomNavigation, View.GONE);
            setVisibility(state.rail, View.VISIBLE);
            setShellStartMargin(state, state.railWidth > 0 ? state.railWidth : dp(activity, RAIL_WIDTH_DP));
        } else {
            setVisibility(state.rail, View.GONE);
            setVisibility(state.bottomNavigation, View.VISIBLE);
            setShellStartMargin(state, 0);
        }

        updateRailSelection(activity, state, selected);
    }

    private static void updateRailSelection(NativeMainActivity activity, State state, int selected) {
        int active = Color.rgb(255, 112, 60);
        int inactive = Color.rgb(165, 165, 176);
        for (Map.Entry<Integer, TextView> entry : state.railButtons.entrySet()) {
            boolean checked = entry.getKey() == selected;
            TextView button = entry.getValue();
            int color = checked ? active : inactive;
            button.setTextColor(color);
            Drawable[] drawables = button.getCompoundDrawables();
            Drawable top = drawables.length > 1 ? drawables[1] : null;
            if (top != null) top.setTint(color);
            button.setBackground(railPill(activity, checked));
        }
    }

    private static GradientDrawable railPill(NativeMainActivity activity, boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(activity, 18));
        background.setColor(selected ? Color.argb(95, 255, 90, 31) : Color.TRANSPARENT);
        return background;
    }

    private static void adaptTopBar(
            NativeMainActivity activity,
            State state,
            boolean landscape,
            boolean chaosFullscreen
    ) {
        View topBar = state.topBar;
        if (topBar == null) return;

        setVisibility(topBar, chaosFullscreen ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams raw = topBar.getLayoutParams();
        if (raw != null) {
            int wanted = dp(activity, landscape ? LANDSCAPE_TOP_BAR_DP : PORTRAIT_TOP_BAR_DP);
            if (raw.height != wanted) {
                raw.height = wanted;
                topBar.setLayoutParams(raw);
            }
        }

        if (!(topBar instanceof LinearLayout)) return;
        LinearLayout bar = (LinearLayout) topBar;
        bar.setPadding(
                dp(activity, landscape ? 8 : 14),
                dp(activity, landscape ? 0 : 7),
                dp(activity, landscape ? 6 : 10),
                dp(activity, landscape ? 0 : 7)
        );

        if (bar.getChildCount() > 0 && bar.getChildAt(0) instanceof ImageView) {
            ImageView icon = (ImageView) bar.getChildAt(0);
            setVisibility(icon, landscape ? View.GONE : View.VISIBLE);
            setSize(icon, dp(activity, 52), dp(activity, 52));
        }

        if (bar.getChildCount() > 1 && bar.getChildAt(1) instanceof LinearLayout) {
            LinearLayout labels = (LinearLayout) bar.getChildAt(1);
            labels.setPadding(dp(activity, landscape ? 8 : 10), 0, dp(activity, 8), 0);
            if (labels.getChildCount() > 0 && labels.getChildAt(0) instanceof TextView) {
                ((TextView) labels.getChildAt(0)).setTextSize(landscape ? 18 : 19);
            }
            if (labels.getChildCount() > 1 && labels.getChildAt(1) instanceof TextView) {
                TextView subtitle = (TextView) labels.getChildAt(1);
                setVisibility(subtitle, landscape ? View.GONE : View.VISIBLE);
            }
        }

        if (bar.getChildCount() > 2 && bar.getChildAt(bar.getChildCount() - 1) instanceof ImageView) {
            ImageView search = (ImageView) bar.getChildAt(bar.getChildCount() - 1);
            int size = dp(activity, landscape ? 44 : 52);
            setSize(search, size, size);
            int pad = dp(activity, landscape ? 10 : 12);
            search.setPadding(pad, pad, pad, pad);
        }
    }

    private static void adaptFeedLayouts(NativeMainActivity activity, State state, boolean landscape) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        List<RecyclerView> recyclers = new ArrayList<>();
        collectRecyclerViews(content, recyclers);
        int widthDp = activity.getResources().getConfiguration().screenWidthDp;
        int feedColumns = widthDp >= 900 ? 3 : 2;
        int categoryColumns = widthDp >= 900 ? 4 : 3;

        for (RecyclerView recycler : recyclers) {
            RecyclerView.Adapter<?> rawAdapter = recycler.getAdapter();
            if (rawAdapter instanceof NativeFeedAdapter) {
                NativeFeedAdapter adapter = (NativeFeedAdapter) rawAdapter;
                if (landscape) {
                    if (adapter.getViewMode() != NativeFeedAdapter.VIEW_GRID) {
                        state.originalFeedModes.put(adapter, adapter.getViewMode());
                        adapter.setViewMode(NativeFeedAdapter.VIEW_GRID);
                    }
                    useGrid(activity, recycler, feedColumns);
                    setRecyclerPadding(activity, recycler, 5, 4, 5, 10);
                } else {
                    Integer restore = state.originalFeedModes.remove(adapter);
                    if (restore != null && adapter.getViewMode() != restore) adapter.setViewMode(restore);
                    int mode = restore == null ? adapter.getViewMode() : restore;
                    if (mode == NativeFeedAdapter.VIEW_GRID) useGrid(activity, recycler, 2);
                    else useLinear(activity, recycler);
                    setRecyclerPadding(activity, recycler, 0, 5, 0, 18);
                }
            } else if (rawAdapter instanceof NativeCategoryAdapter) {
                useGrid(activity, recycler, landscape ? categoryColumns : 2);
            }
        }
    }

    private static void useGrid(NativeMainActivity activity, RecyclerView recycler, int spanCount) {
        RecyclerView.LayoutManager manager = recycler.getLayoutManager();
        if (manager instanceof GridLayoutManager && ((GridLayoutManager) manager).getSpanCount() == spanCount) return;
        int position = firstVisible(manager);
        recycler.setLayoutManager(new GridLayoutManager(activity, spanCount));
        if (recycler.getAdapter() != null && recycler.getAdapter().getItemCount() > 0) {
            recycler.scrollToPosition(Math.min(position, recycler.getAdapter().getItemCount() - 1));
        }
    }

    private static void useLinear(NativeMainActivity activity, RecyclerView recycler) {
        RecyclerView.LayoutManager manager = recycler.getLayoutManager();
        if (manager instanceof LinearLayoutManager && !(manager instanceof GridLayoutManager)) return;
        int position = firstVisible(manager);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        if (recycler.getAdapter() != null && recycler.getAdapter().getItemCount() > 0) {
            recycler.scrollToPosition(Math.min(position, recycler.getAdapter().getItemCount() - 1));
        }
    }

    private static int firstVisible(RecyclerView.LayoutManager manager) {
        if (manager instanceof LinearLayoutManager) {
            return Math.max(0, ((LinearLayoutManager) manager).findFirstVisibleItemPosition());
        }
        return 0;
    }

    private static void setRecyclerPadding(
            NativeMainActivity activity,
            RecyclerView recycler,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int l = dp(activity, left);
        int t = dp(activity, top);
        int r = dp(activity, right);
        int b = dp(activity, bottom);
        if (recycler.getPaddingLeft() == l && recycler.getPaddingTop() == t &&
                recycler.getPaddingRight() == r && recycler.getPaddingBottom() == b) return;
        recycler.setPadding(l, t, r, b);
    }

    private static void installAdaptiveInsets(NativeMainActivity activity, State state) {
        if (state.insetsInstalled || state.shell == null) return;
        state.insetsInstalled = true;
        state.shell.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }

            boolean landscape = isLandscape(activity);
            boolean chaosFullscreen = landscape && state.bottomNavigation != null &&
                    state.bottomNavigation.getSelectedItemId() == NAV_CHAOS;
            view.setPadding(landscape && !chaosFullscreen ? 0 : left, top, right, bottom);

            if (state.rail != null) {
                state.rail.setPadding(left, top, 0, bottom);
                int width = dp(activity, RAIL_WIDTH_DP) + left;
                state.railWidth = width;
                ViewGroup.LayoutParams railRaw = state.rail.getLayoutParams();
                if (railRaw != null && railRaw.width != width) {
                    railRaw.width = width;
                    state.rail.setLayoutParams(railRaw);
                }
                if (landscape && !chaosFullscreen) setShellStartMargin(state, width);
            }
            return insets;
        });
        activity.getWindow().getDecorView().requestApplyInsets();
    }

    private static void setShellStartMargin(State state, int margin) {
        if (state.shell == null) return;
        ViewGroup.LayoutParams raw = state.shell.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
        if (params.getMarginStart() == margin) return;
        params.setMarginStart(margin);
        state.shell.setLayoutParams(params);
    }

    private static void setSize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || (params.width == width && params.height == height)) return;
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    private static void setVisibility(View view, int visibility) {
        if (view != null && view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    private static boolean isLandscape(NativeMainActivity activity) {
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static void collectRecyclerViews(View view, List<RecyclerView> out) {
        if (view instanceof RecyclerView) out.add((RecyclerView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectRecyclerViews(group.getChildAt(i), out);
        }
    }

    private static <T extends View> T findFirst(View view, Class<T> type) {
        if (view == null) return null;
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T found = findFirst(group.getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class State {
        BottomNavigationView bottomNavigation;
        ViewGroup shell;
        ViewGroup overlayRoot;
        View topBar;
        LinearLayout rail;
        int railWidth;
        boolean insetsInstalled;
        View observedRoot;
        ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
        final Map<Integer, TextView> railButtons = new HashMap<>();
        final Map<NativeFeedAdapter, Integer> originalFeedModes = new WeakHashMap<>();
    }
}
