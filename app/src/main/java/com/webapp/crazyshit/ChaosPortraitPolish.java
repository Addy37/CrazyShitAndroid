package com.webapp.crazyshit;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps useful Chaos chrome visible in portrait and transparent chrome containers from stealing
 * touches meant for the video.
 *
 * 2.8 removes the old 180 ms polling loop. Chaos is now refreshed from pager selection and child
 * attach events, with a few finite startup passes to bind views after configuration changes.
 */
final class ChaosPortraitPolish {
    private static final Map<NativeMainActivity, State> STATES = new WeakHashMap<>();

    private ChaosPortraitPolish() {
    }

    static void start(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State old = STATES.remove(activity);
        if (old != null) old.detach();

        State state = new State(activity);
        STATES.put(activity, state);
        state.start();
    }

    static void stop(NativeMainActivity activity) {
        State state = STATES.remove(activity);
        if (state != null) state.detach();
    }

    private static final class State {
        private final WeakReference<NativeMainActivity> activityRef;
        private final List<FeedBinding> bindings = new ArrayList<>();
        private boolean wasPortrait;
        private boolean active = true;

        State(NativeMainActivity activity) {
            activityRef = new WeakReference<>(activity);
            wasPortrait = isPortrait(activity);
        }

        void start() {
            NativeMainActivity activity = activityRef.get();
            if (!active || activity == null || activity.isFinishing()) return;
            View decor = activity.getWindow().getDecorView();
            decor.post(this::bindAndRefresh);
            decor.postDelayed(this::bindAndRefresh, 120L);
            decor.postDelayed(this::bindAndRefresh, 360L);
        }

        void bindAndRefresh() {
            NativeMainActivity activity = activityRef.get();
            if (!active || activity == null || activity.isFinishing()) return;

            View content = activity.findViewById(android.R.id.content);
            if (content == null) return;

            List<ChaosFeedView> feeds = new ArrayList<>();
            collectChaosFeeds(content, feeds);
            for (ChaosFeedView feed : feeds) ensureBinding(feed);

            boolean portrait = isPortrait(activity);
            for (FeedBinding binding : new ArrayList<>(bindings)) {
                binding.refresh(portrait, wasPortrait);
            }
            wasPortrait = portrait;
        }

        private void ensureBinding(ChaosFeedView feed) {
            if (!active || feed == null) return;
            for (FeedBinding binding : bindings) {
                if (binding.feed == feed) return;
            }
            FeedBinding binding = new FeedBinding(this, feed);
            bindings.add(binding);
            binding.attach();
        }

        void detach() {
            active = false;
            for (FeedBinding binding : new ArrayList<>(bindings)) binding.detach();
            bindings.clear();
        }
    }

    private static final class FeedBinding {
        private final State owner;
        private final ChaosFeedView feed;
        private ViewPager2 pager;
        private RecyclerView pagerRecycler;
        private ViewPager2.OnPageChangeCallback pageCallback;
        private RecyclerView.OnChildAttachStateChangeListener childAttachListener;

        FeedBinding(State owner, ChaosFeedView feed) {
            this.owner = owner;
            this.feed = feed;
        }

        void attach() {
            pager = fieldValue(feed, "pager", ViewPager2.class);
            if (pager != null) {
                pageCallback = new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        feed.post(owner::bindAndRefresh);
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {
                        if (state == ViewPager2.SCROLL_STATE_IDLE) {
                            feed.post(owner::bindAndRefresh);
                        }
                    }
                };
                pager.registerOnPageChangeCallback(pageCallback);

                if (pager.getChildCount() > 0 && pager.getChildAt(0) instanceof RecyclerView) {
                    pagerRecycler = (RecyclerView) pager.getChildAt(0);
                    childAttachListener = new RecyclerView.OnChildAttachStateChangeListener() {
                        @Override
                        public void onChildViewAttachedToWindow(View view) {
                            feed.post(owner::bindAndRefresh);
                        }

                        @Override
                        public void onChildViewDetachedFromWindow(View view) {
                        }
                    };
                    pagerRecycler.addOnChildAttachStateChangeListener(childAttachListener);
                }
            }
            feed.post(owner::bindAndRefresh);
        }

        void refresh(boolean portrait, boolean previouslyPortrait) {
            purgeNonMediaRows(feed);

            List<PlayerView> views = new ArrayList<>();
            collectChaosPlayers(feed, true, views);
            for (PlayerView playerView : views) {
                makeEmptyChromePassThrough(playerView);
            }
            if (portrait) {
                for (PlayerView playerView : views) keepChromeVisible(playerView);
            } else if (previouslyPortrait) {
                for (PlayerView playerView : views) restoreLandscapeAutoHide(playerView);
            }
        }

        void detach() {
            if (pager != null && pageCallback != null) {
                pager.unregisterOnPageChangeCallback(pageCallback);
            }
            if (pagerRecycler != null && childAttachListener != null) {
                pagerRecycler.removeOnChildAttachStateChangeListener(childAttachListener);
            }
            pager = null;
            pagerRecycler = null;
            pageCallback = null;
            childAttachListener = null;
        }
    }

    private static boolean isPortrait(NativeMainActivity activity) {
        return activity.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
    }

    private static void collectChaosFeeds(View view, List<ChaosFeedView> out) {
        if (view instanceof ChaosFeedView) out.add((ChaosFeedView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectChaosFeeds(group.getChildAt(i), out);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void purgeNonMediaRows(ChaosFeedView feed) {
        if (feed == null) return;

        List rawItems = fieldValue(feed, "items", List.class);
        RecyclerView.Adapter adapter = fieldValue(feed, "adapter", RecyclerView.Adapter.class);
        ViewPager2 pager = fieldValue(feed, "pager", ViewPager2.class);
        if (rawItems == null || adapter == null) return;

        RecyclerView rv = null;
        if (pager != null && pager.getChildCount() > 0 && pager.getChildAt(0) instanceof RecyclerView) {
            rv = (RecyclerView) pager.getChildAt(0);
        }
        if (rv != null && rv.isComputingLayout()) {
            feed.post(() -> purgeNonMediaRows(feed));
            return;
        }

        int current = pager == null ? 0 : pager.getCurrentItem();
        int removedBeforeCurrent = 0;
        boolean changed = false;

        for (int i = rawItems.size() - 1; i >= 0; i--) {
            Object raw = rawItems.get(i);
            boolean media = raw instanceof NativeContentItem
                    && NativeContentItem.KIND_MEDIA.equals(((NativeContentItem) raw).kind);
            if (media) continue;
            if (i < current) removedBeforeCurrent++;
            rawItems.remove(i);
            changed = true;
        }

        if (!changed) return;

        adapter.notifyDataSetChanged();
        if (rawItems.isEmpty()) {
            setIntField(feed, "selectedPosition", 0);
            return;
        }

        int target = Math.max(0, Math.min(rawItems.size() - 1, current - removedBeforeCurrent));
        setIntField(feed, "selectedPosition", target);
        if (pager != null) pager.setCurrentItem(target, false);
        invoke(feed, "resolveAhead", new Class<?>[] {int.class}, target);
        invoke(feed, "playSelected", new Class<?>[0]);
    }

    private static void collectChaosPlayers(View view, boolean insideChaos, List<PlayerView> out) {
        boolean chaos = insideChaos || view instanceof ChaosFeedView;
        if (chaos && view instanceof PlayerView) out.add((PlayerView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectChaosPlayers(group.getChildAt(i), chaos, out);
        }
    }

    private static void makeEmptyChromePassThrough(PlayerView playerView) {
        if (!(playerView.getParent() instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) playerView.getParent();
        RecyclerView.ViewHolder holder = findHolder(root);

        View lower = holder == null ? null : fieldValue(holder, "lower", View.class);
        if (lower == null) {
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child instanceof LinearLayout) {
                    lower = child;
                    break;
                }
            }
        }
        if (lower == null) return;

        lower.setOnTouchListener(null);
        lower.setOnClickListener(null);
        lower.setOnLongClickListener(null);
        lower.setClickable(false);
        lower.setLongClickable(false);
        lower.setFocusable(false);
        lower.setFocusableInTouchMode(false);
    }

    private static void keepChromeVisible(PlayerView playerView) {
        if (!(playerView.getParent() instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) playerView.getParent();
        RecyclerView.ViewHolder holder = findHolder(root);

        if (holder != null) {
            Runnable hideControls = fieldValue(holder, "hideControlsRunnable", Runnable.class);
            if (hideControls != null) root.removeCallbacks(hideControls);

            setBooleanField(holder, "controlsVisible", true);

            View lower = fieldValue(holder, "lower", View.class);
            View mute = fieldValue(holder, "mute", View.class);
            showNow(lower);
            showNow(mute);
            return;
        }

        LinearLayout lower = null;
        TextView mute = null;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout) lower = (LinearLayout) child;
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && (text.toString().contains("🔇") || text.toString().contains("🔊"))) {
                    mute = (TextView) child;
                }
            }
        }
        showNow(lower);
        showNow(mute);
    }

    private static void restoreLandscapeAutoHide(PlayerView playerView) {
        if (!(playerView.getParent() instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) playerView.getParent();
        RecyclerView.ViewHolder holder = findHolder(root);
        if (holder == null) return;

        Method method = findMethod(holder.getClass(), "showControlsTemporarily");
        if (method == null) return;
        try {
            method.setAccessible(true);
            method.invoke(holder);
        } catch (Exception ignored) {
        }
    }

    private static RecyclerView.ViewHolder findHolder(View itemView) {
        View child = itemView;
        android.view.ViewParent parent = child.getParent();
        while (parent instanceof View) {
            if (parent instanceof RecyclerView) {
                try {
                    return ((RecyclerView) parent).getChildViewHolder(child);
                } catch (Exception ignored) {
                    return null;
                }
            }
            child = (View) parent;
            parent = child.getParent();
        }
        return null;
    }

    private static void showNow(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1f);
    }

    private static <T> T fieldValue(Object target, String name, Class<T> type) {
        Field field = findField(target.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void setBooleanField(Object target, String name, boolean value) {
        Field field = findField(target.getClass(), name);
        if (field == null) return;
        try {
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (Exception ignored) {
        }
    }

    private static void setIntField(Object target, String name, int value) {
        Field field = findField(target.getClass(), name);
        if (field == null) return;
        try {
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (Exception ignored) {
        }
    }

    private static void invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        if (target == null) return;
        Method method = findMethod(target.getClass(), name, parameterTypes);
        if (method == null) return;
        try {
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (Exception ignored) {
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

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
