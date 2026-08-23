package com.webapp.crazyshit;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
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
 * Samples the existing TextureView at low resolution and paints blurred ambient panels only
 * over the unused black area around the sharp video. No second decoder/player is created.
 */
final class ChaosPortraitPolish {
    private static final long TICK_MS = 260L;
    private static final long CAPTURE_MS = 420L;
    private static final int SAMPLE_MAX_WIDTH = 180;
    private static final int SAMPLE_MAX_HEIGHT = 220;
    private static final int DARK_THRESHOLD = 30;

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

        if (state.observedPlayer != player) {
            state.observedPlayer = player;
            state.detectedPortrait = false;
            state.detection = null;
            state.lastCapture = 0L;
        }

        if (player == null) {
            deactivate(state);
            return;
        }

        if (player.isPlaying() && (state.frame == null || now - state.lastCapture >= CAPTURE_MS)) {
            Detection detection = capture(playerView, state);
            if (detection != null) {
                state.detection = detection;
                if (detection.portraitContent) state.detectedPortrait = true;
            }
            state.lastCapture = now;
        }

        VideoSize size = player.getVideoSize();
        boolean portraitBySize = isPortrait(size);
        boolean portrait = portraitBySize || state.detectedPortrait;
        if (!portrait) {
            deactivate(state);
            return;
        }

        RectF focus = focusRect(root, size, state.detection, portraitBySize);
        if (focus == null || focus.width() < 2f || focus.height() < 2f) {
            deactivate(state);
            return;
        }

        activate(playerView, state, focus);
        keepPortraitChromeVisible(root);
    }

    private static boolean isPortrait(VideoSize size) {
        if (size == null || size.width <= 0 || size.height <= 0) return false;
        float ratio = size.pixelWidthHeightRatio > 0f ? size.pixelWidthHeightRatio : 1f;
        return (size.width * ratio) < size.height;
    }

    private static void activate(PlayerView playerView, PlayerState state, RectF focus) {
        ensureAmbient(playerView, state);
        if (state.frame != null && !state.frame.isRecycled()) {
            setFrame(state.top, state.frame);
            setFrame(state.bottom, state.frame);
            setFrame(state.left, state.frame);
            setFrame(state.right, state.frame);
        }
        layoutPanels(state, focus);
    }

    private static void deactivate(PlayerState state) {
        hide(state.top);
        hide(state.bottom);
        hide(state.left);
        hide(state.right);
    }

    private static void ensureAmbient(PlayerView playerView, PlayerState state) {
        if (state.top != null && state.top.getParent() == state.root) return;

        int insert = Math.max(0, state.root.indexOfChild(playerView) + 1);
        state.top = ambientView(state.root);
        state.bottom = ambientView(state.root);
        state.left = ambientView(state.root);
        state.right = ambientView(state.root);

        state.root.addView(state.top, insert++, new FrameLayout.LayoutParams(1, 1));
        state.root.addView(state.bottom, insert++, new FrameLayout.LayoutParams(1, 1));
        state.root.addView(state.left, insert++, new FrameLayout.LayoutParams(1, 1));
        state.root.addView(state.right, insert, new FrameLayout.LayoutParams(1, 1));
    }

    private static ImageView ambientView(FrameLayout root) {
        ImageView view = new ImageView(root.getContext());
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setBackgroundColor(Color.BLACK);
        view.setAlpha(0.78f);
        view.setClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (Build.VERSION.SDK_INT >= 31) {
            view.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP));
        }
        return view;
    }

    private static void setFrame(ImageView view, Bitmap frame) {
        if (view == null || frame == null || frame.isRecycled()) return;
        if (view.getDrawable() == null) view.setImageBitmap(frame);
        else {
            view.setImageBitmap(frame);
            view.invalidate();
        }
    }

    private static void layoutPanels(PlayerState state, RectF focus) {
        int rootW = state.root.getWidth();
        int rootH = state.root.getHeight();
        if (rootW <= 0 || rootH <= 0) return;

        int left = clamp(Math.round(focus.left), 0, rootW);
        int top = clamp(Math.round(focus.top), 0, rootH);
        int right = clamp(Math.round(focus.right), left, rootW);
        int bottom = clamp(Math.round(focus.bottom), top, rootH);

        place(state.top, 0, 0, rootW, top);
        place(state.bottom, 0, bottom, rootW, rootH - bottom);
        place(state.left, 0, top, left, bottom - top);
        place(state.right, right, top, rootW - right, bottom - top);
    }

    private static void place(ImageView view, int x, int y, int width, int height) {
        if (view == null) return;
        if (width <= 1 || height <= 1) {
            view.setVisibility(View.GONE);
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        params.width = width;
        params.height = height;
        params.leftMargin = x;
        params.topMargin = y;
        view.setLayoutParams(params);
        view.setVisibility(View.VISIBLE);
    }

    private static void hide(View view) {
        if (view != null) view.setVisibility(View.GONE);
    }

    private static RectF focusRect(
            FrameLayout root,
            VideoSize size,
            Detection detection,
            boolean portraitBySize
    ) {
        int rootW = root.getWidth();
        int rootH = root.getHeight();
        if (rootW <= 0 || rootH <= 0) return null;

        float videoAspect;
        if (size != null && size.width > 0 && size.height > 0) {
            float pixelRatio = size.pixelWidthHeightRatio > 0f ? size.pixelWidthHeightRatio : 1f;
            videoAspect = (size.width * pixelRatio) / (float) size.height;
        } else if (detection != null && detection.sampleAspect > 0f) {
            videoAspect = detection.sampleAspect;
        } else {
            return null;
        }

        float rootAspect = rootW / (float) rootH;
        float baseLeft = 0f;
        float baseTop = 0f;
        float baseRight = rootW;
        float baseBottom = rootH;

        if (videoAspect > rootAspect) {
            float height = rootW / videoAspect;
            baseTop = (rootH - height) / 2f;
            baseBottom = baseTop + height;
        } else {
            float width = rootH * videoAspect;
            baseLeft = (rootW - width) / 2f;
            baseRight = baseLeft + width;
        }

        RectF base = new RectF(baseLeft, baseTop, baseRight, baseBottom);
        if (portraitBySize || detection == null || !detection.portraitContent) return base;

        float w = base.width();
        float h = base.height();
        return new RectF(
                base.left + detection.left * w,
                base.top + detection.top * h,
                base.left + detection.right * w,
                base.top + detection.bottom * h
        );
    }

    private static Detection capture(PlayerView playerView, PlayerState state) {
        View surface;
        try {
            surface = playerView.getVideoSurfaceView();
        } catch (Exception ignored) {
            return null;
        }
        if (!(surface instanceof TextureView)) return null;

        TextureView texture = (TextureView) surface;
        int width = texture.getWidth();
        int height = texture.getHeight();
        if (!texture.isAvailable() || width <= 0 || height <= 0) return null;

        int sampleW = Math.min(SAMPLE_MAX_WIDTH, width);
        int sampleH = Math.max(1, Math.round(sampleW * (height / (float) width)));
        if (sampleH > SAMPLE_MAX_HEIGHT) {
            sampleH = SAMPLE_MAX_HEIGHT;
            sampleW = Math.max(1, Math.round(sampleH * (width / (float) height)));
        }

        try {
            if (state.frame == null || state.frame.isRecycled()
                    || state.frame.getWidth() != sampleW || state.frame.getHeight() != sampleH) {
                state.frame = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888);
            }
            texture.getBitmap(state.frame);
            return analyze(state.frame);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Detection analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w < 8 || h < 8) return null;

        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        boolean[] activeCols = new boolean[w];
        boolean[] activeRows = new boolean[h];

        int colNeed = Math.max(2, Math.round(h * 0.18f));
        int rowNeed = Math.max(2, Math.round(w * 0.18f));

        for (int x = 0; x < w; x++) {
            int active = 0;
            for (int y = 0; y < h; y++) {
                if (bright(pixels[y * w + x])) active++;
            }
            activeCols[x] = active >= colNeed;
        }

        for (int y = 0; y < h; y++) {
            int active = 0;
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                if (bright(pixels[offset + x])) active++;
            }
            activeRows[y] = active >= rowNeed;
        }

        int firstX = first(activeCols);
        int lastX = last(activeCols);
        int firstY = first(activeRows);
        int lastY = last(activeRows);
        if (firstX < 0 || lastX <= firstX || firstY < 0 || lastY <= firstY) return null;

        float left = firstX / (float) w;
        float right = (lastX + 1) / (float) w;
        float top = firstY / (float) h;
        float bottom = (lastY + 1) / (float) h;
        float activeAspect = (lastX - firstX + 1) / (float) (lastY - firstY + 1);
        float sideMargin = Math.min(left, 1f - right);
        boolean portraitContent = sideMargin >= 0.07f && activeAspect < 0.95f;

        return new Detection(left, top, right, bottom, portraitContent, w / (float) h);
    }

    private static boolean bright(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return Math.max(r, Math.max(g, b)) > DARK_THRESHOLD;
    }

    private static int first(boolean[] values) {
        for (int i = 0; i < values.length; i++) if (values[i]) return i;
        return -1;
    }

    private static int last(boolean[] values) {
        for (int i = values.length - 1; i >= 0; i--) if (values[i]) return i;
        return -1;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
        Player observedPlayer;
        ImageView top;
        ImageView bottom;
        ImageView left;
        ImageView right;
        Bitmap frame;
        Detection detection;
        boolean detectedPortrait;
        long lastCapture;

        PlayerState(FrameLayout root) {
            this.root = root;
        }
    }

    private static final class Detection {
        final float left;
        final float top;
        final float right;
        final float bottom;
        final boolean portraitContent;
        final float sampleAspect;

        Detection(
                float left,
                float top,
                float right,
                float bottom,
                boolean portraitContent,
                float sampleAspect
        ) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.portraitContent = portraitContent;
            this.sampleAspect = sampleAspect;
        }
    }
}
