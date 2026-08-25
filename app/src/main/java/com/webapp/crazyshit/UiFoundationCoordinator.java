package com.webapp.crazyshit;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
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
            // The Material navigation object is intentionally detached from the portrait view
            // hierarchy, but it remains the activity's logical router. Read the field directly
            // instead of searching the view tree so fresh launches can still route to Chaos.
            BottomNavigationView nav = field(main, "bottomNavigation", BottomNavigationView.class);
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

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) {
        Field field = findField(target == null ? null : target.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? (T) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void attachMain(NativeMainActivity main, boolean configurationChange) {
        if (main == null || main.isFinishing()) return;

        GlobalSearchUiController.attachSoon(main);
        FeedViewStyleController.attachMain(main);
        UiPolishController.attach(main);
        // Flash used to create a second floating Chaos button, move an active indicator and poll
        // the Material nav every 320 ms. That entire portrait-nav path stays detached.
        FlashUiController.detach(main);
        WatchStatePolish.attach(main);
        PredictiveBackPolish.attach(main);
        OledImmersiveUiController.attachMain(main);
        FeedMotionController.attach(main);

        boolean landscape = main.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;

        if (landscape) {
            StableBottomNavigationController.applyOrientation(main);
            if (configurationChange) {
                LandscapeUiController.apply(main);
            } else {
                LandscapeUiController.attach(main);
            }
            LandscapeRailPolish.applySoon(main);
            LandscapeMoreDialog.attachSoon(main);
            ResponsiveFitmentController.applySoon(main);
        } else {
            // When rotating back from horizontal mode, let the landscape controller run one final
            // portrait pass so it hides its rail and restores shell margins, then detach it. The
            // custom portrait bar immediately removes the Material router from the layout again.
            if (configurationChange) LandscapeUiController.apply(main);
            LandscapeUiController.detach(main);
            StableBottomNavigationController.applyOrientation(main);
        }
    }
}