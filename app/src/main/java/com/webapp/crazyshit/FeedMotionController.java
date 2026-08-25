package com.webapp.crazyshit;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Single owner for feed-card transforms while RecyclerView is moving.
 *
 * Older visual layers can still style cards and provide press/open animations, but they are not
 * allowed to continuously scale, fade or parallax feed rows under the user's finger. Keeping the
 * scrolling transform at identity avoids the subtle snapping/pulsing that becomes visible during
 * slow drags while preserving motion when the feed is idle.
 */
final class FeedMotionController {
    private static final WeakHashMap<Activity, State> STATES = new WeakHashMap<>();

    private FeedMotionController() {
    }

    static void attach(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        State old = STATES.remove(activity);
        if (old != null) old.detach();

        State state = new State(activity);
        STATES.put(activity, state);
        state.attach();
    }

    static void detach(Activity activity) {
        State state = STATES.remove(activity);
        if (state != null) state.detach();
    }

    private static final class State {
        private final WeakReference<Activity> activityRef;
        private final Set<RecyclerView> recyclers =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<Binding> bindings = new ArrayList<>();
        private ViewPager2 pager;
        private ViewPager2.OnPageChangeCallback pagerCallback;
        private boolean running;

        State(Activity activity) {
            activityRef = new WeakReference<>(activity);
        }

        void attach() {
            Activity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return;
            running = true;
            View decor = activity.getWindow().getDecorView();
            decor.post(this::discover);
            decor.postDelayed(this::discover, 120L);
            decor.postDelayed(this::discover, 360L);
        }

        void detach() {
            running = false;
            if (pager != null && pagerCallback != null) {
                try {
                    pager.unregisterOnPageChangeCallback(pagerCallback);
                } catch (Exception ignored) {
                }
            }
            pager = null;
            pagerCallback = null;

            for (Binding binding : new ArrayList<>(bindings)) binding.detach();
            bindings.clear();
            recyclers.clear();
        }

        void discover() {
            if (!running) return;
            Activity activity = activityRef.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                detach();
                return;
            }

            View root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            collect(root, false);

            if (activity instanceof NativeMainActivity && pager == null) {
                pager = findFirst(root, ViewPager2.class);
                if (pager != null) {
                    pagerCallback = new ViewPager2.OnPageChangeCallback() {
                        @Override
                        public void onPageSelected(int position) {
                            ViewPager2 current = pager;
                            if (current != null) current.postDelayed(State.this::discover, 60L);
                        }
                    };
                    pager.registerOnPageChangeCallback(pagerCallback);
                }
            }
        }

        private void collect(View view, boolean insideChaos) {
            boolean chaos = insideChaos || view instanceof ChaosFeedView;
            if (view instanceof RecyclerView && !chaos) bind((RecyclerView) view);
            if (!(view instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), chaos);
            }
        }

        private void bind(RecyclerView recycler) {
            if (recycler == null || !recyclers.add(recycler)) return;
            Binding binding = new Binding(recycler);
            bindings.add(binding);
            binding.attach();
        }
    }

    private static final class Binding {
        private final RecyclerView recycler;
        private final ViewTreeObserver.OnPreDrawListener preDrawListener;
        private final RecyclerView.OnScrollListener scrollListener;
        private final RecyclerView.OnChildAttachStateChangeListener childListener;
        private boolean attached;

        Binding(RecyclerView recycler) {
            this.recycler = recycler;
            preDrawListener = () -> {
                if (recycler.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                    resetChildren(recycler);
                }
                return true;
            };
            scrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView view, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) resetChildren(view);
                }
            };
            childListener = new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(@NonNull View view) {
                    resetRow(view);
                }

                @Override
                public void onChildViewDetachedFromWindow(@NonNull View view) {
                    resetRow(view);
                }
            };
        }

        void attach() {
            if (attached) return;
            attached = true;
            recycler.addOnScrollListener(scrollListener);
            recycler.addOnChildAttachStateChangeListener(childListener);
            if (recycler.getViewTreeObserver().isAlive()) {
                recycler.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            }
            resetChildren(recycler);
        }

        void detach() {
            if (!attached) return;
            attached = false;
            try {
                recycler.removeOnScrollListener(scrollListener);
            } catch (Exception ignored) {
            }
            try {
                recycler.removeOnChildAttachStateChangeListener(childListener);
            } catch (Exception ignored) {
            }
            ViewTreeObserver observer = recycler.getViewTreeObserver();
            if (observer.isAlive()) {
                try {
                    observer.removeOnPreDrawListener(preDrawListener);
                } catch (Exception ignored) {
                }
            }
            resetChildren(recycler);
        }
    }

    private static void resetChildren(RecyclerView recycler) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            resetRow(recycler.getChildAt(i));
        }
    }

    private static void resetRow(View row) {
        if (row == null) return;
        row.animate().cancel();
        row.setScaleX(1f);
        row.setScaleY(1f);
        row.setAlpha(1f);
        row.setTranslationZ(0f);

        ImageView artwork = largestImage(row);
        if (artwork != null) artwork.setTranslationY(0f);
    }

    private static ImageView largestImage(View view) {
        ImageView best = null;
        long bestArea = -1L;
        List<ImageView> images = new ArrayList<>();
        collectImages(view, images);
        for (ImageView image : images) {
            long width = Math.max(1, image.getWidth());
            long height = Math.max(1, image.getHeight());
            long area = width * height;
            if (area > bestArea) {
                bestArea = area;
                best = image;
            }
        }
        return best;
    }

    private static void collectImages(View view, List<ImageView> out) {
        if (view instanceof ImageView) {
            out.add((ImageView) view);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectImages(group.getChildAt(i), out);
        }
    }

    private static <T> T findFirst(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T found = findFirst(group.getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }
}
