package com.webapp.crazyshit;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps the useful Chaos chrome visible while the app is in portrait orientation and keeps
 * transparent chrome containers from stealing touches meant for the video.
 *
 * The ambient/blur experiment is intentionally disabled for now. The title/actions/mute remain
 * visible in portrait, the scrub bar keeps its independent inactivity timer, and empty space in
 * the full-width lower chrome container is non-interactive so taps/holds can reach PlayerView.
 */
final class ChaosPortraitPolish {
    private static final long TICK_MS = 180L;

    private static final Map<NativeMainActivity, Loop> LOOPS = new WeakHashMap<>();

    private ChaosPortraitPolish() {
    }

    static void start(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        Loop loop = LOOPS.get(activity);
        if (loop == null) {
            loop = new Loop(activity);
            LOOPS.put(activity, loop);
        }
        loop.start();
    }

    static void stop(NativeMainActivity activity) {
        Loop loop = LOOPS.get(activity);
        if (loop != null) loop.stop();
    }

    private static final class Loop implements Runnable {
        private final WeakReference<NativeMainActivity> activityRef;
        private boolean running;
        private boolean wasPortrait;

        Loop(NativeMainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        void start() {
            NativeMainActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return;
            running = true;
            View decor = activity.getWindow().getDecorView();
            decor.removeCallbacks(this);
            decor.post(this);
        }

        void stop() {
            running = false;
            NativeMainActivity activity = activityRef.get();
            if (activity != null) activity.getWindow().getDecorView().removeCallbacks(this);
        }

        @Override
        public void run() {
            NativeMainActivity activity = activityRef.get();
            if (!running || activity == null || activity.isFinishing()) return;

            boolean portrait = activity.getResources().getConfiguration().orientation
                    != Configuration.ORIENTATION_LANDSCAPE;

            View content = activity.findViewById(android.R.id.content);
            if (content != null) {
                List<PlayerView> views = new ArrayList<>();
                collectChaosPlayers(content, false, views);
                for (PlayerView playerView : views) {
                    makeEmptyChromePassThrough(playerView);
                }
                if (portrait) {
                    for (PlayerView playerView : views) keepChromeVisible(playerView);
                } else if (wasPortrait) {
                    for (PlayerView playerView : views) restoreLandscapeAutoHide(playerView);
                }
            }

            wasPortrait = portrait;
            activity.getWindow().getDecorView().postDelayed(this, TICK_MS);
        }
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

        // The container itself used to own a long-press listener, making its entire transparent
        // rectangle a touch target. Children such as title, comments, share, save and More remain
        // interactive, but blank space now returns false so the PlayerView underneath can handle it.
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

        // Fallback for a holder implementation change: only touch the obvious Chaos chrome.
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

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
