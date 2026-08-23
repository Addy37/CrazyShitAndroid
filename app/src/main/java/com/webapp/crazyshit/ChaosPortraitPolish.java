package com.webapp.crazyshit;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Portrait-video polish for Chaos.
 *
 * Uses the existing TextureView as the source for a lightweight, low-resolution ambient
 * background. The sharp player remains centered above it, so we do not run a second decoder.
 * Portrait clips also keep their title/actions visible while the scrub bar retains its own
 * inactivity timeout from ChaosFeedView.
 */
final class ChaosPortraitPolish {
    private static final long TICK_MS = 320L;
    private static final long CAPTURE_MS = 360L;
    private static final int FRAME_WIDTH = 120;
    private static final int FRAME_HEIGHT = 200;

    private static final Map<NativeMainActivity, Loop> LOOPS = new WeakHashMap<>();
    private static final Map<PlayerView, PlayerState> PLAYERS = new WeakHashMap<>();

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

            View content = activity.findViewById(android.R.id.content);
            if (content != null) {
                List<PlayerView> views = new ArrayList<>();
                collectChaosPlayers(content, false, views);
                long now = android.os.SystemClock.uptimeMillis();
                for (PlayerView playerView : views) apply(playerView, now);
            }

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

    private static void apply(PlayerView playerView, long now) {
        if (!(playerView.getParent() instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) playerView.getParent();
        Player player = playerView.getPlayer();

        PlayerState state = PLAYERS.get(playerView);
        if (state == null || state.root != root) {
            state = new PlayerState(root);
            PLAYERS.put(playerView, state);
        }

        if (player == null) {
            deactivate(playerView, state);
            return;
        }

        VideoSize size = player.getVideoSize();
        boolean portrait = size != null && size.width > 0 && size.height > 0 && size.height > size.width;
        if (!portrait) {
            deactivate(playerView, state);
            return;
        }

        activate(playerView, state);
        keepPortraitChromeVisible(root);

        if (player.isPlaying() && now - state.lastCapture >= CAPTURE_MS) {
            captureAmbient(playerView, state);
            state.lastCapture = now;
        }
    }

    private static void activate(PlayerView playerView, PlayerState state) {
        ensureAmbient(state);
        state.ambient.setVisibility(View.VISIBLE);
        state.shade.setVisibility(View.VISIBLE);
        playerView.setBackgroundColor(Color.TRANSPARENT);
        try {
            playerView.setShutterBackgroundColor(Color.TRANSPARENT);
        } catch (Exception ignored) {
        }
    }

    private static void deactivate(PlayerView playerView, PlayerState state) {
        if (state.ambient != null) state.ambient.setVisibility(View.GONE);
        if (state.shade != null) state.shade.setVisibility(View.GONE);
        playerView.setBackgroundColor(Color.BLACK);
        try {
            playerView.setShutterBackgroundColor(Color.BLACK);
        } catch (Exception ignored) {
        }
    }

    private static void ensureAmbient(PlayerState state) {
        if (state.ambient != null && state.ambient.getParent() == state.root) return;

        ImageView ambient = new ImageView(state.root.getContext());
        ambient.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ambient.setBackgroundColor(Color.BLACK);
        ambient.setAlpha(0.82f);
        ambient.setScaleX(1.12f);
        ambient.setScaleY(1.12f);
        if (Build.VERSION.SDK_INT >= 31) {
            ambient.setRenderEffect(RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP));
        }
        state.root.addView(ambient, 0, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(state.root.getContext());
        shade.setBackgroundColor(Color.argb(88, 0, 0, 0));
        state.root.addView(shade, 1, new FrameLayout.LayoutParams(-1, -1));

        state.ambient = ambient;
        state.shade = shade;
    }

    private static void captureAmbient(PlayerView playerView, PlayerState state) {
        View surface;
        try {
            surface = playerView.getVideoSurfaceView();
        } catch (Exception ignored) {
            return;
        }
        if (!(surface instanceof TextureView)) return;
        TextureView texture = (TextureView) surface;
        if (!texture.isAvailable() || texture.getWidth() <= 0 || texture.getHeight() <= 0) return;

        try {
            if (state.frame == null || state.frame.isRecycled()) {
                state.frame = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888);
            }
            texture.getBitmap(state.frame);
            state.ambient.setImageBitmap(state.frame);
            state.ambient.invalidate();
        } catch (Exception ignored) {
        }
    }

    private static void keepPortraitChromeVisible(FrameLayout root) {
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

        if (lower != null) {
            lower.animate().cancel();
            lower.setVisibility(View.VISIBLE);
            lower.setAlpha(1f);
        }
        if (mute != null) {
            mute.animate().cancel();
            mute.setVisibility(View.VISIBLE);
            mute.setAlpha(1f);
        }

        if (!(root.getParent() instanceof RecyclerView)) return;
        try {
            RecyclerView recycler = (RecyclerView) root.getParent();
            RecyclerView.ViewHolder holder = recycler.getChildViewHolder(root);
            Field hideField = findField(holder.getClass(), "hideControlsRunnable");
            if (hideField != null) {
                hideField.setAccessible(true);
                Object value = hideField.get(holder);
                if (value instanceof Runnable) root.removeCallbacks((Runnable) value);
            }
            Field visibleField = findField(holder.getClass(), "controlsVisible");
            if (visibleField != null) {
                visibleField.setAccessible(true);
                visibleField.setBoolean(holder, true);
            }
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

    private static final class PlayerState {
        final FrameLayout root;
        ImageView ambient;
        View shade;
        Bitmap frame;
        long lastCapture;

        PlayerState(FrameLayout root) {
            this.root = root;
        }
    }
}
