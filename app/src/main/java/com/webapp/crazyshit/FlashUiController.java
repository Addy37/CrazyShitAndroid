package com.webapp.crazyshit;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Native-shell visual polish. The 2.8 feed header is static; this controller no longer compacts
 * or expands the top bar in response to scrolling.
 */
final class FlashUiController {
    private static final int NAV_CHAOS = 4;
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
        private FrameLayout overlayRoot;
        private View activeIndicator;
        private ImageView chaosFab;
        private ImageView hiddenChaosIcon;
        private View topBar;
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
            overlayRoot = field(activity, "overlayRoot", FrameLayout.class);
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
            removeOverlay(activeIndicator);
            removeOverlay(chaosFab);
            activeIndicator = null;
            chaosFab = null;
            hiddenChaosIcon = null;
        }

        @Override
        public void run() {
            NativeMainActivity activity = ref.get();
            if (!running || activity == null || activity.isFinishing()) return;

            if (nav == null) nav = field(activity, "bottomNavigation", BottomNavigationView.class);
            if (overlayRoot == null) overlayRoot = field(activity, "overlayRoot", FrameLayout.class);
            if (topBar == null) topBar = findTopBar(activity);

            if (nav != null && !isLandscape(activity)) {
                hideBuiltInChaosIcon();
                ensureActiveIndicator(activity);
                ensureChaosFab(activity);
                int selected = nav.getSelectedItemId();
                if (selected != lastSelectedId) {
                    lastSelectedId = selected;
                    animateSelected(nav, selected);
                    positionActiveIndicator(activity, selected, true);
                    positionChaosFab(activity, selected, true);
                } else {
                    positionActiveIndicator(activity, selected, false);
                    positionChaosFab(activity, selected, false);
                }
            } else {
                if (activeIndicator != null) activeIndicator.setVisibility(View.GONE);
                if (chaosFab != null) chaosFab.setVisibility(View.GONE);
            }

            View root = activity.findViewById(android.R.id.content);
            if (root != null) scanForFeeds(activity, root, false);

            activity.getWindow().getDecorView().postDelayed(this, TICK_MS);
        }

        private void polishFloatingNav(NativeMainActivity activity) {
            if (nav == null || isLandscape(activity)) return;
            nav.setBackground(navBackground(activity));
            nav.setElevation(dp(activity, 14));
            nav.setClipToOutline(false);
            nav.setClipChildren(false);
            nav.setClipToPadding(false);
            nav.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
            nav.setItemIconSize(dp(activity, 21));
            nav.setItemRippleColor(ColorStateList.valueOf(Color.argb(32, 251, 245, 6)));

            if (nav.getParent() instanceof ViewGroup) {
                ((ViewGroup) nav.getParent()).setClipChildren(false);
                ((ViewGroup) nav.getParent()).setClipToPadding(false);
            }

            if (nav.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) nav.getLayoutParams();
                lp.height = dp(activity, 56);
                lp.setMargins(dp(activity, 18), dp(activity, 5), dp(activity, 18), dp(activity, 12));
                nav.setLayoutParams(lp);
            }

            styleLabels(nav);
            hideBuiltInChaosIcon();
            ensureActiveIndicator(activity);
            ensureChaosFab(activity);
            lastSelectedId = nav.getSelectedItemId();
            animateSelected(nav, lastSelectedId);
            positionActiveIndicator(activity, lastSelectedId, false);
            positionChaosFab(activity, lastSelectedId, false);
            nav.setOnItemReselectedListener(item -> {
                if (item.getItemId() == NAV_CHAOS) pulseChaosFab();
                else pulse(nav.findViewById(item.getItemId()));
                positionActiveIndicator(activity, item.getItemId(), true);
            });
        }

        private void ensureActiveIndicator(NativeMainActivity activity) {
            if (overlayRoot == null || activeIndicator != null) return;
            View indicator = new View(activity);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(activity, 2));
            bg.setColor(UiPalette.PRIMARY);
            indicator.setBackground(bg);
            indicator.setClickable(false);
            indicator.setFocusable(false);
            indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            indicator.setElevation(dp(activity, 18));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(activity, 24), dp(activity, 3));
            overlayRoot.addView(indicator, lp);
            activeIndicator = indicator;
        }

        private void positionActiveIndicator(NativeMainActivity activity, int selectedId, boolean animate) {
            if (nav == null || overlayRoot == null || activeIndicator == null || isLandscape(activity)) return;
            if (selectedId == NAV_CHAOS) {
                activeIndicator.animate().cancel();
                activeIndicator.setVisibility(View.INVISIBLE);
                return;
            }
            View selected = nav.findViewById(selectedId);
            if (selected == null || nav.getVisibility() != View.VISIBLE) {
                activeIndicator.setVisibility(View.INVISIBLE);
                return;
            }

            int[] rootLoc = new int[2];
            int[] itemLoc = new int[2];
            int[] navLoc = new int[2];
            overlayRoot.getLocationOnScreen(rootLoc);
            selected.getLocationOnScreen(itemLoc);
            nav.getLocationOnScreen(navLoc);
            float targetX = itemLoc[0] - rootLoc[0] + (selected.getWidth() - dp(activity, 24)) / 2f;
            float targetY = navLoc[1] - rootLoc[1] + dp(activity, 31);

            activeIndicator.setVisibility(View.VISIBLE);
            activeIndicator.setY(targetY);
            if (!animate || activeIndicator.getWidth() == 0) {
                activeIndicator.setX(targetX);
                return;
            }
            activeIndicator.animate().cancel();
            activeIndicator.animate()
                    .x(targetX)
                    .setDuration(185L)
                    .start();
        }

        private void ensureChaosFab(NativeMainActivity activity) {
            if (overlayRoot == null || chaosFab != null) return;
            ImageView fab = new ImageView(activity);
            fab.setImageResource(R.drawable.ic_nav_chaos);
            fab.setImageTintList(ColorStateList.valueOf(UiPalette.ON_PRIMARY));
            fab.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            fab.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
            fab.setBackground(chaosFabBackground(activity, false));
            fab.setElevation(dp(activity, 22));
            fab.setClickable(true);
            fab.setFocusable(true);
            fab.setContentDescription("Chaos");
            fab.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (nav != null) nav.setSelectedItemId(NAV_CHAOS);
                pulseChaosFab();
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(activity, 54), dp(activity, 54));
            overlayRoot.addView(fab, lp);
            chaosFab = fab;
        }

        private void positionChaosFab(NativeMainActivity activity, int selectedId, boolean animate) {
            if (nav == null || overlayRoot == null || chaosFab == null || isLandscape(activity)) return;
            View chaosItem = nav.findViewById(NAV_CHAOS);
            if (chaosItem == null || nav.getVisibility() != View.VISIBLE) {
                chaosFab.setVisibility(View.INVISIBLE);
                return;
            }

            int[] rootLoc = new int[2];
            int[] itemLoc = new int[2];
            int[] navLoc = new int[2];
            overlayRoot.getLocationOnScreen(rootLoc);
            chaosItem.getLocationOnScreen(itemLoc);
            nav.getLocationOnScreen(navLoc);
            float targetX = itemLoc[0] - rootLoc[0] + (chaosItem.getWidth() - dp(activity, 54)) / 2f;
            float targetY = navLoc[1] - rootLoc[1] - dp(activity, 14);

            chaosFab.setVisibility(View.VISIBLE);
            chaosFab.setX(targetX);
            chaosFab.setY(targetY);
            boolean selected = selectedId == NAV_CHAOS;
            chaosFab.setBackground(chaosFabBackground(activity, selected));
            chaosFab.animate().cancel();
            if (animate) {
                chaosFab.setScaleX(selected ? 0.96f : 1.04f);
                chaosFab.setScaleY(selected ? 0.96f : 1.04f);
            }
            chaosFab.animate()
                    .scaleX(selected ? 1.08f : 1f)
                    .scaleY(selected ? 1.08f : 1f)
                    .setDuration(selected ? 210L : 160L)
                    .setInterpolator(new OvershootInterpolator(selected ? 0.45f : 0.15f))
                    .start();
        }

        private void hideBuiltInChaosIcon() {
            if (nav == null) return;
            if (hiddenChaosIcon == null) hiddenChaosIcon = findFirstImage(nav.findViewById(NAV_CHAOS));
            if (hiddenChaosIcon == null) return;
            hiddenChaosIcon.animate().cancel();
            hiddenChaosIcon.setAlpha(0f);
            hiddenChaosIcon.setBackground(null);
            hiddenChaosIcon.setScaleX(1f);
            hiddenChaosIcon.setScaleY(1f);
            hiddenChaosIcon.setTranslationY(0f);
            hiddenChaosIcon.setElevation(0f);
        }

        private void animateSelected(BottomNavigationView nav, int selectedId) {
            if (nav == null) return;
            styleLabels(nav);
            hideBuiltInChaosIcon();
            for (int id = 1; id <= 5; id++) {
                View child = nav.findViewById(id);
                if (child == null) continue;
                boolean selected = id == selectedId;
                child.animate().cancel();
                float scale = selected && id != NAV_CHAOS ? 1.025f : 1f;
                child.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .translationY(0f)
                        .setDuration(155L)
                        .start();
                setTextAlpha(child, selected ? 1f : 0.70f);
            }
            NativeMainActivity activity = ref.get();
            if (activity != null) positionChaosFab(activity, selectedId, true);
        }

        private void styleLabels(BottomNavigationView nav) {
            for (int id = 1; id <= 5; id++) {
                View item = nav.findViewById(id);
                if (item == null) continue;
                List<TextView> labels = new ArrayList<>();
                collectTextViews(item, labels);
                for (TextView label : labels) {
                    label.setTextSize(id == NAV_CHAOS ? 10.5f : 10.25f);
                }
            }
        }

        private void setTextAlpha(View item, float alpha) {
            List<TextView> labels = new ArrayList<>();
            collectTextViews(item, labels);
            for (TextView label : labels) label.setAlpha(alpha);
        }

        private void pulse(View view) {
            if (view == null) return;
            view.animate().cancel();
            view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80L).withEndAction(() -> {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
            }).start();
        }

        private void pulseChaosFab() {
            if (chaosFab == null) return;
            boolean selected = nav != null && nav.getSelectedItemId() == NAV_CHAOS;
            float base = selected ? 1.08f : 1f;
            chaosFab.animate().cancel();
            chaosFab.animate().scaleX(base + 0.08f).scaleY(base + 0.08f).setDuration(85L).withEndAction(() -> {
                chaosFab.animate()
                        .scaleX(base)
                        .scaleY(base)
                        .setDuration(135L)
                        .setInterpolator(new OvershootInterpolator(0.25f))
                        .start();
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
                float scale = 1f - (distance * 0.014f);
                child.setScaleX(scale);
                child.setScaleY(scale);
                child.setAlpha(1f - (distance * 0.06f));
            }
        }
    }

    private static void removeOverlay(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private static View findTopBar(NativeMainActivity activity) {
        View title = field(activity, "headerTitle", View.class);
        if (title == null || !(title.getParent() instanceof View)) return null;
        View labels = (View) title.getParent();
        return labels.getParent() instanceof View ? (View) labels.getParent() : labels;
    }

    private static ImageView findFirstImage(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ImageView found = findFirstImage(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static void collectTextViews(View view, List<TextView> out) {
        if (view instanceof TextView) {
            out.add((TextView) view);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectTextViews(group.getChildAt(i), out);
    }

    private static GradientDrawable navBackground(NativeMainActivity activity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(activity, 20));
        bg.setColor(Color.argb(248, 22, 22, 26));
        bg.setStroke(dp(activity, 1), Color.rgb(42, 42, 48));
        return bg;
    }

    private static GradientDrawable chaosFabBackground(NativeMainActivity activity, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(selected ? UiPalette.PRIMARY : UiPalette.PRIMARY_DIM);
        bg.setStroke(dp(activity, 1), selected ? Color.rgb(255, 253, 140) : UiPalette.PRIMARY);
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
