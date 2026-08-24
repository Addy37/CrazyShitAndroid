package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
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

        SharedPreferences appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        SharedPreferences.Editor migration = appPrefs.edit()
                .putBoolean("minimize_on_back", false)
                .putBoolean("swipe_down_minimize", false);

        // List remains the starting feed style while preserving a user's explicit choice.
        if (!appPrefs.contains("native_view_home")) {
            migration.putInt("native_view_home", NativeFeedAdapter.VIEW_LIST);
        }
        if (!appPrefs.contains("native_view_collection")) {
            migration.putInt("native_view_collection", NativeFeedAdapter.VIEW_LIST);
        }
        // 2.7 OLED is opt-out, so existing installs receive the new black theme automatically.
        if (!appPrefs.contains("oled_black_enabled")) {
            migration.putBoolean("oled_black_enabled", true);
        }
        migration.apply();

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
                OledThemeController.applySoon(activity);

                if (activity instanceof NativeMainActivity) {
                    NativeMainActivity nativeActivity = (NativeMainActivity) activity;
                    currentNativeActivity = new WeakReference<>(nativeActivity);

                    // Content and interaction polish first.
                    SeriesCategoriesNavController.attachSoon(nativeActivity);
                    GlobalSearchUiController.attachSoon(nativeActivity);
                    FeedViewStyleController.attachMain(nativeActivity);
                    UiPolishController.attach(nativeActivity);
                    FlashUiController.attach(nativeActivity);
                    WatchStatePolish.attach(nativeActivity);
                    ChaosPortraitPolish.start(nativeActivity);
                    PredictiveBackPolish.attach(nativeActivity);
                    OledImmersiveUiController.attachMain(nativeActivity);

                    // Geometry owns the final word so rotation cannot be overwritten by delayed
                    // theme/motion passes. The label remap is repeated after the rail exists.
                    LandscapeUiController.attach(nativeActivity);
                    LandscapeRailPolish.applySoon(nativeActivity);
                    LandscapeMoreDialog.attachSoon(nativeActivity);
                    SeriesCategoriesNavController.apply(nativeActivity);
                }
                if (activity instanceof NativeFeedBrowserActivity) {
                    NativeFeedBrowserActivity browser = (NativeFeedBrowserActivity) activity;
                    FeedViewStyleController.attachBrowser(browser);
                    OledImmersiveUiController.attachBrowser(browser);
                }
                if (activity instanceof VideoDetailActivity && !activity.isFinishing()) {
                    VideoDetailActivity detail = (VideoDetailActivity) activity;
                    VideoDetailControllerPolish.applySoon(detail);
                    VideoDetailImmersivePolish.applySoon(detail);
                    RelatedVideosPolish.attach(detail);
                    PredictiveBackPolish.attach(detail);
                }

                ResponsiveFitmentController.applySoon(activity);
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
                ResponsiveFitmentController.release(activity);
                PredictiveBackPolish.detach(activity);
                OledImmersiveUiController.detach(activity);
                if (activity instanceof NativeMainActivity) {
                    NativeMainActivity nativeActivity = (NativeMainActivity) activity;
                    ChaosPortraitPolish.stop(nativeActivity);
                    FeedViewStyleController.detachMain(nativeActivity);
                    SeriesCategoriesNavController.detach(nativeActivity);
                    FlashUiController.detach(nativeActivity);
                    UiPolishController.detach(nativeActivity);
                    LandscapeUiController.detach(nativeActivity);
                    NativeMainActivity current = currentNativeActivity.get();
                    if (current == activity) currentNativeActivity.clear();
                }
                if (activity instanceof NativeFeedBrowserActivity) {
                    FeedViewStyleController.detachBrowser((NativeFeedBrowserActivity) activity);
                }
                if (activity instanceof VideoDetailActivity) {
                    RelatedVideosPolish.detach((VideoDetailActivity) activity);
                    VideoDetailImmersivePolish.detach((VideoDetailActivity) activity);
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
                    OledThemeController.applySoon(activity);
                    SeriesCategoriesNavController.attachSoon(activity);
                    GlobalSearchUiController.attachSoon(activity);
                    FeedViewStyleController.attachMain(activity);
                    UiPolishController.attach(activity);
                    FlashUiController.attach(activity);
                    WatchStatePolish.attach(activity);
                    ChaosPortraitPolish.start(activity);
                    PredictiveBackPolish.attach(activity);
                    OledImmersiveUiController.attachMain(activity);

                    LandscapeUiController.apply(activity);
                    LandscapeRailPolish.applySoon(activity);
                    LandscapeMoreDialog.attachSoon(activity);
                    SeriesCategoriesNavController.apply(activity);
                    ResponsiveFitmentController.applySoon(activity);
                },
                80L
        );
    }
}
