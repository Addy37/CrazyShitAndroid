package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * App-wide lifecycle hook used to apply responsive landscape chrome to the native UI.
 */
public final class CrazyShitApplication extends Application {
    private WeakReference<NativeMainActivity> currentNativeActivity = new WeakReference<>(null);

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
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
                    ChaosPortraitPolish.start(nativeActivity);
                }
                if (activity instanceof VideoDetailActivity) {
                    VideoDetailControllerPolish.applySoon(activity);
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
                    LandscapeUiController.detach(nativeActivity);
                    NativeMainActivity current = currentNativeActivity.get();
                    if (current == activity) currentNativeActivity.clear();
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
                    ChaosPortraitPolish.start(activity);
                },
                80L
        );
    }
}
