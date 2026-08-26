package com.webapp.crazyshit;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.OvershootInterpolator;

public final class SplashActivity extends Activity {
    private static final long MIN_SPLASH_MS = 850L;
    private static final long MAX_SPLASH_MS = 1_350L;
    private static final long READY_POLL_MS = 40L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable readinessRunnable = this::checkReadyToLaunch;

    private long splashStartedAt;
    private boolean leaving;
    private boolean handingOff;
    private boolean chaosHandoff;
    private String launchAction;
    private View wordmark;
    private View glow;
    private View sweep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        splashStartedAt = SystemClock.uptimeMillis();
        launchAction = getIntent() == null ? null : getIntent().getAction();
        chaosHandoff = !AppShortcuts.isShortcutAction(launchAction)
                || AppShortcuts.isChaosAction(launchAction);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(R.layout.activity_splash);

        // The visual intro and native app startup are now one continuous handoff. Metadata begins
        // loading here; NativeMainActivity keeps this same wordmark visible until the selected
        // Chaos player has actually rendered a frame.
        if (chaosHandoff) {
            ChaosStartupHandoff.begin();
            ChaosStartupPreloader.start(this);
        }
        animateSplash();
        handler.postDelayed(readinessRunnable, MIN_SPLASH_MS);
    }

    private void animateSplash() {
        View root = findViewById(R.id.splashRoot);
        wordmark = findViewById(R.id.splashWordmark);
        glow = findViewById(R.id.splashGlow);
        sweep = findViewById(R.id.splashSweep);

        if (glow != null) {
            glow.setAlpha(0f);
            glow.setScaleX(0.88f);
            glow.setScaleY(0.88f);
            glow.animate()
                    .alpha(0.52f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(620L)
                    .start();
        }

        if (wordmark != null) {
            wordmark.setAlpha(0f);
            wordmark.setScaleX(0.94f);
            wordmark.setScaleY(0.94f);

            // A quick broken-signal flicker lets the distressed art reveal itself before snapping
            // into the clean hold frame.
            ObjectAnimator flicker = ObjectAnimator.ofFloat(
                    wordmark,
                    View.ALPHA,
                    0f, 0.16f, 0f, 0.58f, 0.32f, 1f
            );
            flicker.setDuration(300L);

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(wordmark, View.SCALE_X, 0.94f, 1.018f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(wordmark, View.SCALE_Y, 0.94f, 1.018f);
            scaleX.setDuration(430L);
            scaleY.setDuration(430L);

            AnimatorSet entrance = new AnimatorSet();
            entrance.playTogether(flicker, scaleX, scaleY);
            entrance.setInterpolator(new OvershootInterpolator(0.45f));
            entrance.start();

            handler.postDelayed(() -> {
                if (leaving || wordmark == null) return;
                wordmark.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100L)
                        .start();
            }, 430L);

            ObjectAnimator glitchOne = ObjectAnimator.ofFloat(
                    wordmark, View.TRANSLATION_X, 0f, dp(4), -dp(2), 0f
            );
            glitchOne.setStartDelay(165L);
            glitchOne.setDuration(82L);
            glitchOne.start();

            ObjectAnimator glitchTwo = ObjectAnimator.ofFloat(
                    wordmark, View.TRANSLATION_X, 0f, -dp(2), dp(1.3f), 0f
            );
            glitchTwo.setStartDelay(500L);
            glitchTwo.setDuration(66L);
            glitchTwo.start();
        }

        if (root != null && sweep != null) {
            sweep.post(() -> {
                if (leaving) return;
                float travel = root.getWidth() * 0.5f + Math.max(dp(210), sweep.getWidth());
                sweep.setTranslationX(-travel);
                sweep.setAlpha(0f);
                sweep.animate()
                        .alpha(0.92f)
                        .translationX(travel)
                        .setStartDelay(260L)
                        .setDuration(430L)
                        .withEndAction(() -> sweep.animate()
                                .alpha(0f)
                                .setDuration(90L)
                                .start())
                        .start();
            });
        }
    }

    private void checkReadyToLaunch() {
        if (leaving) return;
        long elapsed = SystemClock.uptimeMillis() - splashStartedAt;
        boolean minimumPlayed = elapsed >= MIN_SPLASH_MS;
        boolean ready = !chaosHandoff || ChaosStartupPreloader.isReady();
        boolean timedOut = elapsed >= MAX_SPLASH_MS;

        if (minimumPlayed && (ready || timedOut)) {
            launchApp();
            return;
        }

        long remaining = Math.max(1L, MAX_SPLASH_MS - elapsed);
        handler.postDelayed(readinessRunnable, Math.min(READY_POLL_MS, remaining));
    }

    private void launchApp() {
        if (leaving) return;
        leaving = true;
        handingOff = true;
        handler.removeCallbacks(readinessRunnable);
        if (wordmark != null) wordmark.animate().cancel();
        if (glow != null) glow.animate().cancel();
        if (sweep != null) sweep.animate().cancel();

        Intent intent = new Intent(this, NativeMainActivity.class);
        if (chaosHandoff) {
            intent.putExtra(ChaosStartupOverlayController.EXTRA_STARTUP_HANDOFF, true);
        }
        if (AppShortcuts.isShortcutAction(launchAction)) {
            intent.setAction(launchAction);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        startActivity(intent);
        // NativeMainActivity immediately draws the same black + wordmark composition, so a system
        // activity transition would only introduce a flash between two intentionally identical views.
        overridePendingTransition(0, 0);
        finish();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDestroy() {
        leaving = true;
        handler.removeCallbacksAndMessages(null);
        if (wordmark != null) wordmark.animate().cancel();
        if (glow != null) glow.animate().cancel();
        if (sweep != null) sweep.animate().cancel();
        if (chaosHandoff && !handingOff) ChaosStartupHandoff.finish();
        super.onDestroy();
    }
}
