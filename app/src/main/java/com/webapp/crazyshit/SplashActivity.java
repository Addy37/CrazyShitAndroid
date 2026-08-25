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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        splashStartedAt = SystemClock.uptimeMillis();
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(R.layout.activity_splash);

        // Use the visual intro time to do useful Chaos work instead of adding dead startup delay.
        ChaosStartupPreloader.start(this);
        animateSplash();
        handler.postDelayed(readinessRunnable, MIN_SPLASH_MS);
    }

    private void animateSplash() {
        View root = findViewById(R.id.splashRoot);
        View art = findViewById(R.id.splashArt);
        glow = findViewById(R.id.splashGlow);
        View sweep = findViewById(R.id.splashSweep);

        if (art != null) {
            art.setAlpha(0f);
            art.setScaleX(0.92f);
            art.setScaleY(0.92f);

            AnimatorSet entrance = new AnimatorSet();
            entrance.playTogether(
                    ObjectAnimator.ofFloat(art, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(art, View.SCALE_X, 0.92f, 1.0f),
                    ObjectAnimator.ofFloat(art, View.SCALE_Y, 0.92f, 1.0f)
            );
            entrance.setDuration(520L);
            entrance.start();

            // Two tiny offsets create a restrained glitch rather than a distracting shake.
            ObjectAnimator glitchOne = ObjectAnimator.ofFloat(
                    art, View.TRANSLATION_X, 0f, dp(2), -dp(1), 0f
            );
            glitchOne.setStartDelay(420L);
            glitchOne.setDuration(90L);
            glitchOne.start();

            ObjectAnimator glitchTwo = ObjectAnimator.ofFloat(
                    art, View.TRANSLATION_X, 0f, -dp(1), dp(1), 0f
            );
            glitchTwo.setStartDelay(610L);
            glitchTwo.setDuration(70L);
            glitchTwo.start();
        }

        if (glow != null) {
            glow.setAlpha(0f);
            glow.setScaleX(0.70f);
            glow.setScaleY(0.70f);
            glow.animate()
                    .alpha(0.62f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(460L)
                    .withEndAction(() -> handler.postDelayed(this::pulseGlow, 80L))
                    .start();
        }

        if (root != null && sweep != null) {
            sweep.post(() -> {
                if (leaving) return;
                float start = -Math.max(dp(180), sweep.getWidth() * 1.2f);
                float end = root.getWidth() + Math.max(dp(180), sweep.getWidth());
                sweep.setTranslationX(start);
                sweep.setAlpha(0f);
                sweep.animate()
                        .alpha(0.82f)
                        .translationX(end)
                        .setStartDelay(310L)
                        .setDuration(520L)
                        .withEndAction(() -> sweep.animate()
                                .alpha(0f)
                                .setDuration(100L)
                                .start())
                        .start();
            });
        }
    }

    private void pulseGlow() {
        if (leaving || glow == null) return;
        boolean bright = glow.getAlpha() > 0.48f;
        glow.animate()
                .alpha(bright ? 0.34f : 0.56f)
                .scaleX(bright ? 1.06f : 0.98f)
                .scaleY(bright ? 1.06f : 0.98f)
                .setDuration(340L)
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
        if (glow != null) glow.animate().cancel();
        super.onDestroy();
    }
}
