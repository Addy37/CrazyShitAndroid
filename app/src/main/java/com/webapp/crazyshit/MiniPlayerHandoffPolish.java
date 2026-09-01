package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * v2.2.4 swipe-to-mini-player handoff.
 *
 * The detail screen now exits earlier after a committed swipe. The feed receives the exact visual
 * bounds of the shrinking video plus a tiny frame snapshot, then finishes the travel on top of the
 * already-visible feed so the video visibly lands in the real mini-player media slot.
 */
final class MiniPlayerHandoffPolish {
    static final String EXTRA_DIRECT_HANDOFF = "mini_handoff_direct";
    static final String EXTRA_SNAPSHOT_PATH = "mini_handoff_snapshot";
    static final String EXTRA_SOURCE_LEFT = "mini_handoff_source_left";
    static final String EXTRA_SOURCE_TOP = "mini_handoff_source_top";
    static final String EXTRA_SOURCE_WIDTH = "mini_handoff_source_width";
    static final String EXTRA_SOURCE_HEIGHT = "mini_handoff_source_height";

    private MiniPlayerHandoffPolish() {
    }

    static void applySoon(Activity activity) {
        if (!(activity instanceof VideoDetailActivity) || activity.isFinishing()) return;
        activity.getWindow().getDecorView().postDelayed(
                () -> install((VideoDetailActivity) activity),
                70L
        );
    }

    private static void install(VideoDetailActivity activity) {
        if (activity.isFinishing()) return;

        SwipeMinimizeFrameLayout container = field(activity, "playerContainer", SwipeMinimizeFrameLayout.class);
        PlayerView playerView = field(activity, "playerView", PlayerView.class);
        ScrollView details = field(activity, "detailsScroll", ScrollView.class);
        FrameLayout root = field(activity, "root", FrameLayout.class);
        if (container == null || playerView == null || root == null) return;

        container.setListener(new SwipeMinimizeFrameLayout.Listener() {
            @Override
            public void onDrag(float distancePx, float progress) {
                if (activity.isFinishing() || booleanField(activity, "minimizing")) return;
                playerView.hideController();
                applyDrag(activity, root, container, playerView, details, distancePx, progress);
            }

            @Override
            public void onRelease(boolean minimize, float distancePx) {
                if (activity.isFinishing() || booleanField(activity, "minimizing")) return;
                if (minimize) commit(activity, root, container, playerView, details);
                else restore(activity, container, playerView, details);
            }
        });
    }

    private static void applyDrag(
            VideoDetailActivity activity,
            FrameLayout root,
            SwipeMinimizeFrameLayout container,
            PlayerView playerView,
            ScrollView details,
            float distancePx,
            float progress
    ) {
        Geometry geometry = geometry(activity, root, container);
        if (geometry == null) return;

        float p = Math.max(0f, Math.min(1f, progress));
        float scaleProgress = Math.min(0.52f, p * 0.52f);
        float scaleX = lerp(1f, geometry.targetScaleX, scaleProgress);
        float scaleY = lerp(1f, geometry.targetScaleY, scaleProgress);

        float horizontal = geometry.targetTranslationX * Math.min(0.44f, p * 0.44f);
        float fingerFollow = Math.max(0f, distancePx) * 1.08f;
        float vertical = Math.min(geometry.targetTranslationY * 0.56f, fingerFollow);

        container.setPivotX(0f);
        container.setPivotY(0f);
        container.setScaleX(scaleX);
        container.setScaleY(scaleY);
        container.setTranslationX(horizontal);
        container.setTranslationY(vertical);
        container.setAlpha(1f);

        setPlayerChromeAlpha(container, playerView, 1f - Math.min(0.94f, p * 1.22f));

        if (details != null) {
            details.setAlpha(1f - (0.82f * p));
            details.setTranslationY(Math.min(dp(activity, 18), distancePx * 0.08f));
            details.setScaleX(1f - (0.018f * p));
            details.setScaleY(1f - (0.018f * p));
        }
    }

    private static void commit(
            VideoDetailActivity activity,
            FrameLayout root,
            SwipeMinimizeFrameLayout container,
            PlayerView playerView,
            ScrollView details
    ) {
        setBooleanField(activity, "minimizing", true);
        invoke(activity, "updateSwipeEnabled");
        invoke(activity, "savePlaybackState", new Class<?>[] { boolean.class }, false);
        haptic(activity, container);

        String snapshot = captureSnapshot(activity, playerView);
        Geometry geometry = geometry(activity, root, container);

        playerView.hideController();
        container.animate().cancel();
        if (details != null) details.animate().cancel();

        if (details != null) {
            details.animate()
                    .alpha(0f)
                    .translationY(dp(activity, 16))
                    .scaleX(0.982f)
                    .scaleY(0.982f)
                    .setDuration(82L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        fadePlayerChrome(container, playerView);
        container.setPivotX(0f);
        container.setPivotY(0f);

        if (geometry == null) {
            finishToFeed(activity, snapshot, captureVisualBounds(container));
            return;
        }

        float settleX = lerp(container.getTranslationX(), geometry.targetTranslationX, 0.58f);
        float settleY = lerp(container.getTranslationY(), geometry.targetTranslationY, 0.56f);
        float settleScaleX = lerp(container.getScaleX(), geometry.targetScaleX, 0.46f);
        float settleScaleY = lerp(container.getScaleY(), geometry.targetScaleY, 0.46f);

        container.animate()
                .translationX(settleX)
                .translationY(settleY)
                .scaleX(settleScaleX)
                .scaleY(settleScaleY)
                .alpha(1f)
                .setDuration(96L)
                .setInterpolator(new DecelerateInterpolator(1.18f))
                .withEndAction(() -> finishToFeed(activity, snapshot, captureVisualBounds(container)))
                .start();
    }

    private static void restore(
            VideoDetailActivity activity,
            SwipeMinimizeFrameLayout container,
            PlayerView playerView,
            ScrollView details
    ) {
        container.animate().cancel();
        container.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(185L)
                .setInterpolator(new DecelerateInterpolator(1.35f))
                .withEndAction(() -> {
                    setPlayerChromeAlpha(container, playerView, 1f);
                    playerView.showController();
                })
                .start();

        if (details != null) {
            details.animate().cancel();
            details.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(185L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private static Geometry geometry(
            VideoDetailActivity activity,
            FrameLayout root,
            SwipeMinimizeFrameLayout container
    ) {
        if (root.getWidth() <= 0 || root.getHeight() <= 0 ||
                container.getWidth() <= 0 || container.getHeight() <= 0) return null;

        int[] rootLoc = new int[2];
        int[] playerLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        container.getLocationOnScreen(playerLoc);

        float targetLeft = rootLoc[0] + dp(activity,
                NativeMiniPlayer.CARD_SIDE_MARGIN_DP + NativeMiniPlayer.ROW_PAD_X_DP);
        float targetTop = rootLoc[1] + root.getHeight() -
                dp(activity, NativeMiniPlayer.CARD_BOTTOM_MARGIN_DP + NativeMiniPlayer.CARD_HEIGHT_DP) +
                dp(activity, NativeMiniPlayer.ROW_PAD_TOP_DP);

        float targetScaleX = dp(activity, NativeMiniPlayer.VIDEO_WIDTH_DP) /
                (float) container.getWidth();
        float targetScaleY = dp(activity, NativeMiniPlayer.VIDEO_HEIGHT_DP) /
                (float) container.getHeight();
        float targetTranslationX = targetLeft - playerLoc[0];
        float targetTranslationY = targetTop - playerLoc[1];

        return new Geometry(targetScaleX, targetScaleY, targetTranslationX, targetTranslationY);
    }

    private static VisualBounds captureVisualBounds(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        try {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            int width = Math.max(1, Math.round(view.getWidth() * view.getScaleX()));
            int height = Math.max(1, Math.round(view.getHeight() * view.getScaleY()));
            return new VisualBounds(location[0], location[1], width, height);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String captureSnapshot(VideoDetailActivity activity, PlayerView playerView) {
        try {
            View surface = playerView.getVideoSurfaceView();
            if (!(surface instanceof TextureView)) return null;
            TextureView texture = (TextureView) surface;
            if (!texture.isAvailable()) return null;

            int width = dp(activity, 320);
            int height = dp(activity, 180);
            Bitmap bitmap = texture.getBitmap(width, height);
            if (bitmap == null) return null;

            File file = new File(activity.getCacheDir(),
                    "mini_handoff_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
            } finally {
                bitmap.recycle();
            }
            return file.getAbsolutePath();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void finishToFeed(
            VideoDetailActivity activity,
            String snapshotPath,
            VisualBounds sourceBounds
    ) {
        if (activity.isFinishing()) return;
        invoke(activity, "savePlaybackState", new Class<?>[] { boolean.class }, false);

        Intent result = new Intent();
        result.putExtra(PlayerActivity.EXTRA_MINIMIZED, true);
        result.putExtra(PlayerActivity.EXTRA_MEDIA_URL, stringField(activity, "mediaUrl"));
        result.putExtra(PlayerActivity.EXTRA_PAGE_URL, stringField(activity, "pageUrl"));
        result.putExtra(PlayerActivity.EXTRA_TITLE, stringField(activity, "title"));
        result.putExtra(PlayerActivity.EXTRA_USER_AGENT, stringField(activity, "userAgent"));
        result.putExtra(PlayerActivity.EXTRA_COOKIES, stringField(activity, "cookies"));
        result.putExtra(VideoDetailActivity.EXTRA_REOPEN_DETAIL, true);
        result.putExtra(VideoDetailActivity.EXTRA_VIEWS, stringField(activity, "views"));
        result.putExtra(VideoDetailActivity.EXTRA_UPLOADER, stringField(activity, "uploader"));
        result.putExtra(VideoDetailActivity.EXTRA_COMMENTS, stringField(activity, "comments"));
        result.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, stringField(activity, "relatedFeedUrl"));
        result.putExtra(VideoDetailActivity.EXTRA_SOURCE, stringField(activity, "source"));
        result.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, stringField(activity, "mediaReferer"));

        boolean direct = snapshotPath != null && !snapshotPath.isEmpty() && sourceBounds != null;
        result.putExtra(EXTRA_DIRECT_HANDOFF, direct);
        if (snapshotPath != null && !snapshotPath.isEmpty()) {
            result.putExtra(EXTRA_SNAPSHOT_PATH, snapshotPath);
        }
        if (sourceBounds != null) {
            result.putExtra(EXTRA_SOURCE_LEFT, sourceBounds.left);
            result.putExtra(EXTRA_SOURCE_TOP, sourceBounds.top);
            result.putExtra(EXTRA_SOURCE_WIDTH, sourceBounds.width);
            result.putExtra(EXTRA_SOURCE_HEIGHT, sourceBounds.height);
        }

        ExoPlayer player = field(activity, "player", ExoPlayer.class);
        if (player != null) result.putExtra(PlayerActivity.EXTRA_START_POSITION, player.getCurrentPosition());

        activity.setResult(Activity.RESULT_OK, result);
        activity.finish();
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            activity.overridePendingTransition(0, 0);
        }
    }

    private static void fadePlayerChrome(ViewGroup container, PlayerView playerView) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child == playerView) continue;
            child.animate().cancel();
            child.animate().alpha(0f).setDuration(70L).start();
        }
    }

    private static void setPlayerChromeAlpha(ViewGroup container, PlayerView playerView, float alpha) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child != playerView) child.setAlpha(Math.max(0f, Math.min(1f, alpha)));
        }
    }

    private static void haptic(Activity activity, View view) {
        if (!activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private static String stringField(Object target, String name) {
        Object value = rawField(target, name);
        return value instanceof String ? (String) value : "";
    }

    private static boolean booleanField(Object target, String name) {
        Object value = rawField(target, name);
        return value instanceof Boolean && (Boolean) value;
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

    private static <T> T field(Object target, String name, Class<T> type) {
        Object value = rawField(target, name);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private static Object rawField(Object target, String name) {
        Field field = findField(target.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (Exception ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void invoke(Object target, String name) {
        invoke(target, name, new Class<?>[0]);
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... args) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, types);
                method.setAccessible(true);
                method.invoke(target, args);
                return;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Exception ignored) {
                return;
            }
        }
    }

    private static float lerp(float start, float end, float fraction) {
        return start + ((end - start) * fraction);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Geometry {
        final float targetScaleX;
        final float targetScaleY;
        final float targetTranslationX;
        final float targetTranslationY;

        Geometry(float targetScaleX, float targetScaleY, float targetTranslationX, float targetTranslationY) {
            this.targetScaleX = targetScaleX;
            this.targetScaleY = targetScaleY;
            this.targetTranslationX = targetTranslationX;
            this.targetTranslationY = targetTranslationY;
        }
    }

    private static final class VisualBounds {
        final int left;
        final int top;
        final int width;
        final int height;

        VisualBounds(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }
}
