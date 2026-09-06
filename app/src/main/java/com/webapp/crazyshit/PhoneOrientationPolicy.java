package com.webapp.crazyshit;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps normal browsing portrait on phones while leaving large-screen layouts adaptive.
 * Full-screen video temporarily opts out without recreating the browsing task.
 */
final class PhoneOrientationPolicy {
    private static final int LARGE_SCREEN_MIN_WIDTH_DP = 600;
    private static final Set<Activity> FULLSCREEN_ACTIVITIES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private PhoneOrientationPolicy() {
    }

    static void applyBrowsingOrientation(Activity activity) {
        if (activity == null || activity.isFinishing() || activity instanceof PlayerActivity) return;
        if (!isPhoneSized(activity)) return;

        int orientation = FULLSCREEN_ACTIVITIES.contains(activity)
                ? ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (activity.getRequestedOrientation() != orientation) {
            activity.setRequestedOrientation(orientation);
        }
    }

    static void enterFullscreenVideo(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        FULLSCREEN_ACTIVITIES.add(activity);
        if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    static void exitFullscreenVideo(Activity activity) {
        if (activity == null) return;
        FULLSCREEN_ACTIVITIES.remove(activity);
        int orientation = isPhoneSized(activity)
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (activity.getRequestedOrientation() != orientation) {
            activity.setRequestedOrientation(orientation);
        }
    }

    static void onActivityDestroyed(Activity activity) {
        FULLSCREEN_ACTIVITIES.remove(activity);
    }

    static boolean isPhoneSized(Activity activity) {
        Configuration config = activity.getResources().getConfiguration();
        return config.screenWidthDp < LARGE_SCREEN_MIN_WIDTH_DP
                && config.smallestScreenWidthDp < LARGE_SCREEN_MIN_WIDTH_DP;
    }
}
