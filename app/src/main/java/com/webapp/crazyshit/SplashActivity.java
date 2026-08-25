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

public final class SplashActivity extends Activity {
    private static final long MIN_SPLASH_MS = 850L;
    private static final long MAX_SPLASH_MS = 1_350L;
    private static final long READY_POLL_MS = 40L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable readinessRunnable = this::checkReadyToLaunch;

    private long splashStartedAt;
    private boolean leaving;
    private View glow;
    private View art;
    private View sweep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        splashStartedAt = SystemClock.uptimeMillis();
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(R.layout.activity_splash);

        // The visual intro overlaps useful Chaos work. The preloader now also resolves the first
        // regular clip once so the handoff is much less likely to land on a visible video spinner.
        ChaosStartupPreloader.start(this);
        animateSplash();
        handler.postDelayed(readinessRunnable, MIN_SPLASH_MS);
    }

    private void animateSplash() {
        View root = findViewById(R.id.splashRoot);
        art = findViewById(R.id.splashArt);
        glow = findViewById(R.id.splashGlow);
        sweep = findViewById(R.id.splashSweep);

        if (art != null) {
            art.setAlpha(0f);
            art.setScaleX(0.82f);
            art.setScaleY(0.82f);
            art.setRotation(-1.2f);

            AnimatorSet entrance = new AnimatorSet();
            entrance.playTogether(
                    ObjectAnimator.ofFloat(art, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(art, View.SCALE_X, 0.82f, 1.045f),
                    ObjectAnimator.ofFloat(art, View.SCALE_Y, 0.82f, 1.045f),
                    ObjectAnimator.ofFloat(art, View.ROTATION, -1.2f, 0f)
            );
            entrance.setDuration(390L);
            entrance.start();

            handler.postDelayed(() -> {
                if (leaving || art == null) return;
                art.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120L)
                        .start();
            }, 390L);

            // Two very short offsets give the logo a tiny digital snap without turning the intro
            // into a distracting shake effect.
            ObjectAnimator glitchOne = ObjectAnimator.ofFloat(
                    art, View.TRANSLATION_X, 0f, dp(3), -dp(1.5f), 0f
            );
            glitchOne.setStartDelay(455L);
            glitchOne.setDuration(74L);
            glitchOne.start();

            ObjectAnimator glitchTwo = ObjectAnimator.ofFloat(
                    art, View.TRANSLATION_X, 0f, -dp(1.5f), dp(1), 0f
            );
            glitchTwo.setStartDelay(650L);
            glitchTwo.setDuration(58L);
            glitchTwo.start();
        }

        if (glow != null) {
            glow.setAlpha(0f);
            glow.setScaleX(0.62f);
            glow.setScaleY(0.62f);
            glow.animate()
                    .alpha(0.68f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(430L)
                    .withEndAction(() -> handler.postDelayed(this::pulseGlow, 90L))
                    .start();
        }

        if (root != null && sweep != null) {
            sweep.post(() -> {
                if (leaving) return;
                float start = -Math.max(dp(220), sweep.getWidth() * 1.3f);
                float end = root.getWidth() + Math.max(dp(220), sweep.getWidth());
                sweep.setTranslationX(start);
                sweep.setAlpha(0f);
                sweep.animate()
                        .alpha(0.90f)
                        .translationX(end)
                        .setStartDelay(300L)
                        .setDuration(500L)
                        .withEndAction(() -> sweep.animate()
                                .alpha(0f)
                                .setDuration(90L)
                                .start())
                        .start();
            });
        }
    }

    private void pulseGlow() {
        if (leaving || glow == null) return;
        boolean bright = glow.getAlpha() > 0.50f;
        glow.animate()
                .alpha(bright ? 0.38f : 0.60f)
                .scaleX(bright ? 1.055f : 0.985f)
                .scaleY(bright ? 1.055f : 0.985f)
                .setDuration(330L)
                .withEndAction(this::pulseGlow)
                .start();
    }

    private void checkReadyToLaunch() {
        if (leaving) return;
        long elapsed = SystemClock.uptimeMillis() - splashStartedAt;
        boolean minimumPlayed = elapsed >= MIN_SPLASH_MS;
        boolean ready = ChaosStartupPreloader.isReady();
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
        handler.removeCallbacks(readinessRunnable);
        if (art != null) art.animate().cancel();
        if (glow != null) glow.animate().cancel();
        if (sweep != null) sweep.animate().cancel();

        Intent intent = new Intent(this, NativeMainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDestroy() {
        leaving = true;
        handler.removeCallbacksAndMessages(null);
        if (art != null) art.animate().cancel();
        if (glow != null) glow.animate().cancel();
        if (sweep != null) sweep.animate().cancel();
        super.onDestroy();
    }
}
