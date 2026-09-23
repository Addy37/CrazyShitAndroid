package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.media3.common.util.UnstableApi;

/**
 * Continues the branded splash across NativeMainActivity startup and fades it only after the first
 * selected Chaos player reaches Media3 STATE_READY. A hard timeout keeps a network failure from
 * trapping the user behind the splash.
 */
@UnstableApi
final class ChaosStartupOverlayController {
    static final String EXTRA_STARTUP_HANDOFF = "startup_splash_handoff";

    private static final long MAX_OVERLAY_MS = 5_000L;
    private static final long POLL_MS = 32L;

    private ChaosStartupOverlayController() {
    }

    static void attach(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.getIntent() == null) return;
        if (!activity.getIntent().getBooleanExtra(EXTRA_STARTUP_HANDOFF, false)) return;
        activity.getIntent().removeExtra(EXTRA_STARTUP_HANDOFF);

        if (!ChaosStartupHandoff.isWaiting()) return;
        if (!AccessNoticeDialog.isAccepted(activity)) {
            ChaosStartupHandoff.finish();
            return;
        }

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            ChaosStartupHandoff.finish();
            return;
        }

        View inflated = LayoutInflater.from(activity).inflate(R.layout.activity_splash, content, false);
        if (!(inflated instanceof FrameLayout)) {
            ChaosStartupHandoff.finish();
            return;
        }

        FrameLayout overlay = (FrameLayout) inflated;
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setAlpha(1f);

        int[] finalFrameIds = {
                R.id.splashHorns, R.id.splashFace, R.id.splashXEye, R.id.splashAngryEye,
                R.id.splashTeeth, R.id.splashTongue, R.id.splashOutline,
                R.id.splashWordmark, R.id.splashTagline
        };
        for (int id : finalFrameIds) {
            View layer = overlay.findViewById(id);
            if (layer != null) layer.setAlpha(1f);
        }

        content.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        overlay.bringToFront();

        Handler handler = new Handler(Looper.getMainLooper());
        long attachedAt = SystemClock.uptimeMillis();
        boolean[] leaving = new boolean[1];
        Runnable[] poll = new Runnable[1];

        Runnable fadeAway = () -> {
            if (leaving[0]) return;
            leaving[0] = true;
            overlay.animate()
                    .alpha(0f)
                    .setDuration(160L)
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
                handler.removeCallbacksAndMessages(null);
                ChaosStartupHandoff.finish();
                return;
            }

            boolean timedOut = SystemClock.uptimeMillis() - attachedAt >= MAX_OVERLAY_MS;
            if (ChaosStartupHandoff.isFirstChaosPlayerReady() || timedOut) {
                fadeAway.run();
                return;
            }

            handler.postDelayed(poll[0], POLL_MS);
        };

        handler.postDelayed(poll[0], 40L);
    }
}
