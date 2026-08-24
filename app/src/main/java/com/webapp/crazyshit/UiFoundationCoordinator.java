package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * Single lifecycle owner for the native UI foundation.
 *
 * 2.8 starts by centralizing when visual, responsive and navigation controllers attach so the
 * Application no longer has to know the ordering rules for every screen. The individual
 * controllers still exist for now, but future 2.8 cleanup can fold them into fewer owners without
 * touching application lifecycle plumbing again.
 */
final class UiFoundationCoordinator {
    private static WeakReference<NativeMainActivity> currentMain = new WeakReference<>(null);

    private UiFoundationCoordinator() {
    }

    static void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (activity instanceof VideoDetailActivity) {
            VideoDetailTransitionPolish.apply(activity);
            WatchStatePolish.showResumeToast((VideoDetailActivity) activity);
        }
    }

    static void onActivityResumed(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        OledThemeController.applySoon(activity);

        if (activity instanceof NativeMainActivity) {
            NativeMainActivity main = (NativeMainActivity) activity;
            currentMain = new WeakReference<>(main);
            attachMain(main, false);
        } else if (activity instanceof NativeFeedBrowserActivity) {
            NativeFeedBrowserActivity browser = (NativeFeedBrowserActivity) activity;
            FeedViewStyleController.attachBrowser(browser);
            OledImmersiveUiController.attachBrowser(browser);
        } else if (activity instanceof VideoDetailActivity) {
            VideoDetailActivity detail = (VideoDetailActivity) activity;
            VideoDetailControllerPolish.applySoon(detail);
            VideoDetailImmersivePolish.applySoon(detail);
            RelatedVideosPolish.attach(detail);
            PredictiveBackPolish.attach(detail);
        }

        ResponsiveFitmentController.applySoon(activity);
    }

    static void onActivityPaused(Activity activity) {
        if (activity instanceof NativeMainActivity) {
            ChaosPortraitPolish.stop((NativeMainActivity) activity);
        }
    }

    static void onActivityDestroyed(Activity activity) {
        if (activity == null) return;

        ResponsiveFitmentController.release(activity);
        PredictiveBackPolish.detach(activity);
        OledImmersiveUiController.detach(activity);

        if (activity instanceof NativeMainActivity) {
            NativeMainActivity main = (NativeMainActivity) activity;
            ChaosPortraitPolish.stop(main);
            FeedViewStyleController.detachMain(main);
            SeriesCategoriesNavController.detach(main);
            FlashUiController.detach(main);
            UiPolishController.detach(main);
            LandscapeUiController.detach(main);

            NativeMainActivity current = currentMain.get();
            if (current == activity) currentMain.clear();
        } else if (activity instanceof NativeFeedBrowserActivity) {
            FeedViewStyleController.detachBrowser((NativeFeedBrowserActivity) activity);
        } else if (activity instanceof VideoDetailActivity) {
            RelatedVideosPolish.detach((VideoDetailActivity) activity);
            VideoDetailImmersivePolish.detach((VideoDetailActivity) activity);
        }
    }

    static void onConfigurationChanged() {
        NativeMainActivity main = currentMain.get();
        if (main == null || main.isFinishing()) return;

        main.getWindow().getDecorView().postDelayed(() -> {
            if (main.isFinishing()) return;
            attachMain(main, true);
        }, 80L);
    }

    private static void attachMain(NativeMainActivity main, boolean configurationChange) {
        if (main == null || main.isFinishing()) return;

        // Content and interaction behavior attach first.
        SeriesCategoriesNavController.attachSoon(main);
        GlobalSearchUiController.attachSoon(main);
        FeedViewStyleController.attachMain(main);
        UiPolishController.attach(main);
        FlashUiController.attach(main);
        WatchStatePolish.attach(main);
        ChaosPortraitPolish.start(main);
        PredictiveBackPolish.attach(main);
        OledImmersiveUiController.attachMain(main);

        // Responsive geometry owns the last word. This is intentionally centralized here so a
        // future 2.8 controller merge has one ordering contract instead of lifecycle calls spread
        // across the Application class.
        if (configurationChange) {
            LandscapeUiController.apply(main);
        } else {
            LandscapeUiController.attach(main);
        }
        LandscapeRailPolish.applySoon(main);
        LandscapeMoreDialog.attachSoon(main);
        SeriesCategoriesNavController.apply(main);
        ResponsiveFitmentController.applySoon(main);
    }
}
