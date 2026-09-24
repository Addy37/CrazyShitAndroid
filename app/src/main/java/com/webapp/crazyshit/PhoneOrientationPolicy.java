package com.webapp.crazyshit;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps normal browsing portrait on phones while leaving large-screen layouts adaptive.
 * Full-screen video temporarily opts out without recreating the browsing task.
 */
final class PhoneOrientationPolicy {
    private static final int LARGE_SCREEN_MIN_WIDTH_DP = 600;
    private static final Map<Activity, Integer> FULLSCREEN_ACTIVITIES = new WeakHashMap<>();
    private static final Set<Activity> PORTRAIT_LOCKED_ACTIVITIES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private PhoneOrientationPolicy() {
    }

    static void applyBrowsingOrientation(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (!isPhoneSized(activity)) {
            if (PORTRAIT_LOCKED_ACTIVITIES.remove(activity)) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
            return;
        }

        Integer fullscreenOrientation = FULLSCREEN_ACTIVITIES.get(activity);
        boolean fullscreen = fullscreenOrientation != null;
        int orientation = fullscreen
                ? fullscreenOrientation
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (fullscreen) PORTRAIT_LOCKED_ACTIVITIES.remove(activity);
        else PORTRAIT_LOCKED_ACTIVITIES.add(activity);
        if (activity.getRequestedOrientation() != orientation) {
            activity.setRequestedOrientation(orientation);
        }
    }

    static void enterFullscreenVideo(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        requestFullscreen(activity, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    static void enterSensorFullscreen(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        requestFullscreen(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private static void requestFullscreen(Activity activity, int orientation) {
        FULLSCREEN_ACTIVITIES.put(activity, orientation);
        PORTRAIT_LOCKED_ACTIVITIES.remove(activity);
        if (activity.getRequestedOrientation() != orientation) {
            activity.setRequestedOrientation(orientation);
        }
    }

    static void exitFullscreenVideo(Activity activity) {
        if (activity == null) return;
        FULLSCREEN_ACTIVITIES.remove(activity);
        if (isPhoneSized(activity)) PORTRAIT_LOCKED_ACTIVITIES.add(activity);
        else PORTRAIT_LOCKED_ACTIVITIES.remove(activity);
        int orientation = isPhoneSized(activity)
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (activity.getRequestedOrientation() != orientation) {
            activity.setRequestedOrientation(orientation);
        }
    }

    static void onActivityDestroyed(Activity activity) {
        FULLSCREEN_ACTIVITIES.remove(activity);
        PORTRAIT_LOCKED_ACTIVITIES.remove(activity);
    }

    static boolean isPhoneSized(Activity activity) {
        return isPhoneSized(activity.getResources().getConfiguration());
    }

    static boolean isPhoneSized(Configuration config) {
        if (config == null) return true;
        // screenWidthDp changes when a phone rotates and can exceed 600dp in landscape.
        // smallestScreenWidthDp is rotation-stable, so use it to decide whether normal
        // browsing should return to the phone portrait lock after fullscreen video.
        if (config.smallestScreenWidthDp > 0) {
            return config.smallestScreenWidthDp < LARGE_SCREEN_MIN_WIDTH_DP;
        }
        return config.screenWidthDp < LARGE_SCREEN_MIN_WIDTH_DP;
    }
}
