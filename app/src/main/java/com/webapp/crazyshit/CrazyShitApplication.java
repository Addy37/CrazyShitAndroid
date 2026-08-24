package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;

/**
 * App-wide lifecycle hook. Native UI ordering lives in UiFoundationCoordinator so application
 * startup is no longer coupled to every visual/responsive controller.
 */
public final class CrazyShitApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        SharedPreferences.Editor migration = appPrefs.edit()
                .putBoolean("minimize_on_back", false)
                .putBoolean("swipe_down_minimize", false)
                // 2.8 permanently uses a static feed header. Remove the retired preference so an
                // older install cannot carry stale collapse state forward.
                .remove("collapse_header_enabled");

        // List remains the starting feed style while preserving a user's explicit choice.
        if (!appPrefs.contains("native_view_home")) {
            migration.putInt("native_view_home", NativeFeedAdapter.VIEW_LIST);
        }
        if (!appPrefs.contains("native_view_collection")) {
            migration.putInt("native_view_collection", NativeFeedAdapter.VIEW_LIST);
        }
        // OLED is opt-out, so existing installs receive the black theme automatically.
        if (!appPrefs.contains("oled_black_enabled")) {
            migration.putBoolean("oled_black_enabled", true);
        }
        migration.apply();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                UiFoundationCoordinator.onActivityCreated(activity, savedInstanceState);
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                UiFoundationCoordinator.onActivityResumed(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                UiFoundationCoordinator.onActivityPaused(activity);
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                UiFoundationCoordinator.onActivityDestroyed(activity);
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        UiFoundationCoordinator.onConfigurationChanged();
    }
}
