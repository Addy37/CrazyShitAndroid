package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * App-wide lifecycle hook used to apply responsive native UI polish.
 */
public final class CrazyShitApplication extends Application {
    private WeakReference<NativeMainActivity> currentNativeActivity = new WeakReference<>(null);

    @Override
    public void onCreate() {
        super.onCreate();

        // v2.4 retires the in-app mini-player/minimize experiment. Existing installs may still
        // have these old preferences enabled, so explicitly switch them off during migration.
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("minimize_on_back", false)
                .putBoolean("swipe_down_minimize", false)
                .apply();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity instanceof VideoDetailActivity) {
                    VideoDetailTransitionPolish.apply(activity);
                    WatchStatePolish.showResumeToast((VideoDetailActivity) activity);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (activity instanceof NativeMainActivity) {
                    NativeMainActivity nativeActivity = (NativeMainActivity) activity;
                    currentNativeActivity = new WeakReference<>(nativeActivity);
                    LandscapeUiController.attach(nativeActivity);
                    LandscapeRailPolish.applySoon(nativeActivity);
                    LandscapeMoreDialog.attachSoon(nativeActivity);
                    SeriesCategoriesNavController.attachSoon(nativeActivity);
                    GlobalSearchUiController.attachSoon(nativeActivity);
                    UiPolishController.attach(nativeActivity);
                    FlashUiController.attach(nativeActivity);
                    WatchStatePolish.attach(nativeActivity);
                    ChaosPortraitPolish.start(nativeActivity);
                }
                if (activity instanceof VideoDetailActivity && !activity.isFinishing()) {
                    VideoDetailActivity detail = (VideoDetailActivity) activity;
                    VideoDetailControllerPolish.applySoon(detail);
                    RelatedVideosPolish.attach(detail);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (activity instanceof NativeMainActivity) {
                    ChaosPortraitPolish.stop((NativeMainActivity) activity);
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (activity instanceof NativeMainActivity) {
                    NativeMainActivity nativeActivity = (NativeMainActivity) activity;
                    ChaosPortraitPolish.stop(nativeActivity);
                    SeriesCategoriesNavController.detach(nativeActivity);
                    FlashUiController.detach(nativeActivity);
                    UiPolishController.detach(nativeActivity);
                    LandscapeUiController.detach(nativeActivity);
                    NativeMainActivity current = currentNativeActivity.get();
                    if (current == activity) currentNativeActivity.clear();
                }
                if (activity instanceof VideoDetailActivity) {
                    RelatedVideosPolish.detach((VideoDetailActivity) activity);
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        NativeMainActivity activity = currentNativeActivity.get();
        if (activity == null) return;
        activity.getWindow().getDecorView().postDelayed(
                () -> {
                    LandscapeUiController.apply(activity);
                    LandscapeRailPolish.applySoon(activity);
                    LandscapeMoreDialog.attachSoon(activity);
                    SeriesCategoriesNavController.attachSoon(activity);
                    GlobalSearchUiController.attachSoon(activity);
                    UiPolishController.attach(activity);
                    FlashUiController.attach(activity);
                    WatchStatePolish.attach(activity);
                    ChaosPortraitPolish.start(activity);
                },
                80L
        );
    }
}
