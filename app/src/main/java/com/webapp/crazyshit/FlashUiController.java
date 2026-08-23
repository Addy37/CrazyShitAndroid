package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
 * v2.2 visual layer. Adds floating navigation, collapsing header motion, animated feed depth and
 * content-shaped loading skeletons without changing Chaos playback behavior.
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
        private View activePill;
        private View topBar;
        private TextView headerTitle;
        private TextView headerSubtitle;
        private ImageView headerLogo;
        private ImageView headerSearch;
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
            overlayRoot = field(activity, "overlayRoot", FrameLayout.class);
            topBar = findTopBar(activity);
            headerTitle = field(activity, "headerTitle", TextView.class);
            headerSubtitle = field(activity, "headerSubtitle", TextView.class);
            resolveHeaderIcons();
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
            if (activePill != null && activePill.getParent() instanceof ViewGroup) {
                ((ViewGroup) activePill.getParent()).removeView(activePill);
            }
            activePill = null;
        }

        @Override
        public void run() {
            NativeMainActivity activity = ref.get();
            if (!running || activity == null || activity.isFinishing()) return;

            if (nav == null) nav = field(activity, "bottomNavigation", BottomNavigationView.class);
            if (overlayRoot == null) overlayRoot = field(activity, "overlayRoot", FrameLayout.class);
            if (topBar == null) topBar = findTopBar(activity);

            if (nav != null && !isLandscape(activity)) {
                int selected = nav.getSelectedItemId();
                if (selected != lastSelectedId) {
                    lastSelectedId = selected;
                    animateSelected(nav, selected);
                    positionActivePill(activity, selected, true);
                    expand(activity);
                } else {
                    positionActivePill(activity, selected, false);
                }
            } else if (activePill != null) {
                activePill.setVisibility(View.GONE);
            }

            View root = activity.findViewById(android.R.id.content);
            if (root != null) scanForFeeds(activity, root, false);

            activity.getWindow().getDecorView().postDelayed(this, TICK_MS);
        }

        private void polishFloatingNav(NativeMainActivity activity) {
            if (nav == null || isLandscape(activity)) return;
            nav.setBackground(navBackground(activity));
            nav.setElevation(dp(activity, 16));
            nav.setClipToOutline(false);
            nav.setClipChildren(false);
            nav.setClipToPadding(false);
            nav.setPadding(dp(activity, 3), 0, dp(activity, 3), 0);
            if (nav.getParent() instanceof ViewGroup) {
                ((ViewGroup) nav.getParent()).setClipChildren(false);
                ((ViewGroup) nav.getParent()).setClipToPadding(false);
            }

            if (nav.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) nav.getLayoutParams();
                lp.height = dp(activity, 60);
                lp.setMargins(dp(activity, 16), dp(activity, 3), dp(activity, 16), dp(activity, 8));
                nav.setLayoutParams(lp);
            }

            nav.setItemRippleColor(ColorStateList.valueOf(Color.argb(38, 255, 90, 31)));
            styleLabels(nav);
            styleChaosButton(activity);
            ensureActivePill(activity);
            lastSelectedId = nav.getSelectedItemId();
            animateSelected(nav, lastSelectedId);
            positionActivePill(activity, lastSelectedId, false);
            nav.setOnItemReselectedListener(item -> {
                pulse(nav.findViewById(item.getItemId()), item.getItemId() == NAV_CHAOS);
                positionActivePill(activity, item.getItemId(), true);
            });
        }

        private void ensureActivePill(NativeMainActivity activity) {
            if (overlayRoot == null || activePill != null) return;
            View pill = new View(activity);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(activity, 18));
            bg.setColor(Color.argb(78, 255, 90, 31));
            bg.setStroke(dp(activity, 1), Color.argb(120, 255, 112, 60));
            pill.setBackground(bg);
            pill.setClickable(false);
            pill.setFocusable(false);
            pill.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            pill.setAlpha(1f);
            pill.setElevation(dp(activity, 17));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(activity, 68), dp(activity, 34));
            overlayRoot.addView(pill, lp);
            activePill = pill;
        }

        private void positionActivePill(NativeMainActivity activity, int selectedId, boolean animate) {
            if (nav == null || overlayRoot == null || activePill == null || isLandscape(activity)) return;
            if (selectedId == NAV_CHAOS) {
                activePill.animate().cancel();
                activePill.setVisibility(View.INVISIBLE);
                return;
            }
            View selected = nav.findViewById(selectedId);
            if (selected == null || nav.getVisibility() != View.VISIBLE) {
                activePill.setVisibility(View.INVISIBLE);
                return;
            }

            int[] rootLoc = new int[2];
            int[] itemLoc = new int[2];
            int[] navLoc = new int[2];
            overlayRoot.getLocationOnScreen(rootLoc);
            selected.getLocationOnScreen(itemLoc);
            nav.getLocationOnScreen(navLoc);
            float targetX = itemLoc[0] - rootLoc[0] + (selected.getWidth() - dp(activity, 68)) / 2f;
            float targetY = navLoc[1] - rootLoc[1] + dp(activity, 5);

            activePill.setVisibility(View.VISIBLE);
            if (!animate || activePill.getWidth() == 0) {
                activePill.setX(targetX);
                activePill.setY(targetY);
                return;
            }
            activePill.animate().cancel();
            activePill.animate()
                    .x(targetX)
                    .y(targetY)
                    .setDuration(220L)
                    .setInterpolator(new OvershootInterpolator(0.45f))
                    .start();
        }

        private void animateSelected(BottomNavigationView nav, int selectedId) {
            if (nav == null) return;
            styleLabels(nav);
            NativeMainActivity activity = ref.get();
            if (activity != null) styleChaosButton(activity);
            for (int id = 1; id <= 5; id++) {
                View child = nav.findViewById(id);
                if (child == null) continue;
                boolean selected = id == selectedId;
                child.animate().cancel();
                float scale = selected && id != NAV_CHAOS ? 1.035f : 1f;
                child.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .translationY(0f)
                        .setDuration(180L)
                        .start();
                setTextAlpha(child, selected ? 1f : 0.78f);
            }
            ImageView chaosIcon = findFirstImage(nav.findViewById(NAV_CHAOS));
            if (chaosIcon != null) {
                boolean selected = selectedId == NAV_CHAOS;
                chaosIcon.animate().cancel();
                chaosIcon.animate()
                        .scaleX(selected ? 1.86f : 1.70f)
                        .scaleY(selected ? 1.86f : 1.70f)
                        .translationY(selected ? -dp(nav, 5) : -dp(nav, 4))
                        .setDuration(190L)
                        .setInterpolator(new OvershootInterpolator(0.35f))
                        .start();
            }
        }

        private void styleChaosButton(NativeMainActivity activity) {
            if (nav == null) return;
            View chaosItem = nav.findViewById(NAV_CHAOS);
            ImageView icon = findFirstImage(chaosItem);
            if (icon == null) return;
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.rgb(255, 90, 31));
            circle.setStroke(dp(activity, 1), Color.rgb(255, 132, 86));
            icon.setBackground(circle);
            icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            icon.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
            icon.setElevation(dp(activity, 5));
        }

        private void styleLabels(BottomNavigationView nav) {
            for (int id = 1; id <= 5; id++) {
                View item = nav.findViewById(id);
                if (item == null) continue;
                List<TextView> labels = new ArrayList<>();
                collectTextViews(item, labels);
                for (TextView label : labels) {
                    label.setTextSize(11.5f);
                }
            }
        }

        private void setTextAlpha(View item, float alpha) {
            List<TextView> labels = new ArrayList<>();
            collectTextViews(item, labels);
            for (TextView label : labels) label.setAlpha(alpha);
        }

        private void pulse(View view, boolean stronger) {
            if (view == null) return;
            float up = stronger ? 1.08f : 1.09f;
            view.animate().cancel();
            view.animate().scaleX(up).scaleY(up).setDuration(85L).withEndAction(() -> {
                view.animate().scaleX(1f).scaleY(1f).setDuration(125L).start();
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
                float scale = 1f - (distance * 0.014f);
                child.setScaleX(scale);
                child.setScaleY(scale);
                child.setAlpha(1f - (distance * 0.06f));
            }
        }

        private void collapse(NativeMainActivity activity) {
            if (collapsed || topBar == null || topBar.getVisibility() != View.VISIBLE || isLandscape(activity)) return;
            collapsed = true;
            animateHeader(activity, dp(activity, 52), 175L, true);
        }

        private void expand(NativeMainActivity activity) {
            if (!collapsed || topBar == null) return;
            collapsed = false;
            animateHeader(activity, dp(activity, 70), 195L, false);
        }

        private void animateHeader(NativeMainActivity activity, int targetHeight, long duration, boolean compact) {
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

            if (headerSubtitle != null) {
                headerSubtitle.animate().cancel();
                headerSubtitle.animate()
                        .alpha(compact ? 0f : 1f)
                        .translationY(compact ? -dp(activity, 4) : 0f)
                        .setDuration(compact ? 130L : duration)
                        .start();
            }
            if (headerLogo != null) {
                headerLogo.animate().cancel();
                headerLogo.animate()
                        .scaleX(compact ? 0.80f : 1f)
                        .scaleY(compact ? 0.80f : 1f)
                        .alpha(compact ? 0.88f : 1f)
                        .setDuration(duration)
                        .start();
            }
            if (headerTitle != null) {
                headerTitle.animate().cancel();
                headerTitle.animate()
                        .scaleX(compact ? 0.95f : 1f)
                        .scaleY(compact ? 0.95f : 1f)
                        .setDuration(duration)
                        .start();
            }
            if (headerSearch != null) {
                headerSearch.animate().cancel();
                headerSearch.animate()
                        .scaleX(compact ? 0.92f : 1f)
                        .scaleY(compact ? 0.92f : 1f)
                        .setDuration(duration)
                        .start();
            }
        }

        private void resolveHeaderIcons() {
            if (!(topBar instanceof ViewGroup)) return;
            List<ImageView> images = new ArrayList<>();
            collectImages(topBar, images);
            if (!images.isEmpty()) headerLogo = images.get(0);
            if (images.size() > 1) headerSearch = images.get(images.size() - 1);
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

    private static void collectImages(View view, List<ImageView> out) {
        if (view instanceof ImageView) {
            out.add((ImageView) view);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collectImages(group.getChildAt(i), out);
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
        bg.setCornerRadius(dp(activity, 22));
        bg.setColor(Color.argb(246, 23, 23, 27));
        bg.setStroke(dp(activity, 1), Color.rgb(49, 49, 56));
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
