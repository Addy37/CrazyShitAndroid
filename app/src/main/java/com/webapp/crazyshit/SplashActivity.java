package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

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
        handler.postDelayed(launchAppRunnable, SPLASH_DURATION_MS);
    }

    private void launchApp() {
        Intent intent = new Intent(this, NativeMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(launchAppRunnable);
        super.onDestroy();
    }
}
