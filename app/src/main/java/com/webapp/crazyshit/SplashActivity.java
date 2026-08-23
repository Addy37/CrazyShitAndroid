package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

public final class SplashActivity extends Activity {
    private static final long SPLASH_DURATION_MS = 650L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable launchAppRunnable = this::launchApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(R.layout.activity_splash);

        View art = findViewById(R.id.splashArt);
        if (art != null) {
            art.setAlpha(0f);
            art.setScaleX(0.94f);
            art.setScaleY(0.94f);
            art.animate()
                    .alpha(1f)
                    .scaleX(1.015f)
                    .scaleY(1.015f)
                    .setDuration(390L)
                    .withEndAction(() -> art.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150L)
                            .start())
                    .start();
        }
        handler.postDelayed(launchAppRunnable, SPLASH_DURATION_MS);
    }

    private void launchApp() {
        Intent intent = new Intent(this, NativeMainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(launchAppRunnable);
        super.onDestroy();
    }
}
