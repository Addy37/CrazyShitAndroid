package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

/**
 * Continues the branded splash across NativeMainActivity startup and fades it only after the
 * selected Chaos player renders its first frame. A short hard timeout guarantees that a network
 * failure can never trap the user behind the splash.
 */
@UnstableApi
final class ChaosStartupOverlayController {
    static final String EXTRA_STARTUP_HANDOFF = "startup_splash_handoff";

    private static final long MAX_OVERLAY_MS = 3_500L;
    private static final long POLL_MS = 35L;
    private static final long READY_FALLBACK_MS = 140L;

    private ChaosStartupOverlayController() {
    }

    static void attach(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.getIntent() == null) return;
        if (!activity.getIntent().getBooleanExtra(EXTRA_STARTUP_HANDOFF, false)) return;
        activity.getIntent().removeExtra(EXTRA_STARTUP_HANDOFF);

        if (!ChaosStartupHandoff.isWaiting()) return;
        if (!activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("age_warning_accepted", false)) {
            ChaosStartupHandoff.finish();
            return;
        }

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            ChaosStartupHandoff.finish();
            return;
        }

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setAlpha(1f);

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.splash_wordmark_transparent);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription(null);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 320)
        );
        logoParams.gravity = Gravity.CENTER;
        logoParams.leftMargin = dp(activity, 20);
        logoParams.rightMargin = dp(activity, 20);
        overlay.addView(logo, logoParams);

        content.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        overlay.bringToFront();

        Handler handler = new Handler(Looper.getMainLooper());
        long attachedAt = SystemClock.uptimeMillis();
        Player[] watchedPlayer = new Player[1];
        Player.Listener[] watchedListener = new Player.Listener[1];
        boolean[] leaving = new boolean[1];
        boolean[] fallbackPosted = new boolean[1];
        Runnable[] poll = new Runnable[1];

        Runnable cleanupPlayer = () -> {
            if (watchedPlayer[0] != null && watchedListener[0] != null) {
                try {
                    watchedPlayer[0].removeListener(watchedListener[0]);
                } catch (Exception ignored) {
                }
            }
            watchedPlayer[0] = null;
            watchedListener[0] = null;
        };

        Runnable fadeAway = () -> {
            if (leaving[0]) return;
            leaving[0] = true;
            cleanupPlayer.run();
            overlay.animate()
                    .alpha(0f)
                    .setDuration(220L)
                    .withEndAction(() -> {
                        if (overlay.getParent() == content) content.removeView(overlay);
                        handler.removeCallbacksAndMessages(null);
                        ChaosStartupHandoff.finish();
                    })
                    .start();
        };

        poll[0] = () -> {
            if (leaving[0]) return;
            if (activity.isFinishing() || activity.isDestroyed()) {
                leaving[0] = true;
                cleanupPlayer.run();
                handler.removeCallbacksAndMessages(null);
                ChaosStartupHandoff.finish();
                return;
            }

            boolean timedOut = SystemClock.uptimeMillis() - attachedAt >= MAX_OVERLAY_MS;
            if (ChaosStartupHandoff.isFirstFrameReady() || timedOut) {
                fadeAway.run();
                return;
            }

            PlayerView selectedView = findSelectedPlayerView(content);
            Player selectedPlayer = selectedView == null ? null : selectedView.getPlayer();
            if (selectedPlayer != null && selectedPlayer != watchedPlayer[0]) {
                cleanupPlayer.run();
                watchedPlayer[0] = selectedPlayer;
                fallbackPosted[0] = false;
                watchedListener[0] = new Player.Listener() {
                    @Override
                    public void onRenderedFirstFrame() {
                        ChaosStartupHandoff.markFirstFrameReady();
                        handler.post(poll[0]);
                    }
                };
                selectedPlayer.addListener(watchedListener[0]);
            }

            // If the listener was attached just after Media3 emitted its first-frame callback,
            // READY + actively playing is a safe short fallback. The 140 ms delay gives the
            // TextureView time to present the decoded frame before the overlay fades.
            if (selectedPlayer != null
                    && selectedPlayer == watchedPlayer[0]
                    && selectedPlayer.getPlaybackState() == Player.STATE_READY
                    && selectedPlayer.isPlaying()
                    && !fallbackPosted[0]) {
                fallbackPosted[0] = true;
                Player candidate = selectedPlayer;
                handler.postDelayed(() -> {
                    if (leaving[0] || candidate != watchedPlayer[0]) return;
                    if (candidate.getPlaybackState() == Player.STATE_READY && candidate.isPlaying()) {
                        ChaosStartupHandoff.markFirstFrameReady();
                        handler.post(poll[0]);
                    }
                }, READY_FALLBACK_MS);
            }

            handler.postDelayed(poll[0], POLL_MS);
        };

        handler.postDelayed(poll[0], 40L);
    }

    private static PlayerView findSelectedPlayerView(View root) {
        if (root == null || !root.isShown()) return null;
        if (root instanceof PlayerView) {
            PlayerView view = (PlayerView) root;
            Player player = view.getPlayer();
            if (player != null && (player.getPlayWhenReady() || player.isPlaying())) return view;
            return null;
        }
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            PlayerView found = findSelectedPlayerView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
