package com.webapp.crazyshit;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Single lifecycle owner for the native UI foundation.
 *
 * 2.8 centralizes when visual, responsive and navigation systems attach so the Application no
 * longer has to know the ordering rules for every screen. Portrait navigation has one visible
 * owner; the older Flash/Material geometry loops are deliberately not attached.
 */
final class UiFoundationCoordinator {
    private static WeakReference<NativeMainActivity> currentMain = new WeakReference<>(null);
    private static final WeakHashMap<NativeMainActivity, Boolean> FRESH_MAIN = new WeakHashMap<>();

    private UiFoundationCoordinator() {
    }

    static void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (activity instanceof NativeMainActivity) {
            FRESH_MAIN.put((NativeMainActivity) activity, savedInstanceState == null);
        }
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
            StableBottomNavigationController.attach(main);
            ChaosCompletedReplayController.attachSoon(main);
            openChaosOnFreshLaunch(main);
        } else if (activity instanceof NativeFeedBrowserActivity) {
            NativeFeedBrowserActivity browser = (NativeFeedBrowserActivity) activity;
            FeedViewStyleController.attachBrowser(browser);
            OledImmersiveUiController.attachBrowser(browser);
            FeedMotionController.attach(browser);
            ResponsiveFitmentController.applySoon(activity);
        } else if (activity instanceof VideoDetailActivity) {
            VideoDetailActivity detail = (VideoDetailActivity) activity;
            RelatedVideosPolish.attach(detail);
            PredictiveBackPolish.attach(detail);
            ResponsiveFitmentController.applySoon(activity);
        } else {
            ResponsiveFitmentController.applySoon(activity);
        }
    }

    static void onActivityPaused(Activity activity) {
    }

    static void onActivityDestroyed(Activity activity) {
        if (activity == null) return;

        ResponsiveFitmentController.release(activity);
        PredictiveBackPolish.detach(activity);
        FeedMotionController.detach(activity);
        OledImmersiveUiController.detach(activity);

        if (activity instanceof NativeMainActivity) {
            NativeMainActivity main = (NativeMainActivity) activity;
            FeedViewStyleController.detachMain(main);
            // Flash used to own a second floating Chaos button and a 320 ms nav polling loop.
            // It is intentionally not attached in 2.8's stable portrait navigation path.
            FlashUiController.detach(main);
            UiPolishController.detach(main);
            LandscapeUiController.detach(main);
            StableBottomNavigationController.detach(main);
            ChaosCompletedReplayController.detach(main);
            FRESH_MAIN.remove(main);

            NativeMainActivity current = currentMain.get();
            if (current == activity) currentMain.clear();
        } else if (activity instanceof NativeFeedBrowserActivity) {
            FeedViewStyleController.detachBrowser((NativeFeedBrowserActivity) activity);
        } else if (activity instanceof VideoDetailActivity) {
            RelatedVideosPolish.detach((VideoDetailActivity) activity);
        }
    }

    static void onConfigurationChanged() {
        NativeMainActivity main = currentMain.get();
        if (main == null || main.isFinishing()) return;

        main.getWindow().getDecorView().postDelayed(() -> {
            if (main.isFinishing()) return;
            attachMain(main, true);
            StableBottomNavigationController.applyOrientation(main);
            ChaosCompletedReplayController.attachSoon(main);
        }, 80L);
    }

    private static void openChaosOnFreshLaunch(NativeMainActivity main) {
        if (main == null || main.isFinishing()) return;
        Boolean fresh = FRESH_MAIN.get(main);
        if (!Boolean.TRUE.equals(fresh)) return;
        if (!main.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("age_warning_accepted", false)) {
            return;
        }
        FRESH_MAIN.put(main, false);

        main.getWindow().getDecorView().post(() -> {
            if (main.isFinishing()) return;
            BottomNavigationView nav = findFirst(
                    main.findViewById(android.R.id.content),
                    BottomNavigationView.class
            );
            if (nav == null) return;
            Menu menu = nav.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                if (menu.getItem(i).getTitle() != null
                        && "Chaos".contentEquals(menu.getItem(i).getTitle())) {
                    nav.setSelectedItemId(menu.getItem(i).getItemId());
                    break;
                }
            }
        });
    }

    private static <T> T findFirst(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T found = findFirst(group.getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }

    private static void attachMain(NativeMainActivity main, boolean configurationChange) {
        if (main == null || main.isFinishing()) return;

        GlobalSearchUiController.attachSoon(main);
        FeedViewStyleController.attachMain(main);
        UiPolishController.attach(main);
        // Do not attach FlashUiController here. Its old portrait navigation overlay/polling path
        // was the source of the delayed tab geometry changes seen during device testing.
        FlashUiController.detach(main);
        WatchStatePolish.attach(main);
        PredictiveBackPolish.attach(main);
        OledImmersiveUiController.attachMain(main);

        // Feed motion owns the final card transform state while RecyclerViews are moving.
        FeedMotionController.attach(main);

        boolean landscape = main.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        StableBottomNavigationController.applyOrientation(main);

        if (landscape) {
            if (configurationChange) {
                LandscapeUiController.apply(main);
            } else {
                LandscapeUiController.attach(main);
            }
            LandscapeRailPolish.applySoon(main);
            LandscapeMoreDialog.attachSoon(main);
            // The responsive main pass is useful in horizontal mode. In portrait it would call
            // back into the old Material/Landscape navigation geometry, so portrait skips it.
            ResponsiveFitmentController.applySoon(main);
        } else {
            LandscapeUiController.detach(main);
        }
    }
}